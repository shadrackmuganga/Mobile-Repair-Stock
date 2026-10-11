package com.mobilerepair.stock.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.SessionCookieOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SessionService {

    public static final String COOKIE_NAME = "session";

    private final ObjectProvider<FirebaseAuth> authProvider;
    private final Duration ttl;

    public SessionService(ObjectProvider<FirebaseAuth> authProvider,
                          @Value("${app.firebase.session-ttl-days:5}") long ttlDays) {
        this.authProvider = authProvider;
        this.ttl = Duration.ofDays(ttlDays);
    }

    public boolean isAvailable() {
        return authProvider.getIfAvailable() != null;
    }

    public Duration ttl() {
        return ttl;
    }

    public String create(String idToken) {
        FirebaseAuth auth = requireAuth();
        try {
            SessionCookieOptions options = SessionCookieOptions.builder()
                    .setExpiresIn(ttl.toMillis())
                    .build();
            return auth.createSessionCookie(idToken, options);
        } catch (FirebaseAuthException e) {
            throw new InvalidTokenException(e.getMessage());
        }
    }

    public FirebaseToken verify(String cookie) {
        FirebaseAuth auth = authProvider.getIfAvailable();
        if (auth == null) {
            return null;
        }
        try {
            return auth.verifySessionCookie(cookie, true);
        } catch (FirebaseAuthException e) {
            return null;
        }
    }

    private FirebaseAuth requireAuth() {
        FirebaseAuth auth = authProvider.getIfAvailable();
        if (auth == null) {
            throw new IllegalStateException("Firebase is not configured (app.firebase.enabled=false)");
        }
        return auth;
    }
}
