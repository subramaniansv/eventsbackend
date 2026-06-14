package com.social.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.app.module.iam.models.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * Design:
 *  - Stateless (JWT — no server-side sessions)
 *  - CSRF disabled (REST API, not browser forms)
 *  - JwtAuthFilter runs before UsernamePasswordAuthenticationFilter
 *  - Public paths are explicitly enumerated; everything else requires a valid JWT
 *  - Custom AuthenticationEntryPoint returns our JSON 401 format
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // REST API — no CSRF, no sessions.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Request authorization rules.
            .authorizeHttpRequests(auth -> auth
                // Auth: register / login / Google sign-in / token refresh.
                .requestMatchers(HttpMethod.POST,   "/auth").permitAll()
                // Health probes.
                .requestMatchers("/health", "/ping").permitAll()
                // Email verification click-through (browser lands here from inbox).
                .requestMatchers(HttpMethod.GET,    "/api/email-verify").permitAll()
                // Forgot-password flow (no session available).
                .requestMatchers(HttpMethod.POST,   "/api/password-reset").permitAll()
                .requestMatchers(HttpMethod.POST,   "/api/password-reset/confirm").permitAll()
                // Meta OAuth callback — secured by the state CSRF token, not JWT.
                .requestMatchers(HttpMethod.GET,    "/api/oauth/meta/callback").permitAll()
                // Everything else requires authentication.
                .anyRequest().authenticated()
            )

            // Custom 401 response in our ApiResponse JSON envelope.
            .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                res.setStatus(401);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write(MAPPER.writeValueAsString(
                        new ApiResponse(false, "missing or invalid authorization header", null, 401)));
            }))

            // Register our JWT filter BEFORE Spring Security's form-login filter.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
