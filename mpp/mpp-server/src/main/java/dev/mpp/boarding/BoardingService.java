package dev.mpp.boarding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BoardingService {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Map<String, Wallet> wallets = new ConcurrentHashMap<>();
    private final Map<String, String> agentToWallet = new ConcurrentHashMap<>();

    // ── Agent ──────────────────────────────────────────────────

    public Agent registerAgent(String name, String owner) {
        String agentId = "agent_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);

        Agent agent = Agent.builder()
                .agentId(agentId)
                .name(name)
                .owner(owner)
                .createdAt(Instant.now())
                .active(true)
                .build();

        agents.put(agentId, agent);
        log.info("Boarded agent: {} ({})", agentId, name);
        return agent;
    }

    public Optional<Agent> getAgent(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    public List<Agent> getAllAgents() {
        return new ArrayList<>(agents.values());
    }

    // ── Wallet ─────────────────────────────────────────────────

    public Wallet createWallet(String agentId) {
        if (!agents.containsKey(agentId)) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }

        String walletId = "wallet_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);

        Wallet wallet = Wallet.builder()
                .walletId(walletId)
                .agentId(agentId)
                .paymentMethods(new ArrayList<>())
                .createdAt(Instant.now())
                .active(true)
                .build();

        wallets.put(walletId, wallet);
        agentToWallet.put(agentId, walletId);
        log.info("Created wallet {} for agent {}", walletId, agentId);
        return wallet;
    }

    public Optional<Wallet> getWallet(String walletId) {
        return Optional.ofNullable(wallets.get(walletId));
    }

    public Optional<Wallet> getWalletByAgent(String agentId) {
        String walletId = agentToWallet.get(agentId);
        return walletId != null ? Optional.ofNullable(wallets.get(walletId)) : Optional.empty();
    }

    // ── Payment Method ─────────────────────────────────────────

    public Wallet.PaymentMethod addPaymentMethod(String walletId, String type,
                                                   String brand, String last4,
                                                   String expiry, String spendingLimit) {
        Wallet wallet = wallets.get(walletId);
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet not found: " + walletId);
        }

        String pmId = "pm_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);

        boolean isFirst = wallet.getPaymentMethods().isEmpty();

        Wallet.PaymentMethod pm = Wallet.PaymentMethod.builder()
                .paymentMethodId(pmId)
                .type(type)
                .brand(brand)
                .last4(last4)
                .expiry(expiry)
                .spendingLimit(spendingLimit)
                .isDefault(isFirst)
                .build();

        wallet.getPaymentMethods().add(pm);
        log.info("Added payment method {} ({} ...{}) to wallet {}",
                pmId, brand, last4, walletId);
        return pm;
    }

    /**
     * Validate agent + wallet ownership and return the default payment method.
     */
    public ValidationResult validate(String agentId, String walletId) {
        Agent agent = agents.get(agentId);
        if (agent == null) {
            return ValidationResult.fail("Unknown agent: " + agentId);
        }
        if (!agent.isActive()) {
            return ValidationResult.fail("Agent deactivated: " + agentId);
        }

        Wallet wallet = wallets.get(walletId);
        if (wallet == null) {
            return ValidationResult.fail("Unknown wallet: " + walletId);
        }
        if (!wallet.isActive()) {
            return ValidationResult.fail("Wallet deactivated: " + walletId);
        }
        if (!wallet.getAgentId().equals(agentId)) {
            return ValidationResult.fail("Wallet " + walletId + " does not belong to agent " + agentId);
        }

        Wallet.PaymentMethod pm = wallet.getPaymentMethods().stream()
                .filter(Wallet.PaymentMethod::isDefault)
                .findFirst()
                .or(() -> wallet.getPaymentMethods().stream().findFirst())
                .orElse(null);

        if (pm == null) {
            return ValidationResult.fail("No payment method on file for wallet " + walletId);
        }

        return ValidationResult.ok(agent, wallet, pm);
    }

    public record ValidationResult(
            boolean valid, String error,
            Agent agent, Wallet wallet, Wallet.PaymentMethod paymentMethod
    ) {
        public static ValidationResult ok(Agent a, Wallet w, Wallet.PaymentMethod pm) {
            return new ValidationResult(true, null, a, w, pm);
        }
        public static ValidationResult fail(String error) {
            return new ValidationResult(false, error, null, null, null);
        }
    }
}
