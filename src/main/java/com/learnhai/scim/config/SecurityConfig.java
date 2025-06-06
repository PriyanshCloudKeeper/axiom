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
// We no longer need JWT specific imports if SCIM path only uses static tokens
// import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
// import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint; // For a simple 401
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpStatus; // For HttpStatusEntryPoint

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private StaticTokenAuthenticationProvider staticTokenAuthenticationProvider;

    // AuthenticationManager that ONLY knows about StaticTokenAuthenticationProvider
    @Bean
    public AuthenticationManager staticTokenAuthenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.authenticationProvider(staticTokenAuthenticationProvider);
        return authenticationManagerBuilder.build();
    }

    @Bean
    @Order(1) // This filter chain is specifically for SCIM endpoints.
    public SecurityFilterChain scimFilterChain(HttpSecurity http) throws Exception {
        // Get the AuthenticationManager that only knows about static tokens
        AuthenticationManager scimAuthManager = staticTokenAuthenticationManager(http);

        StaticTokenAuthenticationFilter customStaticTokenFilter = new StaticTokenAuthenticationFilter(scimAuthManager);

        http
            .securityMatcher(new AntPathRequestMatcher("/scim/v2/**")) // Apply this chain ONLY to /scim/v2/**
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context
                .securityContextRepository(new DelegatingSecurityContextRepository(
                        new RequestAttributeSecurityContextRepository(),
                        new HttpSessionSecurityContextRepository()
                ))
            )
            .authorizeHttpRequests(authz -> authz
                .anyRequest().authenticated() // All requests to /scim/v2/** must be authenticated
                // .anyRequest().permitAll() 
            )
            // Add our custom static token filter. This is now the *only* bearer token auth filter for this chain.
            // We use a generic filter position or a standard one if it makes sense.
            // Since there's no BearerTokenAuthenticationFilter from oauth2ResourceServer for this chain,
            // we can add it at a standard position like UsernamePasswordAuthenticationFilter.class or any earlier standard filter.
            // For simplicity, using a known filter class position like `org.springframework.security.web.access.intercept.AuthorizationFilter.class`
            // ensures it runs before authorization decisions are made.
            .addFilterBefore(customStaticTokenFilter, org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            
            // NO .oauth2ResourceServer().jwt() for this scimFilterChain
            // This means JWTs will NOT be validated for /scim/v2/** paths by this chain.

            // Explicitly set the AuthenticationManager for this chain.
            .authenticationManager(scimAuthManager)

            // Define what happens if authentication fails (e.g., static token is bad/missing)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) // Simple 401
            );

        return http.build();
    }

    @Bean
    @Order(2) // This filter chain handles all other requests (like actuators)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().permitAll() // Adjust as needed
            );
        // If you had other paths that NEEDED JWT auth, you would configure .oauth2ResourceServer() here.
        return http.build();
    }

    // JwtAuthenticationConverter bean is no longer strictly needed by SecurityConfig if SCIM doesn't use JWT,
    // but other parts of your app might use it if you add JWT support elsewhere.
    // Keeping it doesn't hurt.
    // @Bean
    // public JwtAuthenticationConverter jwtAuthenticationConverter() {
    //     JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    //     return converter;
    // }
}