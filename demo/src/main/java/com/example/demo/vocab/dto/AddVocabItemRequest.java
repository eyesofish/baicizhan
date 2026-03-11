package com.example.demo.vocab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddVocabItemRequest(
    @NotBlank @Size(max = 255) String text,
    @Size(max = 32) String partOfSpeech,
    String definition,
    @Size(max = 512) String translation,
    String example,
    @Size(max = 128) String ipa,
    @Size(max = 512) String audioUrl
) {
}
