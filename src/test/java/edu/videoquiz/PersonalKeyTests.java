package edu.videoquiz;

import edu.videoquiz.core.*;
import edu.videoquiz.core.Models.Video;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalKeyTests {
    @TempDir Path dir;
    static final String MASTER=Base64.getEncoder().encodeToString(new byte[32]); // Isolated fixture only.
    static final String ALICE="fixture-personal-key-alice", BOB="fixture-personal-key-bob";
    static HttpResponse<String> response(int status,String body) {
        @SuppressWarnings("unchecked") HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(body);return response;
    }
    static Gemini verifier(String key,int status) {
        return new Gemini(key,"fixture-model") {
            @Override protected HttpResponse<String> exchange(HttpRequest request) {
                assertEquals("GET",request.method());assertEquals("https://generativelanguage.googleapis.com/v1beta/models/fixture-model",request.uri().toString());
                assertFalse(request.uri().toString().contains(key));
                assertEquals(key,request.headers().firstValue("x-goog-api-key").orElseThrow());
                return response(status,status==200?"{\"name\":\"models/fixture-model\",\"supportedGenerationMethods\":[\"generateContent\"]}":"provider error that must not be surfaced");
            }
        };
    }
    @Test void encryptionIsRandomAuthenticatedAndBoundToOwner() {
        var cipher=new KeyCipher(MASTER);String first=cipher.encrypt("alice",ALICE),second=cipher.encrypt("alice",ALICE);
        assertNotEquals(first,second);assertFalse(first.contains(ALICE));assertEquals(ALICE,cipher.decrypt("alice",first));
        assertThrows(IllegalStateException.class,()->cipher.decrypt("bob",first));
        byte[] altered=Base64.getDecoder().decode(first.substring(3));altered[15]^=1;
        assertThrows(IllegalStateException.class,()->cipher.decrypt("alice","v1."+Base64.getEncoder().encodeToString(altered)));
        assertThrows(IllegalStateException.class,()->new KeyCipher(Base64.getEncoder().encodeToString(new byte[16])));
    }
    @Test void verificationUsesMetadataAndClassifiesProviderFailuresWithoutLeaking() throws Exception {
        verifier(ALICE,200).verifyKey();
        for(var entry:Map.of(400,"VERIFICATION_REJECTED",401,"AUTHENTICATION_FAILED",403,"PERMISSION_DENIED",404,"MODEL_UNAVAILABLE",429,"QUOTA_EXCEEDED",503,"SERVICE_UNAVAILABLE").entrySet()) {
            var error=assertThrows(Gemini.KeyFailure.class,()->verifier(ALICE,entry.getKey()).verifyKey());
            assertEquals(entry.getValue(),error.code);assertFalse(error.getMessage().contains(ALICE));assertFalse(error.getMessage().contains("provider error"));
        }
        Gemini offline=new Gemini(ALICE,"fixture-model") {
            protected HttpResponse<String> exchange(HttpRequest r) throws IOException {throw new IOException(ALICE);}
        };
        var failure=assertThrows(Gemini.KeyFailure.class,offline::verifyKey);
        assertEquals("NETWORK_ERROR",failure.code);assertNull(failure.getCause());assertFalse(failure.toString().contains(ALICE));
    }
    @Test void localKeysSurviveRestartReplaceAndRemoveWithoutChangingLegacyData() throws Exception {
        Store store=new Store(dir);store.register("alice","legacy-bcrypt-hash");store.register("bob","other-hash");
        var clients=mock(GeminiClients.class);when(clients.forKey(anyString())).thenAnswer(i->verifier(i.getArgument(0),200));
        var keys=new PersonalKeys(store,clients,MASTER);
        Video quiz=video(store,"alice");quiz.status="READY";
        quiz.questions=List.of(new edu.videoquiz.core.Models.Question("Saved question",List.of("A","B","C","D"),1,"Saved explanation",0,2));store.save(quiz);
        var attempt=new edu.videoquiz.core.Models.Attempt();attempt.id=Store.id();attempt.owner="alice";attempt.videoId=quiz.id;attempt.answers.put(0,1);store.save(attempt);
        assertThrows(Gemini.KeyFailure.class,()->keys.forAnalysis("alice"));
        keys.save("alice",ALICE);keys.save("bob",BOB);
        assertFalse(Files.readString(dir.resolve("gemini_keys/alice.json")).contains(ALICE));
        Store restarted=new Store(dir);var cipher=new KeyCipher(MASTER);
        assertEquals(ALICE,cipher.decrypt("alice",restarted.geminiKey("alice").encrypted));
        assertEquals(BOB,cipher.decrypt("bob",restarted.geminiKey("bob").encrypted));
        keys.save("alice",ALICE+"-replacement");assertEquals(ALICE+"-replacement",cipher.decrypt("alice",store.geminiKey("alice").encrypted));
        when(clients.forKey("fixture-invalid-key-value")).thenReturn(verifier("fixture-invalid-key-value",400));
        assertThrows(Gemini.KeyFailure.class,()->keys.save("alice","fixture-invalid-key-value"));
        assertEquals(ALICE+"-replacement",cipher.decrypt("alice",store.geminiKey("alice").encrypted));
        keys.remove("alice");assertFalse(keys.status("alice").configured());assertTrue(keys.status("bob").configured());
        assertThrows(Gemini.KeyFailure.class,()->keys.forAnalysis("alice"));
        assertEquals("READY",restarted.video(quiz.id,"alice").status);assertEquals("legacy-bcrypt-hash",restarted.account("alice").passwordHash());
        assertEquals(1,Validation.score(restarted.attempt(attempt.id,"alice"),restarted.video(quiz.id,"alice")));
        assertEquals("Saved explanation",restarted.video(quiz.id,"alice").questions.get(0).explanation());
        assertFalse(new PersonalKeys(store,clients,"").status("alice").available());
        assertThrows(Gemini.KeyFailure.class,()->new PersonalKeys(store,clients,"").save("alice",ALICE));
    }
    static Video video(Store store,String owner) throws Exception {
        Video v=new Video();v.id=Store.id();v.owner=owner;v.kind="YOUTUBE";v.youtubeId="tVHOBVAFjUw";v.status="FAILED";store.save(v);return v;
    }
    static void awaitStatus(Store store,Video video,String status) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<end) {if(status.equals(store.video(video.id,video.owner).status))return;Thread.sleep(10);}
        fail("Job did not reach expected state: "+status);
    }
    @Test void concurrentJobsAndRetryUseOnlyTheirOwnersCurrentKeys() throws Exception {
        Store store=new Store(dir);store.register("alice","legacy-hash");store.register("bob","legacy-hash");
        var cipher=new KeyCipher(MASTER);store.saveGeminiKey("alice",cipher.encrypt("alice",ALICE));store.saveGeminiKey("bob",cipher.encrypt("bob",BOB));
        var clients=mock(GeminiClients.class);CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1);
        Map<String,List<String>> used=new ConcurrentHashMap<>();
        when(clients.forKey(anyString())).thenAnswer(invocation->{String key=invocation.getArgument(0);return new Gemini(key,"fixture-model") {
            @Override public void analyze(Video v,Path local,java.util.function.Consumer<String> status) throws Exception {
                used.computeIfAbsent(v.owner,k->new CopyOnWriteArrayList<>()).add(key);entered.countDown();release.await(5,TimeUnit.SECONDS);
                if(key.equals(ALICE))throw new IOException("Fixture failure");
            }
        };});
        var keys=new PersonalKeys(store,clients,MASTER);Video a=video(store,"alice"),b=video(store,"bob"),missing=video(store,"without_key");
        try(JobsClose jobs=new JobsClose(new Jobs(store,keys))) {
            assertThrows(Gemini.KeyFailure.class,()->jobs.value.start(missing.id,missing.owner,20.0));
            assertEquals("FAILED",store.video(missing.id,missing.owner).status);
            jobs.value.start(a.id,"alice",20.0);jobs.value.start(b.id,"bob",20.0);assertTrue(entered.await(5,TimeUnit.SECONDS));release.countDown();
            awaitStatus(store,a,"FAILED");awaitStatus(store,b,"READY");
            store.saveGeminiKey("alice",cipher.encrypt("alice",ALICE+"-new"));jobs.value.start(a.id,"alice",20.0);awaitStatus(store,a,"READY");
            assertEquals(List.of(ALICE,ALICE+"-new"),used.get("alice"));assertEquals(List.of(BOB),used.get("bob"));
            keys.remove("bob");Video next=video(store,"bob");assertThrows(Gemini.KeyFailure.class,()->jobs.value.start(next.id,"bob",20.0));
            assertEquals("READY",store.video(b.id,"bob").status);
        } finally {release.countDown();}
    }
    private record JobsClose(Jobs value) implements AutoCloseable {public void close(){value.close();}}
}
