package com.example.demo.learning;

import com.example.demo.config.LearningProperties;
import com.example.demo.domain.entity.Term;
import com.example.demo.domain.entity.UserProgress;
import com.example.demo.domain.repository.ReviewLogRepository;
import com.example.demo.domain.repository.TermRepository;
import com.example.demo.domain.repository.UserProgressRepository;
import com.example.demo.domain.repository.VocabItemRepository;
import com.example.demo.learning.dto.CandidateSource;
import com.example.demo.learning.dto.CandidateTerm;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateRecallService {
    private static final Logger log = LoggerFactory.getLogger(CandidateRecallService.class);

    private final UserProgressRepository userProgressRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final VocabItemRepository vocabItemRepository;
    private final TermRepository termRepository;
    private final LearningProperties learningProperties;
    private final AnnRecallClient annRecallClient;
    private final HashEmbeddingEncoder hashEmbeddingEncoder;

    public CandidateRecallService(
        UserProgressRepository userProgressRepository,
        ReviewLogRepository reviewLogRepository,
        VocabItemRepository vocabItemRepository,
        TermRepository termRepository,
        LearningProperties learningProperties,
        AnnRecallClient annRecallClient,
        HashEmbeddingEncoder hashEmbeddingEncoder
    ) {
        this.userProgressRepository = userProgressRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.vocabItemRepository = vocabItemRepository;
        this.termRepository = termRepository;
        this.learningProperties = learningProperties;
        this.annRecallClient = annRecallClient;
        this.hashEmbeddingEncoder = hashEmbeddingEncoder;
    }

    @Transactional(readOnly = true)
    public List<CandidateTerm> recall(Long userId, int requestedTotal) {
        if (userId == null) {
            return List.of();
        }
        int total = requestedTotal > 0 ? requestedTotal : learningProperties.recall().totalCandidates();
        int reviewQuota = Math.min(learningProperties.recall().reviewQuota(), total);
        int hardQuota = Math.min(learningProperties.recall().hardQuota(), Math.max(0, total - reviewQuota));
        int newQuota = Math.min(learningProperties.recall().newQuota(), Math.max(0, total - reviewQuota - hardQuota));

        LocalDateTime now = LocalDateTime.now();
        List<Long> reviewIds = userProgressRepository.findDueReviews(userId, now, PageRequest.of(0, reviewQuota))
            .stream()
            .map(UserProgress::getTerm)
            .filter(Objects::nonNull)
            .map(Term::getId)
            .filter(Objects::nonNull)
            .toList();
        List<Long> hardIds = reviewLogRepository.findHardTermIds(
            userId,
            now.minusDays(30),
            PageRequest.of(0, hardQuota)
        );
        List<Long> newIds = vocabItemRepository.findTermIdsWithoutProgress(userId, PageRequest.of(0, newQuota));

        Map<Long, CandidateTerm> merged = new LinkedHashMap<>();
        addCandidates(merged, reviewIds, CandidateSource.REVIEW, 1.0D);
        addCandidates(merged, hardIds, CandidateSource.HARD, 0.8D);
        addCandidates(merged, newIds, CandidateSource.NEW, 0.6D);

        int embeddingAdded = addEmbeddingCandidates(userId, total, merged);
        List<CandidateTerm> out = new ArrayList<>(merged.values());
        out.sort(
            Comparator.comparingInt((CandidateTerm c) -> sourcePriority(c.source()))
                .thenComparing(CandidateTerm::recallScore, Comparator.reverseOrder())
        );
        if (out.size() > total) {
            out = out.subList(0, total);
        }
        log.info(
            "recall.review={} recall.hard={} recall.new={} embedding_recall_count={} merged_count={}",
            reviewIds.size(),
            hardIds.size(),
            newIds.size(),
            embeddingAdded,
            out.size()
        );
        return out;
    }

    private int addEmbeddingCandidates(Long userId, int total, Map<Long, CandidateTerm> merged) {
        int remaining = Math.max(0, total - merged.size());
        if (remaining == 0) {
            return 0;
        }
        float[] userVector = buildUserVector(userId);
        if (userVector == null) {
            return 0;
        }
        Set<Long> exclude = new LinkedHashSet<>(merged.keySet());
        int topK = Math.max(learningProperties.ann().topK(), remaining);
        int added = 0;
        List<AnnRecallClient.AnnResult> annResults = annRecallClient.search(userVector, topK, exclude);
        for (AnnRecallClient.AnnResult annResult : annResults) {
            if (annResult.termId() == null) {
                continue;
            }
            CandidateTerm existing = merged.get(annResult.termId());
            if (existing != null) {
                continue;
            }
            merged.put(
                annResult.termId(),
                new CandidateTerm(annResult.termId(), CandidateSource.EMBEDDING, annResult.similarity())
            );
            added++;
            if (merged.size() >= total) {
                break;
            }
        }
        return added;
    }

    private float[] buildUserVector(Long userId) {
        List<Long> recentTermIds = reviewLogRepository.findRecentDistinctTermIds(
            userId,
            PageRequest.of(0, learningProperties.recall().recentTermsForUserVector())
        );
        if (recentTermIds.isEmpty()) {
            return null;
        }
        Map<Long, String> termText = termRepository.findAllById(recentTermIds).stream()
            .collect(LinkedHashMap::new, (m, t) -> m.put(t.getId(), t.getText()), Map::putAll);
        List<String> orderedRecentTerms = recentTermIds.stream()
            .map(termText::get)
            .filter(Objects::nonNull)
            .toList();
        return hashEmbeddingEncoder.average(orderedRecentTerms);
    }

    private void addCandidates(
        Map<Long, CandidateTerm> merged,
        List<Long> termIds,
        CandidateSource source,
        double baseScore
    ) {
        for (int i = 0; i < termIds.size(); i++) {
            Long termId = termIds.get(i);
            if (termId == null) {
                continue;
            }
            double score = baseScore - i * 0.001D;
            CandidateTerm candidate = new CandidateTerm(termId, source, score);
            CandidateTerm existing = merged.get(termId);
            if (existing == null) {
                merged.put(termId, candidate);
                continue;
            }
            if (isBetter(candidate, existing)) {
                merged.put(termId, candidate);
            }
        }
    }

    private boolean isBetter(CandidateTerm next, CandidateTerm current) {
        int nextPriority = sourcePriority(next.source());
        int currentPriority = sourcePriority(current.source());
        if (nextPriority != currentPriority) {
            return nextPriority < currentPriority;
        }
        return next.recallScore() > current.recallScore();
    }

    private int sourcePriority(CandidateSource source) {
        return switch (source) {
            case REVIEW -> 0;
            case HARD -> 1;
            case NEW -> 2;
            case EMBEDDING -> 3;
        };
    }
}
