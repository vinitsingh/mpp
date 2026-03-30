package dev.mpp.controller;

import dev.mpp.config.PaidResourceRegistry;
import dev.mpp.payment.method.InternalLedgerPaymentMethod;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Admin API for managing the MPP server:
 * - Credit agent accounts (internal ledger)
 * - Register paid resources
 * - Check balances
 *
 * In production, protect with authentication.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final InternalLedgerPaymentMethod ledger;
    private final PaidResourceRegistry resourceRegistry;

    // ── Account Management ─────────────────────────────────────

    @PostMapping("/accounts/{source}/credit")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> creditAccount(
            @PathVariable String source,
            @RequestBody CreditRequest request) {
        ledger.credit(source, new BigDecimal(request.getAmount()));
        return Mono.just(Map.of(
                "source", source,
                "credited", request.getAmount(),
                "balance", ledger.getBalance(source).toString()
        ));
    }

    @GetMapping("/accounts/{source}/balance")
    public Mono<Map<String, Object>> getBalance(@PathVariable String source) {
        return Mono.just(Map.of(
                "source", source,
                "balance", ledger.getBalance(source).toString()
        ));
    }

    // ── Resource Pricing ───────────────────────────────────────

    @PostMapping("/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> registerResource(@RequestBody ResourceRegistration reg) {
        PaidResourceRegistry.ResourcePricing pricing =
                PaidResourceRegistry.ResourcePricing.charge(
                        reg.getAmount(), reg.getCurrency(),
                        reg.getRecipient(), reg.getDescription()
                );
        if (reg.getMethod() != null) pricing.setMethod(reg.getMethod());
        if (reg.getIntent() != null) pricing.setIntent(reg.getIntent());

        resourceRegistry.register(reg.getPath(), pricing);
        return Mono.just(Map.of(
                "path", reg.getPath(),
                "pricing", pricing,
                "status", "registered"
        ));
    }

    @GetMapping("/resources")
    public Mono<Map<String, PaidResourceRegistry.ResourcePricing>> listResources() {
        return Mono.just(resourceRegistry.getAll());
    }

    // ── Request DTOs ───────────────────────────────────────────

    @Data
    public static class CreditRequest {
        private String amount;
    }

    @Data
    public static class ResourceRegistration {
        private String path;
        private String amount;
        private String currency;
        private String recipient;
        private String method;
        private String intent;
        private String description;
    }
}
