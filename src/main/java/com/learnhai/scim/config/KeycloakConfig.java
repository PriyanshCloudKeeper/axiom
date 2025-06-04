package com.learnhai.scim.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
// Use the concrete implementation of ResteasyClientBuilder
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakConfig {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Bean
    public Keycloak keycloak() {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ResteasyJackson2Provider jacksonProvider = new ResteasyJackson2Provider();
        jacksonProvider.setMapper(objectMapper);

        // Use the specific ResteasyClientBuilder implementation directly
        // This ResteasyClientBuilderImpl has the .connectionPoolSize() method
        // and its .build() method returns an org.jboss.resteasy.client.jaxrs.ResteasyClient
        ResteasyClient resteasyClient = new ResteasyClientBuilderImpl()
                .register(jacksonProvider)
                .connectionPoolSize(20) // This method should now be found
                .build();

        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .grantType("client_credentials")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .resteasyClient(resteasyClient) // Provide the ResteasyClient instance
                .build();
    }
}