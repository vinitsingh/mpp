package dev.mpp.protocol.challenge;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * MPP Challenge — server-issued payment requirement.
 * Sent in WWW-Authenticate: Payment header on HTTP 402 responses.
 *
 * Per spec: id is HMAC-bound to (realm, method, intent, request-hash, expires)
 * to prevent parameter tampering.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Challenge {

    /** Unique, HMAC-bound challenge identifier */
    private String id;

    /** Protection space (API domain) */
    private String realm;

    /** Payment method identifier: "internal", "stripe", "card", "tempo", etc. */
    private String method;

    /** Payment intent type: "charge" or "session" */
    private String intent;

    /** ISO-8601 expiry timestamp */
    private Instant expires;

    /** Base64url-encoded JSON with method-specific payment details */
    private String request;

    /** Human-readable description of the resource being paid for */
    private String description;

    /** Optional body digest for POST/PUT/PATCH binding (RFC 9530) */
    private String digest;

    /**
     * Serialize to WWW-Authenticate header value.
     * Format: Payment id="...", realm="...", method="...", intent="...", request="..."
     */
    public String toWwwAuthenticate() {
        StringBuilder sb = new StringBuilder("Payment ");
        sb.append("id=\"").append(id).append("\"");
        sb.append(", realm=\"").append(realm).append("\"");
        sb.append(", method=\"").append(method).append("\"");
        sb.append(", intent=\"").append(intent).append("\"");
        if (expires != null) {
            sb.append(", expires=\"").append(expires.toString()).append("\"");
        }
        sb.append(", request=\"").append(request).append("\"");
        if (description != null) {
            sb.append(", description=\"").append(description).append("\"");
        }
        if (digest != null) {
            sb.append(", digest=\"").append(digest).append("\"");
        }
        return sb.toString();
    }
}
