package com.learnhai.scim.config.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class StaticBearerTokenAuthenticationToken extends AbstractAuthenticationToken {

    private final String token;
    private Object principal;

    // Constructor for an unauthenticated token (before validation)
    public StaticBearerTokenAuthenticationToken(String token) {
        super(null);
        this.token = token;
        setAuthenticated(false);
    }

    // Constructor for an authenticated token (after validation)
    public StaticBearerTokenAuthenticationToken(Object principal, String token, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = token; // Store the token for auditing or logging if needed
        super.setAuthenticated(true); // Must call super
    }

    @Override
    public Object getCredentials() {
        return token; // The token itself is the credential
    }

    @Override
    public Object getPrincipal() {
        return principal; // The IdP identifier after successful authentication
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated) {
            throw new IllegalArgumentException(
                    "Cannot set this token to authenticated directly; use constructor");
        }
        super.setAuthenticated(false);
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        // If you want to clear the token string after auth, but it might be useful for logging.
        // For static tokens, "erasing" isn't as critical as passwords.
    }
}