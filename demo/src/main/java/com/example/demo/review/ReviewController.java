package com.example.demo.review;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.review.dto.ReviewCardResponse;
import com.example.demo.review.dto.ReviewResultRequest;
import com.example.demo.review.dto.ReviewResultResponse;
import com.example.demo.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/v1/review")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/next")
    public ApiResponse<List<ReviewCardResponse>> next(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.ok(reviewService.nextCards(principal.getId(), limit));
    }

    @PostMapping("/{termId}/result")
    public ApiResponse<ReviewResultResponse> submit(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long termId,
        @Valid @RequestBody ReviewResultRequest req
    ) {
        return ApiResponse.ok(reviewService.submit(principal.getId(), termId, req));
    }
}
