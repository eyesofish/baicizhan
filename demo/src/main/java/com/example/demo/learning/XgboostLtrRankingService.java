package com.example.demo.learning;

import com.example.demo.learning.dto.FeatureVector;
import com.example.demo.learning.dto.RankedTerm;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class XgboostLtrRankingService implements RankingService {
    private static final Logger log = LoggerFactory.getLogger(XgboostLtrRankingService.class);
    private static final List<String> DEFAULT_FEATURE_SCHEMA = List.of(
        "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10"
    );

    private final ObjectMapper objectMapper;
    private List<String> featureSchema = DEFAULT_FEATURE_SCHEMA;
    private float[] weights = new float[] {1.8F, 0.6F, 0.2F, 1.5F, -0.4F, -0.3F, -0.4F, -0.0006F, 0.2F, 0.05F};
    private float bias = 0.0F;

    public XgboostLtrRankingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.featureSchema = loadFeatureSchema();
        LinearModel model = loadLinearModel(featureSchema.size());
        if (model.featureNames != null && !model.featureNames.equals(featureSchema)) {
            throw new IllegalStateException("LTR feature schema mismatch between model and runtime schema");
        }
        this.weights = model.weights;
        this.bias = model.bias;
        log.info("LTR ranking initialized with schema_size={} model_path=models/ltr_xgb.json", featureSchema.size());
    }

    @Override
    public List<RankedTerm> rank(List<FeatureVector> featureVectors) {
        if (featureVectors == null || featureVectors.isEmpty()) {
            return List.of();
        }
        List<RankedTerm> ranked = new ArrayList<>(featureVectors.size());
        for (FeatureVector vector : featureVectors) {
            float[] features = vector.features();
            if (features.length != featureSchema.size()) {
                throw new IllegalArgumentException(
                    "Feature size mismatch, expected " + featureSchema.size() + " but got " + features.length
                );
            }
            float score = bias;
            for (int i = 0; i < features.length; i++) {
                score += features[i] * weights[i];
            }
            ranked.add(new RankedTerm(vector.termId(), vector.termText(), vector.source(), score));
        }
        ranked.sort(Comparator.comparing(RankedTerm::score).reversed());
        return ranked;
    }

    private List<String> loadFeatureSchema() {
        Path path = Paths.get("models", "ltr_feature_schema.json");
        if (Files.exists(path)) {
            try {
                return objectMapper.readValue(path.toFile(), new TypeReference<>() {});
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to load models/ltr_feature_schema.json", ex);
            }
        }
        log.warn("models/ltr_feature_schema.json not found, fallback to default f1...f10");
        return DEFAULT_FEATURE_SCHEMA;
    }

    private LinearModel loadLinearModel(int expectedFeatures) {
        Path modelPath = Paths.get("models", "ltr_xgb.json");
        if (!Files.exists(modelPath)) {
            log.error("models/ltr_xgb.json not found, fallback to built-in ranking weights");
            return new LinearModel(weights, bias, featureSchema);
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(modelPath.toFile(), new TypeReference<>() {});
            float loadedBias = raw.containsKey("bias") ? ((Number) raw.get("bias")).floatValue() : 0.0F;
            List<Number> loadedWeights = (List<Number>) raw.get("weights");
            if (loadedWeights == null || loadedWeights.size() != expectedFeatures) {
                throw new IllegalStateException("weights length must match feature schema length");
            }
            float[] parsedWeights = new float[loadedWeights.size()];
            for (int i = 0; i < loadedWeights.size(); i++) {
                parsedWeights[i] = loadedWeights.get(i).floatValue();
            }
            List<String> names = null;
            if (raw.get("featureNames") instanceof List<?> fn) {
                names = fn.stream().map(Object::toString).toList();
            }
            return new LinearModel(parsedWeights, loadedBias, names);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load models/ltr_xgb.json", ex);
        }
    }

    private record LinearModel(float[] weights, float bias, List<String> featureNames) {
    }
}
