package com.example.demo.ai;

import com.example.demo.ai.dto.AiJobQueuedResponse;
import com.example.demo.ai.dto.AiJobResponse;
import com.example.demo.ai.dto.CreateAiJobRequest;
import com.example.demo.common.exception.AppException;
import com.example.demo.config.AiProperties;
import com.example.demo.domain.entity.AiJob;
import com.example.demo.domain.entity.AppUser;
import com.example.demo.domain.entity.Term;
import com.example.demo.domain.enums.AiJobStatus;
import com.example.demo.domain.repository.AiJobRepository;
import com.example.demo.domain.repository.AppUserRepository;
import com.example.demo.domain.repository.TermRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {
    private final AppUserRepository appUserRepository;
    private final TermRepository termRepository;
    private final AiJobRepository aiJobRepository;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiService(
        AppUserRepository appUserRepository,
        TermRepository termRepository,
        AiJobRepository aiJobRepository,
        AiProperties aiProperties,
        ObjectMapper objectMapper
    ) {
        this.appUserRepository = appUserRepository;
        this.termRepository = termRepository;
        this.aiJobRepository = aiJobRepository;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        int timeoutSec = Math.max(5, aiProperties.timeoutSeconds());
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSec)).build();
    }

    @Transactional
    public AiJobQueuedResponse createEnrichJob(Long userId, CreateAiJobRequest req) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4040, "USER_NOT_FOUND"));
        Term term = termRepository.findById(req.termId())
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4041, "TERM_NOT_FOUND"));

        String targetLang = req.targetLang() == null || req.targetLang().isBlank() ? "zh-Hans" : req.targetLang().trim();
        Map<String, Object> llmRequest = buildLlmRequest(term, targetLang);

        AiJob job = new AiJob();
        job.setUser(user);
        job.setTerm(term);
        job.setJobType("ENRICH_TERM");
        job.setStatus(AiJobStatus.QUEUED);
        job.setRequestJson(toJson(llmRequest));
        aiJobRepository.save(job);

        runLocalJob(job, llmRequest);
        return new AiJobQueuedResponse(job.getId());
    }

    @Transactional(readOnly = true)
    public AiJobResponse getJob(Long userId, Long jobId) {
        AiJob job = aiJobRepository.findByIdAndUserId(jobId, userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4043, "AI_JOB_NOT_FOUND"));
        return toResponse(job);
    }

    @Transactional
    public void handleOpenAiWebhook(String rawBody, Map<String, String> headers) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException ex) {
            return;
        }

        String eventType = textOrNull(root.path("type"));
        String responseId = textOrNull(root.path("data").path("id"));
        if (responseId == null) {
            responseId = textOrNull(root.path("response_id"));
        }
        if (responseId == null) {
            responseId = headers.getOrDefault("x-openai-response-id", null);
        }
        if (responseId == null || responseId.isBlank()) {
            return;
        }
        aiJobRepository.findByOpenaiResponseId(responseId).ifPresent(job -> {
            if ("response.failed".equalsIgnoreCase(eventType)) {
                job.setStatus(AiJobStatus.FAILED);
                job.setErrorMessage(rawBody);
            } else {
                job.setStatus(AiJobStatus.SUCCEEDED);
                job.setResultJson(rawBody);
            }
            aiJobRepository.save(job);
        });
    }

    private void runLocalJob(AiJob job, Map<String, Object> llmRequest) {
        job.setStatus(AiJobStatus.RUNNING);
        aiJobRepository.save(job);
        try {
            LlmResult llmResult = invokeLocalLlm(llmRequest);
            job.setOpenaiResponseId(llmResult.responseId());
            job.setResultJson(llmResult.rawResponse());
            job.setStatus(AiJobStatus.SUCCEEDED);
            job.setErrorMessage(null);
        } catch (Exception ex) {
            job.setStatus(AiJobStatus.FAILED);
            job.setErrorMessage(shortMessage(ex.getMessage()));
        }
        aiJobRepository.save(job);
    }

    private LlmResult invokeLocalLlm(Map<String, Object> llmRequest) {
        String endpoint = normalizeBaseUrl(aiProperties.baseUrl()) + "/chat/completions";
        String requestBody = toJson(llmRequest);
        String apiKey = aiProperties.apiKey() == null || aiProperties.apiKey().isBlank()
            ? "LOCAL_DUMMY_KEY"
            : aiProperties.apiKey().trim();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(Math.max(5, aiProperties.timeoutSeconds())))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AppException(HttpStatus.BAD_GATEWAY, 5021, "LOCAL_LLM_HTTP_" + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String responseId = textOrNull(root.path("id"));
            String content = textOrNull(root.path("choices").path(0).path("message").path("content"));
            if (content == null) {
                throw new AppException(HttpStatus.BAD_GATEWAY, 5022, "LOCAL_LLM_EMPTY_RESPONSE");
            }
            return new LlmResult(responseId, response.body());
        } catch (IOException ex) {
            throw new AppException(HttpStatus.BAD_GATEWAY, 5023, "LOCAL_LLM_IO_ERROR");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(HttpStatus.BAD_GATEWAY, 5024, "LOCAL_LLM_INTERRUPTED");
        }
    }

    private Map<String, Object> buildLlmRequest(Term term, String targetLang) {
        String model = aiProperties.model() == null || aiProperties.model().isBlank()
            ? "qwen2.5-7b-instruct"
            : aiProperties.model().trim();

        Map<String, String> systemMessage = Map.of(
            "role",
            "system",
            "content",
            "You are a vocabulary enrichment assistant. Return strict JSON only with keys: partOfSpeech, definition, translation, example."
        );
        Map<String, String> userMessage = Map.of(
            "role",
            "user",
            "content",
            buildPrompt(term, targetLang)
        );

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", List.of(systemMessage, userMessage));
        request.put("temperature", aiProperties.temperature());
        request.put("max_tokens", aiProperties.maxTokens());
        return request;
    }

    private String buildPrompt(Term term, String targetLang) {
        return String.format(
            "term=%s%nsrcLang=%s%ntargetLang=%s%nReturn JSON with keys: partOfSpeech, definition, translation, example.",
            term.getText(),
            term.getLanguage().getIsoCode(),
            targetLang
        );
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = (baseUrl == null || baseUrl.isBlank()) ? "http://127.0.0.1:18000/v1" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String shortMessage(String message) {
        if (message == null || message.isBlank()) {
            return "LOCAL_LLM_FAILED";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, 5001, "JSON_SERIALIZE_FAILED");
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String v = node.asText();
        return v == null || v.isBlank() ? null : v;
    }

    private AiJobResponse toResponse(AiJob job) {
        return new AiJobResponse(
            job.getId(),
            job.getStatus().name(),
            job.getTerm() == null ? null : job.getTerm().getId(),
            job.getOpenaiResponseId(),
            job.getRequestJson(),
            job.getResultJson(),
            job.getErrorMessage(),
            job.getCreatedAt(),
            job.getUpdatedAt()
        );
    }

    private record LlmResult(String responseId, String rawResponse) {
    }
}
