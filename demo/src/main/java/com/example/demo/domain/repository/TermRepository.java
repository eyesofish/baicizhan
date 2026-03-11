package com.example.demo.domain.repository;

import com.example.demo.domain.entity.Term;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
