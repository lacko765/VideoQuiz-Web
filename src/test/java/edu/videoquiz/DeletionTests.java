package edu.videoquiz;

import edu.videoquiz.core.*;
import static edu.videoquiz.core.Models.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Delete ownership, cascading history and eventual private object cleanup. */
class DeletionTests {
    @TempDir Path dir;
    Video make(Store s,String owner,String kind) throws Exception {
        Video v=new Video();v.id=Store.id();v.owner=owner;v.kind=kind;v.title="fixture";v.status="FAILED";v.size=4;
        s.save(v);return v;
    }
    Attempt attempt(Store s,Video v) throws Exception {
        Attempt a=new Attempt();a.id=Store.id();a.owner=v.owner;a.videoId=v.id;s.save(a);return a;
    }
    @Test void localDeletionIsOwnerScopedAndCascadesOnlyRelatedAttempts() throws Exception {
        Store s=new Store(dir);Video a=make(s,"alice","LOCAL");Files.write(s.media(a.id),new byte[4]);
        Attempt aAttempt=attempt(s,a);Video b=make(s,"bob","YOUTUBE");Attempt bAttempt=attempt(s,b);
        assertThrows(NoSuchElementException.class,()->s.deleteVideo(a.id,"bob"));
        assertTrue(Files.exists(s.media(a.id)));
        s.deleteVideo(a.id,"alice");
        assertThrows(NoSuchElementException.class,()->s.video(a.id,"alice"));
        assertThrows(NoSuchElementException.class,()->s.attempt(aAttempt.id,"alice"));
        assertFalse(Files.exists(s.media(a.id)));
        assertEquals(b.id,s.video(b.id,"bob").id);
        assertEquals(bAttempt.id,s.attempt(bAttempt.id,"bob").id);
    }
    @Test void processingVideoCannotBeDeleted() throws Exception {
        Store s=new Store(dir);Video v=make(s,"alice","YOUTUBE");v.status="PROCESSING";s.save(v);
        assertThrows(IllegalArgumentException.class,()->s.deleteVideo(v.id,"alice"));
    }
    @Test void cloudDeletionKeepsPendingObjectsUntilSupabaseRecovers() throws Exception {
        String url="jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        Map<String,byte[]> objects=new HashMap<>();boolean[] down={true};
        CloudStore.Objects transport=new CloudStore.Objects(){
            public void put(String key,byte[] bytes){objects.put(key,bytes);}
            public byte[] get(String key){return objects.get(key);}
            public void remove(List<String> keys)throws IOException{if(down[0])throw new IOException("storage offline");for(String k:keys)objects.remove(k);}
        };
        try(var store=new CloudStore(dir.resolve("cloud"),DriverManager.getConnection(url),transport)){
            Video video=make(store,"alice","LOCAL");store.appendMedia(video,0,new byte[]{1,2,3,4});
            Attempt a=attempt(store,video);assertEquals(1,objects.size());
            store.deleteVideo(video.id,"alice");
            assertThrows(NoSuchElementException.class,()->store.video(video.id,"alice"));
            assertThrows(NoSuchElementException.class,()->store.attempt(a.id,"alice"));
            assertEquals(1,objects.size(),"Failed cleanup must stay queued for retry");
            down[0]=false;store.recover();assertEquals(0,objects.size());
            try(var q=DriverManager.getConnection(url);var st=q.createStatement();var rs=st.executeQuery("SELECT COUNT(*) FROM videoquiz_private.deletion_queue")){
                assertTrue(rs.next());assertEquals(0,rs.getInt(1));
            }
        }
    }
}
