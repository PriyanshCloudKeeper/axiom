package com.learnhai.scim.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "scim.bridge.auth")
@Data
public class StaticTokenConfig {

    private List<StaticClientToken> staticTokens = new ArrayList<>();

    @Data
    public static class StaticClientToken {
        private String idpName;
        private String token;
    }
}