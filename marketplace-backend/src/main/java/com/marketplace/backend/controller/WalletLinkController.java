package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.service.WalletLinkService;
import com.marketplace.backend.service.LocalAutoWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/solana/wallet-link")
@RequiredArgsConstructor
public class WalletLinkController {
    private final WalletLinkService service;
    private final LocalAutoWalletService autoWallets;

    public record ChallengeRequest(String walletAddress) { }
    public record VerifyRequest(UUID challengeId, String signatureBase64) { }
    public record SignRequest(String transactionBase64) { }

    @PostMapping("/auto")
    public ResponseAPI<LocalAutoWalletService.ConnectedWallet> auto(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseAPI.<LocalAutoWalletService.ConnectedWallet>builder().code(200)
                .data(autoWallets.connect(principal.getId())).build();
    }

    @PostMapping("/auto/sign")
    public ResponseAPI<LocalAutoWalletService.SignedTransaction> sign(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody SignRequest request) {
        return ResponseAPI.<LocalAutoWalletService.SignedTransaction>builder().code(200)
                .data(autoWallets.sign(principal.getId(), request.transactionBase64())).build();
    }

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
