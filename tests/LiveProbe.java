package edu.videoquiz.core;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
/** Connectivity check only; never prints API keys or provider response bodies. */
public class LiveProbe {
 public static void main(String[] args) {
  String key=System.getenv("GEMINI_API_KEY");
  if(key==null||key.isBlank()){System.out.println("BLOCKED: GEMINI_API_KEY is absent.");return;}
  try {
   var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
   var request=HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/models"))
       .timeout(Duration.ofSeconds(5)).header("x-goog-api-key",key).GET().build();
   int status=client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
   System.out.println("Gemini connectivity HTTP "+status+". This does not test video analysis or question quality.");
  }catch(Exception e){System.out.println("BLOCKED: Gemini connectivity failed ("+e.getClass().getSimpleName()+"). No live video analysis verified.");}
 }
}
