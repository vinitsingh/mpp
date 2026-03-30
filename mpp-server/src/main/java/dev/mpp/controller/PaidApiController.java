package dev.mpp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Example paid API endpoints.
 * These are protected by the MppPaymentFilter — any request
 * without a valid payment credential gets a 402 challenge.
 *
 * Register these via the Admin API:
 *   POST /admin/resources
 *   { "path": "/api/v1/generate", "amount": "100", "currency": "usd", ... }
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaidApiController {

    /**
     * Example: Text generation endpoint (pay-per-request).
     * Agent pays per call, gets generated content.
     */
    @PostMapping("/generate")
    public Mono<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.getOrDefault("prompt", "Hello");
        return Mono.just(Map.of(
                "id", UUID.randomUUID().toString(),
                "content", "Generated response for: " + prompt,
                "model", "mpp-demo-v1",
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 50),
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Example: Web search endpoint (pay-per-query).
     */
    @GetMapping("/search")
    public Mono<Map<String, Object>> search(@RequestParam String q) {
        return Mono.just(Map.of(
                "query", q,
                "results", java.util.List.of(
                        Map.of("title", "Result 1 for: " + q, "url", "https://example.com/1"),
                        Map.of("title", "Result 2 for: " + q, "url", "https://example.com/2")
                ),
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Example: Image generation (pay-per-image).
     */
    @PostMapping("/image")
    public Mono<Map<String, Object>> image(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.getOrDefault("prompt", "A landscape");
        return Mono.just(Map.of(
                "id", UUID.randomUUID().toString(),
                "url", "https://example.com/images/" + UUID.randomUUID(),
                "prompt", prompt,
                "format", "png",
                "dimensions", Map.of("width", 1024, "height", 1024),
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Example: Data API (pay-per-request).
     */
    @GetMapping("/data/{dataset}")
    public Mono<Map<String, Object>> data(@PathVariable String dataset) {
        return Mono.just(Map.of(
                "dataset", dataset,
                "records", 1000,
                "sample", java.util.List.of(
                        Map.of("id", 1, "value", "sample_data_1"),
                        Map.of("id", 2, "value", "sample_data_2")
                ),
                "timestamp", Instant.now().toString()
        ));
    }
}
