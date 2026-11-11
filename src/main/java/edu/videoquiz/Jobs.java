package edu.videoquiz;

import edu.videoquiz.core.*;
import edu.videoquiz.core.Models.Video;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.io.*;

@Service
public class Jobs {
    @org.springframework.beans.factory.annotation.Value("${videoquiz.analysis-per-user-hour:0}") private int userLimit;
    @org.springframework.beans.factory.annotation.Value("${videoquiz.analysis-per-hour:0}") private int globalLimit;
    private final java.util.Map<String,java.util.ArrayDeque<Long>> requests = new java.util.HashMap<>();
    private final Store store; private final Gemini gemini;
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private final Semaphore slots = new Semaphore(4);
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    public Jobs(Store store, Gemini gemini) { this.store=store; this.gemini=gemini; }
    public void start(String id,String owner,Double duration) throws IOException {
        start(id,owner,duration,null);
    }
    public void start(String id,String owner,Double duration,String language) throws IOException {
        if(language!=null && !language.equals("en") && !language.equals("ar")) throw new IllegalArgumentException("Choose Arabic or English for quiz questions.");
        final String run=Store.id();
        synchronized(store) {
            Video v=store.video(id,owner);
            if(v.status.equals("PROCESSING")) return; // A timed-out POST may already have started this job.
            if (!v.status.equals("UPLOADED") && !v.status.equals("FAILED")) throw new IllegalArgumentException("Only uploaded or failed videos can be analyzed. Completed quizzes are retained in history.");
            if(v.kind.equals("YOUTUBE")) {
                if(duration==null || !Double.isFinite(duration) || duration<=0 || duration>1800) throw new IllegalArgumentException("Wait for YouTube duration metadata before retrying. Use a recorded video up to 30 minutes.");
                v.duration=duration;
            }
            if (v.kind.equals("LOCAL") && (v.received != v.size || v.duration<=0)) throw new IllegalArgumentException("Finish uploading a valid video first.");
            long now=System.currentTimeMillis();
            for(var queue:requests.values()) while(!queue.isEmpty() && queue.peekFirst()<=now-3600000) queue.removeFirst();
            requests.entrySet().removeIf(e -> e.getValue().isEmpty());
            var userRequests=requests.computeIfAbsent(owner,k -> new java.util.ArrayDeque<>());
            int total=requests.values().stream().mapToInt(java.util.Collection::size).sum();
            if((userLimit>0 && userRequests.size()>=userLimit) || (globalLimit>0 && total>=globalLimit))
                throw new IllegalArgumentException("Hourly analysis limit reached. Your video is saved. Please try again in an hour.");
            if (!slots.tryAcquire()) throw new IllegalArgumentException("Analysis queue is full. Your video is saved; retry shortly.");
            if(v.quizLanguage==null) v.quizLanguage=language==null?"en":language;
            v.status="PROCESSING"; v.message="Queued for analysis"; v.externalWaitSince=System.currentTimeMillis();
            v.analysisRunId=run; v.points=new java.util.ArrayList<>(); v.questions=new java.util.ArrayList<>();
            try { store.save(v); } catch(IOException e) { slots.release(); throw e; }
            userRequests.addLast(now);
        }
        FutureTask<Void> task=new FutureTask<>(() -> {
            try {
                Video v=store.video(id,owner);
                gemini.analyze(v,v.kind.equals("LOCAL")?store.prepareMedia(v):store.media(id),message -> {
                    try { synchronized(store) { Video current=store.video(id,owner); if(!current.status.equals("PROCESSING") || !run.equals(current.analysisRunId)) throw new CancellationException(); current.message=message; current.externalWaitSince=System.currentTimeMillis(); store.save(current); } }
                    catch(IOException e) { throw new UncheckedIOException(e); }
                });
                synchronized(store) { Video current=store.video(id,owner); if(current.status.equals("PROCESSING") && run.equals(current.analysisRunId)) { v.revision=current.revision; v.status="READY"; v.message="Analysis complete. Your quiz is ready."; v.externalWaitSince=0; store.save(v); } }
            } catch(Exception e) {
                try { synchronized(store) {
                    Video v=store.video(id,owner); v.status="FAILED"; v.externalWaitSince=0;
                    if(!run.equals(v.analysisRunId)) return null;
                    // A deadline's clearer message must not be replaced by cancellation.
                    if(e instanceof InterruptedException || e instanceof CancellationException) return null;
                    v.message = e instanceof java.net.http.HttpTimeoutException ? "AI request timed out. Your video is retained; retry analysis."
                        : e instanceof java.net.ConnectException ? "Cannot reach the AI service. Check server connectivity and retry. Your video is retained."
                        : e instanceof IllegalArgumentException || e instanceof IOException ? safeMessage(e)
                        : "AI returned an unexpected response. Your video is retained; retry analysis.";
                    store.save(v);
                }} catch(IOException failure) { System.err.println("Could not persist failed analysis status for video " + id); }
            }
            return null;
        }) { @Override protected void done() { slots.release(); } };
        ScheduledFuture<?> timeout=deadlines.schedule(() -> {
            try { synchronized(store) {
                Video v=store.video(id,owner);
                if(v.status.equals("PROCESSING") && run.equals(v.analysisRunId)) {
                    v.status="FAILED"; v.message="Analysis exceeded 12 minutes. Your video is retained. Retry when the service is available."; v.externalWaitSince=0; store.save(v);
                    task.cancel(true);
                }
            }} catch(IOException e) { System.err.println("Could not save analysis timeout status"); }
        },12,TimeUnit.MINUTES);
        try { worker.execute(() -> { try { task.run(); } finally { timeout.cancel(false); } }); }
        catch(RejectedExecutionException e) {
            timeout.cancel(false);task.cancel(false);
            synchronized(store) { Video v=store.video(id,owner); if(run.equals(v.analysisRunId)) { v.status="FAILED";v.message="Server is stopping. Retry analysis after restart.";store.save(v); } }
            throw new IOException("Server is stopping. Retry analysis after restart.");
        }
    }
    private String safeMessage(Exception e) {
        String s=e.getMessage();
        if(s == null || s.length()>500 || s.contains("https://") || s.contains("key=")) return "Video processing failed. Check the format, service connection and server configuration, then retry.";
        return s;
    }
    @PreDestroy public void close() { deadlines.shutdownNow(); worker.shutdownNow(); }
}
