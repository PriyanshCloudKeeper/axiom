package com.learnhai.scim.config.auth;

import com.learnhai.scim.config.StaticTokenConfig;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;

@Component
public class StaticTokenAuthenticationProvider implements AuthenticationProvider {

    private final StaticTokenConfig staticTokenConfig;

    public StaticTokenAuthenticationProvider(StaticTokenConfig staticTokenConfig) {
        this.staticTokenConfig = staticTokenConfig;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String presentedToken = (String) authentication.getCredentials();

        Optional<StaticTokenConfig.StaticClientToken> matchedToken = staticTokenConfig.getStaticTokens().stream()
                .filter(configToken -> secureCompare(configToken.getToken(), presentedToken))
                .findFirst();

        if (matchedToken.isPresent()) {
            return new StaticBearerTokenAuthenticationToken(
                    matchedToken.get().getIdpName(),
                    presentedToken,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_SCIM_CLIENT_STATIC"))
            );
        } else {
            throw new BadCredentialsException("Invalid static token");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return StaticBearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private boolean secureCompare(String configuredToken, String presentedToken) {
        if (configuredToken == null || presentedToken == null) {
            return false;
        }
        if (configuredToken.length() != presentedToken.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < configuredToken.length(); i++) {
            result |= configuredToken.charAt(i) ^ presentedToken.charAt(i);
        }
        return result == 0;
    }
}