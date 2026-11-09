package edu.videoquiz.core;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class SupabaseObjectsTests {
    final HttpClient client=mock(HttpClient.class);
    final SupabaseObjects objects=new SupabaseObjects("https://fixture.supabase.co","fixture-secret","videoquiz-private",client);
    final String path=Store.id()+"/"+Store.id();
    @SuppressWarnings("unchecked") void response(int status,byte[] body)throws Exception {
        HttpResponse<InputStream> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(new ByteArrayInputStream(body));
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
    @Test void downloadsUseAuthenticatedEndpointWithServerCredential()throws Exception{
        response(200,new byte[]{1,2});assertArrayEquals(new byte[]{1,2},objects.get(path));
        var capture=ArgumentCaptor.forClass(HttpRequest.class);verify(client).send(capture.capture(),any());
        assertEquals("/storage/v1/object/authenticated/videoquiz-private/"+path,capture.getValue().uri().getPath());
        assertEquals("Bearer fixture-secret",capture.getValue().headers().firstValue("Authorization").orElseThrow());
    }
    @Test void providerErrorsDoNotLeakResponseSecrets()throws Exception{
        response(403,"fixture-secret".getBytes());var e=assertThrows(IOException.class,()->objects.get(path));assertFalse(e.getMessage().contains("fixture-secret"));
    }
    @Test void publicBucketsAreRejected()throws Exception{response(200,"{\"public\":true}".getBytes());assertThrows(IOException.class,objects::verifyPrivateBucket);}
    @Test void privateBucketsAreAccepted()throws Exception{response(200,"{\"public\":false}".getBytes());objects.verifyPrivateBucket();}
}
