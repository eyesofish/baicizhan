package com.example.demo.dictionary.dto;

import java.util.List;

public record DictionaryWordResponse(
    Long termId,
    String word,
    Pronunciation pronunciation,
    List<ResultItem> results
) {
    public record Pronunciation(String all) {
    }

    public record ResultItem(
        Long id,
        String definition,
        String partOfSpeech,
        List<String> synonyms,
        List<String> antonyms,
        List<String> examples
    ) {
    }
}
