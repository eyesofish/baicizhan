package com.example.demo.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.demo.config.LearningProperties;
import com.example.demo.learning.dto.CandidateSource;
import com.example.demo.learning.dto.RankedTerm;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReRankingServiceTest {
    @Test
    void shouldRespectConfiguredLimit() {
        LearningProperties properties = new LearningProperties(
            new LearningProperties.Recall(100, 50, 50, 200, 30),
            new LearningProperties.ReRanking(10, 7, 3, 2),
            new LearningProperties.Ann(false, "http://127.0.0.1:18080", 300, 300)
        );
        ReRankingService service = new ReRankingService(properties);

        List<RankedTerm> ranked = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            CandidateSource source = switch (i % 3) {
                case 0 -> CandidateSource.NEW;
                case 1 -> CandidateSource.REVIEW;
                default -> CandidateSource.HARD;
            };
            ranked.add(new RankedTerm((long) i, "term-" + i, source, 100 - i));
        }

        List<RankedTerm> out = service.reRank(ranked, 20);
        assertEquals(20, out.size());
    }
}
