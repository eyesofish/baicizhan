package com.example.demo.learning;

import com.example.demo.domain.entity.Term;
import com.example.demo.domain.entity.TermStat;
import com.example.demo.domain.entity.UserProgress;
import com.example.demo.domain.repository.ReviewLogRepository;
import com.example.demo.domain.repository.TermRepository;
import com.example.demo.domain.repository.TermStatRepository;
import com.example.demo.domain.repository.UserProgressRepository;
import com.example.demo.learning.dto.CandidateTerm;
import com.example.demo.learning.dto.FeatureVector;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureService {
    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);
    private static final int DEFAULT_FREQUENCY_RANK = 100_000;
    private static final float DEFAULT_DIFFICULTY = 50.0F;
    private static final float DEFAULT_EASE = 2.5F;
    private static final float DEFAULT_LAST_RATING = 3.0F;
    private static final Duration FEATURE_HASH_TTL = Duration.ofDays(30);

    private final UserProgressRepository userProgressRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final TermRepository termRepository;
    private final TermStatRepository termStatRepository;
    private final StringRedisTemplate redisTemplate;

    public FeatureService(
        UserProgressRepository userProgressRepository,
        ReviewLogRepository reviewLogRepository,
        TermRepository termRepository,
        TermStatRepository termStatRepository,
        StringRedisTemplate redisTemplate
    ) {
        this.userProgressRepository = userProgressRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.termRepository = termRepository;
        this.termStatRepository = termStatRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public List<FeatureVector> buildFeatures(Long userId, List<CandidateTerm> candidates) {
        if (userId == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Long> termIds = candidates.stream()
            .map(CandidateTerm::termId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (termIds.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, UserProgress> progressByTerm = userProgressRepository.findByUserIdAndTermIdIn(userId, termIds).stream()
            .collect(Collectors.toMap(up -> up.getTerm().getId(), up -> up));
        Map<Long, TermStat> termStatsByTerm = termStatRepository.findByTermIdIn(termIds).stream()
            .collect(Collectors.toMap(TermStat::getTermId, ts -> ts));
        Map<Long, Term> termById = termRepository.findWithLanguageByIdIn(termIds).stream()
            .collect(Collectors.toMap(Term::getId, t -> t));

        Map<Long, Integer> wrong30dByTerm = loadWrongCount30d(userId, termIds, now.minusDays(30));
        Map<Long, Byte> lastRatingByTerm = loadLastRating(userId, termIds);

        List<FeatureVector> vectors = new ArrayList<>(candidates.size());
        for (CandidateTerm candidate : candidates) {
            Long termId = candidate.termId();
            Term term = termById.get(termId);
            if (term == null) {
                continue;
            }
            UserProgress progress = progressByTerm.get(termId);
            TermStat termStat = termStatsByTerm.get(termId);
            float[] features = buildFeatureArray(
                term,
                progress,
                termStat,
                wrong30dByTerm.getOrDefault(termId, 0),
                lastRatingByTerm.getOrDefault(termId, (byte) DEFAULT_LAST_RATING),
                now
            );
            vectors.add(new FeatureVector(termId, term.getText(), candidate.source(), features));
        }
        if (!vectors.isEmpty()) {
            float[] sample = vectors.get(0).features();
            log.info(
                "term={} features={{isNew={}, overdue={}, wrong30d={}, ease={}, freqRank={}}}",
                vectors.get(0).termId(),
                sample[0],
                sample[1],
                sample[3],
                sample[5],
                sample[7]
            );
        }
        return vectors;
    }

    private float[] buildFeatureArray(
        Term term,
        UserProgress progress,
        TermStat termStat,
        int wrongCount30d,
        byte lastRating,
        LocalDateTime now
    ) {
        int isNewWord = progress == null ? 1 : 0;
        float overdueDays = computeOverdueDays(progress, now);
        float daysSinceLastReview = computeDaysSinceLastReview(progress, now);
        float easeFactor = progress == null ? DEFAULT_EASE : toFloat(progress.getEaseFactor(), DEFAULT_EASE);
        float repetition = progress == null || progress.getRepetition() == null ? 0.0F : progress.getRepetition();
        float frequencyRank = termStat == null || termStat.getFrequencyRank() == null
            ? DEFAULT_FREQUENCY_RANK
            : termStat.getFrequencyRank();
        float difficultyScore = termStat == null || termStat.getDifficultyScore() == null
            ? DEFAULT_DIFFICULTY
            : toFloat(termStat.getDifficultyScore(), DEFAULT_DIFFICULTY);
        float wordLength = term.getText() == null ? 0.0F : term.getText().length();

        return new float[] {
            isNewWord,                    // f1
            overdueDays,                  // f2
            daysSinceLastReview,          // f3
            wrongCount30d,                // f4
            lastRating,                   // f5
            easeFactor,                   // f6
            repetition,                   // f7
            frequencyRank,                // f8
            difficultyScore,              // f9
            wordLength                    // f10
        };
    }

    private float computeOverdueDays(UserProgress progress, LocalDateTime now) {
        if (progress == null || progress.getNextReviewAt() == null || !progress.getNextReviewAt().isBefore(now)) {
            return 0.0F;
        }
        long days = Duration.between(progress.getNextReviewAt(), now).toDays();
        return Math.max(0L, days);
    }

    private float computeDaysSinceLastReview(UserProgress progress, LocalDateTime now) {
        if (progress == null || progress.getLastReviewAt() == null) {
            return 0.0F;
        }
        long days = Duration.between(progress.getLastReviewAt(), now).toDays();
        return Math.max(0L, days);
    }

    private float toFloat(BigDecimal value, float fallback) {
        if (value == null) {
            return fallback;
        }
        return value.floatValue();
    }

    private Map<Long, Integer> loadWrongCount30d(Long userId, Collection<Long> termIds, LocalDateTime since) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        if (termIds.isEmpty()) {
            return out;
        }
        String redisKey = "user:" + userId + ":wrong30d";
        Map<Long, Integer> fromRedis = readIntegerHash(redisKey, termIds);
        out.putAll(fromRedis);

        Set<Long> missing = new LinkedHashSet<>(termIds);
        missing.removeAll(fromRedis.keySet());
        if (!missing.isEmpty()) {
            for (ReviewLogRepository.TermWrongCountProjection row : reviewLogRepository.countWrongAnswers30d(userId, missing, since)) {
                if (row.getTermId() != null && row.getWrongCount() != null) {
                    out.put(row.getTermId(), row.getWrongCount().intValue());
                }
            }
            for (Long termId : missing) {
                out.putIfAbsent(termId, 0);
            }
            writeIntegerHash(redisKey, out);
        }
        return out;
    }

    private Map<Long, Byte> loadLastRating(Long userId, Collection<Long> termIds) {
        Map<Long, Byte> out = new HashMap<>();
        if (termIds.isEmpty()) {
            return out;
        }
        String redisKey = "user:" + userId + ":lastRating";
        Map<Long, Byte> fromRedis = readByteHash(redisKey, termIds);
        out.putAll(fromRedis);
        Set<Long> missing = new LinkedHashSet<>(termIds);
        missing.removeAll(fromRedis.keySet());
        if (!missing.isEmpty()) {
            for (ReviewLogRepository.LastRatingProjection row : reviewLogRepository.findLastRatings(userId, missing)) {
                if (row.getTermId() != null && row.getRating() != null) {
                    out.put(row.getTermId(), row.getRating());
                }
            }
            for (Long termId : missing) {
                out.putIfAbsent(termId, (byte) DEFAULT_LAST_RATING);
            }
            writeByteHash(redisKey, out);
        }
        return out;
    }

    private Map<Long, Integer> readIntegerHash(String key, Collection<Long> termIds) {
        Map<Long, Integer> out = new HashMap<>();
        try {
            HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();
            for (Long termId : termIds) {
                Object raw = ops.get(key, termId.toString());
                if (raw == null) {
                    continue;
                }
                try {
                    out.put(termId, Integer.parseInt(raw.toString()));
                } catch (NumberFormatException ignored) {
                    // ignore malformed cached value
                }
            }
        } catch (DataAccessException ignored) {
            // ignore Redis outage
        }
        return out;
    }

    private void writeIntegerHash(String key, Map<Long, Integer> values) {
        try {
            if (values.isEmpty()) {
                return;
            }
            Map<String, String> payload = new HashMap<>();
            values.forEach((termId, value) -> payload.put(termId.toString(), Integer.toString(value)));
            redisTemplate.opsForHash().putAll(key, payload);
            redisTemplate.expire(key, FEATURE_HASH_TTL);
        } catch (DataAccessException ignored) {
            // ignore Redis outage
        }
    }

    private Map<Long, Byte> readByteHash(String key, Collection<Long> termIds) {
        Map<Long, Byte> out = new HashMap<>();
        try {
            HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();
            for (Long termId : termIds) {
                Object raw = ops.get(key, termId.toString());
                if (raw == null) {
                    continue;
                }
                try {
                    out.put(termId, Byte.parseByte(raw.toString()));
                } catch (NumberFormatException ignored) {
                    // ignore malformed cached value
                }
            }
        } catch (DataAccessException ignored) {
            // ignore Redis outage
        }
        return out;
    }

    private void writeByteHash(String key, Map<Long, Byte> values) {
        try {
            if (values.isEmpty()) {
                return;
            }
            Map<String, String> payload = new HashMap<>();
            values.forEach((termId, value) -> payload.put(termId.toString(), Byte.toString(value)));
            redisTemplate.opsForHash().putAll(key, payload);
            redisTemplate.expire(key, FEATURE_HASH_TTL);
        } catch (DataAccessException ignored) {
            // ignore Redis outage
        }
    }
}
