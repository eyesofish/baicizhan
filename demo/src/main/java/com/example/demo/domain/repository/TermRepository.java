package com.example.demo.domain.repository;

import com.example.demo.domain.entity.Term;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TermRepository extends JpaRepository<Term, Long> {
    Optional<Term> findByLanguageIdAndNormalizedText(Integer languageId, String normalizedText);

    Optional<Term> findByLanguageIsoCodeAndNormalizedText(String isoCode, String normalizedText);

    List<Term> findByLanguageIsoCodeAndNormalizedTextStartingWithOrderByNormalizedTextAsc(
        String isoCode,
        String normalizedTextPrefix,
        Pageable pageable
    );

    Page<Term> findByLanguageIdOrderByIdAsc(Integer languageId, Pageable pageable);

    long countByLanguageId(Integer languageId);

    @Query("""
        select t
        from Term t
        join fetch t.language
        where t.id in :termIds
        """)
    List<Term> findWithLanguageByIdIn(@Param("termIds") List<Long> termIds);
}
