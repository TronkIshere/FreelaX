package com.marketplace.backend.controller;

import com.marketplace.backend.blockchain.PreparedAction;
import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.entity.AvalancheChainTransaction;
import com.marketplace.backend.service.AvalancheEscrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/avalanche")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class AvalancheEscrowController {
    private final AvalancheEscrowService service;

    public record RecordRequest(String txHash, AvalancheEscrowService.Input input) {
    }

    private static boolean admin(UserPrincipal principal) {
        return principal.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @GetMapping
    public ResponseAPI<AvalancheEscrowService.ContractView> status(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId) {
        return ResponseAPI.<AvalancheEscrowService.ContractView>builder().code(200)
                .data(service.status(principal.getId(), admin(principal), contractId)).build();
    }

    public record MutualRefundSignatureRequest(Long authorizationDeadline, String typedDataDigest, String signature) {
    }

    /** F13: each party submits its own wallet signature of the shared mutual-refund typed data. */
    @PostMapping("/mutual-refund/signatures")
    public ResponseAPI<AvalancheEscrowService.ContractView> mutualRefundSignature(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @RequestBody MutualRefundSignatureRequest request) {
        return ResponseAPI.<AvalancheEscrowService.ContractView>builder().code(200)
                .data(service.submitMutualRefundSignature(principal.getId(), contractId, request.authorizationDeadline(),
                        request.typedDataDigest(), request.signature())).build();
    }

    @PostMapping("/enable")
    public ResponseAPI<AvalancheEscrowService.ContractView> enable(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId) {
        return ResponseAPI.<AvalancheEscrowService.ContractView>builder().code(200).data(service.enable(principal.getId(), contractId)).build();
    }

    @PostMapping("/actions/{action}/prepare")
    public ResponseAPI<PreparedAction> prepare(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId,
                                               @PathVariable String action, @RequestBody(required = false) AvalancheEscrowService.Input input) {
        return ResponseAPI.<PreparedAction>builder().code(200)
                .data(service.prepare(principal.getId(), admin(principal), contractId, action, input)).build();
    }

    @PostMapping("/actions/{action}/execute")
    public ResponseAPI<AvalancheChainTransaction> execute(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId,
                                                          @PathVariable String action, @RequestBody(required = false) AvalancheEscrowService.Input input) {
        return ResponseAPI.<AvalancheChainTransaction>builder().code(200)
                .data(service.execute(principal.getId(), admin(principal), contractId, action, input)).build();
    }

    @PostMapping("/actions/{action}/transactions")
    public ResponseAPI<AvalancheChainTransaction> record(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId,
                                                         @PathVariable String action, @RequestBody RecordRequest request) {
        return ResponseAPI.<AvalancheChainTransaction>builder().code(200).data(service.recordUserTransaction(principal.getId(), admin(principal),
                contractId, action, request.txHash(), request.input())).build();
    }

    @PostMapping("/refresh")
    public ResponseAPI<AvalancheEscrowService.ContractView> refresh(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId) {
        return ResponseAPI.<AvalancheEscrowService.ContractView>builder().code(200)
                .data(service.refresh(principal.getId(), admin(principal), contractId)).build();
    }
}
