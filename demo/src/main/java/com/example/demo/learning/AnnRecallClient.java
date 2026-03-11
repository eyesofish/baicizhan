package com.example.demo.learning;

import com.example.demo.config.LearningProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AnnRecallClient {
    private static final Logger log = LoggerFactory.getLogger(AnnRecallClient.class);

    private final LearningProperties learningProperties;
    private final RestTemplate restTemplate;

    public AnnRecallClient(LearningProperties learningProperties) {
        this.learningProperties = learningProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(learningProperties.ann().timeoutMs());
        requestFactory.setReadTimeout(learningProperties.ann().timeoutMs());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public List<AnnResult> search(float[] userVector, int topK, Set<Long> excludeTermIds) {
        if (!learningProperties.ann().enabled() || userVector == null || userVector.length == 0 || topK <= 0) {
            return List.of();
        }
        List<Float> payloadVector = new ArrayList<>(userVector.length);
        for (float value : userVector) {
            payloadVector.add(value);
        }
        List<Long> excludes = new ArrayList<>(excludeTermIds == null ? Set.<Long>of() : new LinkedHashSet<>(excludeTermIds));
        SimilarityRequest request = new SimilarityRequest(payloadVector, topK, excludes);
        try {
            String endpoint = learningProperties.ann().baseUrl() + "/similarity_search";
            SimilarityResponse[] response = restTemplate.postForObject(endpoint, request, SimilarityResponse[].class);
            if (response == null || response.length == 0) {
                return List.of();
            }
            List<AnnResult> out = new ArrayList<>(response.length);
            for (SimilarityResponse item : response) {
                if (item == null || item.termId() == null) {
                    continue;
                }
                out.add(new AnnResult(item.termId(), item.similarity()));
            }
            return out;
        } catch (RestClientException ex) {
            log.warn("ANN recall unavailable, fallback to rule recall only: {}", ex.getMessage());
            return List.of();
        }
    }

    public record AnnResult(Long termId, double similarity) {
    }

    private record SimilarityRequest(
        @JsonProperty("user_vector") List<Float> userVector,
        @JsonProperty("top_k") int topK,
        @JsonProperty("exclude_term_ids") List<Long> excludeTermIds
    ) {
    }

    private record SimilarityResponse(
        @JsonProperty("term_id") Long termId,
        float similarity
    ) {
    }
}
