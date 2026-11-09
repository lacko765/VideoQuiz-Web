package edu.videoquiz;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
class ProductionTests {
    static final Path root;
    static {try {root=Files.createTempDirectory("videoquiz-prod-test");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("videoquiz.data-dir",root::toString);
        r.add("videoquiz.gemini-key",()->"test-fixture-no-api-calls");
    }
    @LocalServerPort int port;
    final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    HttpResponse<String> get(String path,boolean forwarded) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));
        if(forwarded) request.header("X-Forwarded-Proto","https").header("X-Forwarded-Host","demo.example.com").header("X-Forwarded-Port","443");
        return client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void internalHealthWorksWithoutTls() throws Exception {assertEquals(200,get("/healthz",false).statusCode());}
    @Test void plainHttpApplicationRedirects() throws Exception {assertEquals(302,get("/",false).statusCode());}
    @Test void proxyHttpsServesAppWithoutRedirectLoop() throws Exception {assertEquals(200,get("/",true).statusCode());}
    @Test void proxySessionCookieIsSecure() throws Exception {
        var response=get("/api/csrf",true);assertEquals(200,response.statusCode());
        String cookie=response.headers().firstValue("set-cookie").orElseThrow();
        assertTrue(cookie.contains("Secure"));assertTrue(cookie.contains("HttpOnly"));
    }
    @Test void anonymousPrivateApiStillRejected() throws Exception {assertEquals(401,get("/api/videos",true).statusCode());}
    @Test void missingKeyAndDuplicateWriterRejected() throws Exception {
        assertThrows(IllegalStateException.class,()->new ProductionStorage(root.toString(),""));
        assertThrows(Exception.class,()->new ProductionStorage(root.toString(),"test"));
    }
}
