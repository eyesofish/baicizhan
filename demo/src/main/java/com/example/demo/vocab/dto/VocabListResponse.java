package com.example.demo.vocab.dto;

import java.time.LocalDateTime;

public record VocabListResponse(
    Long id,
    String name,
    String sourceLanguage,
    String targetLanguage,
    boolean isPublic,
    long itemCount,
    LocalDateTime updatedAt
) {
}
