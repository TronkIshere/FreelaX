package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.job.AssignFreelancerRequest;
import com.marketplace.backend.dto.request.job.CreateJobRequest;
import com.marketplace.backend.dto.request.job.UpdateJobRequest;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.job.JobPaymentStatusResponse;
import com.marketplace.backend.dto.response.job.JobResponse;
import com.marketplace.backend.dto.response.job.PayJobResponse;
import com.marketplace.backend.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseAPI<JobResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody CreateJobRequest request) {
        return ResponseAPI.<JobResponse>builder()
                .code(200)
                .data(jobService.create(principal.getId(), request))
                .build();
    }

    @GetMapping
    public ResponseAPI<PageResponse<JobResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return ResponseAPI.<PageResponse<JobResponse>>builder()
                .code(200)
                .data(jobService.listForUser(principal.getId(), page, size))
                .build();
    }

    @GetMapping("/{jobId}")
    public ResponseAPI<JobResponse> getById(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID jobId) {
        return ResponseAPI.<JobResponse>builder()
                .code(200)
                .data(jobService.getByIdForParticipant(principal.getId(), jobId))
                .build();
    }

    @PatchMapping("/{jobId}")
    public ResponseAPI<JobResponse> update(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID jobId,
                                            @Valid @RequestBody UpdateJobRequest request) {
        return ResponseAPI.<JobResponse>builder()
                .code(200)
                .data(jobService.update(principal.getId(), jobId, request))
                .build();
    }

    @PostMapping("/{jobId}/pay")
    public ResponseAPI<PayJobResponse> pay(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID jobId) {
        return ResponseAPI.<PayJobResponse>builder()
                .code(200)
                .data(jobService.pay(principal.getId(), jobId))
                .build();
    }

    @PostMapping("/{jobId}/confirm-payment")
    public ResponseAPI<JobResponse> confirmPayment(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID jobId) {
        return ResponseAPI.<JobResponse>builder()
                .code(200)
                .data(jobService.confirmPayment(principal.getId(), jobId))
                .build();
    }

    @PostMapping("/{jobId}/approve")
    public ResponseAPI<JobResponse> approve(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID jobId) {
        return ResponseAPI.<JobResponse>builder()
                .code(200)
                .data(jobService.approve(principal.getId(), jobId))
                .build();
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseAPI<JobResponse> cancel(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID jobId) {
        return ResponseAPI.<JobResponse>builder()
                .code(200)
                .data(jobService.cancel(principal.getId(), jobId))
                .build();
    }

    @GetMapping("/{jobId}/payment-status")
    public ResponseAPI<JobPaymentStatusResponse> getPaymentStatus(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable UUID jobId) {
        return ResponseAPI.<JobPaymentStatusResponse>builder()
                .code(200)
                .data(jobService.getPaymentStatus(principal.getId(), jobId))
                .build();
    }

    @PatchMapping("/{jobId}/assign-freelancer")
    public ResponseAPI<JobResponse> assignFreelancer(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable UUID jobId,
                                                     @Valid @RequestBody AssignFreelancerRequest request) {
        return ResponseAPI.<JobResponse>builder()
                .code(200)
                .message("Đã gán freelancer cho công việc")
                .data(jobService.assignFreelancer(principal.getId(), jobId, request))
                .build();
    }
}
