package dev.mpp.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mpp.config.MppProperties;
import dev.mpp.config.PaidResourceRegistry;
import dev.mpp.model.ProblemDetail;
import dev.mpp.protocol.challenge.Challenge;
import dev.mpp.protocol.challenge.PaymentRequest;
import dev.mpp.service.ChallengeService;
import dev.mpp.service.CredentialService;
import dev.mpp.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Core MPP WebFilter implementing the HTTP 402 Payment Required flow.
 *
 * For every incoming request:
 * 1. Check if the resource is in the PaidResourceRegistry
 * 2. If not paid → pass through (free resource)
 * 3. If paid + no Authorization header → return 402 with Challenge
 * 4. If paid + Authorization: Payment → verify credential → settle → attach receipt
 *
 * Per MPP spec:
 * - 402 responses include WWW-Authenticate: Payment header + Cache-Control: no-store
 * - 200 responses include Payment-Receipt header + Cache-Control: private
 * - Failed credentials get 402 + fresh challenge + Problem Details body
 */
@Slf4j
@Component
@Order(-1) // Run before other filters
@RequiredArgsConstructor
public class MppPaymentFilter implements WebFilter {

    private static final String AUTH_SCHEME = "Payment ";
    private static final String HEADER_PAYMENT_RECEIPT = "Payment-Receipt";

    private final PaidResourceRegistry resourceRegistry;
    private final ChallengeService challengeService;
    private final CredentialService credentialService;
    private final ReceiptService receiptService;
    private final MppProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip non-paid resources and admin/discovery endpoints
        if (path.startsWith("/mpp/") || path.startsWith("/admin/")) {
            return chain.filter(exchange);
        }

        var pricingOpt = resourceRegistry.findPricing(path);
        if (pricingOpt.isEmpty()) {
            return chain.filter(exchange); // Free resource
        }

        PaidResourceRegistry.ResourcePricing pricing = pricingOpt.get();
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // No credential → issue fresh challenge
        if (authHeader == null || !authHeader.startsWith(AUTH_SCHEME)) {
            return respond402WithChallenge(exchange, pricing, null);
        }

        // Has credential → verify and settle
        try {
            var credential = credentialService.parseCredential(authHeader);

            return credentialService.verifyAndSettle(credential)
                    .flatMap(receipt -> {
                        // Attach receipt header and continue to the actual endpoint
                        String encodedReceipt = receiptService.encodeReceipt(receipt);
                        exchange.getResponse().getHeaders()
                                .add(HEADER_PAYMENT_RECEIPT, encodedReceipt);
                        exchange.getResponse().getHeaders()
                                .setCacheControl("private");

                        // Store receipt in exchange attributes for downstream access
                        exchange.getAttributes().put("mpp.receipt", receipt);
                        exchange.getAttributes().put("mpp.paid", true);

                        return chain.filter(exchange);
                    })
                    .onErrorResume(CredentialService.MppProtocolException.class, ex -> {
                        log.warn("Credential verification failed: type={}, msg={}",
                                ex.getErrorType(), ex.getMessage());
                        ProblemDetail problem = toProblemDetail(ex);
                        return respond402WithChallenge(exchange, pricing, problem);
                    });

        } catch (CredentialService.MppProtocolException ex) {
            log.warn("Credential parse failed: {}", ex.getMessage());
            ProblemDetail problem = toProblemDetail(ex);
            return respond402WithChallenge(exchange, pricing, problem);
        }
    }

    // ── 402 Response Builder ───────────────────────────────────

    private Mono<Void> respond402WithChallenge(ServerWebExchange exchange,
                                                PaidResourceRegistry.ResourcePricing pricing,
                                                ProblemDetail problem) {
        PaymentRequest request = PaymentRequest.builder()
                .amount(pricing.getAmount())
                .currency(pricing.getCurrency())
                .recipient(pricing.getRecipient())
                .build();

        Challenge challenge = challengeService.issueChallenge(
                pricing.getMethod(),
                pricing.getIntent(),
                request,
                pricing.getDescription()
        );

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(402));
        response.getHeaders().add("WWW-Authenticate", challenge.toWwwAuthenticate());
        response.getHeaders().setCacheControl("no-store");
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Build response body
        ProblemDetail body = problem != null ? problem
                : ProblemDetail.paymentRequired(pricing.getDescription());

        try {
            String json = objectMapper.writeValueAsString(body);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    private ProblemDetail toProblemDetail(CredentialService.MppProtocolException ex) {
        return switch (ex.getErrorType()) {
            case "verification-failed" -> ProblemDetail.verificationFailed(ex.getMessage());
            case "invalid-challenge" -> ProblemDetail.invalidChallenge(ex.getMessage());
            case "malformed-credential" -> ProblemDetail.malformedCredential(ex.getMessage());
            case "payment-expired" -> ProblemDetail.paymentExpired(ex.getMessage());
            case "payment-insufficient" -> ProblemDetail.paymentInsufficient(ex.getMessage());
            case "method-unsupported" -> ProblemDetail.methodUnsupported(ex.getMessage());
            default -> ProblemDetail.paymentRequired(ex.getMessage());
        };
    }
}
