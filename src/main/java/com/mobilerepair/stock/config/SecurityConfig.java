package com.mobilerepair.stock.config;

import com.mobilerepair.stock.auth.FirebaseSessionFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Value("${app.firebase.enabled:false}")
    private boolean firebaseEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, FirebaseSessionFilter sessionFilter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/", "/index.html", "/app.js", "/firebase-config.js",
                            "/logo.svg", "/logo-full.svg", "/logo.png", "/favicon.ico",
                            "/css/**", "/js/**", "/images/**", "/assets/**",
                            "/api/health",
                            "/api/auth/session"
                    ).permitAll();
                    if (firebaseEnabled) {
                        auth.requestMatchers("/api/**").authenticated();
                    } else {
                        auth.requestMatchers("/api/**").permitAll();
                    }
                    auth.anyRequest().permitAll();
                })
                .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Allows the native shells (Android/iOS/desktop) and the standalone page to
    // call the REST/SSE API from a different origin. Credentials are not used by
    // the API (org scoping is via the path, sessions via Firebase cookies only
    // in cloud mode), so a permissive non-credentialed policy is safe here.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
