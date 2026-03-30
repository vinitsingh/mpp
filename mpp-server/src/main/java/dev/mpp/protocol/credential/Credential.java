package dev.mpp.protocol.credential;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * MPP Credential — client-submitted payment proof.
 * Sent in Authorization: Payment <base64url-encoded-json> header.
 *
 * Each credential is single-use: valid for exactly one request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Credential {

    /** The challenge being responded to (echoed back) */
    private ChallengeRef challenge;

    /** Identity of the payer (address, DID, account ID) */
    private String source;

    /** Method-specific payment proof (signature, tx hash, PaymentIntent ID, etc.) */
    private Map<String, Object> payload;

    /**
     * Embedded challenge reference within a credential.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChallengeRef {
        private String id;
        private String realm;
        private String method;
        private String intent;
        private String request;
        private String expires;
    }
}
