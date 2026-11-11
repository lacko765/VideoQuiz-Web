package edu.videoquiz.core;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;

/** Service credential stays on the server. No public bucket or signed URL is needed. */
public class SupabaseObjects implements CloudStore.Objects {
    private final String base,key,bucket;
    private final HttpClient client;
    public SupabaseObjects(String base,String key,String bucket) {
        this(base,key,bucket,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    SupabaseObjects(String base,String key,String bucket,HttpClient client) {
        if(!base.matches("https://[a-z0-9-]+\\.supabase\\.co") || key.isBlank() || !bucket.matches("[a-z0-9-]{1,60}"))
            throw new IllegalArgumentException("Set the Supabase project HTTPS URL, server secret and private bucket name.");
        this.base=base;this.key=key;this.bucket=bucket;this.client=client;
    }
    private HttpRequest.Builder request(String path,boolean download) {
        if(!path.matches("[a-f0-9-]{36}/[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid object path");
        return HttpRequest.newBuilder(URI.create(base+"/storage/v1/object/"+(download?"authenticated/":"")+bucket+"/"+path)).timeout(Duration.ofSeconds(45))
            .header("apikey",key).header("Authorization","Bearer "+key);
    }
    public void verifyPrivateBucket() throws IOException {
        var req=HttpRequest.newBuilder(URI.create(base+"/storage/v1/bucket/"+bucket)).timeout(Duration.ofSeconds(30))
            .header("apikey",key).header("Authorization","Bearer "+key).GET().build();
        String body=new String(send(req),java.nio.charset.StandardCharsets.UTF_8);
        try {
            var bucketInfo=com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            if(!bucketInfo.has("public") || bucketInfo.get("public").getAsBoolean())throw new IOException("The VideoQuiz storage bucket must be private.");
        }catch(RuntimeException e){throw new IOException("Cannot verify private storage bucket settings.");}
    }
    private byte[] send(HttpRequest request) throws IOException {
        try {
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var body=response.body()) {
                if(response.statusCode()<200 || response.statusCode()>=300)throw new IOException("Private video storage request failed. Check storage access and quota; retry shortly.");
                byte[] bytes=body.readNBytes(1048577);
                if(bytes.length>1048576)throw new IOException("Invalid stored chunk size.");
                return bytes;
            }
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Storage request interrupted.");}
    }
    @Override public void put(String path,byte[] bytes) throws IOException {send(request(path,false).header("Content-Type","application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build());}
    @Override public byte[] get(String path) throws IOException {return send(request(path,true).GET().build());}
    @Override public void remove(java.util.List<String> paths) throws IOException {
        if(paths.isEmpty())return;
        if(paths.size()>1000 || paths.stream().anyMatch(p->!p.matches("[a-f0-9-]{36}/[a-f0-9-]{36}")))
            throw new IllegalArgumentException("Invalid private object deletion request.");
        // Supabase Storage DELETE /object/{bucket} with JSON {prefixes:[...]}; never delete storage.objects with SQL.
        String payload=new com.google.gson.Gson().toJson(java.util.Map.of("prefixes",paths));
        var req=HttpRequest.newBuilder(URI.create(base+"/storage/v1/object/"+bucket)).timeout(Duration.ofSeconds(45))
            .header("apikey",key).header("Authorization","Bearer "+key)
            .header("Content-Type","application/json")
            .method("DELETE",HttpRequest.BodyPublishers.ofString(payload)).build();
        try {
            var response=client.send(req,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()/100!=2)throw new IOException("Could not delete private video objects. Cleanup is queued for retry.");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Private object deletion interrupted.");}
    }
}
