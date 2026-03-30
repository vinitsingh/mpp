package dev.mpp.payment.method;

import dev.mpp.protocol.challenge.PaymentRequest;
import dev.mpp.protocol.credential.Credential;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in ledger payment method for development, testing,
 * and simple single-server deployments.
 *
 * Agents get credited via the admin API, then spend against
 * their balance through MPP challenges.
 *
 * In production, replace or augment with Stripe/crypto methods.
 */
@Slf4j
@Component
public class InternalLedgerPaymentMethod implements PaymentMethod {

    /** In-memory account balances: source -> balance in base units */
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();

    @Override
    public String methodId() {
        return "internal";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Mono<VerificationResult> verify(Credential credential, PaymentRequest request) {
        return Mono.fromCallable(() -> {
            String source = credential.getSource();
            if (source == null || source.isBlank()) {
                return VerificationResult.fail("Missing source (payer identity)");
            }

            // Check payload contains required "signature" (simple HMAC or token)
            Map<String, Object> payload = credential.getPayload();
            if (payload == null || !payload.containsKey("token")) {
                return VerificationResult.fail("Missing 'token' in payload");
            }

            // Check balance
            BigDecimal balance = balances.getOrDefault(source, BigDecimal.ZERO);
            BigDecimal amount = new BigDecimal(request.getAmount());

            if (balance.compareTo(amount) < 0) {
                return VerificationResult.fail(
                        "Insufficient balance: has " + balance + ", needs " + amount);
            }

            return VerificationResult.ok();
        });
    }

    @Override
    public Mono<String> settle(Credential credential, PaymentRequest request) {
        return Mono.fromCallable(() -> {
            String source = credential.getSource();
            BigDecimal amount = new BigDecimal(request.getAmount());

            balances.compute(source, (k, v) -> {
                BigDecimal current = v != null ? v : BigDecimal.ZERO;
                return current.subtract(amount);
            });

            String txRef = "internal-" + UUID.randomUUID().toString().substring(0, 12);
            log.info("Settled {} {} from {} → ref={}", amount, request.getCurrency(), source, txRef);
            return txRef;
        });
    }

    // ── Admin operations (exposed via AdminController) ─────────

    /**
     * Credit an account (for testing / admin top-up).
     */
    public void credit(String source, BigDecimal amount) {
        balances.merge(source, amount, BigDecimal::add);
        log.info("Credited {} to account {}, new balance: {}", amount, source, balances.get(source));
    }

    /**
     * Get current balance.
     */
    public BigDecimal getBalance(String source) {
        return balances.getOrDefault(source, BigDecimal.ZERO);
    }
}
