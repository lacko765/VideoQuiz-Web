package edu.videoquiz;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.*;

/** No discovery/network call or OAuth credentials are required when Google login is disabled. */
@Configuration
public class GoogleOAuth {
    public static final String CALLBACK="/login/oauth2/code/google";
    private final String clientId,clientSecret;
    public GoogleOAuth(@Value("${videoquiz.google-client-id:}") String clientId,
                       @Value("${videoquiz.google-client-secret:}") String clientSecret) {
        this.clientId=clientId;this.clientSecret=clientSecret;
    }
    public boolean enabled() { return !clientId.isBlank() && !clientSecret.isBlank(); }
    @Bean ClientRegistrationRepository googleRegistrations() {
        if(!enabled()) return registrationId -> null;
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
            .clientId(clientId).clientSecret(clientSecret).scope("openid","profile","email")
            .redirectUri("{baseUrl}"+CALLBACK).build());
    }
}
