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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private StaticTokenAuthenticationProvider staticTokenAuthenticationProvider;

    // Bean for the global AuthenticationManager
    // This AuthenticationManager will know about StaticTokenAuthenticationProvider.
    // The JwtAuthenticationProvider used by oauth2ResourceServer is typically configured
    // automatically by Spring Boot when .oauth2ResourceServer().jwt() is used.
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.authenticationProvider(staticTokenAuthenticationProvider);
        // If you were not using .oauth2ResourceServer().jwt() but still wanted JWT,
        // you'd add a JwtAuthenticationProvider here.
        return authenticationManagerBuilder.build();
    }

    @Bean
    @Order(1) // This filter chain is specifically for SCIM endpoints and runs first for these paths.
    public SecurityFilterChain scimFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        StaticTokenAuthenticationFilter customStaticTokenFilter = new StaticTokenAuthenticationFilter(authenticationManager);

        http
            .securityMatcher(new AntPathRequestMatcher("/scim/v2/**")) // Apply this chain ONLY to /scim/v2/**
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Ensures SecurityContext is stored per request for statelessness
            .securityContext(context -> context
                .securityContextRepository(new DelegatingSecurityContextRepository(
                        new RequestAttributeSecurityContextRepository(),
                        new HttpSessionSecurityContextRepository() // Though session is stateless, good to have a full delegate
                ))
            )
            .authorizeHttpRequests(authz -> authz
                .anyRequest().authenticated() // All requests to /scim/v2/** must be authenticated
            )
            // Add our custom static token filter BEFORE the standard BearerTokenAuthenticationFilter
            // This gives static token authentication priority if a bearer token is present.
            .addFilterBefore(customStaticTokenFilter, BearerTokenAuthenticationFilter.class)
            // Configure OAuth2 Resource Server to handle JWTs if static token auth doesn't occur or handle the token.
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            // Explicitly associate the AuthenticationManager (which knows about StaticTokenAuthenticationProvider)
            // with this HttpSecurity configuration.
            .authenticationManager(authenticationManager);

        return http.build();
    }

    @Bean
    @Order(2) // This filter chain handles all other requests (like actuators)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http
            // No securityMatcher means it applies to any request not matched by a higher-order filter chain.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Define security for any other paths. For now, permit all.
                // In a stricter setup, you might .denyAll() or require other authentication.
                .anyRequest().permitAll()
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // If your roles are in a custom claim for JWTs, you can configure it here.
        // Example:
        // JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // grantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles"); // Common Keycloak claim
        // grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        // converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}