package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.review.SubmitReviewRequest;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.review.ReviewResponse;
import com.marketplace.backend.service.ContractReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/reviews")
@RequiredArgsConstructor
public class ContractReviewController {
    private final ContractReviewService service;

    @GetMapping
    public ResponseAPI<List<ReviewResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable UUID contractId) {
        return ResponseAPI.<List<ReviewResponse>>builder().code(200)
                .data(service.forContract(principal.getId(), contractId)).build();
    }

    @PostMapping
    @PreAuthorize("!hasAuthority('ROLE_ADMIN')")
    public ResponseAPI<ReviewResponse> submit(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable UUID contractId,
                                               @RequestBody SubmitReviewRequest request) {
        return ResponseAPI.<ReviewResponse>builder().code(200)
                .data(service.submit(principal.getId(), contractId, request)).build();
    }

    @PostMapping("/{reviewId}/reports")
    public ResponseAPI<ReviewResponse> report(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable UUID contractId, @PathVariable UUID reviewId,
                                               @RequestBody ReportRequest request) {
        return ResponseAPI.<ReviewResponse>builder().code(200)
                .data(service.report(principal.getId(), contractId, reviewId,
                        request == null ? null : request.reason())).build();
    }

    public record ReportRequest(String reason) {}
}
