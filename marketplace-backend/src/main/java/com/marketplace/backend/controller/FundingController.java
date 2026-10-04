package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.funding.FundMilestoneRequest;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.funding.FundingResponse;
import com.marketplace.backend.service.FundingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/milestones/{milestoneId}/fund")
@RequiredArgsConstructor
public class FundingController {
    private final FundingService fundingService;

    @PostMapping
    public ResponseAPI<FundingResponse> fund(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID contractId,
                                              @PathVariable UUID milestoneId,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                              @Valid @RequestBody FundMilestoneRequest request) {
        return ResponseAPI.<FundingResponse>builder().code(200)
                .data(fundingService.fund(principal.getId(), contractId, milestoneId, key, request)).build();
    }

    @GetMapping("/{transactionId}")
    public ResponseAPI<FundingResponse> status(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable UUID contractId,
                                                @PathVariable UUID milestoneId,
                                                @PathVariable UUID transactionId) {
        return ResponseAPI.<FundingResponse>builder().code(200)
                .data(fundingService.get(principal.getId(), contractId, milestoneId, transactionId)).build();
    }

    @GetMapping
    public ResponseAPI<FundingResponse> latest(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable UUID contractId,
                                                @PathVariable UUID milestoneId) {
        return ResponseAPI.<FundingResponse>builder().code(200)
                .data(fundingService.latest(principal.getId(), contractId, milestoneId)).build();
    }
}
