package com.marketplace.backend.controller;
import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.cancellation.*;
import com.marketplace.backend.dto.response.cancellation.CancellationResponse;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.service.ContractCancellationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/contracts/{contractId}/cancellations")
public class ContractCancellationController {
    private final ContractCancellationService cancellations;
    @PostMapping("/late-delivery") public ResponseAPI<CancellationResponse> lateDelivery(
            @AuthenticationPrincipal UserPrincipal user, @PathVariable UUID contractId) {
        return envelope(cancellations.requestLateRefund(user.getId(), contractId));
    }
    @PostMapping public ResponseAPI<CancellationResponse> request(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID contractId, @Valid @RequestBody CreateCancellationRequest request) {
        return envelope(cancellations.request(user.getId(), contractId, request));
    }
    @GetMapping public ResponseAPI<CancellationResponse> get(@AuthenticationPrincipal UserPrincipal user, @PathVariable UUID contractId) {
        return envelope(cancellations.get(user.getId(), contractId));
    }
    @PostMapping("/{cancellationId}/decisions") public ResponseAPI<CancellationResponse> decide(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID contractId, @PathVariable UUID cancellationId, @Valid @RequestBody CancellationDecisionRequest request) {
        return envelope(cancellations.decide(user.getId(), contractId, cancellationId, request));
    }
    private ResponseAPI<CancellationResponse> envelope(CancellationResponse result) {
        return ResponseAPI.<CancellationResponse>builder().code(200).data(result).build();
    }
}
