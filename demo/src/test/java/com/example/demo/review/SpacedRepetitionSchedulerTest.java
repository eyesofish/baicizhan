package com.example.demo.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.domain.entity.UserProgress;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SpacedRepetitionSchedulerTest {
    private final SpacedRepetitionScheduler scheduler = new SpacedRepetitionScheduler();

    @Test
    void shouldResetWhenRatingLowerThanThree() {
        UserProgress progress = new UserProgress();
        progress.setEaseFactor(BigDecimal.valueOf(2.50D));
        progress.setIntervalDays(6);
        progress.setRepetition(3);

        scheduler.apply(progress, 2, LocalDateTime.of(2026, 3, 11, 12, 0));

        assertEquals(0, progress.getRepetition());
        assertEquals(1, progress.getIntervalDays());
    }

    @Test
    void shouldUseOneDayIntervalOnFirstSuccess() {
        UserProgress progress = new UserProgress();
        progress.setEaseFactor(BigDecimal.valueOf(2.50D));
        progress.setIntervalDays(0);
        progress.setRepetition(0);

        scheduler.apply(progress, 4, LocalDateTime.of(2026, 3, 11, 12, 0));

        assertEquals(1, progress.getRepetition());
        assertEquals(1, progress.getIntervalDays());
    }

    @Test
    void shouldUseSixDaysIntervalOnSecondSuccess() {
        UserProgress progress = new UserProgress();
        progress.setEaseFactor(BigDecimal.valueOf(2.50D));
        progress.setIntervalDays(1);
        progress.setRepetition(1);

        scheduler.apply(progress, 4, LocalDateTime.of(2026, 3, 11, 12, 0));

        assertEquals(2, progress.getRepetition());
        assertEquals(6, progress.getIntervalDays());
    }

    @Test
    void shouldClampEaseFactorToOnePointThree() {
        UserProgress progress = new UserProgress();
        progress.setEaseFactor(BigDecimal.valueOf(1.35D));
        progress.setIntervalDays(1);
        progress.setRepetition(0);

        scheduler.apply(progress, 0, LocalDateTime.of(2026, 3, 11, 12, 0));

        assertTrue(progress.getEaseFactor().compareTo(BigDecimal.valueOf(1.30D)) >= 0);
        assertEquals(0, progress.getEaseFactor().compareTo(BigDecimal.valueOf(1.30D)));
    }
}
