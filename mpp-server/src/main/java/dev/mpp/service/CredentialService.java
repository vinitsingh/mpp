package dev.mpp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mpp.payment.method.PaymentMethod;
import dev.mpp.payment.method.PaymentMethodRegistry;
import dev.mpp.protocol.challenge.PaymentRequest;
import dev.mpp.protocol.credential.Credential;
import dev.mpp.protocol.receipt.Receipt;
import dev.mpp.util.MppCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the credential verification and settlement flow:
 * 1. Parse Authorization header → Credential
 * 2. Validate challenge binding (HMAC, expiry, replay)
 * 3. Decode payment request
 * 4. Route to correct PaymentMethod for verification
 * 5. Settle the payment
 * 6. Generate receipt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialService {

    private final ChallengeService challengeService;
    private final PaymentMethodRegistry methodRegistry;
    private final ReceiptService receiptService;
    private final ObjectMapper objectMapper;

    /**
     * Parse the Authorization: Payment <base64url> header value.
     */
    public Credential parseCredential(String authHeaderValue) {
        if (authHeaderValue == null || !authHeaderValue.startsWith("Payment ")) {
            throw new MppProtocolException("malformed-credential",
                    "Authorization header must start with 'Payment '");
        }

        String encoded = authHeaderValue.substring("Payment ".length()).trim();

        try {
            String json = MppCryptoUtil.base64urlDecodeToString(encoded);
            return objectMapper.readValue(json, Credential.class);
        } catch (Exception e) {
            throw new MppProtocolException("malformed-credential",
                    "Failed to decode credential: " + e.getMessage());
        }
    }

    /**
     * Full verification + settlement pipeline.
     * Returns a receipt on success, or errors via MppProtocolException.
     */
    public Mono<Receipt> verifyAndSettle(Credential credential) {
        Credential.ChallengeRef ref = credential.getChallenge();
        if (ref == null || ref.getId() == null) {
            return Mono.error(new MppProtocolException("malformed-credential",
                    "Missing challenge reference in credential"));
        }

        // 1. Validate challenge
        var validation = challengeService.validate(
                ref.getId(), ref.getRealm(), ref.getMethod(),
                ref.getIntent(), ref.getRequest(), ref.getExpires()
        );

        if (!validation.valid()) {
            String errorType = validation.expired() ? "payment-expired" : "invalid-challenge";
            return Mono.error(new MppProtocolException(errorType, validation.error()));
        }

        // 2. Resolve payment method
        PaymentMethod method = methodRegistry.get(ref.getMethod())
                .orElseThrow(() -> new MppProtocolException("method-unsupported",
                        "Payment method '" + ref.getMethod() + "' not supported"));

        // 3. Decode payment request
        PaymentRequest request;
        try {
            request = MppCryptoUtil.decodeFromBase64urlJson(ref.getRequest());
        } catch (Exception e) {
            return Mono.error(new MppProtocolException("malformed-credential",
                    "Failed to decode payment request: " + e.getMessage()));
        }

        // 4. Verify → 5. Settle → 6. Receipt
        return method.verify(credential, request)
                .flatMap(result -> {
                    if (!result.success()) {
                        return Mono.error(new MppProtocolException(
                                "verification-failed", result.error()));
                    }
                    return method.settle(credential, request);
                })
                .map(reference -> {
                    // Mark challenge consumed AFTER successful settlement
                    challengeService.markConsumed(ref.getId());

                    return receiptService.createReceipt(
                            ref.getId(), ref.getMethod(), reference, request);
                });
    }

    // ── Protocol exception ─────────────────────────────────────

    public static class MppProtocolException extends RuntimeException {
        private final String errorType;

        public MppProtocolException(String errorType, String message) {
            super(message);
            this.errorType = errorType;
        }

        public String getErrorType() {
            return errorType;
        }
    }
}
