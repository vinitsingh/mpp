package dev.mpp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * MPP Challenge per draft-httpauth-payment-00.
 *
 * Required params: id, realm, method, intent, request
 * Optional params: expires, description, opaque, digest
 *
 * The 'request' param is a base64url-encoded JCS-serialized JSON
 * containing method-specific payment details (amount, currency, recipient, etc.)
 *
 * Amount/currency/recipient NEVER appear as top-level header params.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MppChallenge {

    // ── Required (Section 5.1.1) ───────────────────────────────
    private String id;
    private String realm;
    private String method;
    private String intent;
    private String request;      // base64url-encoded JSON

    // ── Optional (Section 5.1.2) ───────────────────────────────
    private String expires;      // RFC 3339 timestamp
    private String description;  // human-readable, display only
    private String opaque;       // base64url-encoded server correlation data
    private String digest;       // RFC 9530 content digest

    /**
     * Serialize to WWW-Authenticate header value per Section 5.1.
     *
     * Format: Payment id="...", realm="...", method="...", intent="...",
     *         request="...", expires="...", description="..."
     */
    public String toWwwAuthenticate() {
        StringBuilder sb = new StringBuilder("Payment ");
        appendParam(sb, "id", id, true);
        appendParam(sb, "realm", realm, false);
        appendParam(sb, "method", method, false);
        appendParam(sb, "intent", intent, false);
        appendParam(sb, "request", request, false);
        if (expires != null) appendParam(sb, "expires", expires, false);
        if (description != null) appendParam(sb, "description", description, false);
        if (opaque != null) appendParam(sb, "opaque", opaque, false);
        if (digest != null) appendParam(sb, "digest", digest, false);
        return sb.toString();
    }

    private void appendParam(StringBuilder sb, String name, String value, boolean first) {
        if (!first) sb.append(", ");
        sb.append(name).append("=\"").append(value).append("\"");
    }

    /**
     * Decode the request param to a JSON string.
     */
    public String decodeRequest() {
        byte[] decoded = Base64.getUrlDecoder().decode(request);
        return new String(decoded, StandardCharsets.UTF_8);
    }
}
