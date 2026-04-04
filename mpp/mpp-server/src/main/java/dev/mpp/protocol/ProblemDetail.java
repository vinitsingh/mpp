package dev.mpp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * RFC 9457 Problem Details per draft-httpauth-payment-00, Section 8.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetail {

    private String type;
    private String title;
    private int status;
    private String detail;
    private String challengeId;  // per spec Appendix B.1 example

    public static ProblemDetail paymentRequired(String detail, String challengeId) {
        return ProblemDetail.builder()
                .type(MppHeaders.PROBLEM_BASE + "payment-required")
                .title("Payment Required").status(402)
                .detail(detail).challengeId(challengeId).build();
    }

    public static ProblemDetail verificationFailed(String detail) {
        return ProblemDetail.builder()
                .type(MppHeaders.PROBLEM_BASE + "verification-failed")
                .title("Payment Verification Failed").status(402)
                .detail(detail).build();
    }

    public static ProblemDetail invalidChallenge(String detail) {
        return ProblemDetail.builder()
                .type(MppHeaders.PROBLEM_BASE + "invalid-challenge")
                .title("Invalid Challenge").status(402)
                .detail(detail).build();
    }

    public static ProblemDetail malformedCredential(String detail) {
        return ProblemDetail.builder()
                .type(MppHeaders.PROBLEM_BASE + "malformed-credential")
                .title("Malformed Credential").status(402)
                .detail(detail).build();
    }

    public static ProblemDetail paymentExpired(String detail) {
        return ProblemDetail.builder()
                .type(MppHeaders.PROBLEM_BASE + "payment-expired")
                .title("Payment Expired").status(402)
                .detail(detail).build();
    }

    public static ProblemDetail paymentInsufficient(String detail) {
        return ProblemDetail.builder()
                .type(MppHeaders.PROBLEM_BASE + "payment-insufficient")
                .title("Payment Insufficient").status(402)
                .detail(detail).build();
    }
}
