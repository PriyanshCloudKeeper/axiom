package com.learnhai.scim.config;

import com.learnhai.scim.config.auth.StaticTokenAuthenticationFilter;
import com.learnhai.scim.config.auth.StaticTokenAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder; // Import this
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider; // Import this
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private StaticTokenAuthenticationProvider staticTokenAuthenticationProvider;

    @Autowired
    private JwtDecoder jwtDecoder; // Autowire JwtDecoder (Spring Boot provides one if issuer-uri is set)

    // Combined AuthenticationManager that knows about both static tokens and JWTs
    @Bean
    public AuthenticationManager scimAuthenticationManager() {
        JwtAuthenticationProvider jwtAuthenticationProvider = new JwtAuthenticationProvider(jwtDecoder);
        jwtAuthenticationProvider.setJwtAuthenticationConverter(jwtAuthenticationConverter());
        // ProviderManager will try providers in order. Static first.
        return new ProviderManager(Arrays.asList(staticTokenAuthenticationProvider, jwtAuthenticationProvider));
    }

    @Bean
    @Order(1)
    public SecurityFilterChain scimFilterChain(HttpSecurity http) throws Exception {
        // Use the combined SCIM Authentication Manager
        AuthenticationManager scimAuthManager = scimAuthenticationManager();

        StaticTokenAuthenticationFilter customStaticTokenFilter = new StaticTokenAuthenticationFilter(scimAuthManager);
        
        // This filter will be responsible for JWTs if customStaticTokenFilter doesn't handle it
        BearerTokenAuthenticationFilter bearerTokenJwtFilter = new BearerTokenAuthenticationFilter(scimAuthManager);
        // Set an AuthenticationEntryPoint for this filter to respond correctly on JWT failure
        bearerTokenJwtFilter.setAuthenticationEntryPoint(new BearerTokenAuthenticationEntryPoint());


        http
            .securityMatcher(new AntPathRequestMatcher("/scim/v2/**"))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context
                .securityContextRepository(new DelegatingSecurityContextRepository(
                        new RequestAttributeSecurityContextRepository(),
                        new HttpSessionSecurityContextRepository()
                ))
            )
            .authorizeHttpRequests(authz -> authz
                .anyRequest().authenticated()
            )
            // Our static filter runs first
            .addFilterBefore(customStaticTokenFilter, BearerTokenAuthenticationFilter.class) // Standard BearerTokenAuthFilter position
            // Then the standard JWT bearer token filter.
            // We are adding it manually to ensure it uses our scimAuthenticationManager.
            // .oauth2ResourceServer().jwt() also adds this filter, but by adding it manually,
            // we have more control over the AuthenticationManager it uses.
            // This might be redundant if .authenticationManager(scimAuthManager) below is enough.
            .addFilterAfter(bearerTokenJwtFilter, StaticTokenAuthenticationFilter.class) // Ensure it runs after our static one

            // Set the default authentication manager for this chain
            .authenticationManager(scimAuthManager)
            
            // Define what happens if authentication fails and an entry point is needed
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint()) // For unauthenticated access to secured SCIM
            )
            // We are NOT using .oauth2ResourceServer().jwt() here to avoid Spring Boot
            // adding its own BearerTokenAuthenticationFilter with its own default AuthenticationManager,
            // which might be part of the conflict.
            // Instead, we've manually configured JWT support via JwtAuthenticationProvider
            // in our scimAuthenticationManager and manually added a BearerTokenAuthenticationFilter.
            ;

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().permitAll()
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        return converter;
    }
}