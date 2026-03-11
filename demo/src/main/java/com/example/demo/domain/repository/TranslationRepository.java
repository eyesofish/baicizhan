package com.example.demo.domain.repository;

import com.example.demo.domain.entity.Translation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranslationRepository extends JpaRepository<Translation, Long> {
    List<Translation> findBySenseIdIn(Collection<Long> senseIds);

    boolean existsBySenseIdAndTargetLanguageIdAndTranslatedText(Long senseId, Integer targetLanguageId, String translatedText);

    void deleteBySenseId(Long senseId);
}
