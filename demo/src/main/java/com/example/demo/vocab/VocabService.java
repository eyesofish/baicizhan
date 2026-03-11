package com.example.demo.vocab;

import com.example.demo.common.exception.AppException;
import com.example.demo.domain.entity.*;
import com.example.demo.domain.repository.*;
import com.example.demo.term.TermService;
import com.example.demo.vocab.dto.AddVocabItemRequest;
import com.example.demo.vocab.dto.CreateVocabListRequest;
import com.example.demo.vocab.dto.VocabItemDetailResponse;
import com.example.demo.vocab.dto.VocabItemResponse;
import com.example.demo.vocab.dto.VocabListResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VocabService {
    private final AppUserRepository appUserRepository;
    private final LanguageRepository languageRepository;
    private final TermRepository termRepository;
    private final SenseRepository senseRepository;
    private final TranslationRepository translationRepository;
    private final ExampleSentenceRepository exampleSentenceRepository;
    private final VocabListRepository vocabListRepository;
    private final VocabItemRepository vocabItemRepository;
    private final UserProgressRepository userProgressRepository;
    private final TermService termService;

    public VocabService(
        AppUserRepository appUserRepository,
        LanguageRepository languageRepository,
        TermRepository termRepository,
        SenseRepository senseRepository,
        TranslationRepository translationRepository,
        ExampleSentenceRepository exampleSentenceRepository,
        VocabListRepository vocabListRepository,
        VocabItemRepository vocabItemRepository,
        UserProgressRepository userProgressRepository,
        TermService termService
    ) {
        this.appUserRepository = appUserRepository;
        this.languageRepository = languageRepository;
        this.termRepository = termRepository;
        this.senseRepository = senseRepository;
        this.translationRepository = translationRepository;
        this.exampleSentenceRepository = exampleSentenceRepository;
        this.vocabListRepository = vocabListRepository;
        this.vocabItemRepository = vocabItemRepository;
        this.userProgressRepository = userProgressRepository;
        this.termService = termService;
    }

    @Transactional
    public VocabListResponse createList(Long userId, CreateVocabListRequest req) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4040, "USER_NOT_FOUND"));
        Language source = languageRepository.findByIsoCode(req.sourceLanguage())
            .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, 4004, "SOURCE_LANGUAGE_NOT_FOUND"));
        Language target = languageRepository.findByIsoCode(req.targetLanguage())
            .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, 4005, "TARGET_LANGUAGE_NOT_FOUND"));

        VocabList list = new VocabList();
        list.setUser(user);
        list.setName(req.name().trim());
        list.setSourceLanguage(source);
        list.setTargetLanguage(target);
        list.setIsPublic(Boolean.TRUE.equals(req.isPublic()) ? (byte) 1 : (byte) 0);
        vocabListRepository.save(list);

        return new VocabListResponse(
            list.getId(),
            list.getName(),
            source.getIsoCode(),
            target.getIsoCode(),
            list.getIsPublic() != null && list.getIsPublic() == 1,
            0,
            list.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<VocabListResponse> listMyLists(Long userId) {
        return vocabListRepository.findByUserIdOrderByUpdatedAtDesc(userId)
            .stream()
            .map(list -> new VocabListResponse(
                list.getId(),
                list.getName(),
                list.getSourceLanguage().getIsoCode(),
                list.getTargetLanguage().getIsoCode(),
                list.getIsPublic() != null && list.getIsPublic() == 1,
                vocabItemRepository.countByListId(list.getId()),
                list.getUpdatedAt()
            ))
            .toList();
    }

    @Transactional
    public VocabItemResponse addItem(Long userId, Long listId, AddVocabItemRequest req) {
        VocabList list = vocabListRepository.findByIdAndUserId(listId, userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4042, "VOCAB_LIST_NOT_FOUND"));

        String normalized = normalize(req.text());
        Term term = termRepository.findByLanguageIdAndNormalizedText(list.getSourceLanguage().getId(), normalized)
            .orElseGet(() -> createTerm(list, req, normalized));

        if (vocabItemRepository.existsByListIdAndTermId(listId, term.getId())) {
            throw new AppException(HttpStatus.CONFLICT, 4092, "TERM_ALREADY_IN_LIST");
        }

        Sense sense = maybeCreateSenseAndContent(list, term, req);

        VocabItem item = new VocabItem();
        item.setList(list);
        item.setTerm(term);
        item.setSense(sense);
        vocabItemRepository.save(item);

        termService.evictTermCache(term.getId());
        return new VocabItemResponse(list.getId(), item.getId(), term.getId(), sense == null ? null : sense.getId());
    }

    @Transactional(readOnly = true)
    public List<VocabItemDetailResponse> listItems(Long userId, Long listId) {
        vocabListRepository.findByIdAndUserId(listId, userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4042, "VOCAB_LIST_NOT_FOUND"));
        List<VocabItem> items = vocabItemRepository.findByListIdAndListUserIdOrderByCreatedAtDesc(listId, userId);
        return toItemDetailResponses(userId, items);
    }

    @Transactional
    public VocabItemDetailResponse updateItem(Long userId, Long listId, Long itemId, AddVocabItemRequest req) {
        VocabItem item = vocabItemRepository.findByIdAndListIdAndListUserId(itemId, listId, userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4043, "VOCAB_ITEM_NOT_FOUND"));
        VocabList list = item.getList();

        String normalized = normalize(req.text());
        if (normalized.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, 4006, "WORD_REQUIRED");
        }

        Term originalTerm = item.getTerm();
        Term targetTerm = termRepository.findByLanguageIdAndNormalizedText(list.getSourceLanguage().getId(), normalized).orElse(null);
        if (targetTerm == null) {
            targetTerm = createTerm(list, req, normalized);
        } else if (Objects.equals(targetTerm.getId(), originalTerm.getId())) {
            targetTerm.setText(req.text().trim());
            targetTerm.setNormalizedText(normalized);
            targetTerm.setIpa(blankToNull(req.ipa()));
            targetTerm.setAudioUrl(blankToNull(req.audioUrl()));
            targetTerm = termRepository.save(targetTerm);
        }

        if (!Objects.equals(targetTerm.getId(), originalTerm.getId()) && vocabItemRepository.existsByListIdAndTermId(listId, targetTerm.getId())) {
            throw new AppException(HttpStatus.CONFLICT, 4092, "TERM_ALREADY_IN_LIST");
        }

        item.setTerm(targetTerm);
        item.setSense(upsertSenseAndContent(list, targetTerm, item.getSense(), req));
        vocabItemRepository.save(item);

        termService.evictTermCache(targetTerm.getId());
        if (!Objects.equals(targetTerm.getId(), originalTerm.getId())) {
            termService.evictTermCache(originalTerm.getId());
        }

        return toItemDetailResponses(userId, List.of(item)).stream()
            .findFirst()
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4043, "VOCAB_ITEM_NOT_FOUND"));
    }

    @Transactional
    public void deleteItem(Long userId, Long listId, Long itemId) {
        VocabItem item = vocabItemRepository.findByIdAndListIdAndListUserId(itemId, listId, userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, 4043, "VOCAB_ITEM_NOT_FOUND"));
        Long termId = item.getTerm().getId();
        vocabItemRepository.delete(item);

        if (!vocabItemRepository.existsByListUserIdAndTermId(userId, termId)) {
            userProgressRepository.deleteByUserIdAndTermId(userId, termId);
        }

        termService.evictTermCache(termId);
    }

    private Term createTerm(VocabList list, AddVocabItemRequest req, String normalized) {
        Term term = new Term();
        term.setLanguage(list.getSourceLanguage());
        term.setText(req.text().trim());
        term.setNormalizedText(normalized);
        term.setIpa(blankToNull(req.ipa()));
        term.setAudioUrl(blankToNull(req.audioUrl()));
        return termRepository.save(term);
    }

    private Sense maybeCreateSenseAndContent(VocabList list, Term term, AddVocabItemRequest req) {
        return upsertSenseAndContent(list, term, null, req);
    }

    private Sense upsertSenseAndContent(VocabList list, Term term, Sense currentSense, AddVocabItemRequest req) {
        boolean hasSenseData =
            hasText(req.definition()) || hasText(req.partOfSpeech()) || hasText(req.translation()) || hasText(req.example());
        if (!hasSenseData) {
            return null;
        }
        Sense sense;
        if (
            currentSense != null &&
            currentSense.getTerm() != null &&
            Objects.equals(currentSense.getTerm().getId(), term.getId())
        ) {
            sense = currentSense;
        } else {
            sense = new Sense();
            sense.setTerm(term);
        }
        sense.setPartOfSpeech(blankToNull(req.partOfSpeech()));
        sense.setDefinition(blankToNull(req.definition()));
        senseRepository.save(sense);

        translationRepository.deleteBySenseId(sense.getId());
        exampleSentenceRepository.deleteBySenseId(sense.getId());

        if (hasText(req.translation())) {
            Translation trans = new Translation();
            trans.setSense(sense);
            trans.setTargetLanguage(list.getTargetLanguage());
            trans.setTranslatedText(req.translation().trim());
            trans.setSourceType("manual");
            translationRepository.save(trans);
        }
        if (hasText(req.example())) {
            ExampleSentence example = new ExampleSentence();
            example.setSense(sense);
            example.setLanguage(list.getSourceLanguage());
            example.setSentenceText(req.example().trim());
            example.setSourceType("manual");
            exampleSentenceRepository.save(example);
        }
        return sense;
    }

    private List<VocabItemDetailResponse> toItemDetailResponses(Long userId, List<VocabItem> items) {
        List<Long> termIds = items.stream()
            .map(item -> item.getTerm().getId())
            .distinct()
            .toList();
        Map<Long, UserProgress> progressByTerm = termIds.isEmpty()
            ? Map.of()
            : userProgressRepository.findByUserIdAndTermIdIn(userId, termIds)
                .stream()
                .collect(Collectors.toMap(progress -> progress.getTerm().getId(), progress -> progress));

        List<Long> senseIds = items.stream()
            .map(VocabItem::getSense)
            .filter(Objects::nonNull)
            .map(Sense::getId)
            .toList();
        Map<Long, List<ExampleSentence>> examplesBySense = senseIds.isEmpty()
            ? Map.of()
            : exampleSentenceRepository.findBySenseIdIn(senseIds)
                .stream()
                .collect(Collectors.groupingBy(example -> example.getSense().getId()));

        return items.stream()
            .map(item -> {
                Sense sense = item.getSense();
                UserProgress progress = progressByTerm.get(item.getTerm().getId());
                List<String> examples = sense == null
                    ? List.of()
                    : examplesBySense
                        .getOrDefault(sense.getId(), List.of())
                        .stream()
                        .map(ExampleSentence::getSentenceText)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(text -> !text.isEmpty())
                        .toList();
                return new VocabItemDetailResponse(
                    item.getList().getId(),
                    item.getId(),
                    item.getTerm().getId(),
                    sense == null ? null : sense.getId(),
                    item.getTerm().getText(),
                    item.getTerm().getIpa(),
                    sense == null ? null : sense.getPartOfSpeech(),
                    sense == null ? null : sense.getDefinition(),
                    examples,
                    progress == null || progress.getRepetition() == null ? 0 : progress.getRepetition(),
                    item.getCreatedAt(),
                    item.getTerm().getUpdatedAt(),
                    progress == null ? null : progress.getLastReviewAt()
                );
            })
            .toList();
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
