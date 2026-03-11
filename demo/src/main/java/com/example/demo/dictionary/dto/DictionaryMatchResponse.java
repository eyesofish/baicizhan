package com.example.demo.dictionary.dto;

import java.util.List;

public record DictionaryMatchResponse(Results results) {
    public record Results(List<String> data) {
    }
}
