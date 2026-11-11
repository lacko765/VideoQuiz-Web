package edu.videoquiz;

import edu.videoquiz.core.Store;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class Security {
    @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService users(Store store) {
        return username -> {
            try { var account = store.account(username); return User.withUsername(account.username()).password(account.passwordHash()).roles("USER").build(); }
            catch(Exception e) { throw new UsernameNotFoundException("Invalid username or password"); }
        };
    }
    @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
        // Internal platform health checks use HTTP; all application traffic still requires HTTPS.
        http.requiresChannel(c -> c.requestMatchers(req -> !req.getRequestURI().equals("/healthz")).requiresSecure());
        http.authorizeHttpRequests(c -> c.requestMatchers("/healthz","/","/index.html","/app.js","/quiz-clock.js","/youtube-loader.js","/i18n.js","/styles.css","/api/settings","/api/csrf","/api/register","/api/login","/error").permitAll().anyRequest().authenticated());
        // Session-backed CSRF remains enabled, including registration/login/logout and chunk uploads.
        http.formLogin(c -> c.loginProcessingUrl("/api/login")
            .successHandler((req,res,a) -> { res.setContentType("application/json"); res.getWriter().write("{\"ok\":true}"); })
            .failureHandler((req,res,e) -> { res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Invalid username or password.\"}"); }));
        http.logout(c -> c.logoutUrl("/api/logout").logoutSuccessHandler((req,res,a) -> res.setStatus(204)).deleteCookies("JSESSIONID"));
        http.exceptionHandling(c -> c.authenticationEntryPoint((req,res,e) -> { res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Please sign in to access your videos and results.\"}"); })
            .accessDeniedHandler((req,res,e) -> { res.setStatus(403); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Session security token expired. Refresh the page and try again.\"}"); }));
        http.headers(c -> c.contentSecurityPolicy(p -> p.policyDirectives("default-src 'self'; script-src 'self' https://www.youtube.com https://s.ytimg.com; style-src 'self'; img-src 'self' data: https://i.ytimg.com; media-src 'self' blob:; frame-src https://www.youtube.com https://www.youtube-nocookie.com; connect-src 'self' https://www.youtube.com; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")));
        return http.build();
    }
}
