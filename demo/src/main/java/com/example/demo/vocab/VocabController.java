package com.example.demo.vocab;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.security.UserPrincipal;
import com.example.demo.vocab.dto.AddVocabItemRequest;
import com.example.demo.vocab.dto.CreateVocabListRequest;
import com.example.demo.vocab.dto.VocabItemResponse;
import com.example.demo.vocab.dto.VocabListResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/lists")
public class VocabController {
    private final VocabService vocabService;

    public VocabController(VocabService vocabService) {
        this.vocabService = vocabService;
    }

    @PostMapping
    public ApiResponse<VocabListResponse> create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateVocabListRequest req
    ) {
        return ApiResponse.ok(vocabService.createList(principal.getId(), req));
    }

    @GetMapping
    public ApiResponse<List<VocabListResponse>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(vocabService.listMyLists(principal.getId()));
    }

    @PostMapping("/{listId}/items")
    public ApiResponse<VocabItemResponse> addItem(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long listId,
        @Valid @RequestBody AddVocabItemRequest req
    ) {
        return ApiResponse.ok(vocabService.addItem(principal.getId(), listId, req));
    }
}
