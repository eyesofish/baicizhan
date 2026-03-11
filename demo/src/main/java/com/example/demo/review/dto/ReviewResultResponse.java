package com.example.demo.review.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewResultResponse(
    Long termId,
    int rating,
    BigDecimal easeFactor,
    int intervalDays,
    int repetition,
    LocalDateTime nextReviewAt
) {
}
