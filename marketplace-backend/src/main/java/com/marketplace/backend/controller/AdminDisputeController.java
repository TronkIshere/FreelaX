package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.dispute.ResolveDisputeRequest;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.dispute.*;
import com.marketplace.backend.entity.DisputeStatus;
import com.marketplace.backend.service.ContractDisputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/disputes")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminDisputeController {
    private final ContractDisputeService service;

    @GetMapping
    public ResponseAPI<Page<DisputeResponse>> list(@RequestParam(defaultValue = "OPEN") DisputeStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseAPI.<Page<DisputeResponse>>builder().code(200).data(service.list(status, page, size)).build();
    }

    @GetMapping("/{disputeId}")
    public ResponseAPI<AdminDisputeDetail> detail(@PathVariable UUID disputeId) {
        return ResponseAPI.<AdminDisputeDetail>builder().code(200).data(service.detail(disputeId)).build();
    }

    @PostMapping("/{disputeId}/claim")
    public ResponseAPI<DisputeResponse> claim(@AuthenticationPrincipal UserPrincipal user, @PathVariable UUID disputeId) {
        return wrap(service.claim(user.getId(), disputeId));
    }

    @PostMapping("/{disputeId}/resolve")
    public ResponseAPI<DisputeResponse> resolve(@AuthenticationPrincipal UserPrincipal user, @PathVariable UUID disputeId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody ResolveDisputeRequest request) {
        return wrap(service.resolve(user.getId(), disputeId, key, request));
    }

    private ResponseAPI<DisputeResponse> wrap(DisputeResponse value) {
        return ResponseAPI.<DisputeResponse>builder().code(200).data(value).build();
    }
}
