package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.service.SolanaEscrowFundingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/milestones/{milestoneId}/escrow")
@RequiredArgsConstructor
public class SolanaEscrowFundingController {
    private final SolanaEscrowFundingService service;

    public record SignedTransaction(String buildSessionId, String transactionBase64) { }
    public record WalletSelection(String walletAddress) { }

    @PostMapping("/fund/build")
    public ResponseAPI<SolanaEscrowFundingService.EscrowBuildView> prepare(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @PathVariable UUID milestoneId,
            @RequestBody WalletSelection selection) {
        return ResponseAPI.<SolanaEscrowFundingService.EscrowBuildView>builder().code(200)
                .data(service.prepare(principal.getId(), contractId, milestoneId,
                        selection.walletAddress())).build();
    }

    @PostMapping("/fund/submit")
    public ResponseAPI<SolanaEscrowFundingService.EscrowView> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @PathVariable UUID milestoneId,
            @RequestBody SignedTransaction request) {
        return ResponseAPI.<SolanaEscrowFundingService.EscrowView>builder().code(200)
                .data(service.submitFunding(principal.getId(), contractId, milestoneId,
                        request.buildSessionId(), request.transactionBase64())).build();
    }

    @GetMapping
    public ResponseAPI<SolanaEscrowFundingService.EscrowView> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @PathVariable UUID milestoneId) {
        return ResponseAPI.<SolanaEscrowFundingService.EscrowView>builder().code(200)
                .data(service.get(principal.getId(), contractId, milestoneId)).build();
    }
}
