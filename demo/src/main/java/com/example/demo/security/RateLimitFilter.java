package com.example.demo.security;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (isIgnoredPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = resolveKey(request);
        if (allowRequest(key)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(4290, "TOO_MANY_REQUESTS")));
    }

    private boolean isIgnoredPath(String uri) {
        return uri.startsWith("/actuator/");
    }

    private boolean allowRequest(String key) {
        long currentMinute = System.currentTimeMillis() / 60000L;
        Counter counter = counters.computeIfAbsent(key, k -> new Counter(currentMinute));
        long currentCount;
        synchronized (counter) {
            if (counter.minute != currentMinute) {
                counter.minute = currentMinute;
                counter.count = 0;
            }
            counter.count += 1;
            currentCount = counter.count;
            counter.lastSeenMinute = currentMinute;
        }
        cleanupIfNeeded(currentMinute);
        return currentCount <= Math.max(properties.requestsPerMinute(), 60);
    }

    private void cleanupIfNeeded(long currentMinute) {
        if (counters.size() <= 10_000) {
            return;
        }
        counters.entrySet().removeIf(e -> currentMinute - e.getValue().lastSeenMinute > 5);
    }

    private String resolveKey(HttpServletRequest request) {
        String forward = request.getHeader("X-Forwarded-For");
        if (forward != null && !forward.isBlank()) {
            int idx = forward.indexOf(',');
            return idx > 0 ? forward.substring(0, idx).trim() : forward.trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Counter {
        private long minute;
        private long lastSeenMinute;
        private long count;

        private Counter(long minute) {
            this.minute = minute;
            this.lastSeenMinute = minute;
        }
    }
}
