package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.service.WalletLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/solana/wallet-link")
@RequiredArgsConstructor
public class WalletLinkController {
    private final WalletLinkService service;

    public record ChallengeRequest(String walletAddress) { }
    public record VerifyRequest(UUID challengeId, String signatureBase64) { }

    @GetMapping
    public ResponseAPI<WalletLinkService.BoundWallet> current(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseAPI.<WalletLinkService.BoundWallet>builder().code(200)
                .data(service.current(principal.getId())).build();
    }

    @PostMapping("/challenge")
    public ResponseAPI<WalletLinkService.Challenge> challenge(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody ChallengeRequest request) {
        return ResponseAPI.<WalletLinkService.Challenge>builder().code(200)
                .data(service.challenge(principal.getId(), request.walletAddress())).build();
    }

    @PostMapping("/verify")
    public ResponseAPI<WalletLinkService.BoundWallet> verify(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody VerifyRequest request) {
        return ResponseAPI.<WalletLinkService.BoundWallet>builder().code(200)
                .data(service.verify(principal.getId(), request.challengeId(), request.signatureBase64())).build();
    }
}
