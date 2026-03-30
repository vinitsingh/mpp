package dev.mpp.config;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of paid resources: maps URI patterns to pricing.
 * Services register their endpoints here, and the MppPaymentFilter
 * checks incoming requests against this registry.
 *
 * In production, load from DB or config file.
 */
@Component
public class PaidResourceRegistry {

    private final Map<String, ResourcePricing> resources = new ConcurrentHashMap<>();

    /**
     * Register a paid resource.
     */
    public void register(String pathPattern, ResourcePricing pricing) {
        resources.put(pathPattern, pricing);
    }

    /**
     * Find pricing for a request path. Simple prefix matching.
     */
    public Optional<ResourcePricing> findPricing(String path) {
        // Exact match first
        if (resources.containsKey(path)) {
            return Optional.of(resources.get(path));
        }
        // Prefix match
        return resources.entrySet().stream()
                .filter(e -> path.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public Map<String, ResourcePricing> getAll() {
        return Map.copyOf(resources);
    }

    @Data
    public static class ResourcePricing {
        private String amount;         // base units (e.g. "100" = $1.00)
        private String currency;       // "usd", "eur", etc.
        private String recipient;      // payment destination
        private String method;         // preferred payment method
        private String intent;         // "charge" or "session"
        private String description;    // human-readable

        public static ResourcePricing charge(String amount, String currency,
                                              String recipient, String description) {
            ResourcePricing p = new ResourcePricing();
            p.setAmount(amount);
            p.setCurrency(currency);
            p.setRecipient(recipient);
            p.setMethod("internal");
            p.setIntent("charge");
            p.setDescription(description);
            return p;
        }
    }
}
