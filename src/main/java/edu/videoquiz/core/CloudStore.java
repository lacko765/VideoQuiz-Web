package edu.videoquiz.core;

import com.google.gson.Gson;
import static edu.videoquiz.core.Models.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Durable SQL metadata and private chunk objects; filesystem is only a reconstructable cache. */
public class CloudStore extends Store implements AutoCloseable {
    public interface Objects {
        void put(String key,byte[] bytes) throws IOException;
        byte[] get(String key) throws IOException;
    }
    private Connection db;
    private final Objects objects;
    private final Gson gson=new Gson();
    public CloudStore(Path cache,Connection db,Objects objects) throws IOException {
        super(cache);this.db=db;this.objects=objects;
        try(var statement=db.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS videoquiz_private");
            statement.execute("CREATE TABLE IF NOT EXISTS videoquiz_private.records (kind VARCHAR(12) NOT NULL, id VARCHAR(80) NOT NULL, owner VARCHAR(80) NOT NULL, body TEXT NOT NULL, revision BIGINT NOT NULL, PRIMARY KEY(kind,id))");
            statement.execute("CREATE TABLE IF NOT EXISTS videoquiz_private.chunks (video_id VARCHAR(80) NOT NULL, position BIGINT NOT NULL, object_key VARCHAR(160) NOT NULL, size INTEGER NOT NULL, PRIMARY KEY(video_id,position))");
        } catch(SQLException e) {throw failure();}
    }
    private IOException failure() {return new IOException("Cloud storage is unavailable. Check the database and private bucket settings; retry shortly.");}
    public interface ConnectionFactory {Connection open() throws SQLException;}
    public synchronized void reconnectIfNeeded(ConnectionFactory factory) throws IOException {
        try {
            if(!db.isClosed() && db.isValid(5))return;
        }catch(SQLException ignored){}
        try{db.close();}catch(SQLException ignored){}
        try{db=factory.open();}catch(SQLException e){throw failure();}
    }
    private void validate(String id) {if(id==null || !id.matches("[a-zA-Z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid identifier");}
    private long write(String kind,String id,String owner,Object value,long revision) throws IOException {
        validate(id);
        String sql=revision==0 ? "INSERT INTO videoquiz_private.records(kind,id,owner,body,revision) VALUES(?,?,?,?,1)"
            : "UPDATE videoquiz_private.records SET body=?,revision=revision+1 WHERE kind=? AND id=? AND owner=? AND revision=?";
        try(var p=db.prepareStatement(sql)) {
            if(revision==0) {p.setString(1,kind);p.setString(2,id);p.setString(3,owner);p.setString(4,gson.toJson(value));}
            else {p.setString(1,gson.toJson(value));p.setString(2,kind);p.setString(3,id);p.setString(4,owner);p.setLong(5,revision);}
            if(p.executeUpdate()!=1) throw new IOException("Saved state changed. Refresh and retry the operation.");
            return revision+1;
        } catch(SQLException e) {throw failure();}
    }
    private <T> T read(String kind,String id,String owner,Class<T> type) throws IOException {
        validate(id);
        try(var p=db.prepareStatement("SELECT body,revision FROM videoquiz_private.records WHERE kind=? AND id=? AND owner=?")) {
            p.setString(1,kind);p.setString(2,id);p.setString(3,owner);
            try(var rows=p.executeQuery()) {if(!rows.next()) throw new NoSuchElementException("Item not found");return decode(rows,type);}
        } catch(SQLException e) {throw failure();}
    }
    private <T> T decode(ResultSet rows,Class<T> type) throws SQLException {
        T value=gson.fromJson(rows.getString(1),type);
        if(value instanceof Video v) v.revision=rows.getLong(2);
        if(value instanceof Attempt a) a.revision=rows.getLong(2);
        return value;
    }
    private <T> List<T> list(String kind,String owner,Class<T> type) throws IOException {
        List<T> result=new ArrayList<>();
        try(var p=db.prepareStatement("SELECT body,revision FROM videoquiz_private.records WHERE kind=?"+(owner==null?"":" AND owner=?"))) {
            p.setString(1,kind);if(owner!=null)p.setString(2,owner);
            try(var rows=p.executeQuery()){while(rows.next())result.add(decode(rows,type));}
            return result;
        } catch(SQLException e){throw failure();}
    }
    @Override public synchronized void save(Video v) throws IOException {v.revision=write("videos",v.id,v.owner,v,v.revision);}
    @Override public synchronized void save(Attempt a) throws IOException {a.revision=write("attempts",a.id,a.owner,a,a.revision);}
    @Override public synchronized Video video(String id,String owner) throws IOException {return read("videos",id,owner,Video.class);}
    @Override public synchronized Attempt attempt(String id,String owner) throws IOException {return read("attempts",id,owner,Attempt.class);}
    @Override public synchronized List<Video> videos(String owner) throws IOException {var list=list("videos",owner,Video.class);list.sort(Comparator.comparingLong((Video v)->v.created).reversed());return list;}
    @Override public synchronized List<Attempt> attempts(String owner) throws IOException {var list=list("attempts",owner,Attempt.class);list.sort(Comparator.comparingLong((Attempt a)->a.created).reversed());return list;}
    @Override public synchronized long accountCount() throws IOException {
        try(var p=db.createStatement();var rows=p.executeQuery("SELECT COUNT(*) FROM videoquiz_private.records WHERE kind='accounts'")){rows.next();return rows.getLong(1);}catch(SQLException e){throw failure();}
    }
    @Override public synchronized Account account(String username) throws IOException {return read("accounts",username,username,Account.class);}
    @Override public synchronized void register(String username,String hash) throws IOException {
        if(!username.matches("[a-z0-9_]{3,32}")) throw new IllegalArgumentException("Invalid username");
        try {account(username);throw new IllegalArgumentException("Username is already registered.");}catch(NoSuchElementException ignored){}
        write("accounts",username,username,new Account(username,hash),0);
    }
    @Override public void initializeMedia(Video v) { /* No ephemeral file required to reserve an upload. */ }
    @Override public long uploadedBytes(Video v) {return v.received;}
    @Override public synchronized void appendMedia(Video v,long offset,byte[] bytes) throws IOException {
        if(offset!=v.received || bytes.length==0 || bytes.length>1048576 || offset+bytes.length>v.size) throw new IllegalArgumentException("Upload offset changed. Resume from the saved position.");
        String key=v.id+"/"+id(); // Immutable object: concurrent or uncertain retries cannot overwrite committed bytes.
        objects.put(key,bytes);
        long oldRevision=v.revision;
        try {
            db.setAutoCommit(false);
            try(var p=db.prepareStatement("INSERT INTO videoquiz_private.chunks(video_id,position,object_key,size) VALUES(?,?,?,?)")) {
                p.setString(1,v.id);p.setLong(2,offset);p.setString(3,key);p.setInt(4,bytes.length);p.executeUpdate();
            }
            v.received=offset+bytes.length;v.message="Uploaded "+v.received+" of "+v.size+" bytes";save(v);
            db.commit();
        } catch(SQLException|IOException e) {
            try{db.rollback();}catch(SQLException ignored){}
            v.revision=oldRevision;v.received=offset;throw failure();
        } finally {try{db.setAutoCommit(true);}catch(SQLException e){throw failure();}}
        // An interrupted transaction may leave an unreferenced private object. Never delete on an uncertain commit.
    }
    @Override public synchronized Path prepareMedia(Video v) throws IOException {
        Path target=media(v.id);
        if(Files.exists(target) && Files.size(target)==v.received)return target;
        if(usableSpace()<v.received+67108864)throw new IOException("Temporary media cache is full.");
        Path tmp=Files.createTempFile(target.getParent(),"download-",".tmp");
        try {
            long position=0;
            try(var out=Files.newOutputStream(tmp);var p=db.prepareStatement("SELECT position,object_key,size FROM videoquiz_private.chunks WHERE video_id=? ORDER BY position")) {
                p.setString(1,v.id);
                try(var rows=p.executeQuery()) {
                    while(rows.next()) {
                        if(rows.getLong(1)!=position)throw failure();
                        byte[] bytes=objects.get(rows.getString(2));
                        if(bytes.length!=rows.getInt(3) || position+bytes.length>v.received)throw failure();
                        out.write(bytes);position+=bytes.length;
                    }
                }
            }
            if(position!=v.received)throw failure();
            Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);
            return target;
        }catch(SQLException e){throw failure();}finally{Files.deleteIfExists(tmp);}
    }
    @Override public synchronized void recover() throws IOException {
        // During a rolling deploy the old server may still own active jobs. Only expire stale jobs.
        for(Video v:videos(null))if("PROCESSING".equals(v.status) && System.currentTimeMillis()-v.externalWaitSince>720000) {
            v.status="FAILED";v.message="Analysis was interrupted. Your video is saved; retry analysis.";v.externalWaitSince=0;save(v);
        }
    }
    @Override public synchronized void close() throws SQLException {db.close();}
}
