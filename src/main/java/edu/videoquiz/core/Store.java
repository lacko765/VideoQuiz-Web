package edu.videoquiz.core;

import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static edu.videoquiz.core.Models.*;

/** Single-server file repository. Writes use temporary files and atomic replacement. */
public class Store {
    private final Path root;
    private final Gson gson = new Gson();
    public Store(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        for (String dir : List.of("videos", "attempts", "accounts", "media")) Files.createDirectories(this.root.resolve(dir));
    }
    public static String id() { return UUID.randomUUID().toString(); }
    public void initializeMedia(Video v) throws IOException { Files.createFile(media(v.id)); }
    public long uploadedBytes(Video v) throws IOException { return Files.size(media(v.id)); }
    public Path prepareMedia(Video v) throws IOException {return media(v.id);}
    public synchronized void appendMedia(Video v,long offset,byte[] bytes) throws IOException {
        try(RandomAccessFile f=new RandomAccessFile(media(v.id).toFile(),"rw")) {f.seek(offset);f.write(bytes);f.getFD().sync();}
        v.received=offset+bytes.length;v.message="Uploaded "+v.received+" of "+v.size+" bytes";save(v);
    }
    public long usableSpace() throws IOException { return Files.getFileStore(root).getUsableSpace(); }
    public synchronized long accountCount() throws IOException {
        try(var files=Files.list(root.resolve("accounts"))) {return files.filter(p->p.toString().endsWith(".json")).count();}
    }
    private Path path(String type, String id) {
        if (!id.matches("[a-zA-Z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid identifier");
        return root.resolve(type).resolve(id + ".json");
    }
    private <T> T read(String type, String id, Class<T> cls) throws IOException {
        Path p = path(type, id);
        if (!Files.exists(p)) throw new NoSuchElementException("Item not found");
        return gson.fromJson(Files.readString(p), cls);
    }
    private void write(String type, String id, Object value) throws IOException {
        Path p = path(type, id), tmp = Files.createTempFile(p.getParent(), "save-", ".tmp");
        try {
            Files.writeString(tmp, gson.toJson(value));
            try { Files.move(tmp, p, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
    public synchronized void save(Video v) throws IOException { write("videos", v.id, v); }
    public synchronized void save(Attempt a) throws IOException { write("attempts", a.id, a); }
    public synchronized Video video(String id, String owner) throws IOException {
        Video v = read("videos", id, Video.class);
        if (!v.owner.equals(owner)) throw new NoSuchElementException("Item not found");
        return v;
    }
    public synchronized Attempt attempt(String id, String owner) throws IOException {
        Attempt a = read("attempts", id, Attempt.class);
        if (!a.owner.equals(owner)) throw new NoSuchElementException("Item not found");
        return a;
    }
    public synchronized List<Video> videos(String owner) throws IOException {
        List<Video> out = new ArrayList<>();
        try (var files = Files.list(root.resolve("videos"))) {
            for (Path p : files.filter(x -> x.toString().endsWith(".json")).toList()) {
                Video v = gson.fromJson(Files.readString(p), Video.class);
                if (owner == null || owner.equals(v.owner)) out.add(v);
            }
        }
        out.sort(Comparator.comparingLong((Video v) -> v.created).reversed());
        return out;
    }
    public synchronized List<Attempt> attempts(String owner) throws IOException {
        List<Attempt> out = new ArrayList<>();
        try (var files = Files.list(root.resolve("attempts"))) {
            for (Path p : files.filter(x -> x.toString().endsWith(".json")).toList()) {
                Attempt a = gson.fromJson(Files.readString(p), Attempt.class);
                if (owner.equals(a.owner)) out.add(a);
            }
        }
        out.sort(Comparator.comparingLong((Attempt a) -> a.created).reversed());
        return out;
    }
    public synchronized void register(String username, String hash) throws IOException {
        if (!username.matches("[a-z0-9_]{3,32}")) throw new IllegalArgumentException("Use 3–32 lowercase letters, digits or underscores for your username.");
        if (Files.exists(path("accounts", username))) throw new IllegalArgumentException("Username is already registered.");
        write("accounts", username, new Account(username, hash));
    }
    public synchronized Account account(String username) throws IOException { return read("accounts", username, Account.class); }
    public Path media(String id) {
        if (!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid video ID");
        return root.resolve("media").resolve(id + ".mp4");
    }
    public synchronized void recover() throws IOException {
        for (Video v : videos(null)) if (v.status.equals("PROCESSING")) {
            v.status = "FAILED"; v.message = "Server restarted during analysis. Your video is retained; retry analysis.";
            v.externalWaitSince = 0; save(v);
        }
    }
}
