package com.example.demo.review;

import com.example.demo.domain.entity.UserProgress;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class SpacedRepetitionScheduler {
    private static final BigDecimal MIN_EASE_FACTOR = BigDecimal.valueOf(1.30D);
    private static final BigDecimal DEFAULT_EASE_FACTOR = BigDecimal.valueOf(2.50D);

    public void apply(UserProgress progress, int rating, LocalDateTime now) {
        if (progress == null) {
            return;
        }
        LocalDateTime currentTime = now == null ? LocalDateTime.now() : now;
        BigDecimal ease = progress.getEaseFactor() == null ? DEFAULT_EASE_FACTOR : progress.getEaseFactor();
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

        if (ease.compareTo(MIN_EASE_FACTOR) < 0) {
            ease = MIN_EASE_FACTOR;
        }
        ease = ease.setScale(2, RoundingMode.HALF_UP);
        progress.setEaseFactor(ease);
        progress.setIntervalDays(interval);
        progress.setRepetition(repetition);
        progress.setLastReviewAt(currentTime);
        progress.setNextReviewAt(currentTime.plusDays(interval));
    }
}
