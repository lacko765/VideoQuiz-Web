package edu.videoquiz;
import edu.videoquiz.core.*;
import static edu.videoquiz.core.Models.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.io.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** SQL/object fixtures test persistence behavior; not a claim of a live Supabase deployment. */
class CloudStoreTests {
    @TempDir Path dir;
    Map<String,byte[]> objects=new HashMap<>();
    boolean failPut;
    CloudStore.Objects transport=new CloudStore.Objects(){
        public void put(String key,byte[] bytes)throws IOException{if(failPut)throw new IOException("fixture outage");objects.put(key,bytes.clone());}
        public byte[] get(String key)throws IOException{if(!objects.containsKey(key))throw new IOException("fixture missing");return objects.get(key);}
    };
    String url="jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
    CloudStore open(String cache)throws Exception{return new CloudStore(dir.resolve(cache),DriverManager.getConnection(url),transport);}
    Video video(CloudStore s)throws Exception{Video v=new Video();v.id=Store.id();v.owner="alice";v.kind="LOCAL";v.size=5;s.initializeMedia(v);s.save(v);return v;}
    @Test void accountHistoryAndChunkedMediaSurviveEmptyCacheRestart()throws Exception{
        String id,attempt;
        try(var s=open("first")){
            s.register("alice","fixture-hash");Video v=video(s);id=v.id;v.quizLanguage="ar";s.save(v);
            s.appendMedia(v,0,new byte[]{1,2});s.appendMedia(v,2,new byte[]{3,4,5});
            Attempt a=new Attempt();a.id=Store.id();a.owner="alice";a.videoId=id;a.answers.put(0,2);s.save(a);attempt=a.id;
        }
        try(var s=open("second")){
            assertEquals("fixture-hash",s.account("alice").passwordHash());
            Video v=s.video(id,"alice");assertEquals(5,s.uploadedBytes(v));
            assertEquals("ar",v.quizLanguage);
            assertArrayEquals(new byte[]{1,2,3,4,5},Files.readAllBytes(s.prepareMedia(v)));
            assertEquals(2,s.attempt(attempt,"alice").answers.get(0));
            assertThrows(NoSuchElementException.class,()->s.video(id,"bob"));
            assertThrows(NoSuchElementException.class,()->s.attempt(attempt,"bob"));
        }
    }
    @Test void objectFailureDoesNotAdvanceUpload()throws Exception{
        try(var s=open("cache")){Video v=video(s);failPut=true;assertThrows(IOException.class,()->s.appendMedia(v,0,new byte[]{1,2}));assertEquals(0,s.video(v.id,"alice").received);failPut=false;s.appendMedia(s.video(v.id,"alice"),0,new byte[]{1,2});assertEquals(2,s.video(v.id,"alice").received);}
    }
    @Test void staleWritesCannotReplaceAnswersOrStartDuplicateJobs()throws Exception{
        try(var a=open("a");var b=open("b")){Video v=video(a);Video stale=b.video(v.id,"alice");v.status="PROCESSING";a.save(v);stale.status="READY";assertThrows(IOException.class,()->b.save(stale));assertEquals("PROCESSING",a.video(v.id,"alice").status);}
    }
    @Test void duplicateChunkTransactionRollsBackAndKeepsCommittedBytes()throws Exception{
        try(var a=open("a");var b=open("b")){Video v=video(a);Video stale=b.video(v.id,"alice");a.appendMedia(v,0,new byte[]{1,2});assertThrows(IOException.class,()->b.appendMedia(stale,0,new byte[]{9,9}));assertArrayEquals(new byte[]{1,2},Files.readAllBytes(a.prepareMedia(a.video(v.id,"alice"))));}
    }
    @Test void recoveryExpiresStaleJobsButLeavesActiveDeploymentJobs()throws Exception{
        try(var s=open("cache")){Video v=video(s);v.status="PROCESSING";v.externalWaitSince=System.currentTimeMillis();s.save(v);s.recover();assertEquals("PROCESSING",s.video(v.id,"alice").status);v.externalWaitSince=System.currentTimeMillis()-721000;s.save(v);s.recover();assertEquals("FAILED",s.video(v.id,"alice").status);}
    }
    @Test void lostDatabaseConnectionCanRecoverWithoutLosingRecords()throws Exception{
        Connection connection=DriverManager.getConnection(url);
        try(var s=new CloudStore(dir.resolve("cache"),connection,transport)){
            s.register("alice","fixture-hash");connection.close();
            s.reconnectIfNeeded(()->DriverManager.getConnection(url));
            assertEquals("fixture-hash",s.account("alice").passwordHash());
        }
    }
}
