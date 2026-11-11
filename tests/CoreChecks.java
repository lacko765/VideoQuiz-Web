package edu.videoquiz.core;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.*;
import java.io.*;
import java.util.*;
import static edu.videoquiz.core.Models.*;

/** Offline tests only. All quiz/video fixtures here are synthetic, not AI output or playable media. */
public class CoreChecks {
    private static int passed;
    private interface Task { void run() throws Exception; }
    private static void check(boolean ok,String name) { if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name); }
    private static void rejects(Task task,String name) throws Exception {try{task.run();}catch(IllegalArgumentException|NoSuchElementException|IOException e){check(true,name);return;}throw new AssertionError(name);}
    private static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    public static void main(String[] args) throws Exception {
        String id="9hE5-98ZeCg";
        for(String url:List.of("https://www.youtube.com/watch?v="+id,"https://youtu.be/"+id,"https://youtube.com/shorts/"+id,"https://m.youtube.com/embed/"+id))check(Validation.youtubeId(url).equals(id),"valid YouTube URL "+url);
        for(String bad:List.of("http://youtube.com/watch?v="+id,"https://youtube.com.evil.test/watch?v="+id,"https://youtube.com@evil.test/watch?v="+id,"https://127.0.0.1/v.mp4","file:///etc/passwd","https://youtu.be/short","https://youtube.com/watch?v="+id+"&v="+id,"https://youtube.com:443/watch?v="+id,"https://youtu.be/"+id+"#x","not a URL"))rejects(()->Validation.youtubeId(bad),"reject unsupported URL "+bad);
        var points=Validation.points(json("{\"points\":[{\"start\":1,\"end\":4,\"summary\":\"A fact\",\"evidence\":\"Source sentence\"},{\"start\":6,\"end\":9,\"summary\":\"Another fact\",\"evidence\":\"Second source sentence\"}]}"),10);
        check(points.size()==2,"valid evidence extraction");
        rejects(()->Validation.points(json("{\"points\":[]}"),10),"reject empty evidence");
        rejects(()->Validation.points(json("{\"points\":[{\"start\":0,\"end\":11,\"summary\":\"x\",\"evidence\":\"x\"}]}"),10),"reject out-of-range timestamp");
        rejects(()->Validation.points(json("{\"points\":[{\"start\":-1,\"end\":2,\"summary\":\"x\",\"evidence\":\"x\"}]}"),10),"reject negative timestamp");
        rejects(()->Validation.points(json("{\"points\":[{\"start\":2,\"end\":1,\"summary\":\"x\",\"evidence\":\"x\"}]}"),10),"reject reversed interval");
        String question="{\"questions\":[{\"question\":\"Test one?\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"correctIndex\":1,\"explanation\":\"Source explanation\",\"pointIndex\":0}]}";
        var questions=Validation.questions(json(question),points);
        check(questions.get(0).timestamp()==4,"question timestamp derives from evidence end");
        rejects(()->Validation.questions(json(question.replace("\"correctIndex\":1","\"correctIndex\":4")),points),"reject invalid correct answer");
        rejects(()->Validation.questions(json(question.replace("\"pointIndex\":0","\"pointIndex\":0.5")),points),"reject fractional evidence index");
        rejects(()->Validation.questions(json(question.replace("\"pointIndex\":0","\"pointIndex\":9")),points),"reject missing evidence reference");
        rejects(()->Validation.questions(json(question.replace("\"B\"","\"A\"")),points),"reject duplicate options");
        rejects(()->Validation.questions(json(question.replace(",\"D\"","")),points),"reject missing option");
        rejects(()->Validation.questions(json(question.replace("Test one?","")),points),"reject blank question");
        String two="{\"questions\":["+json(question).getAsJsonArray("questions").get(0)+","+json(question).getAsJsonArray("questions").get(0)+"]}";
        rejects(()->Validation.questions(json(two),points),"reject duplicate question/evidence");
        Video v=new Video();v.id=Store.id();v.owner="alice";v.title="Fixture";v.kind="YOUTUBE";v.youtubeId=id;v.questions=questions;v.points=points;v.status="READY";
        Attempt a=new Attempt();a.id=Store.id();a.owner="alice";a.videoId=v.id;
        check(Validation.answer(a,v,0,1).score()==1,"correct answer scores one");
        check(Validation.answer(a,v,0,2).selectedIndex()==1 && Validation.score(a,v)==1,"duplicate answer cannot alter result");
        Attempt wrong=new Attempt();check(Validation.answer(wrong,v,0,2).score()==0,"wrong answer scores zero");
        rejects(()->Validation.answer(a,v,1,0),"reject question outside quiz");
        rejects(()->Validation.answer(a,v,0,4),"reject option outside range");
        Path temp=Files.createTempDirectory("videoquiz-checks-");Store store=new Store(temp);store.save(v);store.save(a);
        check(new Store(temp).video(v.id,"alice").questions.size()==1,"video persists across repository reload");
        check(new Store(temp).attempt(a.id,"alice").answers.get(0)==1,"answer persists across repository reload");
        rejects(()->store.video(v.id,"bob"),"video ownership isolation");
        rejects(()->store.attempt(a.id,"bob"),"attempt ownership isolation");
        check(store.videos("bob").isEmpty(),"history isolation");
        rejects(()->store.video("../escape","alice"),"reject path traversal");
        store.register("alice","test-hash");check(store.account("alice").passwordHash().equals("test-hash"),"account persistence");
        rejects(()->store.register("alice","new"),"duplicate registration rejected");
        rejects(()->store.register("../bad","hash"),"invalid username rejected");
        v.status="PROCESSING";store.save(v);store.recover();check(store.video(v.id,"alice").status.equals("FAILED"),"interrupted analysis becomes retryable on restart");
        Path mp4=temp.resolve("container.mp4");Files.write(mp4,fixture());check(Mp4.inspect(mp4)==10,"MP4 container duration parsed");
        Files.write(mp4,new byte[100]);rejects(()->Mp4.inspect(mp4),"reject renamed non-video bytes");
        Files.write(mp4,Arrays.copyOf(fixture(),fixture().length-1));rejects(()->Mp4.inspect(mp4),"reject truncated MP4");
        check(!new Gemini("","gemini-3.5-flash-lite").configured(),"missing key recognized");
        rejects(()->new Gemini("x","bad/model"),"model path injection rejected");
        rejects(()->new Gemini("","gemini-3.5-flash-lite").analyze(v,mp4,s->{}),"no fake questions when API key absent");
        System.out.println("RESULT: "+passed+" checks passed; 0 failed. Synthetic fixtures do not verify playback or AI quality.");
    }
    public static byte[] fixture() throws IOException {
        byte[] mvhd=ByteBuffer.allocate(20).putInt(0).putInt(0).putInt(0).putInt(1000).putInt(10000).array();
        byte[] hdlr=ByteBuffer.allocate(12).putInt(0).putInt(0).put("vide".getBytes()).array();
        return concat(box("ftyp","isom0000".getBytes()),box("moov",concat(box("mvhd",mvhd),box("trak",box("mdia",concat(box("hdlr",hdlr),box("minf",box("stbl",box("stsd",concat(new byte[8],box("avc1",new byte[8])))))))))),box("mdat",new byte[4]));
    }
    private static byte[] box(String tag,byte[] data)throws IOException{return concat(ByteBuffer.allocate(4).putInt(data.length+8).array(),tag.getBytes(),data);}
    private static byte[] concat(byte[]... parts)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] p:parts)out.write(p);return out.toByteArray();}
}
