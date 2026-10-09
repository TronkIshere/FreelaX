package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.service.SolanaEscrowWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/escrow/actions")
@RequiredArgsConstructor
public class SolanaEscrowWorkflowController {
    private final SolanaEscrowWorkflowService service;

    @PostMapping("/{action}/build")
    public ResponseAPI<SolanaEscrowWorkflowService.BuildView> build(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId,
            @PathVariable String action, @RequestBody SolanaEscrowWorkflowService.ActionInput input) {
        return ResponseAPI.<SolanaEscrowWorkflowService.BuildView>builder().code(200)
                .data(service.build(principal.getId(), contractId, action, input)).build();
    }

    @PostMapping("/{intentId}/submit")
    public ResponseAPI<SolanaEscrowWorkflowService.ActionStatus> submit(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId,
            @PathVariable UUID intentId, @RequestBody SolanaEscrowWorkflowService.SignedInput input) {
        return ResponseAPI.<SolanaEscrowWorkflowService.ActionStatus>builder().code(200)
                .data(service.submit(principal.getId(), contractId, intentId, input)).build();
    }

    @PostMapping("/mutual-refund/build")
    public ResponseAPI<SolanaEscrowWorkflowService.MutualRefundView> prepareMutualRefund(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId) {
        return ResponseAPI.<SolanaEscrowWorkflowService.MutualRefundView>builder().code(200)
                .data(service.prepareMutualRefund(principal.getId(), contractId)).build();
    }

    @PostMapping("/mutual-refund/{intentId}/client-sign")
    public ResponseAPI<SolanaEscrowWorkflowService.MutualRefundView> clientSignMutualRefund(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId,
            @PathVariable UUID intentId, @RequestBody SolanaEscrowWorkflowService.SignedInput input) {
        return ResponseAPI.<SolanaEscrowWorkflowService.MutualRefundView>builder().code(200)
                .data(service.clientSignMutualRefund(principal.getId(), contractId, intentId, input)).build();
    }

    @GetMapping("/mutual-refund/pending")
    public ResponseAPI<SolanaEscrowWorkflowService.MutualRefundView> pendingMutualRefund(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId) {
        return ResponseAPI.<SolanaEscrowWorkflowService.MutualRefundView>builder().code(200)
                .data(service.pendingMutualRefund(principal.getId(), contractId)).build();
    }

    @PostMapping("/mutual-refund/{intentId}/finish")
    public ResponseAPI<SolanaEscrowWorkflowService.ActionStatus> finishMutualRefund(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId,
            @PathVariable UUID intentId, @RequestBody SolanaEscrowWorkflowService.SignedInput input) {
        return ResponseAPI.<SolanaEscrowWorkflowService.ActionStatus>builder().code(200)
                .data(service.finishMutualRefund(principal.getId(), contractId, intentId, input)).build();
    }
}
