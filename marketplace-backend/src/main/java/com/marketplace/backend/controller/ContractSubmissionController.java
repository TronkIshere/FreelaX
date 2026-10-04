package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.submission.CreateContractSubmissionRequest;
import com.marketplace.backend.dto.request.submission.ReviewSubmissionRequest;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.submission.ContractSubmissionResponse;
import com.marketplace.backend.service.ContractSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/submissions")
@RequiredArgsConstructor
public class ContractSubmissionController {
    private final ContractSubmissionService service;

    @PostMapping
    public ResponseAPI<ContractSubmissionResponse> submit(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable UUID contractId,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                           @Valid @RequestBody CreateContractSubmissionRequest request) {
        return ResponseAPI.<ContractSubmissionResponse>builder().code(200)
                .data(service.submit(principal.getId(), contractId, key, request)).build();
    }

    @GetMapping
    public ResponseAPI<List<ContractSubmissionResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                                @PathVariable UUID contractId) {
        return ResponseAPI.<List<ContractSubmissionResponse>>builder().code(200)
                .data(service.list(principal.getId(), contractId)).build();
    }

    @PostMapping("/{submissionId}/decisions")
    public ResponseAPI<ContractSubmissionResponse> decide(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable UUID contractId,
                                                           @PathVariable UUID submissionId,
                                                           @Valid @RequestBody ReviewSubmissionRequest request) {
        return ResponseAPI.<ContractSubmissionResponse>builder().code(200)
                .data(service.decide(principal.getId(), contractId, submissionId, request)).build();
    }
}
