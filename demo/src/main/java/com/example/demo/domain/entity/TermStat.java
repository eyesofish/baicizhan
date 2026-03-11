package com.example.demo.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "term_stats")
public class TermStat {
    @Id
    @Column(name = "term_id")
    private Long termId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Column(name = "frequency_rank", nullable = false)
    private Integer frequencyRank;

    @Column(name = "difficulty_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal difficultyScore = BigDecimal.valueOf(50.00D);

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType = "import";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
