package dev.mpp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * RFC 9457 Problem Details for MPP error responses.
 * Returned in 402 responses when payment fails.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetail {

    public static final String BASE_URI = "https://paymentauth.org/problems/";

    private String type;
    private String title;
    private int status;
    private String detail;

    public static ProblemDetail paymentRequired(String detail) {
        return ProblemDetail.builder()
                .type(BASE_URI + "payment-required")
                .title("Payment Required")
                .status(402)
                .detail(detail != null ? detail : "Payment is required.")
                .build();
    }

    public static ProblemDetail verificationFailed(String detail) {
        return ProblemDetail.builder()
                .type(BASE_URI + "verification-failed")
                .title("Payment Verification Failed")
                .status(402)
                .detail(detail != null ? detail : "Invalid payment proof.")
                .build();
    }

    public static ProblemDetail invalidChallenge(String detail) {
        return ProblemDetail.builder()
                .type(BASE_URI + "invalid-challenge")
                .title("Invalid Challenge")
                .status(402)
                .detail(detail != null ? detail : "Challenge ID unknown, expired, or already used.")
                .build();
    }

    public static ProblemDetail malformedCredential(String detail) {
        return ProblemDetail.builder()
                .type(BASE_URI + "malformed-credential")
                .title("Malformed Credential")
                .status(402)
                .detail(detail != null ? detail : "Invalid credential format.")
                .build();
    }

    public static ProblemDetail paymentInsufficient(String detail) {
        return ProblemDetail.builder()
                .type(BASE_URI + "payment-insufficient")
                .title("Payment Insufficient")
                .status(402)
                .detail(detail != null ? detail : "Amount too low.")
                .build();
    }

    public static ProblemDetail methodUnsupported(String detail) {
        return ProblemDetail.builder()
                .type(BASE_URI + "method-unsupported")
                .title("Method Unsupported")
                .status(402)
                .detail(detail != null ? detail : "Payment method not accepted.")
                .build();
    }

    public static ProblemDetail paymentExpired(String detail) {
        return ProblemDetail.builder()
                .type(BASE_URI + "payment-expired")
                .title("Payment Expired")
                .status(402)
                .detail(detail != null ? detail : "Challenge or authorization expired.")
                .build();
    }
}
