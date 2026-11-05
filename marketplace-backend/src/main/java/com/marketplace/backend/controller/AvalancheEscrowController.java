package com.marketplace.backend.controller;

import com.marketplace.backend.blockchain.PreparedAction;
import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.avalanche.AvalancheActionInput;
import com.marketplace.backend.dto.request.avalanche.MutualRefundSignatureRequest;
import com.marketplace.backend.dto.request.avalanche.MutualSettlementSignatureRequest;
import com.marketplace.backend.dto.request.avalanche.RecordTransactionRequest;
import com.marketplace.backend.dto.response.avalanche.AvalancheContractView;
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

    private static boolean admin(UserPrincipal principal) {
        return principal.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @GetMapping
    public ResponseAPI<AvalancheContractView> status(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId) {
        return ResponseAPI.<AvalancheContractView>builder().code(200)
                .data(service.status(principal.getId(), admin(principal), contractId)).build();
    }

    /** F13: each party submits its own wallet signature of the shared mutual-refund typed data. */
    @PostMapping("/mutual-refund/signatures")
    public ResponseAPI<AvalancheContractView> mutualRefundSignature(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @RequestBody MutualRefundSignatureRequest request) {
        return ResponseAPI.<AvalancheContractView>builder().code(200)
                .data(service.submitMutualRefundSignature(principal.getId(), contractId, request.authorizationDeadline(),
                        request.typedDataDigest(), request.signature())).build();
    }

    /** R12: each party submits its own wallet signature of the shared mutual-settlement typed data for a Disputed escrow. */
    @PostMapping("/mutual-settle/signatures")
    public ResponseAPI<AvalancheContractView> mutualSettlementSignature(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @RequestBody MutualSettlementSignatureRequest request) {
        return ResponseAPI.<AvalancheContractView>builder().code(200)
                .data(service.submitMutualSettlementSignature(principal.getId(), contractId, request.disputeId(), request.releaseToFreelancer(),
                        request.authorizationDeadline(), request.typedDataDigest(), request.signature())).build();
    }

    @PostMapping("/enable")
    public ResponseAPI<AvalancheContractView> enable(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId) {
        return ResponseAPI.<AvalancheContractView>builder().code(200).data(service.enable(principal.getId(), contractId)).build();
    }

    @PostMapping("/actions/{action}/prepare")
    public ResponseAPI<PreparedAction> prepare(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId,
                                               @PathVariable String action, @RequestBody(required = false) AvalancheActionInput input) {
        return ResponseAPI.<PreparedAction>builder().code(200)
                .data(service.prepare(principal.getId(), admin(principal), contractId, action, input)).build();
    }

    @PostMapping("/actions/{action}/execute")
    public ResponseAPI<AvalancheChainTransaction> execute(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId,
                                                          @PathVariable String action, @RequestBody(required = false) AvalancheActionInput input) {
        return ResponseAPI.<AvalancheChainTransaction>builder().code(200)
                .data(service.execute(principal.getId(), admin(principal), contractId, action, input)).build();
    }

    @PostMapping("/actions/{action}/transactions")
    public ResponseAPI<AvalancheChainTransaction> record(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId,
                                                         @PathVariable String action, @RequestBody RecordTransactionRequest request) {
        return ResponseAPI.<AvalancheChainTransaction>builder().code(200).data(service.recordUserTransaction(principal.getId(), admin(principal),
                contractId, action, request.txHash(), request.input())).build();
    }

    @PostMapping("/refresh")
    public ResponseAPI<AvalancheContractView> refresh(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId) {
        return ResponseAPI.<AvalancheContractView>builder().code(200)
                .data(service.refresh(principal.getId(), admin(principal), contractId)).build();
    }
}
