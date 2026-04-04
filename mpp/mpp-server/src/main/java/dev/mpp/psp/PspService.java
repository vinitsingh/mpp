package dev.mpp.psp;

import dev.mpp.boarding.BoardingService;
import dev.mpp.boarding.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PspService {

    private static final int SINGLE_TOKEN_TTL = 300;
    private static final int MULTI_TOKEN_TTL = 86400;

    private final BoardingService boardingService;

    private final Map<String, SingleUseToken> singleTokens = new ConcurrentHashMap<>();
    private final Map<String, MultiUseToken> multiTokens = new ConcurrentHashMap<>();

    // ══════════════════════════════════════════════════════════
    //  Case 2: Single-Use Token
    // ══════════════════════════════════════════════════════════

    /**
     * Issue single-use token. Agent passes the MPP challenge context.
     * No money moves — just validates agent + wallet + card.
     */
    public SingleUseToken issueSingleUseToken(
            String agentId, String walletId,
            SingleUseToken.ChallengeContext challengeCtx) {

        var v = boardingService.validate(agentId, walletId);
        if (!v.valid()) throw new PspException("verification-failed", v.error());

        Wallet.PaymentMethod pm = v.paymentMethod();
        String tokenId = "tok_" + shortUuid();

        SingleUseToken token = SingleUseToken.builder()
                .tokenId(tokenId)
                .agentId(agentId)
                .walletId(walletId)
                .paymentMethodId(pm.getPaymentMethodId())
                .cardBrand(pm.getBrand())
                .cardLast4(pm.getLast4())
                .challengeContext(challengeCtx)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(SINGLE_TOKEN_TTL))
                .redeemed(false)
                .build();

        singleTokens.put(tokenId, token);
        log.info("Issued tok {} for agent {} challenge {}",
                tokenId, agentId, challengeCtx.getChallengeId());
        return token;
    }

    /**
     * Merchant calls this to authorize + settle a single-use token.
     */
    public PaymentResult authorizeSingleUse(String tokenId, String amount, String currency) {
        SingleUseToken token = singleTokens.get(tokenId);
        if (token == null) throw new PspException("invalid-challenge", "Unknown token: " + tokenId);
        if (token.isRedeemed()) throw new PspException("invalid-challenge", "Token already redeemed: " + tokenId);
        if (Instant.now().isAfter(token.getExpiresAt())) {
            singleTokens.remove(tokenId);
            throw new PspException("payment-expired", "Token expired: " + tokenId);
        }

        var v = boardingService.validate(token.getAgentId(), token.getWalletId());
        if (!v.valid()) throw new PspException("verification-failed", v.error());

        long amountVal = Long.parseLong(amount);
        long limit = Long.parseLong(v.paymentMethod().getSpendingLimit());
        if (amountVal > limit) {
            throw new PspException("payment-insufficient", "Amount " + amount + " exceeds limit " + limit);
        }

        String ref = "txn_" + shortUuid();
        token.setRedeemed(true);
        token.setSettlementRef(ref);

        log.info("Settled tok {} → {} {} ref={}", tokenId, amount, currency, ref);
        return new PaymentResult(ref, amount, currency, "settled", token.getAgentId());
    }

    // ══════════════════════════════════════════════════════════
    //  Case 3: Multi-Use Token
    // ══════════════════════════════════════════════════════════

    public MultiUseToken issueMultiUseToken(
            String agentId, String walletId,
            String holdAmount, String currency) {

        var v = boardingService.validate(agentId, walletId);
        if (!v.valid()) throw new PspException("verification-failed", v.error());

        long hold = Long.parseLong(holdAmount);
        long limit = Long.parseLong(v.paymentMethod().getSpendingLimit());
        if (hold > limit) {
            throw new PspException("payment-insufficient", "Hold " + holdAmount + " exceeds limit " + limit);
        }

        String tokenId = "mtok_" + shortUuid();
        MultiUseToken token = MultiUseToken.builder()
                .tokenId(tokenId)
                .agentId(agentId)
                .walletId(walletId)
                .paymentMethodId(v.paymentMethod().getPaymentMethodId())
                .cardBrand(v.paymentMethod().getBrand())
                .cardLast4(v.paymentMethod().getLast4())
                .holdAmount(holdAmount)
                .usedAmount("0")
                .currency(currency)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(MULTI_TOKEN_TTL))
                .exhausted(false)
                .usageLog(new ArrayList<>())
                .build();

        multiTokens.put(tokenId, token);
        log.info("Issued mtok {} for agent {} hold={} {}", tokenId, agentId, holdAmount, currency);
        return token;
    }

    public PaymentResult authorizeMultiUse(String tokenId, String amount,
                                            String currency, String resourcePath) {
        MultiUseToken token = multiTokens.get(tokenId);
        if (token == null) throw new PspException("invalid-challenge", "Unknown token: " + tokenId);
        if (token.isExhausted()) throw new PspException("payment-insufficient", "Token exhausted: " + tokenId);
        if (Instant.now().isAfter(token.getExpiresAt())) {
            multiTokens.remove(tokenId);
            throw new PspException("payment-expired", "Token expired: " + tokenId);
        }

        BigDecimal remaining = token.remaining();
        BigDecimal requestAmt = new BigDecimal(amount);
        if (requestAmt.compareTo(remaining) > 0) {
            token.setExhausted(true);
            throw new PspException("payment-insufficient", "Remaining " + remaining + " < " + amount);
        }

        String ref = "txn_" + shortUuid();
        BigDecimal newUsed = new BigDecimal(token.getUsedAmount()).add(requestAmt);
        token.setUsedAmount(newUsed.toString());
        if (newUsed.compareTo(new BigDecimal(token.getHoldAmount())) >= 0) {
            token.setExhausted(true);
        }

        token.getUsageLog().add(MultiUseToken.UsageRecord.builder()
                .amount(amount).resource(resourcePath)
                .settlementRef(ref).timestamp(Instant.now()).build());

        log.info("Debited mtok {} → {} {} for {} (used={}/{})",
                tokenId, amount, currency, resourcePath, newUsed, token.getHoldAmount());
        return new PaymentResult(ref, amount, currency, "settled", token.getAgentId());
    }

    public Optional<SingleUseToken> getSingleUseToken(String id) { return Optional.ofNullable(singleTokens.get(id)); }
    public Optional<MultiUseToken> getMultiUseToken(String id) { return Optional.ofNullable(multiTokens.get(id)); }

    public record PaymentResult(String reference, String amount, String currency, String status, String agentId) {}

    public static class PspException extends RuntimeException {
        private final String errorType;
        public PspException(String errorType, String msg) { super(msg); this.errorType = errorType; }
        public String getErrorType() { return errorType; }
    }

    private String shortUuid() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
}
