package dev.mpp.payment.method;

import dev.mpp.protocol.challenge.PaymentRequest;
import dev.mpp.protocol.credential.Credential;
import dev.mpp.protocol.receipt.Receipt;
import reactor.core.publisher.Mono;

/**
 * Service Provider Interface for MPP payment methods.
 *
 * Each payment method (internal ledger, Stripe, card, Lightning, etc.)
 * implements this interface. The PaymentMethodRegistry discovers them
 * via Spring's component scan.
 *
 * Lifecycle per transaction:
 * 1. Server calls supports() to check if method handles this request
 * 2. Server calls verify() to validate the credential's payment proof
 * 3. On success, server calls settle() to finalize the payment
 */
public interface PaymentMethod {

    /** Unique method identifier (e.g. "internal", "stripe", "card", "tempo") */
    String methodId();

    /** Whether this method is currently active/configured */
    boolean isEnabled();

    /**
     * Verify a credential's payment proof.
     * Must check: amount, recipient, currency, signature/proof validity.
     *
     * @param credential the client-submitted credential
     * @param request    the decoded payment request from the challenge
     * @return verification result
     */
    Mono<VerificationResult> verify(Credential credential, PaymentRequest request);

    /**
     * Settle the payment (finalize, capture, submit on-chain, etc.)
     * Called only after verify() returns success.
     *
     * @param credential the verified credential
     * @param request    the payment request
     * @return settlement reference (tx hash, PaymentIntent ID, etc.)
     */
    Mono<String> settle(Credential credential, PaymentRequest request);

    // ── Verification result ────────────────────────────────────

    record VerificationResult(boolean success, String error) {
        public static VerificationResult ok() {
            return new VerificationResult(true, null);
        }
        public static VerificationResult fail(String error) {
            return new VerificationResult(false, error);
        }
    }
}
