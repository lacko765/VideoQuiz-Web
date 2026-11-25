package edu.videoquiz;

import com.google.gson.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.*;
import edu.videoquiz.core.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real authorization-code filter chain and signed JWT validation against an isolated local provider. */
@SpringBootTest(properties={"spring.profiles.active=local","server.ssl.enabled=false","server.ssl.key-store-password=test-only",
    "videoquiz.google-client-id=fixture-google-client","videoquiz.google-client-secret=fixture-client-secret"})
@AutoConfigureMockMvc
@Import(GoogleOAuthTests.ProviderConfiguration.class)
class GoogleOAuthTests {
    static final Path ROOT;
    static final HttpServer PROVIDER;
    static final RSAKey SIGNING_KEY;
    static volatile String subject="fixture-subject",nonce="",mode="valid",tokenForm="";
    static final AtomicInteger tokenCalls=new AtomicInteger();
    static {
        try {
            ROOT=Files.createTempDirectory("videoquiz-oidc-");
            SIGNING_KEY=new RSAKeyGenerator(2048).keyID("fixture-signing-key").generate();
            PROVIDER=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            PROVIDER.createContext("/jwks",x->send(x,new JWKSet(SIGNING_KEY.toPublicJWK()).toString()));
            PROVIDER.createContext("/token",x->{
                tokenCalls.incrementAndGet();tokenForm=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                try {
                    Instant now=Instant.now();
                    var claims=new JWTClaimsSet.Builder().issuer(mode.equals("issuer")?"https://evil.example":"https://accounts.google.com")
                        .audience(mode.equals("audience")?"wrong-client":"fixture-google-client").subject(subject)
                        .issueTime(Date.from(now.minusSeconds(30))).expirationTime(Date.from(now.plusSeconds(mode.equals("expired")?-600:300)))
                        .claim("nonce",mode.equals("nonce")?"wrong-nonce":nonce).claim("email","same@example.test")
                        .claim("email_verified",true).claim("name","Google Fixture").build();
                    SignedJWT jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID()).build(),claims);
                    jwt.sign(new RSASSASigner(mode.equals("signature")?new RSAKeyGenerator(2048).generate():SIGNING_KEY));
                    JsonObject body=new JsonObject();body.addProperty("access_token","fixture-access-token");body.addProperty("token_type","Bearer");
                    body.addProperty("expires_in",300);body.addProperty("scope","openid profile email");body.addProperty("id_token",jwt.serialize());send(x,body.toString());
                } catch(Exception e) {x.sendResponseHeaders(500,-1);x.close();}
            });
            PROVIDER.createContext("/userinfo",x->send(x,"{\"sub\":\""+subject+"\",\"email\":\"same@example.test\",\"email_verified\":true,\"name\":\"Google Fixture\"}"));
            PROVIDER.start();
        } catch(Exception e) {throw new ExceptionInInitializerError(e);}
    }
    static void send(HttpExchange exchange,String body) throws java.io.IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
    }
    @TestConfiguration static class ProviderConfiguration {
        @Bean @Primary ClientRegistrationRepository fixtureRegistrations() {
            String base="http://127.0.0.1:"+PROVIDER.getAddress().getPort();
            return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId("fixture-google-client").clientSecret("fixture-client-secret").scope("openid","profile","email")
                .authorizationUri(base+"/authorize").tokenUri(base+"/token").jwkSetUri(base+"/jwks").userInfoUri(base+"/userinfo")
                .issuerUri("https://accounts.google.com").redirectUri("{baseUrl}"+GoogleOAuth.CALLBACK).build());
        }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("videoquiz.data-dir",ROOT::toString);r.add("videoquiz.key-encryption-key",()->PersonalKeyTests.MASTER);}
    @Autowired MockMvc mvc;
    @Autowired Store store;
    @Autowired PasswordEncoder encoder;
    @MockBean GeminiClients clients;
    @BeforeEach void reset(){mode="valid";tokenCalls.set(0);tokenForm="";}
    @AfterAll static void stopProvider(){PROVIDER.stop(0);}
    record Start(MockHttpSession session,String state,String sessionId) {}
    Start start() throws Exception {
        var result=mvc.perform(get("https://localhost:443/oauth2/authorization/google").secure(true)).andExpect(status().is3xxRedirection()).andReturn();
        Map<String,String> query=new HashMap<>();
        for(String part:URI.create(result.getResponse().getRedirectedUrl()).getRawQuery().split("&")) {
            String[] pair=part.split("=",2);query.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
        }
        assertEquals("code",query.get("response_type"));assertEquals("https://localhost"+GoogleOAuth.CALLBACK,query.get("redirect_uri"));
        assertNotNull(query.get("state"));assertNotNull(query.get("nonce"));assertEquals("S256",query.get("code_challenge_method"));assertNotNull(query.get("code_challenge"));
        nonce=query.get("nonce");MockHttpSession session=(MockHttpSession)result.getRequest().getSession(false);
        return new Start(session,query.get("state"),session.getId());
    }
    MockHttpSession login(String sub) throws Exception {
        subject=sub;Start start=start();
        var result=mvc.perform(get(GoogleOAuth.CALLBACK).secure(true).session(start.session()).param("code","fixture-code").param("state",start.state()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/")).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession(false);assertNotEquals(start.sessionId(),session.getId());
        assertTrue(tokenForm.contains("code_verifier="));return session;
    }
    @Test void fullCodeFlowCreatesIsolatedProfilesAndPersonalKeysWithoutMergingLocalAccounts() throws Exception {
        String local="local_"+UUID.randomUUID().toString().substring(0,8);store.register(local,encoder.encode("fixture-password"));String original=store.account(local).passwordHash();
        var a=login("subject-a");String ownerA=Store.googleUsername("subject-a");
        mvc.perform(get("/api/me").secure(true).session(a)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(ownerA)).andExpect(jsonPath("$.displayName").value("Google Fixture")).andExpect(jsonPath("$.aiConfigured").value(false)).andExpect(jsonPath("$.idToken").doesNotExist());
        when(clients.forKey(PersonalKeyTests.ALICE)).thenReturn(PersonalKeyTests.verifier(PersonalKeyTests.ALICE,200));
        mvc.perform(put("/api/gemini-key").secure(true).session(a).with(csrf()).contentType("application/json").content("{\"key\":\""+PersonalKeyTests.ALICE+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true));
        var video=PersonalKeyTests.video(store,ownerA);
        var b=login("subject-b");String ownerB=Store.googleUsername("subject-b");assertNotEquals(ownerA,ownerB);
        mvc.perform(get("/api/videos/"+video.id).secure(true).session(b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/gemini-key").secure(true).session(b)).andExpect(jsonPath("$.configured").value(false));
        assertNull(store.geminiKey(ownerB));assertNotNull(store.geminiKey(ownerA));assertEquals(original,store.account(local).passwordHash());
        mvc.perform(post("/api/login").secure(true).with(csrf()).param("username",local).param("password","fixture-password")).andExpect(status().isOk());
        mvc.perform(post("/api/login").secure(true).with(csrf()).param("username",ownerA).param("password","fixture-password")).andExpect(status().isUnauthorized());
        var again=login("subject-a");mvc.perform(get("/api/gemini-key").secure(true).session(again)).andExpect(jsonPath("$.configured").value(true));
        mvc.perform(post("/api/logout").secure(true).session(again)).andExpect(status().isForbidden());
        mvc.perform(post("/api/logout").secure(true).session(again).with(csrf())).andExpect(status().isNoContent());assertTrue(again.isInvalid());
    }
    @Test void forgedStateAndCancelledLoginNeverCallTokenEndpoint() throws Exception {
        Start start=start();
        mvc.perform(get(GoogleOAuth.CALLBACK).secure(true).session(start.session()).param("code","attacker-code").param("state","wrong-state"))
            .andExpect(redirectedUrl("/?auth=google-failed"));assertEquals(0,tokenCalls.get());
        Start cancelled=start();mvc.perform(get(GoogleOAuth.CALLBACK).secure(true).session(cancelled.session()).param("error","access_denied").param("state",cancelled.state()))
            .andExpect(redirectedUrl("/?auth=google-failed"));assertEquals(0,tokenCalls.get());
    }
    @Test void invalidSignatureIssuerAudienceExpiryAndNonceCannotAuthenticate() throws Exception {
        for(String invalid:List.of("signature","issuer","audience","expired","nonce")) {
            mode=invalid;subject="rejected-"+invalid;Start start=start();
            mvc.perform(get(GoogleOAuth.CALLBACK).secure(true).session(start.session()).param("code","fixture-code").param("state",start.state()))
                .andExpect(redirectedUrl("/?auth=google-failed"));
            mvc.perform(get("/api/me").secure(true).session(start.session())).andExpect(status().isUnauthorized());
            assertThrows(NoSuchElementException.class,()->store.account(Store.googleUsername(subject)));
        }
    }
    @Test void publicSettingsOnlyAdvertiseAvailability() throws Exception {
        mvc.perform(get("/api/settings").secure(true)).andExpect(jsonPath("$.googleSignInEnabled").value(true)).andExpect(jsonPath("$.clientSecret").doesNotExist()).andExpect(jsonPath("$.clientId").doesNotExist());
        assertFalse(new GoogleOAuth("","").enabled());assertFalse(new GoogleOAuth("only-id","").enabled());
    }
}
