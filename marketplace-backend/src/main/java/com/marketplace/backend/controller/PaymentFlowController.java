package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.service.PaymentFlowService;
import com.marketplace.backend.service.UnifiedUsdFundingService;
import com.marketplace.backend.service.UnifiedExitService;
import com.marketplace.backend.service.UnifiedReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentFlowController {
    private final PaymentFlowService service;
    private final UnifiedUsdFundingService funding;
    private final UnifiedExitService exit;
    private final UnifiedReconciliationService reconciliation;
    private final com.marketplace.backend.service.UnifiedFundingExpiryService expiry;
    private final com.marketplace.backend.service.UnifiedLedgerSummaryService ledger;

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/api/v1/admin/payment-flows/summary")
    public ResponseAPI<com.marketplace.backend.service.UnifiedLedgerSummaryService.Summary> summary() {
        return ResponseAPI.<com.marketplace.backend.service.UnifiedLedgerSummaryService.Summary>builder()
                .code(200).data(ledger.summary()).build();
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/api/v1/admin/payment-flows/reconciliation")
    public ResponseAPI<java.util.List<UnifiedReconciliationService.Case>> reconciliation() {
        return ResponseAPI.<java.util.List<UnifiedReconciliationService.Case>>builder().code(200)
                .data(reconciliation.recent()).build();
    }

    public record ReconciliationReview(String boundary, String decision, String note) { }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @org.springframework.web.bind.annotation.PostMapping(
            "/api/v1/admin/payment-flows/{paymentFlowId}/reconciliation-reviews")
    public ResponseAPI<UnifiedReconciliationService.Case> review(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID paymentFlowId,
            @org.springframework.web.bind.annotation.RequestBody ReconciliationReview request) {
        if (request == null) throw new com.marketplace.backend.exception.ApplicationException(
                com.marketplace.backend.exception.ErrorCode.INVALID_DATA);
        return ResponseAPI.<UnifiedReconciliationService.Case>builder().code(200)
                .data(reconciliation.review(principal.getId(), paymentFlowId, request.boundary(),
                        request.decision(), request.note())).build();
    }

    public record ExpiredFundingCancel(String note) { }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @org.springframework.web.bind.annotation.PostMapping(
            "/api/v1/admin/contracts/{contractId}/payment-flow/expired-funding-cancel")
    public ResponseAPI<PaymentFlowService.Timeline> cancelExpiredFunding(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID contractId,
            @org.springframework.web.bind.annotation.RequestBody ExpiredFundingCancel request) {
        if (request == null) throw new com.marketplace.backend.exception.ApplicationException(
                com.marketplace.backend.exception.ErrorCode.INVALID_DATA);
        return ResponseAPI.<PaymentFlowService.Timeline>builder().code(200)
                .data(expiry.cancelExpired(principal.getId(), contractId, request.note())).build();
    }

    public record SignedWithdrawal(String buildSessionId, String transactionBase64) { }

    @org.springframework.web.bind.annotation.PostMapping(
            "/api/v1/contracts/{contractId}/milestones/{milestoneId}/payment-flow/withdrawal/prepare")
    public ResponseAPI<UnifiedExitService.BuildView> prepareWithdrawal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @PathVariable UUID milestoneId) {
        return ResponseAPI.<UnifiedExitService.BuildView>builder().code(200)
                .data(exit.prepare(principal.getId(), contractId, milestoneId)).build();
    }

    @org.springframework.web.bind.annotation.PostMapping(
            "/api/v1/contracts/{contractId}/milestones/{milestoneId}/payment-flow/withdrawal/submit")
    public ResponseAPI<PaymentFlowService.Timeline> submitWithdrawal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @PathVariable UUID milestoneId,
            @org.springframework.web.bind.annotation.RequestBody SignedWithdrawal request) {
        if (request == null) throw new com.marketplace.backend.exception.ApplicationException(
                com.marketplace.backend.exception.ErrorCode.INVALID_DATA);
        return ResponseAPI.<PaymentFlowService.Timeline>builder().code(200)
                .data(exit.submit(principal.getId(), contractId, milestoneId,
                        request.buildSessionId(), request.transactionBase64())).build();
    }

    @org.springframework.web.bind.annotation.PostMapping(
            "/api/v1/contracts/{contractId}/milestones/{milestoneId}/payment-flow/usd-order")
    public ResponseAPI<PaymentFlowService.Timeline> openUsdOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @PathVariable UUID milestoneId,
            @org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String key) {
        return ResponseAPI.<PaymentFlowService.Timeline>builder().code(200)
                .data(funding.openUsdOrder(principal.getId(), contractId, milestoneId, key)).build();
    }

    @org.springframework.web.bind.annotation.PostMapping(
            "/api/v1/contracts/{contractId}/milestones/{milestoneId}/payment-flow/usd-order/submit")
    public ResponseAPI<PaymentFlowService.Timeline> submitUsdOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @PathVariable UUID milestoneId) {
        return ResponseAPI.<PaymentFlowService.Timeline>builder().code(200)
                .data(funding.submitUsdOrder(principal.getId(), contractId, milestoneId)).build();
    }

    @GetMapping("/api/v1/contracts/{contractId}/milestones/{milestoneId}/payment-flow")
    public ResponseAPI<PaymentFlowService.Timeline> participant(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @PathVariable UUID milestoneId) {
        return ResponseAPI.<PaymentFlowService.Timeline>builder().code(200)
                .data(service.timeline(principal.getId(), contractId, milestoneId, false)).build();
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/api/v1/admin/contracts/{contractId}/milestones/{milestoneId}/payment-flow")
    public ResponseAPI<PaymentFlowService.Timeline> admin(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID contractId, @PathVariable UUID milestoneId) {
        return ResponseAPI.<PaymentFlowService.Timeline>builder().code(200)
                .data(service.timeline(principal.getId(), contractId, milestoneId, true)).build();
    }
}
