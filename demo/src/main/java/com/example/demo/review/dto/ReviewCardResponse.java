package com.example.demo.review.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewCardResponse(
    Long termId,
    String text,
    String language,
    BigDecimal easeFactor,
    int intervalDays,
    int repetition,
    LocalDateTime nextReviewAt
) {
}
