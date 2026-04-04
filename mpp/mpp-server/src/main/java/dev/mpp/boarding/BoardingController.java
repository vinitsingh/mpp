package dev.mpp.boarding;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Case 1: Agent Boarding & Wallet Setup
 *
 * Step 1: POST /boarding/agents          → register agent, returns agent_id
 * Step 2: POST /boarding/agents/{id}/wallet → create wallet, returns wallet_id
 * Step 3: POST /boarding/wallets/{id}/payment-methods → add card to wallet
 *
 * After this, agent is ready for Case 2 (pay-as-go) or Case 3 (pre-pay).
 */
@RestController
@RequestMapping("/boarding")
@RequiredArgsConstructor
public class BoardingController {

    private final BoardingService boardingService;

    // ── Step 1: Register agent ─────────────────────────────────

    @PostMapping("/agents")
    public ResponseEntity<Map<String, Object>> registerAgent(@RequestBody AgentRequest req) {
        Agent agent = boardingService.registerAgent(req.getName(), req.getOwner());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", agent.getAgentId());
        body.put("name", agent.getName());
        body.put("owner", agent.getOwner());
        body.put("status", "active");
        body.put("created_at", agent.getCreatedAt().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/agents")
    public ResponseEntity<?> listAgents() {
        var agents = boardingService.getAllAgents();
        return ResponseEntity.ok(agents);
    }

    @GetMapping("/agents/{agentId}")
    public ResponseEntity<?> getAgent(@PathVariable String agentId) {
        return boardingService.getAgent(agentId)
                .map(agent -> ResponseEntity.ok((Object) agent))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/agents/{agentId}/wallet")
    public ResponseEntity<?> getAgentWallet(@PathVariable String agentId) {
        return boardingService.getWalletByAgent(agentId)
                .map(w -> ResponseEntity.ok((Object) w))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Step 2: Create wallet for agent ────────────────────────

    @PostMapping("/agents/{agentId}/wallet")
    public ResponseEntity<Map<String, Object>> createWallet(@PathVariable String agentId) {
        try {
            Wallet wallet = boardingService.createWallet(agentId);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("wallet_id", wallet.getWalletId());
            body.put("agent_id", wallet.getAgentId());
            body.put("payment_methods", wallet.getPaymentMethods().size());
            body.put("status", "active");
            body.put("created_at", wallet.getCreatedAt().toString());

            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Step 3: Add payment method to wallet ───────────────────

    @PostMapping("/wallets/{walletId}/payment-methods")
    public ResponseEntity<Map<String, Object>> addPaymentMethod(
            @PathVariable String walletId,
            @RequestBody PaymentMethodRequest req) {
        try {
            Wallet.PaymentMethod pm = boardingService.addPaymentMethod(
                    walletId, req.getType(), req.getBrand(),
                    req.getLast4(), req.getExpiry(), req.getSpendingLimit());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("payment_method_id", pm.getPaymentMethodId());
            body.put("type", pm.getType());
            body.put("brand", pm.getBrand());
            body.put("last4", pm.getLast4());
            body.put("spending_limit", pm.getSpendingLimit());
            body.put("is_default", pm.isDefault());

            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/wallets/{walletId}")
    public ResponseEntity<?> getWallet(@PathVariable String walletId) {
        return boardingService.getWallet(walletId)
                .map(w -> ResponseEntity.ok((Object) w))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DTOs ───────────────────────────────────────────────────

    @Data
    public static class AgentRequest {
        private String name;
        private String owner;
    }

    @Data
    public static class PaymentMethodRequest {
        private String type;
        private String brand;
        private String last4;
        private String expiry;
        private String spendingLimit;
    }
}
