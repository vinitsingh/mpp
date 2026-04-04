package dev.mpp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * MPP Receipt per draft-httpauth-payment-00, Section 5.3.
 *
 * Returned in Payment-Receipt header as base64url-encoded JSON.
 *
 * Required fields: status, method, timestamp, reference
 * status MUST be "success" — receipts are only issued on success.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MppReceipt {

    /** MUST be "success" */
    @Builder.Default
    private String status = "success";

    /** Payment method used */
    private String method;

    /** RFC 3339 settlement timestamp */
    private String timestamp;

    /** Method-specific reference (tx hash, invoice id, etc.) */
    private String reference;
}
