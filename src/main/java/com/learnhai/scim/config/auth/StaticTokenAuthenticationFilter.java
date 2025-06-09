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

        Authentication existingAuthentication = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuthentication != null && existingAuthentication.isAuthenticated() &&
            !(existingAuthentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            if (logger.isDebugEnabled()) {
                logger.debug("SecurityContext already contains a non-anonymous, authenticated object ("+ existingAuthentication.getClass().getSimpleName() +"). Skipping StaticTokenAuthenticationFilter.");
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (logger.isDebugEnabled() && existingAuthentication != null) {
            logger.debug("Existing authentication is: " + existingAuthentication.getClass().getSimpleName() + 
                         ", isAuthenticated: " + existingAuthentication.isAuthenticated() + ". Proceeding with StaticTokenAuthenticationFilter logic.");
        }


        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.toLowerCase().startsWith("bearer ")) {
            if (logger.isDebugEnabled()) {
                logger.debug("No Bearer token header found or not a Bearer token. Passing to next filter / default handling.");
            }
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Attempting static token authentication for token: " + token);
            }
            StaticBearerTokenAuthenticationToken authRequest = new StaticBearerTokenAuthenticationToken(token);
            Authentication authResult = this.authenticationManager.authenticate(authRequest); 

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authResult);
            SecurityContextHolder.setContext(context);

            if (logger.isDebugEnabled()) {
                logger.debug("Static token authentication successful. Principal: " + authResult.getPrincipal() + 
                            ". SecurityContextHolder NOW CONTAINS: " + SecurityContextHolder.getContext().getAuthentication());
            }

            filterChain.doFilter(request, response);

            if (logger.isDebugEnabled()) {
                logger.debug("StaticTokenAuthenticationFilter: AFTER filterChain.doFilter. Response status: " + response.getStatus() +
                            ". SecurityContextHolder NOW CONTAINS: " + SecurityContextHolder.getContext().getAuthentication());
            }

        } catch (AuthenticationException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Static token authentication failed: " + e.getMessage());
            }
            SecurityContextHolder.clearContext();
            throw e;
        }
    }
}