package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(Term term, Review review) {
    public record Term(int ttlMinutes, int nullTtlSeconds) {
    }

    public record Review(int ttlSeconds) {
    }
}
