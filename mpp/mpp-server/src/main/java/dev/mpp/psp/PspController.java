package dev.mpp.psp;

import dev.mpp.protocol.ProblemDetail;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PSP Controller — spec-compliant token issuance.
 *
 * POST /tokens:
 *   Agent sends agent_id, wallet_id, AND the MPP challenge headers
 *   (id, realm, method, intent, request containing amount/currency/recipient).
 *   PSP validates agent/wallet, stores challenge context, returns token.
 *
 * POST /tokens/session:
 *   Agent sends agent_id, wallet_id, hold amount. PSP holds funds.
 *
 * POST /payments/authorize:
 *   Merchant sends token + amount + currency. PSP authorizes + settles.
 */
@RestController
@RequiredArgsConstructor
public class PspController {

    private final PspService pspService;

    // ── Case 2: Single-Use Token ───────────────────────────────

    @PostMapping("/tokens")
    public ResponseEntity<?> issueSingleUseToken(@RequestBody SingleUseTokenRequest req) {
        try {
            SingleUseToken.ChallengeContext ctx = SingleUseToken.ChallengeContext.builder()
                    .challengeId(req.getChallenge().getId())
                    .realm(req.getChallenge().getRealm())
                    .method(req.getChallenge().getMethod())
                    .intent(req.getChallenge().getIntent())
                    .request(req.getChallenge().getRequest())
                    .expires(req.getChallenge().getExpires())
                    .description(req.getChallenge().getDescription())
                    .build();

            SingleUseToken token = pspService.issueSingleUseToken(
                    req.getAgentId(), req.getWalletId(), ctx);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("token_id", token.getTokenId());
            body.put("type", "single_use");
            body.put("agent_id", token.getAgentId());
            body.put("wallet_id", token.getWalletId());
            body.put("card", Map.of("brand", token.getCardBrand(), "last4", token.getCardLast4()));
            body.put("challenge", Map.of(
                    "id", ctx.getChallengeId(),
                    "realm", ctx.getRealm(),
                    "method", ctx.getMethod(),
                    "intent", ctx.getIntent(),
                    "request", ctx.getRequest()
            ));
            body.put("expires_at", token.getExpiresAt().toString());

            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (PspService.PspException ex) {
            return errorResponse(ex);
        }
    }

    // ── Case 3: Multi-Use Token ────────────────────────────────

    @PostMapping("/tokens/session")
    public ResponseEntity<?> issueMultiUseToken(@RequestBody SessionTokenRequest req) {
        try {
            MultiUseToken token = pspService.issueMultiUseToken(
                    req.getAgentId(), req.getWalletId(),
                    req.getHoldAmount(), req.getCurrency());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("token_id", token.getTokenId());
            body.put("type", "multi_use");
            body.put("agent_id", token.getAgentId());
            body.put("wallet_id", token.getWalletId());
            body.put("card", Map.of("brand", token.getCardBrand(), "last4", token.getCardLast4()));
            body.put("hold_amount", token.getHoldAmount());
            body.put("used_amount", token.getUsedAmount());
            body.put("remaining", token.remaining().toString());
            body.put("currency", token.getCurrency());
            body.put("expires_at", token.getExpiresAt().toString());

            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (PspService.PspException ex) {
            return errorResponse(ex);
        }
    }

    // ── Token status ───────────────────────────────────────────

    @GetMapping("/tokens/{tokenId}")
    public ResponseEntity<?> getToken(@PathVariable String tokenId) {
        if (tokenId.startsWith("tok_")) {
            return pspService.getSingleUseToken(tokenId)
                    .map(t -> ResponseEntity.ok((Object) t))
                    .orElse(ResponseEntity.notFound().build());
        }
        if (tokenId.startsWith("mtok_")) {
            return pspService.getMultiUseToken(tokenId)
                    .map(t -> {
                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("token_id", t.getTokenId());
                        body.put("type", "multi_use");
                        body.put("hold_amount", t.getHoldAmount());
                        body.put("used_amount", t.getUsedAmount());
                        body.put("remaining", t.remaining().toString());
                        body.put("currency", t.getCurrency());
                        body.put("exhausted", t.isExhausted());
                        body.put("usage_count", t.getUsageLog().size());
                        body.put("expires_at", t.getExpiresAt().toString());
                        return ResponseEntity.ok((Object) body);
                    })
                    .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.notFound().build();
    }

    // ── Merchant-facing: Payment authorization ─────────────────

    @PostMapping("/payments/authorize")
    public ResponseEntity<?> authorizePayment(@RequestBody AuthorizeRequest req) {
        try {
            PspService.PaymentResult result;
            if (req.getToken().startsWith("tok_")) {
                result = pspService.authorizeSingleUse(req.getToken(), req.getAmount(), req.getCurrency());
            } else if (req.getToken().startsWith("mtok_")) {
                result = pspService.authorizeMultiUse(req.getToken(), req.getAmount(), req.getCurrency(), req.getResourcePath());
            } else {
                return ResponseEntity.badRequest().body(ProblemDetail.malformedCredential("Invalid token format"));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", result.status());
            body.put("reference", result.reference());
            body.put("amount", result.amount());
            body.put("currency", result.currency());
            body.put("agent_id", result.agentId());
            return ResponseEntity.ok(body);
        } catch (PspService.PspException ex) {
            return errorResponse(ex);
        }
    }

    // ── DTOs ───────────────────────────────────────────────────

    @Data
    public static class SingleUseTokenRequest {
        private String agentId;
        private String walletId;
        private ChallengeInput challenge;

        @Data
        public static class ChallengeInput {
            private String id;
            private String realm;
            private String method;
            private String intent;
            private String request;      // base64url with amount/currency/recipient
            private String expires;
            private String description;
        }
    }

    @Data
    public static class SessionTokenRequest {
        private String agentId;
        private String walletId;
        private String holdAmount;
        private String currency;
    }

    @Data
    public static class AuthorizeRequest {
        private String token;
        private String amount;
        private String currency;
        private String resourcePath;
    }

    private ResponseEntity<?> errorResponse(PspService.PspException ex) {
        ProblemDetail p = switch (ex.getErrorType()) {
            case "verification-failed" -> ProblemDetail.verificationFailed(ex.getMessage());
            case "invalid-challenge" -> ProblemDetail.invalidChallenge(ex.getMessage());
            case "payment-expired" -> ProblemDetail.paymentExpired(ex.getMessage());
            case "payment-insufficient" -> ProblemDetail.paymentInsufficient(ex.getMessage());
            default -> ProblemDetail.verificationFailed(ex.getMessage());
        };
        return ResponseEntity.status(p.getStatus()).body(p);
    }
}
