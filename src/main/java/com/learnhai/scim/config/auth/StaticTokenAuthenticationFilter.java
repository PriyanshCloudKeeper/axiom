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

        // Only process if no authentication is already in the context
        if (SecurityContextHolder.getContext().getAuthentication() != null &&
            SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            if (logger.isDebugEnabled()) {
                logger.debug("SecurityContext already contains an authentication object. Skipping StaticTokenAuthenticationFilter.");
            }
            filterChain.doFilter(request, response);
            return;
        }

        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.toLowerCase().startsWith("bearer ")) {
            if (logger.isDebugEnabled()) {
                logger.debug("No Bearer token header found. Skipping StaticTokenAuthenticationFilter.");
            }
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7); // "Bearer ".length()

        // Heuristic: If token contains '.', assume it's a JWT and let JWT filter handle it.
        // Otherwise, attempt static token authentication.
        if (token.contains(".")) {
            if (logger.isDebugEnabled()) {
                logger.debug("Token contains '.' suggesting JWT. Skipping static token auth, passing to JWT filter for token: " + token);
            }
            filterChain.doFilter(request, response);
            return;
        }

        // Token does not contain '.', attempt static token authentication
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Attempting static token authentication for token: " + token);
            }
            StaticBearerTokenAuthenticationToken authRequest = new StaticBearerTokenAuthenticationToken(token);
            Authentication authResult = this.authenticationManager.authenticate(authRequest); // This uses StaticTokenAuthenticationProvider

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authResult);
            SecurityContextHolder.setContext(context);

            if (logger.isDebugEnabled()) {
                logger.debug("Static token authentication successful. Principal: " + authResult.getPrincipal());
            }
            // After successful static auth, proceed down the chain.
            // The BearerTokenAuthenticationFilter should now see an authenticated context.
            filterChain.doFilter(request, response);

        } catch (AuthenticationException e) {
            // Static token authentication failed (e.g., token not found in config)
            if (logger.isDebugEnabled()) {
                logger.debug("Static token authentication failed: " + e.getMessage());
            }
            // Clear context just in case, though it should be if auth failed
            SecurityContextHolder.clearContext();
            // Proceed the chain. If this was a bad static token, the request will ultimately fail
            // with a 401 from the main security exception handling if no other auth succeeds (e.g. JWT).
            filterChain.doFilter(request, response);
        }
    }
}