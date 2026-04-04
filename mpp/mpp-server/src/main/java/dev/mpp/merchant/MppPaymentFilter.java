package dev.mpp.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mpp.protocol.*;
import dev.mpp.psp.PspService;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Merchant-side MPP filter — fully spec-compliant per draft-httpauth-payment-00.
 *
 * 402 response:
 *   WWW-Authenticate: Payment id="...", realm="...", method="...",
 *       intent="...", request="<b64url-json>", expires="...", description="..."
 *   Body: RFC 9457 Problem Details
 *   Cache-Control: no-store
 *
 * Credential (Authorization: Payment <base64url-json>):
 *   Decoded JSON: { challenge: { id, realm, method, intent, request, ... },
 *                   source: "did:...", payload: { token_id: "tok_..." } }
 *
 * The payload.token_id is the PSP token. Merchant extracts it
 * and calls PSP /payments/authorize to settle.
 *
 * Receipt: Payment-Receipt: <base64url-json>
 *   { status: "success", method: "...", timestamp: "...", reference: "..." }
 */
@Slf4j
@Component
@Order(-1)
@RequiredArgsConstructor
public class MppPaymentFilter implements WebFilter {

    private static final String HMAC_SECRET = "merchant-hmac-secret-change-in-prod";

    private final ResourceRegistry resourceRegistry;
    private final PspService pspService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/boarding/") || path.startsWith("/tokens")
                || path.startsWith("/payments/") || path.startsWith("/admin/")
                || path.equals("/.well-known/mpp")) {
            return chain.filter(exchange);
        }

        var pricingOpt = resourceRegistry.findPricing(path);
        if (pricingOpt.isEmpty()) return chain.filter(exchange);

        ResourceRegistry.ResourcePricing pricing = pricingOpt.get();
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // No credential → 402 + challenge
        if (authHeader == null || !authHeader.startsWith(MppHeaders.PAYMENT_SCHEME)) {
            return respond402(exchange, pricing, null);
        }

        // Parse spec-compliant credential: Authorization: Payment <base64url-json>
        String b64 = authHeader.substring(MppHeaders.PAYMENT_SCHEME.length()).trim();
        MppCredential credential;
        try {
            String json = new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8);
            credential = objectMapper.readValue(json, MppCredential.class);
        } catch (Exception e) {
            log.warn("Malformed credential: {}", e.getMessage());
            return respond402(exchange, pricing, ProblemDetail.malformedCredential(e.getMessage()));
        }

        // Extract PSP token from payload
        Map<String, Object> payload = credential.getPayload();
        if (payload == null || !payload.containsKey("token_id")) {
            return respond402(exchange, pricing,
                    ProblemDetail.malformedCredential("Missing payload.token_id"));
        }

        String tokenId = (String) payload.get("token_id");

        // Call PSP to authorize + settle
        try {
            PspService.PaymentResult result;

            if (tokenId.startsWith("tok_")) {
                result = pspService.authorizeSingleUse(tokenId, pricing.getAmount(), pricing.getCurrency());
            } else if (tokenId.startsWith("mtok_")) {
                result = pspService.authorizeMultiUse(tokenId, pricing.getAmount(), pricing.getCurrency(), path);
            } else {
                return respond402(exchange, pricing,
                        ProblemDetail.malformedCredential("Invalid token format: " + tokenId));
            }

            // Build spec-compliant receipt (Section 5.3)
            MppReceipt receipt = MppReceipt.builder()
                    .status("success")
                    .method(credential.getChallenge() != null ? credential.getChallenge().getMethod() : "card")
                    .timestamp(Instant.now().toString())
                    .reference(result.reference())
                    .build();

            String receiptJson = objectMapper.writeValueAsString(receipt);
            String receiptB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(receiptJson.getBytes(StandardCharsets.UTF_8));

            exchange.getResponse().getHeaders().add(MppHeaders.PAYMENT_RECEIPT, receiptB64);
            exchange.getResponse().getHeaders().setCacheControl("private");
            exchange.getAttributes().put("mpp.paid", true);
            exchange.getAttributes().put("mpp.result", result);

            return chain.filter(exchange);

        } catch (PspService.PspException ex) {
            log.warn("PSP authorization failed: {}", ex.getMessage());
            ProblemDetail problem = switch (ex.getErrorType()) {
                case "invalid-challenge" -> ProblemDetail.invalidChallenge(ex.getMessage());
                case "payment-expired" -> ProblemDetail.paymentExpired(ex.getMessage());
                case "payment-insufficient" -> ProblemDetail.paymentInsufficient(ex.getMessage());
                default -> ProblemDetail.verificationFailed(ex.getMessage());
            };
            return respond402(exchange, pricing, problem);
        } catch (Exception ex) {
            log.error("Payment error", ex);
            return respond402(exchange, pricing, ProblemDetail.verificationFailed(ex.getMessage()));
        }
    }

    // ── Spec-compliant 402 response ────────────────────────────

    private Mono<Void> respond402(ServerWebExchange exchange,
                                   ResourceRegistry.ResourcePricing pricing,
                                   ProblemDetail problem) {
        // Build request JSON with amount/currency/recipient (goes INSIDE request param)
        String requestJson = "{\"amount\":\"" + pricing.getAmount()
                + "\",\"currency\":\"" + pricing.getCurrency()
                + "\",\"recipient\":\"merchant_acct_001\"}";
        String requestB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(requestJson.getBytes(StandardCharsets.UTF_8));

        String expires = Instant.now().plusSeconds(300).toString();
        String challengeId = computeHmacId("merchant.example.com", "card", "charge",
                requestB64, expires, "", "");

        MppChallenge challenge = MppChallenge.builder()
                .id(challengeId)
                .realm("merchant.example.com")
                .method("card")
                .intent("charge")
                .request(requestB64)
                .expires(expires)
                .description(pricing.getDescription())
                .build();

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(402));
        response.getHeaders().add(MppHeaders.WWW_AUTHENTICATE, challenge.toWwwAuthenticate());
        response.getHeaders().setCacheControl("no-store");
        response.getHeaders().setContentType(MediaType.valueOf("application/problem+json"));

        ProblemDetail body = problem != null ? problem
                : ProblemDetail.paymentRequired(pricing.getDescription(), challengeId);

        return writeJson(response, body);
    }

    /**
     * HMAC-SHA256 challenge binding per Section 5.1.2.1.1.
     * 7 fixed positional slots: realm|method|intent|request|expires|digest|opaque
     */
    private String computeHmacId(String realm, String method, String intent,
                                  String request, String expires,
                                  String digest, String opaque) {
        String input = String.join("|", realm, method, intent, request,
                expires != null ? expires : "",
                digest != null ? digest : "",
                opaque != null ? opaque : "");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC failed", e);
        }
    }

    private Mono<Void> writeJson(ServerHttpResponse response, Object body) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return response.setComplete();
        }
    }
}
