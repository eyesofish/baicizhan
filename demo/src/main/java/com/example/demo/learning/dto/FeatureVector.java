package com.example.demo.learning.dto;

import java.util.Arrays;

public record FeatureVector(
    Long termId,
    String termText,
    CandidateSource source,
    float[] features
) {
    public FeatureVector {
        features = features == null ? new float[0] : Arrays.copyOf(features, features.length);
    }

    @Override
    public float[] features() {
        return Arrays.copyOf(features, features.length);
    }
}
