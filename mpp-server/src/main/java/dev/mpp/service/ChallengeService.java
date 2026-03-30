package dev.mpp.service;

import dev.mpp.config.MppProperties;
import dev.mpp.protocol.challenge.Challenge;
import dev.mpp.protocol.challenge.PaymentRequest;
import dev.mpp.util.MppCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages MPP challenge lifecycle:
 * - Issue HMAC-bound challenges
 * - Validate challenge IDs (tamper detection)
 * - Track used challenges (replay protection)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final MppProperties props;

    /**
     * In-memory set of consumed challenge IDs for replay protection.
     * In production, replace with Redis/DB-backed store with TTL.
     */
    private final Map<String, Instant> consumedChallenges = new ConcurrentHashMap<>();

    /**
     * Issue a new challenge for a paid resource.
     *
     * @param method  payment method ("internal", "stripe", "card", etc.)
     * @param intent  payment intent ("charge" or "session")
     * @param request the payment request details
     * @param description human-readable description
     * @return a fully formed Challenge with HMAC-bound ID
     */
    public Challenge issueChallenge(String method, String intent,
                                     PaymentRequest request, String description) {
        String requestB64 = MppCryptoUtil.encodeToBase64urlJson(request);
        Instant expires = Instant.now().plusSeconds(props.getChallenge().getExpirySeconds());
        String expiresStr = expires.toString();

        String challengeId = MppCryptoUtil.generateChallengeId(
                props.getChallenge().getHmacSecret(),
                props.getRealm(), method, intent, requestB64, expiresStr
        );

        Challenge challenge = Challenge.builder()
                .id(challengeId)
                .realm(props.getRealm())
                .method(method)
                .intent(intent)
                .expires(expires)
                .request(requestB64)
                .description(description)
                .build();

        log.debug("Issued challenge: id={}, method={}, intent={}, expires={}",
                challengeId, method, intent, expiresStr);
        return challenge;
    }

    /**
     * Validate a challenge referenced in a credential.
     *
     * Checks:
     * 1. HMAC integrity (tamper detection)
     * 2. Expiry
     * 3. Single-use (replay protection)
     */
    public ChallengeValidation validate(String challengeId, String realm, String method,
                                         String intent, String requestB64, String expires) {
        // 1. Check replay
        if (consumedChallenges.containsKey(challengeId)) {
            return ChallengeValidation.invalidResult("Challenge already consumed (replay detected)");
        }

        // 2. Verify HMAC binding
        boolean hmacValid = MppCryptoUtil.verifyChallengeId(
                challengeId, props.getChallenge().getHmacSecret(),
                realm, method, intent, requestB64, expires
        );
        if (!hmacValid) {
            return ChallengeValidation.invalidResult("Challenge ID HMAC verification failed (tampered)");
        }

        // 3. Check expiry
        if (expires != null) {
            Instant expiresAt = Instant.parse(expires);
            if (Instant.now().isAfter(expiresAt)) {
                return ChallengeValidation.expiredResult("Challenge expired at " + expires);
            }
        }

        return ChallengeValidation.validResult();
    }

    /**
     * Mark a challenge as consumed (single-use enforcement).
     */
    public void markConsumed(String challengeId) {
        consumedChallenges.put(challengeId, Instant.now());
        log.debug("Challenge consumed: {}", challengeId);

        // Lazy cleanup: remove challenges older than 2x expiry
        long cutoff = Instant.now()
                .minusSeconds(props.getChallenge().getExpirySeconds() * 2L)
                .toEpochMilli();
        consumedChallenges.entrySet()
                .removeIf(e -> e.getValue().toEpochMilli() < cutoff);
    }

    // ── Validation result ──────────────────────────────────────

    public record ChallengeValidation(boolean valid, String error, boolean expired) {
        public static ChallengeValidation validResult() {
            return new ChallengeValidation(true, null, false);
        }
        public static ChallengeValidation invalidResult(String error) {
            return new ChallengeValidation(false, error, false);
        }
        public static ChallengeValidation expiredResult(String error) {
            return new ChallengeValidation(false, error, true);
        }
    }
}
