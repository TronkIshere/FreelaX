package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.dispute.*;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.dispute.DisputeResponse;
import com.marketplace.backend.service.ContractDisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contracts/{contractId}/disputes")
public class ContractDisputeController {
    private final ContractDisputeService service;

    @PostMapping
    public ResponseAPI<DisputeResponse> open(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID contractId, @Valid @RequestBody OpenDisputeRequest request) {
        return wrap(service.open(user.getId(), contractId, request));
    }

    @GetMapping
    public ResponseAPI<DisputeResponse> get(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID contractId) {
        return wrap(service.get(user.getId(), contractId));
    }

    @PostMapping("/{disputeId}/evidence")
    public ResponseAPI<DisputeResponse> evidence(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID contractId, @PathVariable UUID disputeId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody List<DisputeEvidenceInput> request) {
        return wrap(service.addEvidence(user.getId(), contractId, disputeId, key, request));
    }

    private ResponseAPI<DisputeResponse> wrap(DisputeResponse value) {
        return ResponseAPI.<DisputeResponse>builder().code(200).data(value).build();
    }
}
