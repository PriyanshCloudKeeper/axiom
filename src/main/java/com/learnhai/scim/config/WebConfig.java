package com.learnhai.scim.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // If you need CORS enabled
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Or more specific like "/scim/v2/**"
                .allowedOrigins("*") // Be more specific in production!
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false); // true if you need cookies/auth headers from browser
    }

    // Bean for customizing ObjectMapper if Spring Boot's auto-configuration isn't enough
    // Spring Boot auto-configures JavaTimeModule, so this might not be strictly necessary
    // unless you need other specific settings like FAIL_ON_UNKNOWN_PROPERTIES.
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // For Java 8 Date/Time (Instant, etc.)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE); // If your JSON uses snake_case
        return mapper;
    }
}