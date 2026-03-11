package com.example.demo.review;

import com.example.demo.common.exception.AppException;
import com.example.demo.config.CacheProperties;
import com.example.demo.domain.entity.*;
import com.example.demo.domain.repository.*;
import com.example.demo.review.dto.ReviewCardResponse;
import com.example.demo.review.dto.ReviewResultRequest;
import com.example.demo.review.dto.ReviewResultResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
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
    private final VocabItemRepository vocabItemRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;

    public ReviewService(
        AppUserRepository appUserRepository,
        TermRepository termRepository,
        UserProgressRepository userProgressRepository,
        ReviewLogRepository reviewLogRepository,
        VocabItemRepository vocabItemRepository,
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        CacheProperties cacheProperties
    ) {
        this.appUserRepository = appUserRepository;
        this.termRepository = termRepository;
        this.userProgressRepository = userProgressRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.vocabItemRepository = vocabItemRepository;
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

        List<UserProgress> due = userProgressRepository.findDueReviews(userId, LocalDateTime.now(), PageRequest.of(0, limit));
        if (due.isEmpty()) {
            bootstrapProgress(userId, limit);
            due = userProgressRepository.findDueReviews(userId, LocalDateTime.now(), PageRequest.of(0, limit));
        }

        List<ReviewCardResponse> cards = due.stream().map(up -> new ReviewCardResponse(
            up.getTerm().getId(),
            up.getTerm().getText(),
            up.getTerm().getLanguage().getIsoCode(),
            up.getEaseFactor(),
            up.getIntervalDays(),
            up.getRepetition(),
            up.getNextReviewAt()
        )).toList();
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

        updateSrs(progress, req.rating());
        userProgressRepository.save(progress);
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

    private void bootstrapProgress(Long userId, int limit) {
        List<Long> termIds = vocabItemRepository.findTermIdsWithoutProgress(userId, PageRequest.of(0, limit));
        if (termIds.isEmpty()) {
            return;
        }
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4040, "USER_NOT_FOUND"));
        for (Long termId : termIds) {
            Term term = termRepository.findById(termId).orElse(null);
            if (term == null) {
                continue;
            }
            if (userProgressRepository.findByUserIdAndTermId(userId, termId).isPresent()) {
                continue;
            }
            UserProgress up = new UserProgress();
            up.setUser(user);
            up.setTerm(term);
            up.setEaseFactor(BigDecimal.valueOf(2.50D));
            up.setIntervalDays(0);
            up.setRepetition(0);
            up.setNextReviewAt(LocalDateTime.now());
            userProgressRepository.save(up);
        }
    }

    private void updateSrs(UserProgress progress, int rating) {
        BigDecimal ease = progress.getEaseFactor() == null ? BigDecimal.valueOf(2.50D) : progress.getEaseFactor();
        int repetition = progress.getRepetition() == null ? 0 : progress.getRepetition();
        int interval = progress.getIntervalDays() == null ? 0 : progress.getIntervalDays();

        if (rating < 3) {
            repetition = 0;
            interval = 1;
            ease = ease.subtract(BigDecimal.valueOf(0.20D));
        } else {
            if (repetition == 0) {
                interval = 1;
            } else if (repetition == 1) {
                interval = 6;
            } else {
                interval = Math.max(1, Math.round(interval * ease.floatValue()));
            }
            repetition += 1;
            double penalty = (5 - rating) * (0.08D + (5 - rating) * 0.02D);
            ease = ease.add(BigDecimal.valueOf(0.1D - penalty));
        }
        if (ease.compareTo(BigDecimal.valueOf(1.30D)) < 0) {
            ease = BigDecimal.valueOf(1.30D);
        }
        ease = ease.setScale(2, RoundingMode.HALF_UP);
        progress.setEaseFactor(ease);
        progress.setIntervalDays(interval);
        progress.setRepetition(repetition);
        progress.setLastReviewAt(LocalDateTime.now());
        progress.setNextReviewAt(LocalDateTime.now().plusDays(interval));
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

    private void evictUserReviewCache(Long userId) {
        try {
            redisTemplate.delete("user:" + userId + ":nextReview");
        } catch (DataAccessException ignored) {
            // ignore
        }
    }
}
