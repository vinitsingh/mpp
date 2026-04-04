package dev.mpp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * MPP Credential per draft-httpauth-payment-00, Section 5.2.
 *
 * Sent as: Authorization: Payment <base64url-encoded-json>
 *
 * The JSON contains:
 *   challenge: { id, realm, method, intent, request, expires?, opaque?, digest? }
 *   source:    payer identity (RECOMMENDED: DID format)
 *   payload:   method-specific payment proof
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MppCredential {

    /** Echoed challenge parameters */
    private ChallengeRef challenge;

    /** Payer identifier (DID recommended, e.g. "did:key:z6Mk...") */
    private String source;

    /** Method-specific payment proof */
    private Map<String, Object> payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChallengeRef {
        private String id;
        private String realm;
        private String method;
        private String intent;
        private String request;
        private String expires;
        private String description;
        private String opaque;
        private String digest;
    }
}
