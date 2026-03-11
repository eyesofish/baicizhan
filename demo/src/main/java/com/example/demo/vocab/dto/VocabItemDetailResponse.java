package com.example.demo.vocab.dto;

import java.time.LocalDateTime;
import java.util.List;

public record VocabItemDetailResponse(
    Long listId,
    Long itemId,
    Long termId,
    Long senseId,
    String word,
    String pronunciation,
    String partOfSpeech,
    String definition,
    List<String> examples,
    int points,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime lastReviewed
) {
}
