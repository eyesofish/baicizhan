package com.example.demo.dictionary;

import com.example.demo.common.exception.AppException;
import com.example.demo.dictionary.dto.DictionaryMatchResponse;
import com.example.demo.dictionary.dto.DictionaryWordResponse;
import com.example.demo.domain.entity.ExampleSentence;
import com.example.demo.domain.entity.Language;
import com.example.demo.domain.entity.Sense;
import com.example.demo.domain.entity.Term;
import com.example.demo.domain.repository.ExampleSentenceRepository;
import com.example.demo.domain.repository.LanguageRepository;
import com.example.demo.domain.repository.SenseRepository;
import com.example.demo.domain.repository.TermRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictionaryService {
    private static final String DEFAULT_SOURCE_LANGUAGE = "en";

    private final TermRepository termRepository;
    private final SenseRepository senseRepository;
    private final ExampleSentenceRepository exampleSentenceRepository;
    private final LanguageRepository languageRepository;

    public DictionaryService(
        TermRepository termRepository,
        SenseRepository senseRepository,
        ExampleSentenceRepository exampleSentenceRepository,
        LanguageRepository languageRepository
    ) {
        this.termRepository = termRepository;
        this.senseRepository = senseRepository;
        this.exampleSentenceRepository = exampleSentenceRepository;
        this.languageRepository = languageRepository;
    }

    @Transactional(readOnly = true)
    public DictionaryWordResponse lookup(String word) {
        String normalized = normalize(word);
        if (normalized.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, 4006, "WORD_REQUIRED");
        }
        Term term = termRepository.findByLanguageIsoCodeAndNormalizedText(DEFAULT_SOURCE_LANGUAGE, normalized)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4041, "TERM_NOT_FOUND"));
        return toWordResponse(term);
    }

    @Transactional(readOnly = true)
    public DictionaryMatchResponse match(String prefix, int limit) {
        String normalized = normalize(prefix);
        if (normalized.isEmpty()) {
            return new DictionaryMatchResponse(new DictionaryMatchResponse.Results(List.of()));
        }

        List<String> words = termRepository
            .findByLanguageIsoCodeAndNormalizedTextStartingWithOrderByNormalizedTextAsc(
                DEFAULT_SOURCE_LANGUAGE,
                normalized,
                PageRequest.of(0, limit)
            )
            .stream()
            .map(Term::getText)
            .distinct()
            .toList();
        return new DictionaryMatchResponse(new DictionaryMatchResponse.Results(words));
    }

    @Transactional(readOnly = true)
    public DictionaryWordResponse random() {
        Language sourceLanguage = languageRepository.findByIsoCode(DEFAULT_SOURCE_LANGUAGE)
            .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, 4004, "SOURCE_LANGUAGE_NOT_FOUND"));
        long count = termRepository.countByLanguageId(sourceLanguage.getId());
        if (count <= 0) {
            throw new AppException(HttpStatus.NOT_FOUND, 4041, "TERM_NOT_FOUND");
        }
        int page = (int) ThreadLocalRandom.current().nextLong(count);
        Term term = termRepository.findByLanguageIdOrderByIdAsc(sourceLanguage.getId(), PageRequest.of(page, 1))
            .stream()
            .findFirst()
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4041, "TERM_NOT_FOUND"));
        return toWordResponse(term);
    }

    private DictionaryWordResponse toWordResponse(Term term) {
        List<Sense> senses = senseRepository.findByTermIdOrderByIdAsc(term.getId());
        List<Long> senseIds = senses.stream().map(Sense::getId).toList();
        Map<Long, List<ExampleSentence>> examplesBySense = senseIds.isEmpty()
            ? Map.of()
            : exampleSentenceRepository.findBySenseIdIn(senseIds)
                .stream()
                .collect(Collectors.groupingBy(example -> example.getSense().getId()));

        List<DictionaryWordResponse.ResultItem> results = senses.stream()
            .map(sense -> new DictionaryWordResponse.ResultItem(
                sense.getId(),
                sense.getDefinition(),
                sense.getPartOfSpeech(),
                List.of(),
                List.of(),
                examplesBySense
                    .getOrDefault(sense.getId(), List.of())
                    .stream()
                    .map(ExampleSentence::getSentenceText)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(text -> !text.isEmpty())
                    .toList()
            ))
            .toList();

        DictionaryWordResponse.Pronunciation pronunciation = hasText(term.getIpa())
            ? new DictionaryWordResponse.Pronunciation(term.getIpa().trim())
            : null;

        return new DictionaryWordResponse(term.getId(), term.getText(), pronunciation, results);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
