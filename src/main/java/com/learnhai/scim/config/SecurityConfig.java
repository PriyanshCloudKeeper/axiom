package com.learnhai.scim.config;

import com.learnhai.scim.config.auth.StaticTokenAuthenticationFilter;
import com.learnhai.scim.config.auth.StaticTokenAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private StaticTokenAuthenticationProvider staticTokenAuthenticationProvider;

    // Bean for AuthenticationManager
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.authenticationProvider(staticTokenAuthenticationProvider);
        // The JwtAuthenticationProvider will be automatically registered by .oauth2ResourceServer().jwt()
        return authenticationManagerBuilder.build();
    }

    @Bean
    @Order(1) // Ensure this filter chain for SCIM is processed with high priority
    public SecurityFilterChain scimFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        StaticTokenAuthenticationFilter customStaticTokenFilter = new StaticTokenAuthenticationFilter(authenticationManager);

        http
            .securityMatcher("/scim/v2/**") // Apply this filter chain ONLY to /scim/v2/** paths
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Add our custom static token filter. It attempts static auth.
            // If static auth fails, it continues the chain, allowing BearerTokenAuthenticationFilter (for JWTs) to try.
            .addFilterBefore(customStaticTokenFilter, BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(authz -> authz
                .anyRequest().authenticated() // All /scim/v2/** requests must be authenticated
            )
            .oauth2ResourceServer(oauth2 -> oauth2 // Configure JWT validation as a fallback or primary for non-static tokens
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }

    @Bean
    @Order(2) // Lower priority for other paths
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().permitAll() // Or denyAll() if you want to be strict for non-SCIM, non-actuator paths
            );
          // No .oauth2ResourceServer here unless other paths also need JWT auth
        return http.build();
    }


    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // Customize if needed
        return converter;
    }
}