package com.mergefruit.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mergefruit.backend.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/*
 Learning Notes — SKELETON (you extend this)

 What: In-memory per-IP rate limiting filter.
 Why: Reduces abuse of expensive endpoints (DB writes, auth attempts).
      This is application-level throttling — not a substitute for CDN/WAF DDoS protection.

 Limitations of this simple approach:
 - Resets on app restart
 - Not shared across multiple server instances
 - Production: use Redis + Bucket4j or an API gateway

 TODO (Student):
 1. Track requests per IP in a sliding window (hint: store timestamps in a List).
 2. Return 429 Too Many Requests with Retry-After header.
 3. Apply stricter limits to POST /api/auth/login than to GET /api/scores.

 Try yourself:
 - Replace ConcurrentHashMap with a Caffeine cache that evicts old entries.

 Common mistake:
 - Rate limiting only at the controller — attackers can hit other endpoints.
*/
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final int requestsPerMinute;
    private final ObjectMapper objectMapper;
    private final Map<String, List<Instant>> requestsByIp = new ConcurrentHashMap<>();
    private final int windowDurationSeconds = 60;

    public RateLimitFilter(
            @Value("${app.rate-limit.requests-per-minute}") int requestsPerMinute,
            ObjectMapper objectMapper) {
        this.requestsPerMinute = requestsPerMinute;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        int limit = "/api/auth/login".equals(request.getRequestURI()) ? 10 : this.requestsPerMinute;
        String clientIp = resolveClientIp(request);
        var now = Instant.now();
        var windowStart = now.minusSeconds(windowDurationSeconds);
        List<Instant> requests = requestsByIp.computeIfAbsent(clientIp, key -> new ArrayList<>());
        synchronized (requests) {
            requests.removeIf(timestamp -> timestamp.isBefore(windowStart));
            if (requests.size() >= limit) {
                response.setHeader("Retry-After", Integer.toString(windowDurationSeconds));
                sendTooManyRequests(response, request);
                return;
            }
            requests.add(now);
        }

        filterChain.doFilter(request, response);
    }

    private void sendTooManyRequests(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                429,
                "Too Many Requests",
                "Rate limit exceeded. Please try again later.",
                request.getRequestURI()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
