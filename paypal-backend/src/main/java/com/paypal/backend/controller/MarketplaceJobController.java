package com.paypal.backend.marketplace.controller;

import com.paypal.backend.configuration.UserPrincipal;
import com.paypal.backend.dto.response.common.ResponseAPI;
import com.paypal.backend.marketplace.dto.request.CreateJobRequest;
import com.paypal.backend.marketplace.dto.request.LinkCheckoutOrderRequest;
import com.paypal.backend.marketplace.dto.response.JobResponse;
import com.paypal.backend.marketplace.service.MarketplaceJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace/jobs")
@RequiredArgsConstructor
public class MarketplaceJobController {

    private final MarketplaceJobService marketplaceJobService;

    @PostMapping
    public ResponseAPI<JobResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                             @Valid @RequestBody CreateJobRequest request) {
        return ResponseAPI.<JobResponse>builder()
                .code(200)
                .data(marketplaceJobService.create(principal.getId(), request))
                .build();
    }

    @GetMapping
    public ResponseAPI<List<JobResponse>> listOpen() {
        return ResponseAPI.<List<JobResponse>>builder()
                .code(200)
                .data(marketplaceJobService.listOpenJobs())
                .build();
    }

    @GetMapping("/{jobId}")
    public ResponseAPI<JobResponse> getById(@PathVariable UUID jobId) {
        return ResponseAPI.<JobResponse>builder()
                .code(200)
                .data(marketplaceJobService.getById(jobId))
                .build();
    }

    @PostMapping("/{jobId}/checkout-order")
    public ResponseAPI<JobResponse> linkCheckoutOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable UUID jobId,
                                                         @Valid @RequestBody LinkCheckoutOrderRequest request) {
        return ResponseAPI.<JobResponse>builder()
                .code(200)
                .data(marketplaceJobService.linkCheckoutOrder(principal.getId(), jobId, request))
                .build();
    }
}
