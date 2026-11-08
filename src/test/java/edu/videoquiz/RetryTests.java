package edu.videoquiz;
import edu.videoquiz.core.*;
import edu.videoquiz.core.Models.Video;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Job lifecycle tests only; mocked service outcomes are not live AI verification. */
class RetryTests {
    @TempDir Path dir;
    private Video video(Store store) throws Exception {Video v=new Video();v.id=Store.id();v.owner="test";v.kind="YOUTUBE";v.youtubeId="tVHOBVAFjUw";v.status="FAILED";store.save(v);return v;}
    private Video waitFor(Store s,String id,String state) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<end){Video v=s.video(id,"test");if(v.status.equals(state))return v;Thread.sleep(10);}throw new AssertionError("Timed out waiting for "+state);}
    @Test void failureThenRetryUsesSameVideoAndNewJob() throws Exception {
        Store store=new Store(dir);Video v=video(store);Gemini ai=mock(Gemini.class);
        doThrow(new IllegalArgumentException("Invalid evidence")).doAnswer(invocation->null).when(ai).analyze(any(),any(),any());
        Jobs jobs=new Jobs(store,ai);
        try {
            jobs.start(v.id,"test",218.0);assertEquals("Invalid evidence",waitFor(store,v.id,"FAILED").message);
            String run=store.video(v.id,"test").analysisRunId;
            jobs.start(v.id,"test",218.0);Video ready=waitFor(store,v.id,"READY");
            assertNotEquals(run,ready.analysisRunId);assertEquals(v.youtubeId,ready.youtubeId);assertEquals(218,ready.duration);
            verify(ai,times(2)).analyze(any(),any(),any());
        } finally {jobs.close();}
    }
    @Test void duplicatePostsDoNotStartDuplicateWork() throws Exception {
        Store store=new Store(dir);Video v=video(store);Gemini ai=mock(Gemini.class);CountDownLatch gate=new CountDownLatch(1);
        doAnswer(invocation->{gate.await(5,TimeUnit.SECONDS);return null;}).when(ai).analyze(any(),any(),any());
        Jobs jobs=new Jobs(store,ai);
        try {jobs.start(v.id,"test",218.0);String run=store.video(v.id,"test").analysisRunId;jobs.start(v.id,"test",218.0);assertEquals(run,store.video(v.id,"test").analysisRunId);gate.countDown();waitFor(store,v.id,"READY");verify(ai,times(1)).analyze(any(),any(),any());}
        finally {gate.countDown();jobs.close();}
    }
    @Test void unknownYouTubeDurationDoesNotQueueWork() throws Exception {
        Store store=new Store(dir);Video v=video(store);Gemini ai=mock(Gemini.class);Jobs jobs=new Jobs(store,ai);
        try {assertThrows(IllegalArgumentException.class,()->jobs.start(v.id,"test",null));assertThrows(IllegalArgumentException.class,()->jobs.start(v.id,"test",0.0));assertEquals("FAILED",store.video(v.id,"test").status);verifyNoInteractions(ai);}
        finally {jobs.close();}
    }
}
