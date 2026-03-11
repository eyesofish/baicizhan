package com.example.demo.domain.repository;

import com.example.demo.domain.entity.Language;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LanguageRepository extends JpaRepository<Language, Integer> {
    Optional<Language> findByIsoCode(String isoCode);
}
