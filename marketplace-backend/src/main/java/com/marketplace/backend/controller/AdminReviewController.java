package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.review.ModerateReviewRequest;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.review.ReviewResponse;
import com.marketplace.backend.dto.response.review.AdminReviewDetail;
import com.marketplace.backend.service.ContractReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminReviewController {
    private final ContractReviewService service;

    @GetMapping("/reported")
    public ResponseAPI<List<ReviewResponse>> reported(@RequestParam(defaultValue = "20") int size) {
        return ResponseAPI.<List<ReviewResponse>>builder().code(200).data(service.reported(size)).build();
    }

    @GetMapping("/{reviewId}")
    public ResponseAPI<AdminReviewDetail> detail(@PathVariable UUID reviewId) {
        return ResponseAPI.<AdminReviewDetail>builder().code(200).data(service.adminDetail(reviewId)).build();
    }

    @PostMapping("/{reviewId}/moderation")
    public ResponseAPI<ReviewResponse> moderate(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID reviewId, @RequestBody ModerateReviewRequest request) {
        return ResponseAPI.<ReviewResponse>builder().code(200)
                .data(service.moderate(principal.getId(), reviewId, request)).build();
    }
}
