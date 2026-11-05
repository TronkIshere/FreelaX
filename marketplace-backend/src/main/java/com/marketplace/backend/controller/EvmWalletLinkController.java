package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.avalanche.WalletChallengeRequest;
import com.marketplace.backend.dto.request.avalanche.WalletVerifyRequest;
import com.marketplace.backend.dto.response.avalanche.EvmBoundWalletResponse;
import com.marketplace.backend.dto.response.avalanche.EvmWalletChallengeResponse;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.service.EvmWalletLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** EVM wallet linking for the Avalanche rail (register A8). Authenticated (not on the security white list). */
@RestController
@RequestMapping("/api/v1/avalanche/wallet-link")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class EvmWalletLinkController {
    private final EvmWalletLinkService service;

    @GetMapping
    public ResponseAPI<EvmBoundWalletResponse> current(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseAPI.<EvmBoundWalletResponse>builder().code(200).data(service.current(principal.getId())).build();
    }

    @PostMapping("/challenge")
    public ResponseAPI<EvmWalletChallengeResponse> challenge(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @RequestBody WalletChallengeRequest request) {
        return ResponseAPI.<EvmWalletChallengeResponse>builder().code(200)
                .data(service.challenge(principal.getId(), request.walletAddress())).build();
    }

    @PostMapping("/verify")
    public ResponseAPI<EvmBoundWalletResponse> verify(@AuthenticationPrincipal UserPrincipal principal,
                                                                @RequestBody WalletVerifyRequest request) {
        return ResponseAPI.<EvmBoundWalletResponse>builder().code(200)
                .data(service.verify(principal.getId(), request.challengeId(), request.signature())).build();
    }
}
