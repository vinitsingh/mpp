package dev.mpp.protocol.challenge;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Decoded contents of the Challenge 'request' parameter.
 * Contains method-specific payment details.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = PaymentRequest.PaymentRequestBuilder.class)
public class PaymentRequest {

    /** Amount in base units (e.g. cents for USD, satoshis for BTC) */
    private String amount;

    /** Currency code ("usd") or token address ("0x20c0...") */
    private String currency;

    /** Payment destination in method-native format */
    private String recipient;

    /** Optional: session duration in seconds (for session intents) */
    private Long sessionDurationSeconds;

    /** Optional: max amount for session intents */
    private String maxAmount;

    /** Extensible: additional method-specific fields */
    private Map<String, Object> extra;

    @JsonPOJOBuilder(withPrefix = "")
    public static class PaymentRequestBuilder {
    }
}
