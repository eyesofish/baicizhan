package com.example.demo.term;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.term.dto.TermDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/terms")
public class TermController {
    private final TermService termService;

    public TermController(TermService termService) {
        this.termService = termService;
    }

    @GetMapping("/{termId}")
    public ApiResponse<TermDetailResponse> detail(@PathVariable Long termId) {
        return ApiResponse.ok(termService.getTermDetail(termId));
    }
}
