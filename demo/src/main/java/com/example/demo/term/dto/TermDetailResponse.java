package com.example.demo.term.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TermDetailResponse(
    Long id,
    String text,
    String normalizedText,
    String ipa,
    String audioUrl,
    String language,
    LocalDateTime updatedAt,
    List<SenseDto> senses
) {
    public record SenseDto(
        Long id,
        String partOfSpeech,
        String definition,
        List<TranslationDto> translations,
        List<ExampleSentenceDto> examples
    ) {
    }

    public record TranslationDto(Long id, String targetLanguage, String translatedText, String sourceType) {
    }

    public record ExampleSentenceDto(Long id, String language, String sentenceText, String sentenceTrans, String sourceType) {
    }
}
