package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.learning")
public record LearningProperties(
    Recall recall,
    ReRanking reRanking,
    Ann ann
) {
    public record Recall(
        int reviewQuota,
        int hardQuota,
        int newQuota,
        int totalCandidates,
        int recentTermsForUserVector
    ) {
    }

    public record ReRanking(
        int newQuota,
        int reviewQuota,
        int hardQuota,
        int maxConsecutiveSameSource
    ) {
    }

    public record Ann(
        boolean enabled,
        String baseUrl,
        int timeoutMs,
        int topK
    ) {
    }
}
