package com.example.demo.domain.repository;

import com.example.demo.domain.entity.UserProgress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {
    Optional<UserProgress> findByUserIdAndTermId(Long userId, Long termId);

    @Query("""
        select up
        from UserProgress up
        where up.user.id = :userId
          and up.nextReviewAt <= :now
        order by up.nextReviewAt asc
        """)
    List<UserProgress> findDueReviews(@Param("userId") Long userId, @Param("now") LocalDateTime now, Pageable pageable);
}
