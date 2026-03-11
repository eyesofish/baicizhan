package com.example.demo.dictionary;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.dictionary.dto.DictionaryMatchResponse;
import com.example.demo.dictionary.dto.DictionaryWordResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/dictionary")
public class DictionaryController {
    private final DictionaryService dictionaryService;

    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @GetMapping("/lookup")
    public ApiResponse<DictionaryWordResponse> lookup(@RequestParam String word) {
        return ApiResponse.ok(dictionaryService.lookup(word));
    }

    @GetMapping("/match")
    public ApiResponse<DictionaryMatchResponse> match(
        @RequestParam String prefix,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit
    ) {
        return ApiResponse.ok(dictionaryService.match(prefix, limit));
    }

    @GetMapping("/random")
    public ApiResponse<DictionaryWordResponse> random() {
        return ApiResponse.ok(dictionaryService.random());
    }
}
