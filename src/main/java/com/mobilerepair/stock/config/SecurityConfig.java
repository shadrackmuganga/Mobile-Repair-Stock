package com.mobilerepair.stock.config;

import com.mobilerepair.stock.auth.FirebaseSessionFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Value("${app.firebase.enabled:false}")
    private boolean firebaseEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, FirebaseSessionFilter sessionFilter) throws Exception {
        http
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
}
