package edu.videoquiz;
import edu.videoquiz.core.*;
import edu.videoquiz.core.Models.Video;
import com.google.gson.*;
import java.util.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanguageTests {
    @Test void arabicPromptsUseRealMediaReferenceAndValidatedEvidence()throws Exception{
        var prompts=new ArrayList<String>();var mediaInputs=new ArrayList<List<JsonObject>>();
        Gemini fixture=new Gemini("fixture-no-network","fixture-model"){
            protected JsonObject generate(List<JsonObject> media,String prompt){
                prompts.add(prompt);mediaInputs.add(media);
                return JsonParser.parseString(prompts.size()==1?"{\"title\":\"درس\",\"points\":[{\"start\":\"00:01\",\"end\":\"00:02\",\"summary\":\"معلومة\",\"evidence\":\"دليل\"}]}":"{\"questions\":[{\"question\":\"سؤال؟\",\"options\":[\"أ\",\"ب\",\"ج\",\"د\"],\"correctIndex\":0,\"explanation\":\"شرح\",\"pointIndex\":0}]}").getAsJsonObject();
            }
        };
        Video video=new Video();video.kind="YOUTUBE";video.youtubeId="tVHOBVAFjUw";video.duration=20;video.quizLanguage="ar";
        fixture.analyze(video,Path.of("unused"),s->{});
        assertTrue(prompts.stream().allMatch(p->p.contains("Arabic")));
        assertTrue(mediaInputs.get(0).get(0).toString().contains(video.youtubeId));
        assertTrue(mediaInputs.get(1).isEmpty());assertEquals(2,video.questions.get(0).timestamp());
    }
}
