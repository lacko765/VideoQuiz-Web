package edu.videoquiz;

import edu.videoquiz.core.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"spring.profiles.active=local","server.ssl.enabled=false","server.ssl.key-store-password=test-only",
    "videoquiz.google-client-id=","videoquiz.google-client-secret="})
@AutoConfigureMockMvc
class PersonalKeyApiTests {
    static final Path ROOT;
    static {try{ROOT=Files.createTempDirectory("videoquiz-personal-key-api-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("videoquiz.data-dir",ROOT::toString);r.add("videoquiz.key-encryption-key",()->PersonalKeyTests.MASTER);}
    @Autowired MockMvc mvc;
    @Autowired Store store;
    @MockBean GeminiClients clients;
    String alice,bob;
    @BeforeEach void setup() throws Exception {
        alice="a_"+UUID.randomUUID().toString().replace("-","").substring(0,12);bob="b_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        store.register(alice,"legacy-hash");store.register(bob,"other-hash");
        when(clients.forKey(anyString())).thenAnswer(i->PersonalKeyTests.verifier(i.getArgument(0),200));
    }
    @Test void keyEndpointsRequireAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/gemini-key").secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/gemini-key").secure(true).with(user(alice)).contentType("application/json").content("{\"key\":\""+PersonalKeyTests.ALICE+"\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/gemini-key").secure(true).with(user(alice))).andExpect(status().isForbidden());
    }
    @Test void keyManagementNeverReturnsSecretsAndUsesOnlySessionOwner() throws Exception {
        String result=mvc.perform(put("/api/gemini-key").secure(true).with(user(alice)).with(csrf()).contentType("application/json")
            .content("{\"key\":\""+PersonalKeyTests.ALICE+"\",\"owner\":\""+bob+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.key").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertFalse(result.contains(PersonalKeyTests.ALICE));assertNotNull(store.geminiKey(alice));assertNull(store.geminiKey(bob));
        mvc.perform(get("/api/gemini-key").secure(true).with(user(bob))).andExpect(jsonPath("$.configured").value(false));
        mvc.perform(get("/api/gemini-key").secure(true).with(user(alice))).andExpect(jsonPath("$.encrypted").doesNotExist());
        mvc.perform(delete("/api/gemini-key").secure(true).with(user(bob)).with(csrf())).andExpect(status().isOk());assertNotNull(store.geminiKey(alice));
        mvc.perform(delete("/api/gemini-key").secure(true).with(user(alice)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
        assertNull(store.geminiKey(alice));
    }
    @Test void missingKeyBlocksAnalysisButNotHistoryAndQuotaErrorsAreSafe() throws Exception {
        var v=PersonalKeyTests.video(store,alice);
        mvc.perform(post("/api/videos/"+v.id+"/analyze").secure(true).with(user(alice)).with(csrf()).contentType("application/json").content("{\"durationSeconds\":20}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("KEY_REQUIRED"));
        mvc.perform(get("/api/videos").secure(true).with(user(alice))).andExpect(status().isOk());
        mvc.perform(get("/api/attempts").secure(true).with(user(alice))).andExpect(status().isOk());
        when(clients.forKey(PersonalKeyTests.ALICE)).thenReturn(PersonalKeyTests.verifier(PersonalKeyTests.ALICE,429));
        mvc.perform(put("/api/gemini-key").secure(true).with(user(alice)).with(csrf()).contentType("application/json").content("{\"key\":\""+PersonalKeyTests.ALICE+"\"}"))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").value("Quota Exceeded"));
        assertNull(store.geminiKey(alice));
    }
    @Test void opaqueKeySaveAndFailedReplacementKeepEncryptedKeyAndExposeOnlySafeDiagnostics() throws Exception {
        String key=GeminiVerificationTests.OPAQUE;
        String input=new com.google.gson.Gson().toJson(Map.of("key",key));
        when(clients.forKey(key)).thenReturn(GeminiVerificationTests.verifier(key,200,GeminiVerificationTests.METADATA));
        mvc.perform(put("/api/gemini-key").secure(true).with(user(alice)).with(csrf()).contentType("application/json").content(input))
            .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true));
        String encrypted=store.geminiKey(alice).encrypted;
        assertTrue(key.equals(new KeyCipher(PersonalKeyTests.MASTER).decrypt(alice,encrypted)));assertFalse(encrypted.contains(key));
        for(int providerStatus:List.of(400,401,403,404,429,500,503)) {
            String reason=providerStatus==400?"API_KEY_INVALID":providerStatus==403?"API_KEY_HTTP_REFERRER_BLOCKED":"unknown";
            when(clients.forKey(key)).thenReturn(GeminiVerificationTests.verifier(key,providerStatus,GeminiVerificationTests.providerError("UNKNOWN",reason)));
            var response=mvc.perform(put("/api/gemini-key").secure(true).with(user(alice)).with(csrf()).contentType("application/json").content(input))
                .andExpect(jsonPath("$.providerStatus").value(providerStatus)).andExpect(jsonPath("$.providerReason").exists())
                .andReturn().getResponse();
            assertTrue(response.getStatus()>=400);assertNotEquals(401,response.getStatus());
            assertFalse(response.getContentAsString().contains(key));assertFalse(response.getContentAsString().contains("Provider echoed"));
            assertEquals(encrypted,store.geminiKey(alice).encrypted);assertNull(store.geminiKey(bob));
        }
        mvc.perform(get("/api/gemini-key").secure(true).with(user(alice))).andExpect(jsonPath("$.configured").value(true));
        mvc.perform(get("/api/videos").secure(true).with(user(alice))).andExpect(status().isOk());
    }
}
