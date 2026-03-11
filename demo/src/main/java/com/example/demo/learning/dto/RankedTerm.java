package com.example.demo.learning.dto;

public record RankedTerm(
    Long termId,
    String termText,
    CandidateSource source,
    float score
) {
}
