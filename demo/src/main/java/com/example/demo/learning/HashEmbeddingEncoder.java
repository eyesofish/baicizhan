package com.example.demo.learning;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HashEmbeddingEncoder {
    private static final int DIM = 300;

    public float[] encode(String text) {
        float[] out = new float[DIM];
        if (text == null || text.isBlank()) {
            return out;
        }
        for (int i = 0; i < text.length(); i++) {
            int cp = Character.toLowerCase(text.charAt(i));
            int hash = Integer.rotateLeft(cp * 0x45d9f3b, i % 17);
            int index = Math.floorMod(hash, DIM);
            float sign = (hash & 1) == 0 ? 1.0F : -1.0F;
            out[index] += sign;
        }
        normalize(out);
        return out;
    }

    public float[] average(List<String> recentTerms) {
        if (recentTerms == null || recentTerms.isEmpty()) {
            return null;
        }
        float[] userVector = new float[DIM];
        double weightSum = 0.0D;
        for (int i = 0; i < recentTerms.size(); i++) {
            float[] termVector = encode(recentTerms.get(i));
            double weight = Math.pow(0.9D, i);
            weightSum += weight;
            for (int d = 0; d < DIM; d++) {
                userVector[d] += (float) (termVector[d] * weight);
            }
        }
        if (weightSum <= 0.0D) {
            return null;
        }
        normalize(userVector);
        return userVector;
    }

    private void normalize(float[] vector) {
        double norm = 0.0D;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm <= 0.0D) {
            return;
        }
        float invNorm = (float) (1.0D / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= invNorm;
        }
    }
}
