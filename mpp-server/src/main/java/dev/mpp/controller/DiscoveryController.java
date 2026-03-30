package dev.mpp.controller;

import dev.mpp.config.MppProperties;
import dev.mpp.config.PaidResourceRegistry;
import dev.mpp.payment.method.PaymentMethodRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MPP Discovery endpoint.
 * Agents query this to learn what payment methods are accepted
 * and what resources are available for purchase.
 *
 * GET /.well-known/mpp → discovery document
 * GET /mpp/discovery/services → list of paid services
 */
@RestController
@RequiredArgsConstructor
public class DiscoveryController {

    private final MppProperties props;
    private final PaymentMethodRegistry methodRegistry;
    private final PaidResourceRegistry resourceRegistry;

    @GetMapping("/.well-known/mpp")
    public Mono<Map<String, Object>> wellKnown() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("protocol", "MPP");
        doc.put("version", "0.1.0");
        doc.put("realm", props.getRealm());
        doc.put("methods", methodRegistry.enabledMethods());
        doc.put("intents", List.of("charge", "session"));
        doc.put("services_url", "/mpp/discovery/services");
        return Mono.just(doc);
    }

    @GetMapping("/mpp/discovery/services")
    public Mono<Map<String, Object>> services() {
        Map<String, Object> result = new HashMap<>();
        result.put("realm", props.getRealm());

        var serviceList = resourceRegistry.getAll().entrySet().stream()
                .map(e -> {
                    Map<String, Object> svc = new HashMap<>();
                    svc.put("path", e.getKey());
                    svc.put("amount", e.getValue().getAmount());
                    svc.put("currency", e.getValue().getCurrency());
                    svc.put("method", e.getValue().getMethod());
                    svc.put("intent", e.getValue().getIntent());
                    svc.put("description", e.getValue().getDescription());
                    return svc;
                })
                .toList();

        result.put("services", serviceList);
        return Mono.just(result);
    }
}
