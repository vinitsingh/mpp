package dev.mpp.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.mpp.protocol.challenge.PaymentRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Utility class for MPP cryptographic operations:
 * - Base64url encoding/decoding (RFC 4648 §5)
 * - HMAC-SHA256 for challenge ID binding
 * - SHA-256 content digest (RFC 9530)
 */
public final class MppCryptoUtil {

    private static final ObjectMapper MAPPER = createObjectMapper();

    private MppCryptoUtil() {}

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.findAndRegisterModules();
        return mapper;
    }

    // ── Base64url ──────────────────────────────────────────────

    public static String base64urlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static String base64urlEncode(String json) {
        return base64urlEncode(json.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] base64urlDecode(String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }

    public static String base64urlDecodeToString(String encoded) {
        return new String(base64urlDecode(encoded), StandardCharsets.UTF_8);
    }

    /**
     * Encode an object to base64url JSON (for the 'request' field).
     */
    public static String encodeToBase64urlJson(Object obj) {
        try {
            String json = MAPPER.writeValueAsString(obj);
            return base64urlEncode(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Decode a base64url JSON string back to an object.
     */
    public static PaymentRequest decodeFromBase64urlJson(String encoded) {
        try {
            String json = base64urlDecodeToString(encoded);
            return MAPPER.readValue(json, PaymentRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize base64url JSON", e);
        }
    }

    // ── HMAC-SHA256 (Challenge ID binding) ─────────────────────

    /**
     * Generate an HMAC-bound challenge ID.
     * Binds: realm | method | intent | sha256(request) | expires
     */
    public static String generateChallengeId(String secret, String realm, String method,
                                              String intent, String requestB64, String expires) {
        String data = String.join("|", realm, method, intent,
                sha256Hex(requestB64), expires != null ? expires : "");
        return hmacSha256Base64url(secret, data);
    }

    /**
     * Verify that a challenge ID matches its parameters (tamper detection).
     */
    public static boolean verifyChallengeId(String challengeId, String secret, String realm,
                                             String method, String intent, String requestB64,
                                             String expires) {
        String expected = generateChallengeId(secret, realm, method, intent, requestB64, expires);
        return MessageDigest.isEqual(
                challengeId.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String hmacSha256Base64url(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64urlEncode(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    // ── SHA-256 Content Digest (RFC 9530) ──────────────────────

    /**
     * Compute SHA-256 content digest for request body binding.
     * Returns in RFC 9530 format: sha-256=:<base64>:
     */
    public static String contentDigest(byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(body);
            return "sha-256=:" + Base64.getEncoder().encodeToString(hash) + ":";
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
