package com.example.demo.review;

import com.example.demo.common.exception.AppException;
import com.example.demo.config.CacheProperties;
import com.example.demo.domain.entity.AppUser;
import com.example.demo.domain.entity.ReviewLog;
import com.example.demo.domain.entity.Term;
import com.example.demo.domain.entity.UserProgress;
import com.example.demo.domain.repository.AppUserRepository;
import com.example.demo.domain.repository.ReviewLogRepository;
import com.example.demo.domain.repository.TermRepository;
import com.example.demo.domain.repository.UserProgressRepository;
import com.example.demo.learning.LearningService;
import com.example.demo.review.dto.ReviewCardResponse;
import com.example.demo.review.dto.ReviewResultRequest;
import com.example.demo.review.dto.ReviewResultResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private static final TypeReference<List<ReviewCardResponse>> REVIEW_CARD_LIST = new TypeReference<>() {};

    private final AppUserRepository appUserRepository;
    private final TermRepository termRepository;
    private final UserProgressRepository userProgressRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final LearningService learningService;
    private final SpacedRepetitionScheduler spacedRepetitionScheduler;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;

    public ReviewService(
        AppUserRepository appUserRepository,
        TermRepository termRepository,
        UserProgressRepository userProgressRepository,
        ReviewLogRepository reviewLogRepository,
        LearningService learningService,
        SpacedRepetitionScheduler spacedRepetitionScheduler,
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        CacheProperties cacheProperties
    ) {
        this.appUserRepository = appUserRepository;
        this.termRepository = termRepository;
        this.userProgressRepository = userProgressRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.learningService = learningService;
        this.spacedRepetitionScheduler = spacedRepetitionScheduler;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    @Transactional
    public List<ReviewCardResponse> nextCards(Long userId, int limit) {
        String cacheKey = "user:" + userId + ":nextReview";
        List<ReviewCardResponse> cached = readCachedCards(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<ReviewCardResponse> cards = learningService.nextCards(userId, limit);
        writeCachedCards(cacheKey, cards);
        return cards;
    }

    @Transactional
    public ReviewResultResponse submit(Long userId, Long termId, ReviewResultRequest req) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4040, "USER_NOT_FOUND"));
        Term term = termRepository.findById(termId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4041, "TERM_NOT_FOUND"));

        UserProgress progress = userProgressRepository.findByUserIdAndTermId(userId, termId).orElseGet(() -> {
            UserProgress up = new UserProgress();
            up.setUser(user);
            up.setTerm(term);
            up.setNextReviewAt(LocalDateTime.now());
            return up;
        });

        ReviewLog log = new ReviewLog();
        log.setUser(user);
        log.setTerm(term);
        log.setRating((byte) req.rating());
        log.setElapsedMs(req.elapsedMs());
        reviewLogRepository.save(log);

        spacedRepetitionScheduler.apply(progress, req.rating(), LocalDateTime.now());
        userProgressRepository.save(progress);
        updateOnlineFeatureCache(userId, termId, req.rating());
        evictUserReviewCache(userId);
        return new ReviewResultResponse(
            termId,
            req.rating(),
            progress.getEaseFactor(),
            progress.getIntervalDays(),
            progress.getRepetition(),
            progress.getNextReviewAt()
        );
    }

    private List<ReviewCardResponse> readCachedCards(String key) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, REVIEW_CARD_LIST);
        } catch (DataAccessException | JsonProcessingException ex) {
            return null;
        }
    }

    private void writeCachedCards(String key, List<ReviewCardResponse> cards) {
        try {
            String raw = objectMapper.writeValueAsString(cards);
            int jitter = ThreadLocalRandom.current().nextInt(0, 6);
            redisTemplate.opsForValue()
                .set(key, raw, Duration.ofSeconds(cacheProperties.review().ttlSeconds() + jitter));
        } catch (DataAccessException | JsonProcessingException ignored) {
            // ignore
        }
    }

    private void updateOnlineFeatureCache(Long userId, Long termId, int rating) {
        String wrongCountKey = "user:" + userId + ":wrong30d";
        String lastRatingKey = "user:" + userId + ":lastRating";
        String field = termId.toString();
        try {
            redisTemplate.opsForHash().put(lastRatingKey, field, Integer.toString(rating));
            redisTemplate.expire(lastRatingKey, Duration.ofDays(30));
            if (rating <= 2) {
                redisTemplate.opsForHash().increment(wrongCountKey, field, 1);
                redisTemplate.expire(wrongCountKey, Duration.ofDays(30));
            }
        } catch (DataAccessException ignored) {
            // ignore Redis outage, DB remains source of truth
        }
    }

    private void evictUserReviewCache(Long userId) {
        try {
            redisTemplate.delete("user:" + userId + ":nextReview");
        } catch (DataAccessException ignored) {
            // ignore
        }
    }
}
