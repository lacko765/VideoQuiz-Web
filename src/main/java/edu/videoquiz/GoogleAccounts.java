package edu.videoquiz;

import edu.videoquiz.core.Store;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.*;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.*;
import java.util.List;

/** Spring validates the signed ID token, issuer, audience, expiry, state and nonce before mapping. */
@Service
public class GoogleAccounts implements OAuth2UserService<OidcUserRequest,OidcUser> {
    private final Store store;
    private final OidcUserService delegate=new OidcUserService();
    @Value("${videoquiz.max-accounts:0}") private long maxAccounts;
    public GoogleAccounts(Store store) { this.store=store; }
    @Override public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        if(!"google".equals(request.getClientRegistration().getRegistrationId())) throw failure();
        return mapVerifiedUser(delegate.loadUser(request));
    }
    OidcUser mapVerifiedUser(OidcUser user) {
        String issuer=user.getClaimAsString("iss");
        if(!"https://accounts.google.com".equals(issuer) && !"accounts.google.com".equals(issuer)) throw failure();
        try {
            // Email and display name never select, merge or link application accounts.
            var account=store.googleAccount(user.getSubject(),maxAccounts);
            return new Profile(user,account.username());
        } catch(Exception e) { throw failure(); }
    }
    private static OAuth2AuthenticationException failure() {
        return new OAuth2AuthenticationException(new OAuth2Error("google_sign_in_failed"),"Google sign-in could not be completed.");
    }
    private static final class Profile extends DefaultOidcUser {
        private static final long serialVersionUID=1L;
        private final String accountName;
        Profile(OidcUser user,String accountName) {
            super(List.of(new SimpleGrantedAuthority("ROLE_USER")),user.getIdToken(),user.getUserInfo());
            this.accountName=accountName;
        }
        @Override public String getName() {return accountName;}
    }
}
