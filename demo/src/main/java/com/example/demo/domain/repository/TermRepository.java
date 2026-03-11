package com.example.demo.domain.repository;

import com.example.demo.domain.entity.Term;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {
    Optional<Term> findByLanguageIdAndNormalizedText(Integer languageId, String normalizedText);
}
