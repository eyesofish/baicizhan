package com.example.demo.domain.repository;

import com.example.demo.domain.entity.ReviewLog;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {
    interface TermWrongCountProjection {
        Long getTermId();
        Long getWrongCount();
    }

    interface LastRatingProjection {
        Long getTermId();
        Byte getRating();
    }

    @Query("""
        select rl.term.id
        from ReviewLog rl
        where rl.user.id = :userId
          and rl.rating <= 2
          and rl.createdAt >= :since
        group by rl.term.id
        order by count(rl.id) desc, max(rl.createdAt) desc
        """)
    List<Long> findHardTermIds(
        @Param("userId") Long userId,
        @Param("since") LocalDateTime since,
        Pageable pageable
    );

    @Query("""
        select rl.term.id as termId, count(rl.id) as wrongCount
        from ReviewLog rl
        where rl.user.id = :userId
          and rl.term.id in :termIds
          and rl.rating <= 2
          and rl.createdAt >= :since
        group by rl.term.id
        """)
    List<TermWrongCountProjection> countWrongAnswers30d(
        @Param("userId") Long userId,
        @Param("termIds") Collection<Long> termIds,
        @Param("since") LocalDateTime since
    );

    @Query("""
        select rl.term.id as termId, rl.rating as rating
        from ReviewLog rl
        where rl.id in (
            select max(innerRl.id)
            from ReviewLog innerRl
            where innerRl.user.id = :userId
              and innerRl.term.id in :termIds
            group by innerRl.term.id
        )
        """)
    List<LastRatingProjection> findLastRatings(
        @Param("userId") Long userId,
        @Param("termIds") Collection<Long> termIds
    );

    @Query("""
        select rl.term.id
        from ReviewLog rl
        where rl.user.id = :userId
        group by rl.term.id
        order by max(rl.createdAt) desc
        """)
    List<Long> findRecentDistinctTermIds(@Param("userId") Long userId, Pageable pageable);
}
