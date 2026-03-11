package com.example.demo.ai;

import com.example.demo.common.api.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks")
public class OpenAiWebhookController {
    private final AiService aiService;

    public OpenAiWebhookController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/openai")
    public ApiResponse<Void> receive(
        @RequestBody String rawBody,
        @RequestHeader Map<String, String> headers
    ) {
        aiService.handleOpenAiWebhook(rawBody, lowerCaseKeys(headers));
        return ApiResponse.ok();
    }

    private Map<String, String> lowerCaseKeys(Map<String, String> rawHeaders) {
        Map<String, String> map = new HashMap<>();
        rawHeaders.forEach((k, v) -> map.put(k.toLowerCase(), v));
        return map;
    }
}
