package dev.mpp.psp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-Use Token — Case 3: Pre-Pay Session.
 *
 * PSP holds funds on agent's stored card, returns a token.
 * Agent uses this token for multiple resource accesses.
 * Each merchant call debits from the hold via PSP.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MultiUseToken {

    private String tokenId;
    private String agentId;
    private String walletId;
    private String paymentMethodId;
    private String cardBrand;
    private String cardLast4;

    private String holdAmount;
    private String usedAmount;
    private String currency;

    private Instant createdAt;
    private Instant expiresAt;

    @Builder.Default
    private boolean exhausted = false;

    @Builder.Default
    private List<UsageRecord> usageLog = new ArrayList<>();

    @Data
    @Builder
    public static class UsageRecord {
        private String amount;
        private String resource;
        private String settlementRef;
        private Instant timestamp;
    }

    public BigDecimal remaining() {
        return new BigDecimal(holdAmount).subtract(new BigDecimal(usedAmount));
    }
}
