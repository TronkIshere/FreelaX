package com.marketplace.backend.service;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * Funding expired after the provider confirmed USD and the on-ramp delivered USDC, but the
 * Client never funded the vault. A timer must not cancel this; an Admin closes the contract and
 * the existing refund exit returns USDC to the treasury and then USD to the Client.
 */
@Service
@RequiredArgsConstructor
public class UnifiedFundingExpiryService {
    /** Longer than a blockhash lifetime: a fund transaction built before expiry can no longer land. */
    static final long SETTLE_SECONDS = 15 * 60;
    static final String SOURCE = "UNFUNDED_CLIENT_WALLET";

    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final JobRepository jobs;
    private final PaymentFlowRepository flows;
    private final PaymentFlowStepRepository steps;
    private final PaymentFlowEvidenceRepository evidence;
    private final PaymentFlowService paymentFlows;
    private final SolanaCprClient solana;
    private final NotificationService notifications;

    @Transactional
    public PaymentFlowService.Timeline cancelExpired(UUID adminId, UUID contractId, String note) {
        if (!StringUtils.hasText(note) || note.trim().length() < 10 || note.length() > 450)
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        Milestone milestone = milestones.findWithLockByContractId(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        WorkContract contract = contracts.findById(contractId)
                .filter(c -> PaymentFlow.RAIL.equals(c.getPaymentRail()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        Job job = jobs.findWithLockById(contract.getJobId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        PaymentFlow flow = flows.findWithLockByMilestoneId(milestone.getId())
                .filter(f -> f.getContractId().equals(contractId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        if (contract.getStatus() != ContractStatus.PENDING_FUNDING
                || milestone.getStatus() != MilestoneStatus.PENDING_FUNDING
                || job.getStatus() != JobStatus.AWAITING_PAYMENT
                || Instant.now().isBefore(paymentFlows.fundingDeadline(contract).plusSeconds(SETTLE_SECONDS)))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        PaymentFlowStep clientUsdc = locked(flow, "CLIENT_USDC");
        PaymentFlowStep escrow = locked(flow, "ESCROW");
        PaymentFlowStep release = locked(flow, "USDC_RELEASE");
        PaymentFlowStep refund = locked(flow, "USDC_REFUND");
        // Only the "USDC sits in the Client wallet" case is closed here. USD received without a
        // confirmed on-ramp stays open until the on-ramp itself resolves.
        if (!"CONFIRMED".equals(clientUsdc.getStatus()) || "CONFIRMED".equals(escrow.getStatus())
                || "CONFIRMED".equals(release.getStatus()) || "CONFIRMED".equals(refund.getStatus()))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        if (solana.findEscrow(milestone.getId().toString()).isPresent())
            throw new ApplicationException(ErrorCode.FUNDING_IN_PROGRESS);

        contract.setStatus(ContractStatus.CANCELLED);
        milestone.setStatus(MilestoneStatus.CANCELLED);
        job.setStatus(JobStatus.CANCELLED);
        refund.setStatus("CONFIRMED");
        refund.setAmount(flow.getEscrowUsdc());
        refund.setCurrency("USDC");
        refund.setProvider("CLIENT_WALLET");
        refund.setReference(clientUsdc.getReference());
        refund.setEvidenceSource(SOURCE);
        refund.setConfirmedAt(Instant.now());
        for (String next : new String[]{"WITHDRAWAL", "USD_REFUND"}) {
            PaymentFlowStep step = locked(flow, next);
            if ("NOT_STARTED".equals(step.getStatus())) step.setStatus("PENDING");
        }
        evidence.save(PaymentFlowEvidence.adminAction(flow, adminId, "FUNDING_EXPIRED_CANCEL",
                clientUsdc.getReference(), note.trim()));
        evidence.save(new PaymentFlowEvidence(flow, "USDC_REFUND", "CONFIRMED", null,
                clientUsdc.getReference(), SOURCE, flow.getEscrowUsdc(), "USDC"));
        String message = "Hợp đồng hủy vì quá hạn ký quỹ. USDC trong ví Client sẽ được gửi lại để hoàn USD.";
        notifications.notify(contract.getClientUserId(), NotificationType.JOB_CANCELLED,
                "Hết hạn ký quỹ", message, job.getId());
        notifications.notify(contract.getFreelancerId(), NotificationType.JOB_CANCELLED,
                "Hết hạn ký quỹ", message, job.getId());
        return paymentFlows.timeline(adminId, contractId, milestone.getId(), true);
    }

    /** True when USDC_REFUND records USDC that never entered the vault. */
    public static boolean unfunded(PaymentFlowStep refund) {
        return refund != null && "CONFIRMED".equals(refund.getStatus()) && SOURCE.equals(refund.getEvidenceSource());
    }

    private PaymentFlowStep locked(PaymentFlow flow, String kind) {
        return steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), kind)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
    }
}
