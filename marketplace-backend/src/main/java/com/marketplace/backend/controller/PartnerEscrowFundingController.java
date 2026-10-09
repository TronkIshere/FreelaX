package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.funding.FundingResponse;
import com.marketplace.backend.service.PartnerEscrowFundingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contracts/{contractId}/milestones/{milestoneId}/partner-escrow")
public class PartnerEscrowFundingController {
    private final PartnerEscrowFundingService service;

    @PostMapping("/fund")
    public ResponseAPI<FundingResponse> fund(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @PathVariable UUID milestoneId,
            @RequestHeader("Idempotency-Key") String key) {
        return ResponseAPI.<FundingResponse>builder().code(200)
                .data(service.fund(principal.getId(), contractId, milestoneId, key)).build();
    }

    @GetMapping("/fund")
    public ResponseAPI<FundingResponse> status(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @PathVariable UUID milestoneId) {
        return ResponseAPI.<FundingResponse>builder().code(200)
                .data(service.get(principal.getId(), contractId, milestoneId)).build();
    }
}
