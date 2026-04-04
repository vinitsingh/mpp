package dev.mpp.merchant;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ResourceRegistry {

    private final Map<String, ResourcePricing> resources = new ConcurrentHashMap<>();

    public void register(String path, ResourcePricing pricing) {
        resources.put(path, pricing);
    }

    public Optional<ResourcePricing> findPricing(String path) {
        if (resources.containsKey(path)) return Optional.of(resources.get(path));
        return resources.entrySet().stream()
                .filter(e -> path.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public Map<String, ResourcePricing> getAll() { return Map.copyOf(resources); }

    @Data
    public static class ResourcePricing {
        private String amount;
        private String currency;
        private String description;

        public static ResourcePricing of(String amount, String currency, String desc) {
            ResourcePricing p = new ResourcePricing();
            p.setAmount(amount);
            p.setCurrency(currency);
            p.setDescription(desc);
            return p;
        }
    }
}
