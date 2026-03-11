package com.example.demo.term;

import com.example.demo.common.exception.AppException;
import com.example.demo.config.CacheProperties;
import com.example.demo.domain.entity.ExampleSentence;
import com.example.demo.domain.entity.Sense;
import com.example.demo.domain.entity.Term;
import com.example.demo.domain.entity.Translation;
import com.example.demo.domain.repository.ExampleSentenceRepository;
import com.example.demo.domain.repository.SenseRepository;
import com.example.demo.domain.repository.TermRepository;
import com.example.demo.domain.repository.TranslationRepository;
import com.example.demo.term.dto.TermDetailResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermService {
    private static final String NULL_TOKEN = "__NULL__";

    private final TermRepository termRepository;
    private final SenseRepository senseRepository;
    private final TranslationRepository translationRepository;
    private final ExampleSentenceRepository exampleSentenceRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;

    public TermService(
        TermRepository termRepository,
        SenseRepository senseRepository,
        TranslationRepository translationRepository,
        ExampleSentenceRepository exampleSentenceRepository,
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        CacheProperties cacheProperties
    ) {
        this.termRepository = termRepository;
        this.senseRepository = senseRepository;
        this.translationRepository = translationRepository;
        this.exampleSentenceRepository = exampleSentenceRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    @Transactional(readOnly = true)
    public TermDetailResponse getTermDetail(Long termId) {
        String key = "term:" + termId;
        TermDetailResponse fromCache = getCachedValue(key);
        if (fromCache != null) {
            return fromCache;
        }

        String lockKey = "lock:term:" + termId;
        boolean locked = tryLock(lockKey);
        if (!locked) {
            sleep(50);
            TermDetailResponse retryCache = getCachedValue(key);
            if (retryCache != null) {
                return retryCache;
            }
            return loadAndCache(termId, key);
        }
        try {
            TermDetailResponse retryCache = getCachedValue(key);
            if (retryCache != null) {
                return retryCache;
            }
            return loadAndCache(termId, key);
        } finally {
            deleteQuietly(lockKey);
        }
    }

    @Transactional(readOnly = true)
    public TermDetailResponse loadTermDetail(Long termId) {
        Term term = termRepository.findById(termId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4041, "TERM_NOT_FOUND"));

        List<Sense> senses = senseRepository.findByTermIdOrderByIdAsc(termId);
        List<Long> senseIds = senses.stream().map(Sense::getId).toList();
        Map<Long, List<Translation>> translationsBySense;
        Map<Long, List<ExampleSentence>> examplesBySense;
        if (senseIds.isEmpty()) {
            translationsBySense = Map.of();
            examplesBySense = Map.of();
        } else {
            translationsBySense = translationRepository.findBySenseIdIn(senseIds)
                .stream()
                .collect(Collectors.groupingBy(t -> t.getSense().getId()));
            examplesBySense = exampleSentenceRepository.findBySenseIdIn(senseIds)
                .stream()
                .collect(Collectors.groupingBy(e -> e.getSense().getId()));
        }

        List<TermDetailResponse.SenseDto> senseDtos = senses.stream().map(sense -> {
            List<TermDetailResponse.TranslationDto> translationDtos = translationsBySense
                .getOrDefault(sense.getId(), List.of())
                .stream()
                .map(t -> new TermDetailResponse.TranslationDto(
                    t.getId(),
                    t.getTargetLanguage().getIsoCode(),
                    t.getTranslatedText(),
                    t.getSourceType()
                ))
                .toList();
            List<TermDetailResponse.ExampleSentenceDto> exampleDtos = examplesBySense
                .getOrDefault(sense.getId(), List.of())
                .stream()
                .map(e -> new TermDetailResponse.ExampleSentenceDto(
                    e.getId(),
                    e.getLanguage().getIsoCode(),
                    e.getSentenceText(),
                    e.getSentenceTrans(),
                    e.getSourceType()
                ))
                .toList();
            return new TermDetailResponse.SenseDto(
                sense.getId(),
                sense.getPartOfSpeech(),
                sense.getDefinition(),
                translationDtos,
                exampleDtos
            );
        }).toList();

        return new TermDetailResponse(
            term.getId(),
            term.getText(),
            term.getNormalizedText(),
            term.getIpa(),
            term.getAudioUrl(),
            term.getLanguage().getIsoCode(),
            term.getUpdatedAt(),
            senseDtos
        );
    }

    public void evictTermCache(Long termId) {
        deleteQuietly("term:" + termId);
    }

    private TermDetailResponse loadAndCache(Long termId, String key) {
        TermDetailResponse detail;
        try {
            detail = loadTermDetail(termId);
        } catch (AppException ex) {
            if (ex.getStatus() == HttpStatus.NOT_FOUND) {
                setStringQuietly(key, NULL_TOKEN, Duration.ofSeconds(cacheProperties.term().nullTtlSeconds()));
            }
            throw ex;
        }
        setJsonQuietly(key, detail, Duration.ofMinutes(cacheProperties.term().ttlMinutes() + randomJitterMinutes()));
        return detail;
    }

    private int randomJitterMinutes() {
        return ThreadLocalRandom.current().nextInt(0, 6);
    }

    private boolean tryLock(String lockKey) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
            return Boolean.TRUE.equals(ok);
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private TermDetailResponse getCachedValue(String key) {
        String raw = getStringQuietly(key);
        if (raw == null) {
            return null;
        }
        if (NULL_TOKEN.equals(raw)) {
            throw new AppException(HttpStatus.NOT_FOUND, 4041, "TERM_NOT_FOUND");
        }
        try {
            return objectMapper.readValue(raw, TermDetailResponse.class);
        } catch (JsonProcessingException ex) {
            deleteQuietly(key);
            return null;
        }
    }

    private String getStringQuietly(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private void setStringQuietly(String key, String value, Duration duration) {
        try {
            redisTemplate.opsForValue().set(key, value, duration);
        } catch (DataAccessException ignored) {
            // ignore Redis outage, DB remains source of truth
        }
    }

    private void setJsonQuietly(String key, Object value, Duration duration) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, duration);
        } catch (DataAccessException | JsonProcessingException ignored) {
            // ignore Redis outage, DB remains source of truth
        }
    }

    private void deleteQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException ignored) {
            // ignore
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
