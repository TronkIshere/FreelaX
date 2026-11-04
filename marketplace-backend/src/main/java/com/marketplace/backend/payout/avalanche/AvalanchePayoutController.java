package com.marketplace.backend.payout.avalanche;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.ResponseAPI;
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

/** Non-custodial Avalanche payout (Phase 3). Authenticated; freelancer of the contract only. Default: UNSUPPORTED_RAIL. */
@RestController
@RequestMapping("/api/v1/avalanche/payouts")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class AvalanchePayoutController {
    private final AvalanchePayoutService service;

    public record TransferRequest(String txHash) { }

    @PostMapping("/contracts/{contractId}")
    public ResponseAPI<AvalanchePayoutService.RequestResult> request(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable UUID contractId) {
        return ResponseAPI.<AvalanchePayoutService.RequestResult>builder().code(200)
                .data(service.request(principal.getId(), contractId)).build();
    }

    @GetMapping("/contracts/{contractId}")
    public ResponseAPI<AvalanchePayoutService.PayoutView> forContract(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable UUID contractId) {
        return ResponseAPI.<AvalanchePayoutService.PayoutView>builder().code(200)
                .data(service.forContract(principal.getId(), contractId).orElse(null)).build();
    }

    @PostMapping("/{payoutId}/transfer")
    public ResponseAPI<AvalanchePayoutService.PayoutView> transfer(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable UUID payoutId, @RequestBody TransferRequest request) {
        return ResponseAPI.<AvalanchePayoutService.PayoutView>builder().code(200)
                .data(service.recordTransfer(principal.getId(), payoutId, request == null ? null : request.txHash())).build();
    }

    @PostMapping("/{payoutId}/refresh")
    public ResponseAPI<AvalanchePayoutService.PayoutView> refresh(@AuthenticationPrincipal UserPrincipal principal,
                                                                @PathVariable UUID payoutId) {
        return ResponseAPI.<AvalanchePayoutService.PayoutView>builder().code(200)
                .data(service.refresh(principal.getId(), payoutId)).build();
    }
}
