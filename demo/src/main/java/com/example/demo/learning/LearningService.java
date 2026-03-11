package com.example.demo.learning;

import com.example.demo.config.LearningProperties;
import com.example.demo.domain.entity.Term;
import com.example.demo.domain.entity.UserProgress;
import com.example.demo.domain.repository.TermRepository;
import com.example.demo.domain.repository.UserProgressRepository;
import com.example.demo.learning.dto.CandidateTerm;
import com.example.demo.learning.dto.FeatureVector;
import com.example.demo.learning.dto.RankedTerm;
import com.example.demo.review.dto.ReviewCardResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {
    private static final BigDecimal DEFAULT_EASE = BigDecimal.valueOf(2.50D);

    private final CandidateRecallService candidateRecallService;
    private final FeatureService featureService;
    private final RankingService rankingService;
    private final ReRankingService reRankingService;
    private final TermRepository termRepository;
    private final UserProgressRepository userProgressRepository;
    private final LearningProperties learningProperties;

    public LearningService(
        CandidateRecallService candidateRecallService,
        FeatureService featureService,
        RankingService rankingService,
        ReRankingService reRankingService,
        TermRepository termRepository,
        UserProgressRepository userProgressRepository,
        LearningProperties learningProperties
    ) {
        this.candidateRecallService = candidateRecallService;
        this.featureService = featureService;
        this.rankingService = rankingService;
        this.reRankingService = reRankingService;
        this.termRepository = termRepository;
        this.userProgressRepository = userProgressRepository;
        this.learningProperties = learningProperties;
    }

    @Transactional(readOnly = true)
    public List<ReviewCardResponse> nextCards(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }

        int candidateLimit = Math.max(learningProperties.recall().totalCandidates(), limit * 5);
        List<CandidateTerm> candidates = candidateRecallService.recall(userId, candidateLimit);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<FeatureVector> featureVectors = featureService.buildFeatures(userId, candidates);
        if (featureVectors.isEmpty()) {
            return List.of();
        }

        List<RankedTerm> ranked = rankingService.rank(featureVectors);
        if (ranked.isEmpty()) {
            return List.of();
        }

        List<RankedTerm> finalRanked = reRankingService.reRank(ranked, limit);
        if (finalRanked.isEmpty()) {
            return List.of();
        }

        return toReviewCards(userId, finalRanked);
    }

    private List<ReviewCardResponse> toReviewCards(Long userId, List<RankedTerm> rankedTerms) {
        List<Long> termIds = rankedTerms.stream()
            .map(RankedTerm::termId)
            .filter(Objects::nonNull)
            .toList();
        if (termIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Term> termById = termRepository.findWithLanguageByIdIn(termIds).stream()
            .collect(Collectors.toMap(Term::getId, term -> term));
        Map<Long, UserProgress> progressByTerm = userProgressRepository.findByUserIdAndTermIdIn(userId, termIds).stream()
            .collect(Collectors.toMap(progress -> progress.getTerm().getId(), progress -> progress));

        List<ReviewCardResponse> out = new ArrayList<>(rankedTerms.size());
        for (RankedTerm rankedTerm : rankedTerms) {
            Long termId = rankedTerm.termId();
            Term term = termById.get(termId);
            if (term == null) {
                continue;
            }
            UserProgress progress = progressByTerm.get(termId);
            out.add(new ReviewCardResponse(
                term.getId(),
                term.getText(),
                term.getLanguage().getIsoCode(),
                progress == null || progress.getEaseFactor() == null ? DEFAULT_EASE : progress.getEaseFactor(),
                progress == null || progress.getIntervalDays() == null ? 0 : progress.getIntervalDays(),
                progress == null || progress.getRepetition() == null ? 0 : progress.getRepetition(),
                progress == null ? null : progress.getNextReviewAt()
            ));
        }
        return out;
    }
}

