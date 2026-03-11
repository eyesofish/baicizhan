package com.example.demo.domain.repository;

import com.example.demo.domain.entity.VocabItem;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VocabItemRepository extends JpaRepository<VocabItem, Long> {
    boolean existsByListIdAndTermId(Long listId, Long termId);

    long countByListId(Long listId);

    @Query("""
        select vi.term.id
        from VocabItem vi
        where vi.list.user.id = :userId
          and vi.term.id not in (
              select up.term.id
              from UserProgress up
              where up.user.id = :userId
          )
        group by vi.term.id
        order by min(vi.id)
        """)
    List<Long> findTermIdsWithoutProgress(@Param("userId") Long userId, Pageable pageable);
}
