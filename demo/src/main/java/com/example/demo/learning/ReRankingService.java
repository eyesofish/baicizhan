package com.example.demo.learning;

import com.example.demo.config.LearningProperties;
import com.example.demo.learning.dto.CandidateSource;
import com.example.demo.learning.dto.RankedTerm;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ReRankingService {
    private final LearningProperties learningProperties;

    public ReRankingService(LearningProperties learningProperties) {
        this.learningProperties = learningProperties;
    }

    public List<RankedTerm> reRank(List<RankedTerm> ranked, int limit) {
        if (ranked == null || ranked.isEmpty() || limit <= 0) {
            return List.of();
        }
        int pickLimit = Math.min(limit, ranked.size());
        Quota quota = scaledQuota(pickLimit);

        Map<CandidateSource, List<RankedTerm>> buckets = new EnumMap<>(CandidateSource.class);
        for (CandidateSource source : CandidateSource.values()) {
            buckets.put(source, new ArrayList<>());
        }
        for (RankedTerm term : ranked) {
            buckets.get(normalizeSource(term.source())).add(term);
        }

        List<RankedTerm> selected = new ArrayList<>(pickLimit);
        Set<Long> selectedIds = new HashSet<>();
        takeFromBucket(buckets.get(CandidateSource.NEW), quota.newQuota, selected, selectedIds);
        takeFromBucket(buckets.get(CandidateSource.REVIEW), quota.reviewQuota, selected, selectedIds);
        takeFromBucket(buckets.get(CandidateSource.HARD), quota.hardQuota, selected, selectedIds);

        if (selected.size() < pickLimit) {
            for (RankedTerm term : ranked) {
                if (selected.size() >= pickLimit) {
                    break;
                }
                if (term.termId() != null && selectedIds.add(term.termId())) {
                    selected.add(term);
                }
            }
        }
        return enforceDiversity(selected, learningProperties.reRanking().maxConsecutiveSameSource());
    }

    private void takeFromBucket(
        List<RankedTerm> bucket,
        int quota,
        List<RankedTerm> selected,
        Set<Long> selectedIds
    ) {
        if (quota <= 0 || bucket == null || bucket.isEmpty()) {
            return;
        }
        for (RankedTerm term : bucket) {
            if (quota <= 0) {
                break;
            }
            if (term.termId() == null || !selectedIds.add(term.termId())) {
                continue;
            }
            selected.add(term);
            quota--;
        }
    }

    private List<RankedTerm> enforceDiversity(List<RankedTerm> ranked, int maxConsecutiveSameSource) {
        if (ranked.size() <= 2 || maxConsecutiveSameSource < 1) {
            return ranked;
        }
        List<RankedTerm> remaining = new ArrayList<>(ranked);
        List<RankedTerm> out = new ArrayList<>(ranked.size());

        while (!remaining.isEmpty()) {
            CandidateSource blockedSource = sourceBlockedByTail(out, maxConsecutiveSameSource);
            int pickIdx = 0;
            if (blockedSource != null) {
                pickIdx = -1;
                for (int i = 0; i < remaining.size(); i++) {
                    if (normalizeSource(remaining.get(i).source()) != blockedSource) {
                        pickIdx = i;
                        break;
                    }
                }
                if (pickIdx < 0) {
                    pickIdx = 0;
                }
            }
            out.add(remaining.remove(pickIdx));
        }
        return out;
    }

    private CandidateSource sourceBlockedByTail(List<RankedTerm> out, int maxConsecutiveSameSource) {
        if (out.size() < maxConsecutiveSameSource) {
            return null;
        }
        CandidateSource tail = normalizeSource(out.get(out.size() - 1).source());
        for (int i = out.size() - 2, seen = 1; i >= 0 && seen < maxConsecutiveSameSource; i--, seen++) {
            if (normalizeSource(out.get(i).source()) != tail) {
                return null;
            }
        }
        return tail;
    }

    private Quota scaledQuota(int limit) {
        int baseTotal = learningProperties.reRanking().newQuota()
            + learningProperties.reRanking().reviewQuota()
            + learningProperties.reRanking().hardQuota();
        if (baseTotal <= 0) {
            return new Quota(limit, 0, 0);
        }
        int newQuota = Math.round((float) limit * learningProperties.reRanking().newQuota() / baseTotal);
        int reviewQuota = Math.round((float) limit * learningProperties.reRanking().reviewQuota() / baseTotal);
        int hardQuota = Math.max(0, limit - newQuota - reviewQuota);
        return new Quota(newQuota, reviewQuota, hardQuota);
    }

    private CandidateSource normalizeSource(CandidateSource source) {
        return source == CandidateSource.EMBEDDING ? CandidateSource.REVIEW : source;
    }

    private record Quota(int newQuota, int reviewQuota, int hardQuota) {
    }
}
