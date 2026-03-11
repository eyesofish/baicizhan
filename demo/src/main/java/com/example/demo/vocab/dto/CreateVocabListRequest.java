package com.example.demo.vocab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVocabListRequest(
    @NotBlank @Size(max = 64) String name,
    @NotBlank String sourceLanguage,
    @NotBlank String targetLanguage,
    Boolean isPublic
) {
}
