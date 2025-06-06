package com.learnhai.scim.config.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class StaticTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;

    public StaticTokenAuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Corrected Check: Only skip if already properly authenticated (not anonymous)
        Authentication existingAuthentication = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuthentication != null && existingAuthentication.isAuthenticated() &&
            !(existingAuthentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) { // Be explicit
            if (logger.isDebugEnabled()) {
                logger.debug("SecurityContext already contains a non-anonymous, authenticated object ("+ existingAuthentication.getClass().getSimpleName() +"). Skipping StaticTokenAuthenticationFilter.");
            }
            filterChain.doFilter(request, response);
            return;
        }
        // If existingAuthentication is null, or not authenticated, or is Anonymous, proceed with filter logic.
        if (logger.isDebugEnabled() && existingAuthentication != null) {
            logger.debug("Existing authentication is: " + existingAuthentication.getClass().getSimpleName() + 
                         ", isAuthenticated: " + existingAuthentication.isAuthenticated() + ". Proceeding with StaticTokenAuthenticationFilter logic.");
        }


        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.toLowerCase().startsWith("bearer ")) {
            if (logger.isDebugEnabled()) {
                logger.debug("No Bearer token header found or not a Bearer token. Passing to next filter / default handling.");
            }
            // If no bearer token, this request will fail authorization later if auth is required,
            // triggering the AuthenticationEntryPoint.
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7); // "Bearer ".length()

        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Attempting static token authentication for token: " + token);
            }
            StaticBearerTokenAuthenticationToken authRequest = new StaticBearerTokenAuthenticationToken(token);
            // This AuthenticationManager only knows about StaticTokenAuthenticationProvider for this chain
            Authentication authResult = this.authenticationManager.authenticate(authRequest); 

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authResult);
            SecurityContextHolder.setContext(context);

            if (logger.isDebugEnabled()) {
                logger.debug("Static token authentication successful. Principal: " + authResult.getPrincipal() + 
                            ". SecurityContextHolder NOW CONTAINS: " + SecurityContextHolder.getContext().getAuthentication());
            }

            // Call the next filter in the chain
            filterChain.doFilter(request, response);

            // Log AFTER the rest of the chain has processed (if it returns here)
            if (logger.isDebugEnabled()) {
                logger.debug("StaticTokenAuthenticationFilter: AFTER filterChain.doFilter. Response status: " + response.getStatus() +
                            ". SecurityContextHolder NOW CONTAINS: " + SecurityContextHolder.getContext().getAuthentication());
            }

        } catch (AuthenticationException e) {
            // Static token authentication failed (e.g., token not found in config or malformed for static needs)
            if (logger.isDebugEnabled()) {
                logger.debug("Static token authentication failed: " + e.getMessage());
            }
            SecurityContextHolder.clearContext(); // Ensure context is clear on failure
            // Authentication failed. The exception will propagate, and Spring Security's
            // ExceptionTranslationFilter will eventually handle it using the configured AuthenticationEntryPoint
            // (HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED) for scimFilterChain).
            // We re-throw or let it propagate. For this filter, it's better to let it propagate
            // so Spring's machinery handles the response.
            // Or, you could call a failureHandler here, but HttpStatusEntryPoint is simpler for just a 401.
            // For now, let the exception propagate to be handled by Spring Security's main mechanisms.
            // The filter chain won't proceed further for this request in an authenticated state.
            // The framework will handle sending the 401.
            
            // To be explicit that this filter caused the failure that leads to entry point:
            // response.sendError(HttpStatus.UNAUTHORIZED.value(), e.getMessage()); // This is one way
            // Or rely on the configured AuthenticationEntryPoint for the chain:
            throw e; // Re-throw the exception to be caught by ExceptionTranslationFilter
        }
    }
}