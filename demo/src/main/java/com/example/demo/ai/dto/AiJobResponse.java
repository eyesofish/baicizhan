package com.example.demo.ai.dto;

import java.time.LocalDateTime;

public record AiJobResponse(
    Long jobId,
    String status,
    Long termId,
    String openaiResponseId,
    String requestJson,
    String resultJson,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
