package edu.videoquiz;

import edu.videoquiz.core.*;
import static edu.videoquiz.core.Models.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.*;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api")
public class Api {
    static final long MAX_VIDEO=256L*1024*1024, MAX_USER=1024L*1024*1024;
    static final int CHUNK=1024*1024;
    private final Store store; private final Jobs jobs; private final PasswordEncoder passwords; private final Gemini gemini;
    @org.springframework.beans.factory.annotation.Value("${videoquiz.total-storage-bytes:0}") private long totalStorage;
    @org.springframework.beans.factory.annotation.Value("${videoquiz.storage-reserve-bytes:0}") private long reserve;
    @org.springframework.beans.factory.annotation.Value("${videoquiz.max-accounts:0}") private long maxAccounts;
    public Api(Store store,Jobs jobs,PasswordEncoder passwords,Gemini gemini) { this.store=store; this.jobs=jobs; this.passwords=passwords; this.gemini=gemini; }
    public record Credentials(String username,String password) {}
    public record Upload(String name,long size,String sha256) {}
    public record Link(String url) {}
    public record Selection(Integer questionIndex,Integer optionIndex) {}
    public record Analysis(Double durationSeconds,String language) {}

    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"header",token.getHeaderName()); }
    @PostMapping("/register") public Map<String,Boolean> register(@RequestBody Credentials c) throws IOException {
        if(c.username()==null || !c.username().matches("[a-z0-9_]{3,32}") || c.password()==null || c.password().length()<10 || c.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Use a 3–32 character lowercase username and a password of at least 10 characters (maximum 72 UTF-8 bytes).");
        String hash=passwords.encode(c.password());
        synchronized(store) {
            if(maxAccounts>0 && store.accountCount()>=maxAccounts) throw new IllegalArgumentException("This demonstration has reached its account limit. Contact the website owner.");
            store.register(c.username(),hash);
        }
        return Map.of("ok",true);
    }
    @GetMapping("/me") public Map<String,Object> me(Principal p) { return Map.of("username",p.getName(),"aiConfigured",gemini.configured()); }
    private void quota(String owner,long newSize) throws IOException {
        List<Video> videos=store.videos(owner);
        if(videos.size()>=30 || videos.stream().mapToLong(v -> v.size).sum()+newSize>MAX_USER) throw new IllegalArgumentException("Your storage limit is reached (30 videos / 1 GiB). Ask the server administrator to archive old data.");
        if(totalStorage>0 && store.videos(null).stream().mapToLong(v->v.size).sum()+newSize>totalStorage)
            throw new IllegalArgumentException("The website's shared video storage limit is reached. Try a YouTube link or contact the owner.");
        if(reserve>0 && store.usableSpace()<newSize+reserve) throw new IllegalArgumentException("The server has insufficient free storage. Contact the website owner.");
    }
    @PostMapping("/uploads") public Map<String,Object> begin(@RequestBody Upload input,Principal p) throws IOException {
        if(input.name()==null || input.name().length()>180 || !input.name().toLowerCase(Locale.ROOT).endsWith(".mp4") || input.size()<32 || input.size()>MAX_VIDEO || input.sha256()==null || !input.sha256().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Select an MP4 file between 32 bytes and 256 MiB. H.264 video is required; AAC audio is recommended.");
        synchronized(store) {
            quota(p.getName(),input.size()); Video v=new Video(); v.id=Store.id(); v.owner=p.getName(); v.kind="LOCAL"; v.title=input.name(); v.size=input.size(); v.sha256=input.sha256();
            store.initializeMedia(v); store.save(v); return view(v);
        }
    }
    @PutMapping(value="/uploads/{id}",consumes="application/octet-stream")
    public Map<String,Object> chunk(@PathVariable String id,@RequestParam long offset,HttpServletRequest request,Principal p) throws IOException {
        if(request.getContentLengthLong()>CHUNK) throw new IllegalArgumentException("Upload chunk exceeds 1 MiB.");
        // Authorize before consuming any upload bytes.
        store.video(id,p.getName());
        byte[] bytes=request.getInputStream().readNBytes(CHUNK+1);
        if(bytes.length==0 || bytes.length>CHUNK) throw new IllegalArgumentException("Invalid upload chunk size.");
        synchronized(store) {
            Video v=store.video(id,p.getName());
            if(!v.kind.equals("LOCAL") || !v.status.equals("UPLOADING")) throw new IllegalArgumentException("This upload is no longer writable.");
            long actual=store.uploadedBytes(v);
            if(offset!=actual) throw new IllegalArgumentException("Upload offset changed. Resume from the saved position.");
            if(actual+bytes.length>v.size) throw new IllegalArgumentException("Upload exceeds its declared size.");
            if(reserve>0 && store.usableSpace()<bytes.length+reserve) throw new IllegalArgumentException("Server storage is almost full. Your saved upload chunks are retained; contact the owner.");
            store.appendMedia(v,actual,bytes); return view(v);
        }
    }
    @PostMapping("/uploads/{id}/complete") public Map<String,Object> complete(@PathVariable String id,Principal p) throws IOException {
        synchronized(store) {
            Video v=store.video(id,p.getName());
            if(!v.kind.equals("LOCAL")) throw new IllegalArgumentException("This is not a local upload.");
            if(v.status.equals("UPLOADED")) return view(v); // Retry-safe completion.
            if(!v.status.equals("UPLOADING") || store.uploadedBytes(v)!=v.size) throw new IllegalArgumentException("Upload is incomplete. Reselect the same file to resume.");
            store.prepareMedia(v);
            try {
                var digest=java.security.MessageDigest.getInstance("SHA-256");
                try(InputStream in=Files.newInputStream(store.media(id))) { byte[] buf=new byte[65536]; int n; while((n=in.read(buf))!=-1) digest.update(buf,0,n); }
                if(!java.util.HexFormat.of().formatHex(digest.digest()).equals(v.sha256)) throw new IllegalArgumentException("Uploaded bytes do not match the selected file. Start a new upload with the correct video.");
            } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            v.received=v.size; v.duration=Mp4.inspect(store.media(id)); v.status="UPLOADED"; v.message="Video validated and saved. Ready for analysis."; store.save(v); return view(v);
        }
    }
    @PostMapping("/links") public Map<String,Object> link(@RequestBody Link input,Principal p) throws IOException {
        if(input.url()==null || input.url().length()>2048) throw new IllegalArgumentException("Enter a supported YouTube URL.");
        String youtube=Validation.youtubeId(input.url());
        synchronized(store) {
            quota(p.getName(),0); Video v=new Video(); v.id=Store.id(); v.owner=p.getName(); v.kind="YOUTUBE"; v.youtubeId=youtube;
            v.title="YouTube · "+youtube; v.status="UPLOADED"; v.message="Link format validated. Availability will be checked during analysis and playback."; store.save(v); return view(v);
        }
    }
    @GetMapping("/videos") public List<Map<String,Object>> videos(Principal p) throws IOException {
        List<Map<String,Object>> out=new ArrayList<>(); for(Video v:store.videos(p.getName())) out.add(view(v)); return out;
    }
    @GetMapping("/videos/{id}") public Map<String,Object> video(@PathVariable String id,Principal p) throws IOException { return view(store.video(id,p.getName())); }
    @PostMapping("/videos/{id}/analyze") public Map<String,Object> analyze(@PathVariable String id,@RequestBody(required=false) Analysis input,Principal p) throws IOException { jobs.start(id,p.getName(),input==null?null:input.durationSeconds(),input==null?null:input.language()); return view(store.video(id,p.getName())); }
    @GetMapping("/videos/{id}/media") public ResponseEntity<Resource> media(@PathVariable String id,Principal p) throws IOException {
        Video v=store.video(id,p.getName());
        if(!v.kind.equals("LOCAL") || v.duration<=0 || v.received!=v.size) throw new NoSuchElementException("Playable video not found");
        // Spring MVC handles Range requests for Resource responses, including 206 and 416.
        return ResponseEntity.ok().contentType(MediaType.valueOf("video/mp4")).header("Accept-Ranges","bytes")
            .cacheControl(CacheControl.noStore()).body(new FileSystemResource(store.prepareMedia(v)));
    }
    @PostMapping("/videos/{id}/attempts") public Map<String,Object> newAttempt(@PathVariable String id,Principal p) throws IOException {
        synchronized(store) {
            Video v=store.video(id,p.getName()); if(!v.status.equals("READY") || v.questions.isEmpty()) throw new IllegalArgumentException("Wait for successful analysis before starting a quiz.");
            if(store.attempts(p.getName()).size()>=500) throw new IllegalArgumentException("Attempt limit reached. Ask the administrator to archive older attempts.");
            Attempt a=new Attempt(); a.id=Store.id(); a.videoId=id; a.owner=p.getName(); store.save(a); return attemptView(a,v);
        }
    }
    @GetMapping("/attempts") public List<Map<String,Object>> attempts(Principal p) throws IOException {
        List<Map<String,Object>> out=new ArrayList<>();
        for(Attempt a:store.attempts(p.getName())) { Video v=store.video(a.videoId,p.getName()); int score=Validation.score(a,v),total=v.questions.size(); Map<String,Object> summary=new LinkedHashMap<>();
            summary.put("id",a.id);summary.put("videoId",v.id);summary.put("title",v.title);summary.put("created",a.created);summary.put("score",score);summary.put("answered",a.answers.size());summary.put("total",total);
            summary.put("incorrect",a.answers.size()-score);summary.put("percentage",total==0?0:Math.round(100.0*score/total));summary.put("complete",a.answers.size()==total);out.add(summary); }
        return out;
    }
    @GetMapping("/attempts/{id}") public Map<String,Object> attempt(@PathVariable String id,Principal p) throws IOException {
        Attempt a=store.attempt(id,p.getName()); return attemptView(a,store.video(a.videoId,p.getName()));
    }
    @PostMapping("/attempts/{id}/answers") public Feedback answer(@PathVariable String id,@RequestBody Selection selection,Principal p) throws IOException {
        if(selection.questionIndex()==null || selection.optionIndex()==null) throw new IllegalArgumentException("Select a question and an answer option.");
        synchronized(store) {
            Attempt a=store.attempt(id,p.getName()); Video v=store.video(a.videoId,p.getName());
            Feedback feedback=Validation.answer(a,v,selection.questionIndex(),selection.optionIndex()); store.save(a); return feedback;
        }
    }
    private Map<String,Object> view(Video v) throws IOException {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",v.id); out.put("title",v.title); out.put("kind",v.kind); out.put("youtubeId",v.youtubeId);
        out.put("status",v.status); out.put("duration",v.duration); out.put("size",v.size); out.put("sha256",v.sha256); out.put("received",v.kind.equals("LOCAL") ? store.uploadedBytes(v) : 0);
        out.put("created",v.created); out.put("message",v.message);
        out.put("quizLanguage",v.quizLanguage==null?"en":v.quizLanguage);
        boolean waiting=v.status.equals("PROCESSING") && v.externalWaitSince>0 && System.currentTimeMillis()-v.externalWaitSince>10000;
        out.put("externalWait",waiting);
        out.put("notice",waiting ? "The external service has not finished responding for over 10 seconds; it may be slow or unreachable. Your saved upload is retained." : "");
        List<Map<String,Object>> questions=new ArrayList<>();
        for(int i=0;i<v.questions.size();i++) { Question q=v.questions.get(i); questions.add(Map.of("index",i,"question",q.question(),"options",q.options(),"timestamp",q.timestamp())); }
        out.put("questions",questions); return out;
    }
    private Map<String,Object> attemptView(Attempt a,Video v) throws IOException {
        List<Map<String,Object>> review=new ArrayList<>();
        for(var entry:a.answers.entrySet()) {
            Question q=v.questions.get(entry.getKey());
            review.add(Map.of("questionIndex",entry.getKey(),"selectedIndex",entry.getValue(),"correctIndex",q.correctIndex(),"explanation",q.explanation(),"evidence",v.points.get(q.pointIndex())));
        }
        return Map.of("id",a.id,"video",view(v),"answers",a.answers,"review",review,"score",Validation.score(a,v),"total",v.questions.size(),"complete",a.answers.size()==v.questions.size());
    }
}
