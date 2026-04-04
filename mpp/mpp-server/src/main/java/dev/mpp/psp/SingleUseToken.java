package dev.mpp.psp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Single-Use Token — Case 2: Pay-As-Go.
 *
 * Created when agent forwards the MPP challenge to PSP.
 * PSP validates agent + wallet + card, stores the challenge context,
 * and returns a token. No money moves.
 *
 * The token payload is later embedded in the spec-compliant
 * Authorization: Payment <credential-json> by the agent.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SingleUseToken {

    private String tokenId;
    private String agentId;
    private String walletId;
    private String paymentMethodId;
    private String cardBrand;
    private String cardLast4;

    /** The MPP challenge this token was issued against */
    private ChallengeContext challengeContext;

    private Instant createdAt;
    private Instant expiresAt;

    @Builder.Default
    private boolean redeemed = false;
    private String settlementRef;

    @Data
    @Builder
    public static class ChallengeContext {
        private String challengeId;
        private String realm;
        private String method;
        private String intent;
        private String request;   // base64url — contains amount, currency, recipient
        private String expires;
        private String description;
    }
}
