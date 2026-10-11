package com.mobilerepair.stock.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SessionService sessionService;

    public AuthController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/session")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody Map<String, String> body,
                                                             HttpServletResponse response) {
        if (!sessionService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of("error", "firebase-not-configured"));
        }
        String idToken = body.get("idToken");
        if (idToken == null || idToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing-id-token"));
        }
        try {
            String cookie = sessionService.create(idToken);
            ResponseCookie sessionCookie = ResponseCookie.from(SessionService.COOKIE_NAME, cookie)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .sameSite("Lax")
                    .maxAge(sessionService.ttl())
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (InvalidTokenException e) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid-token"));
        }
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> deleteSession(HttpServletResponse response) {
        ResponseCookie sessionCookie = ResponseCookie.from(SessionService.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());
        return ResponseEntity.noContent().build();
    }
}
