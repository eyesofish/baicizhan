package com.example.demo.ai;

import com.example.demo.ai.dto.AiJobQueuedResponse;
import com.example.demo.ai.dto.AiJobResponse;
import com.example.demo.ai.dto.CreateAiJobRequest;
import com.example.demo.common.api.ApiResponse;
import com.example.demo.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/ai")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/enrich")
    public ResponseEntity<ApiResponse<AiJobQueuedResponse>> create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateAiJobRequest req
    ) {
        AiJobQueuedResponse resp = aiService.createEnrichJob(principal.getId(), req);
        return ResponseEntity.accepted().body(ApiResponse.accepted("JOB_QUEUED", resp));
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<AiJobResponse> detail(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long jobId
    ) {
        return ApiResponse.ok(aiService.getJob(principal.getId(), jobId));
    }
}
