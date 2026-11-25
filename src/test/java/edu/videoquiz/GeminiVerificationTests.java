package edu.videoquiz;

import com.google.gson.*;
import edu.videoquiz.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.io.*;
import java.net.http.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiVerificationTests {
    @TempDir Path dir;
    // Deliberately synthetic, non-credential values; never use a real key in tests.
    static final String OPAQUE="AQ.fixture."+"not-a-real-key_".repeat(30)+"+/=:-";
    static final String METADATA="{\"name\":\"models/fixture-canonical-001\",\"supportedGenerationMethods\":[\"generateContent\"]}";
    static Gemini verifier(String key,int status,String body) {
        return new Gemini(key,"fixture-alias") {
            @Override protected HttpResponse<String> exchange(HttpRequest request) {
                assertEquals("GET",request.method());
                assertEquals("https://generativelanguage.googleapis.com/v1beta/models/fixture-alias",request.uri().toString());
                assertNull(request.uri().getQuery());assertTrue(request.bodyPublisher().isEmpty());
                assertTrue(request.headers().firstValue("x-goog-api-key").orElseThrow().equals(key));
                assertTrue(request.headers().firstValue("Authorization").isEmpty());
                return PersonalKeyTests.response(status,body);
            }
        };
    }
    static String providerError(String status,String reason) {
        JsonObject error=new JsonObject();error.addProperty("status",status);
        error.addProperty("message","Provider echoed secret: "+OPAQUE);
        JsonObject detail=new JsonObject();detail.addProperty("@type","type.googleapis.com/google.rpc.ErrorInfo");
        detail.addProperty("reason",reason);detail.addProperty("domain","googleapis.com");
        JsonObject metadata=new JsonObject();metadata.addProperty("credential",OPAQUE);detail.add("metadata",metadata);
        JsonArray details=new JsonArray();details.add(detail);error.add("details",details);
        JsonObject root=new JsonObject();root.add("error",error);return root.toString();
    }
    @Test void opaqueKeysAndAliasesAreAcceptedAndStoredEncrypted() throws Exception {
        Store store=new Store(dir);store.register("alice","legacy-hash");
        GeminiClients clients=mock(GeminiClients.class);
        when(clients.forKey(anyString())).thenAnswer(i->verifier(i.getArgument(0),200,METADATA));
        PersonalKeys keys=new PersonalKeys(store,clients,PersonalKeyTests.MASTER);
        for(String value:List.of(OPAQUE,"short.fixture","future-prefix:/+=_-", "x".repeat(4096))) {
            assertTrue(keys.save("alice"," \r\n"+value+"\t ").configured());
            assertTrue(value.equals(new KeyCipher(PersonalKeyTests.MASTER).decrypt("alice",store.geminiKey("alice").encrypted)));
            assertFalse(store.geminiKey("alice").encrypted.contains(value));
        }
        assertEquals("legacy-hash",store.account("alice").passwordHash());
    }
    @Test void unsafeHeaderValuesAreRejectedBeforeAnyProviderCall() throws Exception {
        Store store=new Store(dir);store.register("alice","legacy-hash");
        GeminiClients clients=mock(GeminiClients.class);PersonalKeys keys=new PersonalKeys(store,clients,PersonalKeyTests.MASTER);
        for(String value:Arrays.asList(null,""," \r\n ","has space","has\ttab","has\r\nheader", "has\u0000null", "has\u007fdel", "nonascii\u200b", "x".repeat(4097))) {
            var failure=assertThrows(Gemini.KeyFailure.class,()->keys.save("alice",value));
            assertEquals("KEY_INPUT_INVALID",failure.code);assertNull(failure.providerStatus);assertNull(failure.getCause());
        }
        verifyNoInteractions(clients);assertNull(store.geminiKey("alice"));
    }
    static Stream<Arguments> errors() {
        return Stream.of(
            Arguments.of(400,"INVALID_ARGUMENT","API_KEY_INVALID","INVALID_KEY",400,"API_KEY_INVALID"),
            Arguments.of(400,"INVALID_ARGUMENT","API_KEY_EXPIRED","INVALID_KEY",400,"API_KEY_EXPIRED"),
            Arguments.of(400,"INVALID_ARGUMENT","unknown","VERIFICATION_REJECTED",422,"INVALID_ARGUMENT"),
            Arguments.of(400,"FAILED_PRECONDITION","unknown","VERIFICATION_REJECTED",422,"FAILED_PRECONDITION"),
            Arguments.of(401,"UNAUTHENTICATED","unknown","AUTHENTICATION_FAILED",400,"UNAUTHENTICATED"),
            Arguments.of(401,"UNAUTHENTICATED","ACCESS_TOKEN_TYPE_UNSUPPORTED","AUTHENTICATION_FAILED",400,"ACCESS_TOKEN_TYPE_UNSUPPORTED"),
            Arguments.of(401,"UNAUTHENTICATED","API_KEY_SERVICE_BLOCKED","PERMISSION_DENIED",403,"API_KEY_SERVICE_BLOCKED"),
            Arguments.of(402,"FAILED_PRECONDITION","unknown","BILLING_REQUIRED",402,"FAILED_PRECONDITION"),
            Arguments.of(403,"PERMISSION_DENIED","API_KEY_SERVICE_BLOCKED","PERMISSION_DENIED",403,"API_KEY_SERVICE_BLOCKED"),
            Arguments.of(403,"PERMISSION_DENIED","API_KEY_HTTP_REFERRER_BLOCKED","PERMISSION_DENIED",403,"API_KEY_HTTP_REFERRER_BLOCKED"),
            Arguments.of(403,"PERMISSION_DENIED","API_KEY_IP_ADDRESS_BLOCKED","PERMISSION_DENIED",403,"API_KEY_IP_ADDRESS_BLOCKED"),
            Arguments.of(403,"PERMISSION_DENIED","SERVICE_DISABLED","PERMISSION_DENIED",403,"SERVICE_DISABLED"),
            Arguments.of(403,"PERMISSION_DENIED","BILLING_DISABLED","PERMISSION_DENIED",403,"BILLING_DISABLED"),
            Arguments.of(403,"PERMISSION_DENIED","IAM_PERMISSION_DENIED","PERMISSION_DENIED",403,"IAM_PERMISSION_DENIED"),
            Arguments.of(403,"PERMISSION_DENIED",OPAQUE,"PERMISSION_DENIED",403,"PERMISSION_DENIED"),
            Arguments.of(404,"NOT_FOUND","unknown","MODEL_UNAVAILABLE",422,"NOT_FOUND"),
            Arguments.of(429,"RESOURCE_EXHAUSTED","QUOTA_EXCEEDED","QUOTA_EXCEEDED",429,"QUOTA_EXCEEDED"),
            Arguments.of(429,"RESOURCE_EXHAUSTED","RATE_LIMIT_EXCEEDED","QUOTA_EXCEEDED",429,"RATE_LIMIT_EXCEEDED"),
            Arguments.of(408,"DEADLINE_EXCEEDED","unknown","VERIFICATION_TIMEOUT",504,"DEADLINE_EXCEEDED"),
            Arguments.of(500,"INTERNAL","unknown","SERVICE_UNAVAILABLE",503,"INTERNAL"),
            Arguments.of(503,"UNAVAILABLE","unknown","SERVICE_UNAVAILABLE",503,"UNAVAILABLE"),
            Arguments.of(504,"DEADLINE_EXCEEDED","unknown","VERIFICATION_TIMEOUT",504,"DEADLINE_EXCEEDED"),
            Arguments.of(400,OPAQUE,OPAQUE,"VERIFICATION_REJECTED",422,"UNRECOGNIZED_ERROR")
        );
    }
    @ParameterizedTest(name="provider error case {index}") @MethodSource("errors")
    void classifiesOnlySafeProviderReasons(int status,String rpcStatus,String reason,String code,int appStatus,String safeReason) {
        var failure=assertThrows(Gemini.KeyFailure.class,()->verifier(OPAQUE,status,providerError(rpcStatus,reason)).verifyKey());
        assertEquals(code,failure.code);assertEquals(appStatus,failure.httpStatus);
        assertEquals(status,failure.providerStatus);assertEquals(safeReason,failure.providerReason);assertSafe(failure);
    }
    @Test void malformedErrorsAndMetadataCannotEchoSecretsOrBecomeSuccess() {
        var failure=assertThrows(Gemini.KeyFailure.class,()->verifier(OPAQUE,403,"<html>"+OPAQUE+"</html>").verifyKey());
        assertEquals("PERMISSION_DENIED",failure.code);assertEquals("UNRECOGNIZED_ERROR",failure.providerReason);assertSafe(failure);
        for(String body:List.of("not JSON "+OPAQUE,"{}","[]","null","{\"name\":123}","{\"name\":\"models/x\",\"supportedGenerationMethods\":null}")) {
            var invalid=assertThrows(Gemini.KeyFailure.class,()->verifier(OPAQUE,200,body).verifyKey());
            assertEquals("INVALID_RESPONSE",invalid.code);assertEquals(200,invalid.providerStatus);assertSafe(invalid);
        }
        var unsupported=assertThrows(Gemini.KeyFailure.class,()->verifier(OPAQUE,200,METADATA.replace("generateContent","embedContent")).verifyKey());
        assertEquals("MODEL_UNAVAILABLE",unsupported.code);assertEquals("MODEL_UNSUPPORTED",unsupported.providerReason);
    }
    @Test void transportFailuresAreDistinctAndNeverExposeExceptionDetails() {
        for(IOException cause:List.of(new IOException(OPAQUE),new HttpTimeoutException(OPAQUE))) {
            Gemini client=new Gemini(OPAQUE,"fixture-model") {
                @Override protected HttpResponse<String> exchange(HttpRequest request) throws IOException {throw cause;}
            };
            var failure=assertThrows(Gemini.KeyFailure.class,client::verifyKey);
            assertEquals(cause instanceof HttpTimeoutException?"VERIFICATION_TIMEOUT":"NETWORK_ERROR",failure.code);
            assertNull(failure.providerStatus);assertNull(failure.providerReason);assertSafe(failure);
        }
        Gemini interrupted=new Gemini(OPAQUE,"fixture-model") {
            @Override protected HttpResponse<String> exchange(HttpRequest request) throws InterruptedException {throw new InterruptedException(OPAQUE);}
        };
        try {
            var failure=assertThrows(Gemini.KeyFailure.class,interrupted::verifyKey);
            assertEquals("VERIFICATION_INTERRUPTED",failure.code);assertTrue(Thread.currentThread().isInterrupted());assertSafe(failure);
        } finally {Thread.interrupted();}
    }
    static void assertSafe(Gemini.KeyFailure failure) {
        assertNull(failure.getCause());StringWriter stack=new StringWriter();failure.printStackTrace(new PrintWriter(stack));
        assertFalse(stack.toString().contains(OPAQUE));assertFalse(stack.toString().contains("Provider echoed"));
    }
}
