package dev.mpp.controller;

import dev.mpp.config.MppProperties;
import dev.mpp.protocol.payment.TokenRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static dev.mpp.util.MppCryptoUtil.generateChallengeId;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TokensController {

    private final MppProperties props;

    @PostMapping("/tokens")
    public Mono<String> generate(@RequestBody TokenRequest body) {
        return Mono.just(generateChallengeId(props.getChallenge().getHmacSecret(), body.getRealm(), body.getMethod(), body.getIntent(), body.getRequestB64(), body.getExpires()));
    }
}
