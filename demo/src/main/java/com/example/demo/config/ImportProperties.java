package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.import")
public record ImportProperties(
    boolean enabled,
    String sourceFile,
    int limit,
    String sourceLanguage,
    String targetLanguage,
    boolean dryRun,
    boolean exitOnFinish
) {
}
