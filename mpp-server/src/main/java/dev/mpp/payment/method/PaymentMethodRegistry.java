package dev.mpp.payment.method;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of all available PaymentMethod implementations.
 * Spring auto-discovers all @Component classes implementing PaymentMethod.
 */
@Slf4j
@Component
public class PaymentMethodRegistry {

    private final Map<String, PaymentMethod> methods;

    public PaymentMethodRegistry(List<PaymentMethod> paymentMethods) {
        this.methods = paymentMethods.stream()
                .collect(Collectors.toMap(PaymentMethod::methodId, Function.identity()));
        log.info("Registered {} payment methods: {}", methods.size(), methods.keySet());
    }

    public Optional<PaymentMethod> get(String methodId) {
        return Optional.ofNullable(methods.get(methodId))
                .filter(PaymentMethod::isEnabled);
    }

    public List<String> enabledMethods() {
        return methods.values().stream()
                .filter(PaymentMethod::isEnabled)
                .map(PaymentMethod::methodId)
                .toList();
    }

    public boolean supports(String methodId) {
        return get(methodId).isPresent();
    }
}
