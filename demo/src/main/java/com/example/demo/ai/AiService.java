package com.example.demo.ai;

import com.example.demo.ai.dto.AiJobQueuedResponse;
import com.example.demo.ai.dto.AiJobResponse;
import com.example.demo.ai.dto.CreateAiJobRequest;
import com.example.demo.common.exception.AppException;
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
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {
    private final AppUserRepository appUserRepository;
    private final TermRepository termRepository;
    private final AiJobRepository aiJobRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AiService(
        AppUserRepository appUserRepository,
        TermRepository termRepository,
        AiJobRepository aiJobRepository,
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper
    ) {
        this.appUserRepository = appUserRepository;
        this.termRepository = termRepository;
        this.aiJobRepository = aiJobRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiJobQueuedResponse createEnrichJob(Long userId, CreateAiJobRequest req) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4040, "USER_NOT_FOUND"));
        Term term = termRepository.findById(req.termId())
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4041, "TERM_NOT_FOUND"));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("termId", req.termId());
        request.put("sourceLang", term.getLanguage().getIsoCode());
        request.put("targetLang", req.targetLang() == null || req.targetLang().isBlank() ? "zh-Hans" : req.targetLang());
        request.put("jobType", "ENRICH_TERM");

        AiJob job = new AiJob();
        job.setUser(user);
        job.setTerm(term);
        job.setJobType("ENRICH_TERM");
        job.setStatus(AiJobStatus.QUEUED);
        job.setRequestJson(toJson(request));
        aiJobRepository.save(job);

        publishAiJob(job, request);
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

    private void publishAiJob(AiJob job, Map<String, Object> request) {
        try {
            MapRecord<String, String, String> record = MapRecord.create("stream:ai_jobs", Map.of(
                "jobId", String.valueOf(job.getId()),
                "termId", String.valueOf(job.getTerm().getId()),
                "request", toJson(request)
            ));
            RecordId id = redisTemplate.opsForStream().add(record);
            if (id != null) {
                // no-op
            }
        } catch (DataAccessException ignored) {
            // Redis unavailable, job state in DB still QUEUED
        }
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
}
