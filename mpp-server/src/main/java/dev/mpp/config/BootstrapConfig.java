package dev.mpp.config;

import dev.mpp.payment.method.InternalLedgerPaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Bootstrap configuration: registers sample paid resources
 * and seeds test accounts on startup.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BootstrapConfig {

    @Bean
    CommandLineRunner seedData(PaidResourceRegistry registry,
                                InternalLedgerPaymentMethod ledger) {
        return args -> {
            // Register sample paid endpoints
            registry.register("/api/v1/generate",
                    PaidResourceRegistry.ResourcePricing.charge(
                            "100", "usd", "service-account-1",
                            "Text generation - $1.00 per request"));

            registry.register("/api/v1/search",
                    PaidResourceRegistry.ResourcePricing.charge(
                            "10", "usd", "service-account-1",
                            "Web search - $0.10 per query"));

            registry.register("/api/v1/image",
                    PaidResourceRegistry.ResourcePricing.charge(
                            "500", "usd", "service-account-1",
                            "Image generation - $5.00 per image"));

            registry.register("/api/v1/data",
                    PaidResourceRegistry.ResourcePricing.charge(
                            "50", "usd", "service-account-1",
                            "Data API - $0.50 per request"));

            log.info("Registered {} paid resources", registry.getAll().size());

            // Seed a test agent account with $100.00
            ledger.credit("agent-001", new BigDecimal("10000")); // 10000 cents = $100
            ledger.credit("agent-002", new BigDecimal("5000"));  // $50
            log.info("Seeded test accounts: agent-001=$100, agent-002=$50");
        };
    }
}
