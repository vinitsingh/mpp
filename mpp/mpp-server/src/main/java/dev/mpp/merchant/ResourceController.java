package dev.mpp.merchant;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Merchant's paid API endpoints.
 * Protected by MppPaymentFilter — only accessible after payment.
 */
@RestController
@RequestMapping("/api/v1")
public class ResourceController {

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

    @PostMapping("/generate")
    public Mono<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.getOrDefault("prompt", "Hello");
        return Mono.just(Map.of(
                "id", UUID.randomUUID().toString(),
                "content", "Generated response for: " + prompt,
                "model", "mpp-demo-v1",
                "tokens_used", 842,
                "timestamp", Instant.now().toString()
        ));
    }

    @PostMapping("/image")
    public Mono<Map<String, Object>> image(@RequestBody Map<String, Object> body) {
        return Mono.just(Map.of(
                "id", UUID.randomUUID().toString(),
                "url", "https://images.example.com/" + UUID.randomUUID(),
                "prompt", body.getOrDefault("prompt", "landscape"),
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/data/{dataset}")
    public Mono<Map<String, Object>> data(@PathVariable String dataset) {
        return Mono.just(Map.of(
                "dataset", dataset,
                "records", 1000,
                "timestamp", Instant.now().toString()
        ));
    }
}
