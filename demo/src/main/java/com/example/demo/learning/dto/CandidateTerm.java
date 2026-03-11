package com.example.demo.learning.dto;

public record CandidateTerm(
    Long termId,
    CandidateSource source,
    double recallScore
) {
}
