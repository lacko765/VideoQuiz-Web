package edu.videoquiz;

import edu.videoquiz.core.*;
import static edu.videoquiz.core.Models.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import java.nio.file.*;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

/** HTTP/security integration checks. Fixtures are intentionally synthetic, never production AI responses. */
@SpringBootTest(properties={"server.ssl.enabled=false","server.ssl.key-store-password=test-only","videoquiz.gemini-key="})
@AutoConfigureMockMvc
class ApiTests {
    static Path root;
    static { try { root=Files.createTempDirectory("videoquiz-api-tests-"); } catch(Exception e) {throw new ExceptionInInitializerError(e);} }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("videoquiz.data-dir",()->root.toString()); }
    @Autowired MockMvc mvc;
    @Autowired Store store;
    @MockBean Jobs jobs;
    Video video;
    @BeforeEach void fixture() throws Exception {
        video=new Video();video.id=Store.id();video.owner="alice";video.title="Test fixture";video.kind="YOUTUBE";video.youtubeId="9hE5-98ZeCg";video.status="READY";
        video.points=List.of(new Point(0,2,"Fixture fact","Fixture evidence"));
        video.questions=List.of(new Question("Fixture question?",List.of("A","B","C","D"),1,"Fixture explanation",0,2));store.save(video);
    }
    @Test void anonymousCannotReadVideos() throws Exception {mvc.perform(get("/api/videos").secure(true)).andExpect(status().isUnauthorized());}
    @Test void httpRedirectsToHttps() throws Exception {mvc.perform(get("/api/videos")).andExpect(status().is3xxRedirection());}
    @Test @WithMockUser(username="alice") void answerKeysAreNotExposedBeforeSubmission() throws Exception {
        mvc.perform(get("/api/videos/"+video.id).secure(true)).andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].correctIndex").doesNotExist()).andExpect(jsonPath("$.points").doesNotExist());
    }
    @Test @WithMockUser(username="bob") void crossUserVideoIsNotFound() throws Exception {mvc.perform(get("/api/videos/"+video.id).secure(true)).andExpect(status().isNotFound());}
    @Test @WithMockUser(username="alice") void mutationRequiresCsrf() throws Exception {mvc.perform(post("/api/videos/"+video.id+"/attempts").secure(true)).andExpect(status().isForbidden());}
    @Test @WithMockUser(username="alice") void scoresAreIdempotentAndReviewIsSaved() throws Exception {
        Attempt a=new Attempt();a.id=Store.id();a.owner="alice";a.videoId=video.id;store.save(a);
        mvc.perform(post("/api/attempts/"+a.id+"/answers").secure(true).with(csrf()).contentType("application/json").content("{\"questionIndex\":0,\"optionIndex\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1));
        mvc.perform(post("/api/attempts/"+a.id+"/answers").secure(true).with(csrf()).contentType("application/json").content("{\"questionIndex\":0,\"optionIndex\":2}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.selectedIndex").value(1)).andExpect(jsonPath("$.score").value(1));
        mvc.perform(get("/api/attempts/"+a.id).secure(true)).andExpect(status().isOk()).andExpect(jsonPath("$.complete").value(true)).andExpect(jsonPath("$.review[0].evidence.evidence").value("Fixture evidence"));
    }
    @Test @WithMockUser(username="alice") void missingAnswerFieldsAreRejected() throws Exception {mvc.perform(post("/api/attempts/anything/answers").secure(true).with(csrf()).contentType("application/json").content("{}")) .andExpect(status().isBadRequest());}
    @Test @WithMockUser(username="alice") void unsupportedLinkIsRejected() throws Exception {mvc.perform(post("/api/links").secure(true).with(csrf()).contentType("application/json").content("{\"url\":\"https://127.0.0.1/private\"}")) .andExpect(status().isBadRequest());}
    @Test @WithMockUser(username="alice") void uploadChunksAreDurableAndWrongOffsetsRejected() throws Exception {
        Video v=new Video();v.id=Store.id();v.owner="alice";v.title="fixture.mp4";v.kind="LOCAL";v.size=100;store.save(v);Files.createFile(store.media(v.id));
        mvc.perform(put("/api/uploads/"+v.id+"?offset=0").secure(true).with(csrf()).contentType("application/octet-stream").content(new byte[32])).andExpect(status().isOk()).andExpect(jsonPath("$.received").value(32));
        mvc.perform(put("/api/uploads/"+v.id+"?offset=0").secure(true).with(csrf()).contentType("application/octet-stream").content(new byte[32])).andExpect(status().isBadRequest());
        Assertions.assertEquals(32,Files.size(store.media(v.id)));
        mvc.perform(post("/api/uploads/"+v.id+"/complete").secure(true).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test @WithMockUser(username="alice") void slowExternalServiceStatusPreservesVideo() throws Exception {
        video.status="PROCESSING";video.externalWaitSince=System.currentTimeMillis()-11000;store.save(video);
        mvc.perform(get("/api/videos/"+video.id).secure(true)).andExpect(status().isOk()).andExpect(jsonPath("$.externalWait").value(true));
    }
    @Test @WithMockUser(username="bob") void mediaRequiresOwnership() throws Exception {mvc.perform(get("/api/videos/"+video.id+"/media").secure(true)).andExpect(status().isNotFound());}
    @Test @WithMockUser(username="alice") void mediaSupportsByteRanges() throws Exception {
        video.kind="LOCAL";video.duration=10;video.size=100;video.received=100;store.save(video);Files.write(store.media(video.id),new byte[100]);
        mvc.perform(get("/api/videos/"+video.id+"/media").secure(true).header("Range","bytes=10-19")).andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 10-19/100")).andExpect(content().bytes(new byte[10]));
    }
}
