package com.example.demo.ai.dto;

import jakarta.validation.constraints.NotNull;

public record CreateAiJobRequest(
    @NotNull Long termId,
    String targetLang
) {
}
