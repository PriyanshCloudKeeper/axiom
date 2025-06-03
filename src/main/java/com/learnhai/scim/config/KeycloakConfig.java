package com.learnhai.scim.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder; // Import for older Keycloak client versions if needed

@Configuration
public class KeycloakConfig {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm; // This is the realm for the admin client's service account

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Bean
    public Keycloak keycloak() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .grantType("client_credentials")
                .clientId(clientId)
                .clientSecret(clientSecret)
                // For newer Keycloak admin client versions that might need explicit Resteasy client:
                // .resteasyClient(ResteasyClientBuilder.newBuilder().connectionPoolSize(20).build()) // Example
                .build();
    }
}