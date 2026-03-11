package com.example.demo.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReviewResultRequest(
    @Min(0) @Max(5) int rating,
    Integer elapsedMs
) {
}
