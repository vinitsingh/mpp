package dev.mpp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "mpp")
public class MppProperties {

    private String realm = "localhost";

    private ChallengeProps challenge = new ChallengeProps();
    private List<PaymentMethodConfig> paymentMethods = new ArrayList<>();
    private ReceiptProps receipts = new ReceiptProps();
    private DiscoveryProps discovery = new DiscoveryProps();

    @Data
    public static class ChallengeProps {
        private int expirySeconds = 300;
        private String hmacSecret = "change-me";
    }

    @Data
    public static class PaymentMethodConfig {
        private String name;
        private boolean enabled;
        private String apiKey;
    }

    @Data
    public static class ReceiptProps {
        private boolean enabled = true;
    }

    @Data
    public static class DiscoveryProps {
        private boolean enabled = true;
    }
}
