package dev.mpp.protocol.receipt;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * MPP Receipt — server acknowledgment of successful payment.
 * Returned in Payment-Receipt header on 200 OK responses.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Receipt {

    /** The challenge this receipt responds to */
    private String challengeId;

    /** Payment method used */
    private String method;

    /** Method-specific payment reference (tx hash, PaymentIntent ID, etc.) */
    private String reference;

    /** Settlement details */
    private Settlement settlement;

    /** Payment outcome */
    @Builder.Default
    private String status = "success";

    /** When the payment was processed */
    private Instant timestamp;

    @Data
    @Builder
    public static class Settlement {
        private String amount;
        private String currency;
    }
}
