package com.example.demo.tools;

import com.example.demo.config.ImportProperties;
import com.example.demo.domain.entity.Language;
import com.example.demo.domain.entity.Sense;
import com.example.demo.domain.entity.Term;
import com.example.demo.domain.entity.TermStat;
import com.example.demo.domain.entity.Translation;
import com.example.demo.domain.repository.LanguageRepository;
import com.example.demo.domain.repository.SenseRepository;
import com.example.demo.domain.repository.TermRepository;
import com.example.demo.domain.repository.TermStatRepository;
import com.example.demo.domain.repository.TranslationRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.import", name = "enabled", havingValue = "true")
public class HighFrequencyVocabImportRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(HighFrequencyVocabImportRunner.class);
    private static final Pattern POS_PATTERN = Pattern.compile("\\b([a-zA-Z]{1,12})\\.");

    private final ImportProperties properties;
    private final LanguageRepository languageRepository;
    private final TermRepository termRepository;
    private final TermStatRepository termStatRepository;
    private final SenseRepository senseRepository;
    private final TranslationRepository translationRepository;
    private final ConfigurableApplicationContext applicationContext;

    public HighFrequencyVocabImportRunner(
        ImportProperties properties,
        LanguageRepository languageRepository,
        TermRepository termRepository,
        TermStatRepository termStatRepository,
        SenseRepository senseRepository,
        TranslationRepository translationRepository,
        ConfigurableApplicationContext applicationContext
    ) {
        this.properties = properties;
        this.languageRepository = languageRepository;
        this.termRepository = termRepository;
        this.termStatRepository = termStatRepository;
        this.senseRepository = senseRepository;
        this.translationRepository = translationRepository;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path source = resolveSourceFile();
        if (!Files.exists(source)) {
            log.error("Vocabulary source file not found: {}", source.toAbsolutePath());
            return;
        }
        int limit = properties.limit() > 0 ? properties.limit() : 10_000;
        String sourceLangCode = normalizeLang(properties.sourceLanguage(), "en");
        String targetLangCode = normalizeLang(properties.targetLanguage(), "zh-Hans");
        boolean dryRun = properties.dryRun();

        Language sourceLang = ensureLanguage(sourceLangCode, defaultLanguageName(sourceLangCode));
        Language targetLang = ensureLanguage(targetLangCode, defaultLanguageName(targetLangCode));

        ImportStats stats = new ImportStats();
        log.info(
            "Starting vocabulary import: source={}, limit={}, sourceLang={}, targetLang={}, dryRun={}",
            source.toAbsolutePath(),
            limit,
            sourceLangCode,
            targetLangCode,
            dryRun
        );

        Instant begin = Instant.now();
        parseAndImport(source, limit, sourceLang, targetLang, dryRun, stats);
        Duration elapsed = Duration.between(begin, Instant.now());
        log.info(
            "Import done in {}s: parsed={}, processed={}, skipped={}, insertedTerms={}, insertedSenses={}, insertedTranslations={}, updatedSenses={}",
            elapsed.toSeconds(),
            stats.parsed.get(),
            stats.processed.get(),
            stats.skipped.get(),
            stats.insertedTerms.get(),
            stats.insertedSenses.get(),
            stats.insertedTranslations.get(),
            stats.updatedSenses.get()
        );

        if (properties.exitOnFinish()) {
            log.info("Import completed, shutting down because app.import.exit-on-finish=true");
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }

    private void parseAndImport(
        Path source,
        int limit,
        Language sourceLang,
        Language targetLang,
        boolean dryRun,
        ImportStats stats
    ) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String currentWord = null;
            List<String> block = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty()) {
                    if (currentWord != null) {
                        if (handleEntry(currentWord, block, sourceLang, targetLang, dryRun, stats, limit)) {
                            return;
                        }
                        currentWord = null;
                        block.clear();
                    }
                    continue;
                }

                if (isHeaderLine(trimmed)) {
                    if (currentWord != null) {
                        if (handleEntry(currentWord, block, sourceLang, targetLang, dryRun, stats, limit)) {
                            return;
                        }
                        block.clear();
                    }
                    currentWord = extractWord(trimmed);
                    continue;
                }
                if (currentWord != null) {
                    block.add(trimmed);
                }
            }
            if (currentWord != null) {
                handleEntry(currentWord, block, sourceLang, targetLang, dryRun, stats, limit);
            }
        }
    }

    private boolean handleEntry(
        String rawWord,
        List<String> blockLines,
        Language sourceLang,
        Language targetLang,
        boolean dryRun,
        ImportStats stats,
        int limit
    ) {
        stats.parsed.incrementAndGet();
        if (stats.processed.get() >= limit) {
            return true;
        }

        String word = sanitizeWord(rawWord);
        if (word.isBlank()) {
            stats.skipped.incrementAndGet();
            return false;
        }

        ParsedMeaning parsedMeaning = parseMeaning(blockLines);
        if (parsedMeaning.translation().isBlank() && parsedMeaning.definition().isBlank()) {
            stats.skipped.incrementAndGet();
            return false;
        }

        stats.processed.incrementAndGet();
        if (dryRun) {
            return false;
        }

        String normalized = normalizeWord(word);
        Term term = termRepository.findByLanguageIdAndNormalizedText(sourceLang.getId(), normalized).orElse(null);
        if (term == null) {
            term = new Term();
            term.setLanguage(sourceLang);
            term.setText(word);
            term.setNormalizedText(normalized);
            term = termRepository.save(term);
            stats.insertedTerms.incrementAndGet();
        }
        upsertTermStat(term, stats.processed.get(), word);

        Sense sense = ensureSense(term, parsedMeaning, stats);
        if (!parsedMeaning.translation().isBlank()) {
            String translated = truncate(parsedMeaning.translation(), 512);
            boolean exists = translationRepository.existsBySenseIdAndTargetLanguageIdAndTranslatedText(
                sense.getId(),
                targetLang.getId(),
                translated
            );
            if (!exists) {
                Translation translation = new Translation();
                translation.setSense(sense);
                translation.setTargetLanguage(targetLang);
                translation.setTranslatedText(translated);
                translation.setSourceType("import");
                translationRepository.save(translation);
                stats.insertedTranslations.incrementAndGet();
            }
        }

        if (stats.processed.get() % 500 == 0) {
            log.info(
                "Import progress: processed={}, insertedTerms={}, insertedSenses={}, insertedTranslations={}",
                stats.processed.get(),
                stats.insertedTerms.get(),
                stats.insertedSenses.get(),
                stats.insertedTranslations.get()
            );
        }
        return false;
    }

    private void upsertTermStat(Term term, int rank, String word) {
        if (term == null || term.getId() == null || rank <= 0) {
            return;
        }
        TermStat termStat = termStatRepository.findById(term.getId()).orElseGet(TermStat::new);
        termStat.setTerm(term);
        termStat.setTermId(term.getId());
        termStat.setFrequencyRank(rank);
        termStat.setDifficultyScore(computeDifficultyScore(word));
        termStat.setSourceType("import");
        termStatRepository.save(termStat);
    }

    private BigDecimal computeDifficultyScore(String word) {
        int length = word == null ? 0 : word.length();
        double raw = 20.0D + Math.min(12, Math.max(1, length)) * 5.0D;
        return BigDecimal.valueOf(Math.min(95.0D, raw)).setScale(2, RoundingMode.HALF_UP);
    }

    private Sense ensureSense(Term term, ParsedMeaning parsedMeaning, ImportStats stats) {
        List<Sense> senses = senseRepository.findByTermIdOrderByIdAsc(term.getId());
        if (senses.isEmpty()) {
            Sense sense = new Sense();
            sense.setTerm(term);
            sense.setPartOfSpeech(parsedMeaning.partOfSpeech());
            sense.setDefinition(truncate(parsedMeaning.definition(), 5000));
            sense = senseRepository.save(sense);
            stats.insertedSenses.incrementAndGet();
            return sense;
        }
        Sense first = senses.get(0);
        boolean changed = false;
        if ((first.getPartOfSpeech() == null || first.getPartOfSpeech().isBlank()) && parsedMeaning.partOfSpeech() != null) {
            first.setPartOfSpeech(parsedMeaning.partOfSpeech());
            changed = true;
        }
        if ((first.getDefinition() == null || first.getDefinition().isBlank()) && !parsedMeaning.definition().isBlank()) {
            first.setDefinition(truncate(parsedMeaning.definition(), 5000));
            changed = true;
        }
        if (changed) {
            first = senseRepository.save(first);
            stats.updatedSenses.incrementAndGet();
        }
        return first;
    }

    private ParsedMeaning parseMeaning(List<String> blockLines) {
        if (blockLines == null || blockLines.isEmpty()) {
            return new ParsedMeaning(null, "", "");
        }
        List<String> cjkLines = blockLines.stream().filter(this::containsChinese).toList();
        List<String> source = cjkLines.isEmpty() ? blockLines : cjkLines;
        String joined = source.stream().map(s -> s.replace('\t', ' ')).map(String::trim).filter(s -> !s.isBlank())
            .reduce((a, b) -> a + " " + b).orElse("").replaceAll("\\s+", " ").trim();

        String pos = null;
        if (!source.isEmpty()) {
            Matcher matcher = POS_PATTERN.matcher(source.get(0));
            if (matcher.find()) {
                pos = matcher.group(1).toLowerCase(Locale.ROOT);
            }
        }
        return new ParsedMeaning(pos, joined, joined);
    }

    private boolean containsChinese(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private boolean isHeaderLine(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 2) {
            return false;
        }
        return isInteger(tokens[tokens.length - 1]);
    }

    private String extractWord(String line) {
        String[] tokens = line.split("\\s+");
        return tokens.length == 0 ? "" : tokens[0];
    }

    private boolean isInteger(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private String sanitizeWord(String rawWord) {
        String word = rawWord == null ? "" : rawWord.trim();
        if (word.isEmpty()) {
            return "";
        }
        if (word.length() > 255) {
            word = word.substring(0, 255);
        }
        return word;
    }

    private String normalizeWord(String word) {
        return word.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private Language ensureLanguage(String isoCode, String name) {
        Optional<Language> existing = languageRepository.findByIsoCode(isoCode);
        if (existing.isPresent()) {
            return existing.get();
        }
        Language language = new Language();
        language.setIsoCode(isoCode);
        language.setName(name);
        language.setDirection("ltr");
        return languageRepository.save(language);
    }

    private String defaultLanguageName(String isoCode) {
        return switch (isoCode) {
            case "en" -> "English";
            case "zh-Hans" -> "Chinese (Simplified)";
            case "ja" -> "Japanese";
            default -> isoCode;
        };
    }

    private String normalizeLang(String value, String fallback) {
        String out = value == null ? "" : value.trim();
        return out.isEmpty() ? fallback : out;
    }

    private Path resolveSourceFile() {
        String sourcePath = properties.sourceFile();
        if (sourcePath == null || sourcePath.isBlank()) {
            sourcePath = "../datasets/high-frequency-vocabulary/30k-explained.txt";
        }
        Path path = Paths.get(sourcePath);
        return path.isAbsolute() ? path : Paths.get("").toAbsolutePath().resolve(path).normalize();
    }

    private record ParsedMeaning(String partOfSpeech, String definition, String translation) {
    }

    private static final class ImportStats {
        private final AtomicInteger parsed = new AtomicInteger();
        private final AtomicInteger processed = new AtomicInteger();
        private final AtomicInteger skipped = new AtomicInteger();
        private final AtomicInteger insertedTerms = new AtomicInteger();
        private final AtomicInteger insertedSenses = new AtomicInteger();
        private final AtomicInteger insertedTranslations = new AtomicInteger();
        private final AtomicInteger updatedSenses = new AtomicInteger();

        private ImportStats() {}
    }
}
