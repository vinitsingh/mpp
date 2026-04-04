package dev.mpp.boarding;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wallet {

    private String walletId;
    private String agentId;

    @Builder.Default
    private List<PaymentMethod> paymentMethods = new ArrayList<>();

    private Instant createdAt;

    @Builder.Default
    private boolean active = true;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaymentMethod {
        private String paymentMethodId;
        private String type;       // "card", "bank_account", "stablecoin"
        private String brand;      // "visa", "mastercard", "amex"
        private String last4;
        private String expiry;
        private String spendingLimit;  // per-transaction limit in base units

        @Builder.Default
        private boolean isDefault = false;
    }
}
