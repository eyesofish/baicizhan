package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    String baseUrl,
    String apiKey,
    String model,
    double temperature,
    int maxTokens,
    int timeoutSeconds
) {
}
