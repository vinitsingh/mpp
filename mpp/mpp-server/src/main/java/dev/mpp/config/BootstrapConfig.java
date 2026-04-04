package dev.mpp.config;

import dev.mpp.boarding.BoardingService;
import dev.mpp.merchant.ResourceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BootstrapConfig {

    @Bean
    CommandLineRunner seedData(ResourceRegistry registry, BoardingService boarding) {
        return args -> {
            // ── Merchant: register paid resources ──────────────
            registry.register("/api/v1/search",
                    ResourceRegistry.ResourcePricing.of("10", "usd",
                            "Web search - $0.10 per query"));
            registry.register("/api/v1/generate",
                    ResourceRegistry.ResourcePricing.of("100", "usd",
                            "Text generation - $1.00 per request"));
            registry.register("/api/v1/image",
                    ResourceRegistry.ResourcePricing.of("500", "usd",
                            "Image generation - $5.00 per image"));
            registry.register("/api/v1/data",
                    ResourceRegistry.ResourcePricing.of("50", "usd",
                            "Data API - $0.50 per request"));

            log.info("Registered {} paid resources", registry.getAll().size());

            // ── Boarding: seed test agent + wallet + card ──────
            var agent = boarding.registerAgent("Test Agent Alpha", "vinit@example.com");
            var wallet = boarding.createWallet(agent.getAgentId());
            boarding.addPaymentMethod(wallet.getWalletId(),
                    "card", "visa", "4242", "12/28", "100000");

            var agent2 = boarding.registerAgent("Test Agent Beta", "vinit@example.com");
            var wallet2 = boarding.createWallet(agent2.getAgentId());
            boarding.addPaymentMethod(wallet2.getWalletId(),
                    "card", "mastercard", "5555", "06/27", "500000");

            log.info("Seeded agents: {} ({}), {} ({})",
                    agent.getAgentId(), wallet.getWalletId(),
                    agent2.getAgentId(), wallet2.getWalletId());
        };
    }
}
