package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.bofa.CreateReleaseRequest;
import com.marketplace.backend.dto.response.bofa.PaymentReleaseResult;
import com.marketplace.backend.dto.response.partner.PartnerEscrowResult;
import com.marketplace.backend.dto.response.settlement.SettlementResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class SettlementService {
    private final ContractSettlementRepository settlements;
    private final EscrowContractRepository escrowContracts;
    private final MilestoneRepository milestones;
    private final WorkContractRepository contracts;
    private final JobRepository jobs;
    private final UserRepository users;
    private final JobSubmissionRepository submissions;
    private final FundingTransactionRepository funding;
    private final ContractDisputeRepository disputes;
    private final PaymentBackendClient payment;
    private final PartnerReconciliationService partnerReconciliation;
    private final NotificationService notifications;
    private final TransactionTemplate transactionTemplate;
    private final SettlementDownstreamService downstream;

    /** Key and immutable payload commit in a separate transaction BEFORE any release request. */
    public UUID prepare(UUID milestoneId) {
        return committed(() -> {
            Milestone milestone = milestones.findWithLockById(milestoneId).orElseThrow(this::ineligible);
            ContractSettlement prior = settlements.findByMilestoneId(milestoneId).orElse(null);
            if (prior != null) return prior.getId();
            Eligibility eligible = eligible(milestone);
            ContractSettlement s = new ContractSettlement();
            s.setContractId(eligible.contract().getId());
            s.setMilestoneId(milestoneId);
            s.setJobId(eligible.job().getId());
            s.setFundingTransactionId(eligible.funding().getId());
            s.setCheckoutOrderId(eligible.funding().getCheckoutOrderId());
            s.setFreelancerId(eligible.contract().getFreelancerId());
            s.setAmount(milestone.getAmount());
            s.setCurrency(milestone.getCurrency());
            s.setReleaseKey("marketplace-settlement-" + milestoneId);
            // Immediately eligible, independent of database fractional timestamp rounding.
            s.setNextAttemptAt(Instant.EPOCH);
            return settlements.saveAndFlush(s).getId();
        });
    }

    public void process(UUID settlementId) {
        processMoney(settlementId);
        // Partner mock already models FX and bank payout; never run Solana/off-ramp again.
        ContractSettlement row = settlements.findById(settlementId).orElseThrow(this::ineligible);
        WorkContract contract = contracts.findById(row.getContractId()).orElseThrow(this::ineligible);
        if (!PartnerEscrowFundingService.RAIL.equals(contract.getPaymentRail())) {
            downstream.process(settlementId);
        }
    }

    public void processMoney(UUID settlementId) {
        committed(() -> {
            // Same lock order as review/dispute: milestone first, then settlement.
            UUID milestoneId = settlements.findMilestoneId(settlementId).orElseThrow(this::ineligible);
            Milestone milestone = milestones.findWithLockById(milestoneId).orElseThrow(this::ineligible);
            ContractSettlement s = settlements.findWithLockById(settlementId).orElseThrow(this::ineligible);
            if (s.getMoneyStatus() == SettlementMoneyStatus.SUCCEEDED
                    || s.getMoneyStatus() == SettlementMoneyStatus.FAILED || !s.isRetryable()
                    || s.getNextAttemptAt().isAfter(Instant.now())) return null;
            Eligibility eligible = eligible(milestone);
            validateSnapshot(s, eligible, milestone);
            s.setMoneyStatus(SettlementMoneyStatus.PROCESSING);
            PaymentReleaseResult result;
            PartnerEscrowResult partnerResult = null;
            boolean partnerRail = PartnerEscrowFundingService.RAIL.equals(eligible.contract().getPaymentRail());
            boolean creating = false;
            try {
                // Always reconcile first, including PENDING after a lost Marketplace commit.
                if (partnerRail) {
                    partnerResult = payment.getPartnerEscrow(milestoneId);
                    if (!"PAID".equals(partnerResult.status())) {
                        if (!partnerReconciliation.snapshot().matched()) {
                            moneyError(s, SettlementMoneyStatus.UNKNOWN, "PARTNER_RECONCILIATION_MISMATCH", true);
                            return null;
                        }
                        User recipient = users.findById(s.getFreelancerId()).orElseThrow(this::ineligible);
                        if (recipient.getBankCode() == null || recipient.getBankAccountNumber() == null)
                            throw new ApplicationException(ErrorCode.SETTLEMENT_INELIGIBLE);
                        creating = true;
                        partnerResult = payment.releasePartnerEscrow(milestoneId, s.getReleaseKey(),
                                recipient.getBankCode().name(), recipient.getBankAccountNumber(),
                                disputes.findByContractId(s.getContractId())
                                        .map(d -> d.getStatus() == DisputeStatus.DECISION_PENDING_RELEASE)
                                        .orElse(false));
                    }
                    if ("PAID".equals(partnerResult.status())
                            && !partnerReconciliation.confirms(milestoneId, "RELEASE", s.getAmount())) {
                        moneyError(s, SettlementMoneyStatus.UNKNOWN, "PARTNER_STATEMENT_UNCONFIRMED", true);
                        return null;
                    }
                    result = new PaymentReleaseResult(milestoneId, s.getReleaseKey(), milestoneId,
                            s.getFreelancerId(), "PAID".equals(partnerResult.status()) ? "SUCCEEDED" : "PENDING",
                            s.getAmount(), "USD", true, "partner-mock-release-" + milestoneId,
                            false, partnerResult.updatedAt(), partnerResult.updatedAt());
                } else {
                    result = payment.findRelease(s.getReleaseKey());
                    if (result == null) {
                        creating = true;
                        result = payment.createRelease(new CreateReleaseRequest(s.getCheckoutOrderId(),
                                s.getFreelancerId(), new CreateReleaseRequest.ExpectedAmount(
                                s.getAmount().toPlainString(), s.getCurrency()), s.getReleaseKey()));
                    }
                }
            } catch (HttpStatusCodeException ex) {
                int code = ex.getStatusCode().value();
                if (!creating) {
                    // A failed lookup cannot establish whether an earlier POST committed.
                    moneyError(s, SettlementMoneyStatus.UNKNOWN, "PAYMENT_LOOKUP_UNAVAILABLE", true);
                } else if (code == 429) {
                    moneyError(s, SettlementMoneyStatus.FAILED_RETRYABLE, "PAYMENT_RATE_LIMITED", true);
                } else if (code >= 400 && code < 500 && code != 408) {
                    moneyError(s, SettlementMoneyStatus.FAILED, "PAYMENT_RELEASE_REJECTED", false);
                } else {
                    moneyError(s, SettlementMoneyStatus.UNKNOWN, "PAYMENT_OUTCOME_UNKNOWN", true);
                }
                return null;
            } catch (RestClientException ex) {
                moneyError(s, SettlementMoneyStatus.UNKNOWN, "PAYMENT_OUTCOME_UNKNOWN", true);
                return null;
            }
            if (partnerRail ? !matchesPartner(s, eligible, partnerResult) : !matches(s, result)) {
                moneyError(s, SettlementMoneyStatus.UNKNOWN, "PAYMENT_RESPONSE_MISMATCH", true);
                return null;
            }
            if (partnerRail && partnerResult != null) {
                s.setPlatformFeeUsd(partnerResult.feeUsd());
                s.setFreelancerUsd(partnerResult.freelancerUsd());
                s.setLockedUsdVndRate(partnerResult.usdVndRate());
                s.setPartnerPayoutVnd(partnerResult.payoutVnd());
            }
            s.setPaymentReleaseId(result.releaseId());
            // Do not forward arbitrary provider text to participants.
            s.setPaymentReleaseReference(result.releaseReference());
            s.setSimulation(result.simulation());
            switch (result.status()) {
                case "SUCCEEDED" -> {
                    s.setMoneyStatus(SettlementMoneyStatus.SUCCEEDED);
                    if (s.getMoneySucceededAt() == null) s.setMoneySucceededAt(Instant.now());
                    s.setLastError(null);
                    s.setRetryable(true); // Independent unfinished downstream work.
                    s.setNextAttemptAt(Instant.now());
                    milestone.setStatus(MilestoneStatus.RELEASED);
                    eligible.contract().setStatus(ContractStatus.COMPLETED);
                    eligible.job().setStatus(JobStatus.COMPLETED);
                    disputes.findByContractId(s.getContractId())
                            .filter(d -> d.getStatus() == DisputeStatus.DECISION_PENDING_RELEASE
                                    && Objects.equals(d.getMilestoneId(), milestone.getId()))
                            .ifPresent(d -> {
                                d.setStatus(DisputeStatus.RESOLVED_RELEASE);
                                d.setResolvedAt(Instant.now());
                            });
                    milestones.save(milestone);
                    contracts.save(eligible.contract());
                    jobs.save(eligible.job());
                    notifications.notify(s.getFreelancerId(), NotificationType.RELEASE_CONFIRMED,
                            partnerRail ? "Đối tác mock đã chi trả" : "Đã ghi nhận release mô phỏng",
                            partnerRail ? "Đối tác mô phỏng đã xác nhận chi VND; phí FreelaX 3% được tính lúc này. Không phải chuyển khoản ngân hàng thật."
                                    : "Quyền nhận tiền đã được ghi có trong sổ mô phỏng. "
                                    + "Solana, off-ramp và chứng từ thuế được xử lý riêng; chưa xác nhận chuyển khoản ngân hàng.",
                            s.getJobId());
                }
                case "FAILED" -> moneyError(s, result.retryable()
                        ? SettlementMoneyStatus.FAILED_RETRYABLE : SettlementMoneyStatus.FAILED,
                        "PAYMENT_RELEASE_FAILED", result.retryable());
                case "PENDING", "PROCESSING" -> moneyError(s, SettlementMoneyStatus.PROCESSING,
                        null, true);
                default -> moneyError(s, SettlementMoneyStatus.UNKNOWN, "PAYMENT_OUTCOME_UNKNOWN", true);
            }
            // Persistence/commit errors propagate. Never label a remote success as a release failure.
            settlements.saveAndFlush(s);
            return null;
        });
    }

    public SettlementResponse get(UUID participantId, UUID contractId) {
        return committed(() -> {
            WorkContract contract = contracts.findById(contractId)
                    .filter(c -> participantId.equals(c.getClientUserId()) || participantId.equals(c.getFreelancerId()))
                    .orElseThrow(() -> new ApplicationException(ErrorCode.SETTLEMENT_NOT_FOUND));
            return settlements.findByContractId(contract.getId()).map(SettlementResponse::from).orElse(null);
        });
    }

    private Eligibility eligible(Milestone milestone) {
        WorkContract contract = contracts.findById(milestone.getContractId()).orElseThrow(this::ineligible);
        if (PaymentFlow.RAIL.equals(contract.getPaymentRail())) throw ineligible();
        if (escrowContracts.existsByContractId(contract.getId())) throw ineligible();
        Job job = jobs.findById(contract.getJobId()).orElseThrow(this::ineligible);
        JobSubmission approved = submissions.findFirstByContractIdOrderByVersionDesc(contract.getId()).orElse(null);
        ContractDispute adminDecision = disputes.findByContractId(contract.getId())
                .filter(d -> d.getStatus() == DisputeStatus.DECISION_PENDING_RELEASE
                        && Objects.equals(d.getMilestoneId(), milestone.getId())).orElse(null);
        boolean adminRelease = adminDecision != null;
        boolean normalRelease = contract.getStatus() == ContractStatus.UNDER_REVIEW
                && job.getStatus() == JobStatus.SUBMITTED_FOR_REVIEW
                && approved != null && approved.getStatus() == JobSubmissionStatus.APPROVED
                && approved.getReviewedAt() != null;
        boolean validAdminSubmission = approved == null
                ? adminDecision != null && adminDecision.getSubmissionId() == null
                : adminDecision != null && Objects.equals(adminDecision.getSubmissionId(), approved.getId())
                    && (approved.getStatus() == JobSubmissionStatus.DISPUTED
                        || approved.getStatus() == JobSubmissionStatus.REVISION_REQUESTED);
        boolean disputedRelease = adminRelease && contract.getStatus() == ContractStatus.DISPUTED
                && adminDecision.getDecisionAt() != null && adminDecision.getResolvedBy() != null
                && adminDecision.getResolutionKey() != null
                && Objects.equals(adminDecision.getJobId(), job.getId())
                && (job.getStatus() == JobStatus.IN_PROGRESS
                    || job.getStatus() == JobStatus.REVISION_REQUESTED
                    || job.getStatus() == JobStatus.SUBMITTED_FOR_REVIEW)
                && validAdminSubmission;
        FundingTransaction paid = funding.findFirstByMilestoneIdOrderByCreatedAtDesc(milestone.getId())
                .orElseThrow(this::ineligible);
        if (milestone.getStatus() != MilestoneStatus.RELEASE_PENDING
                || (!normalRelease && !disputedRelease)
                || !Objects.equals(contract.getClientUserId(), job.getClientUserId())
                || !Objects.equals(contract.getFreelancerId(), job.getFreelancerId())
                || Objects.equals(contract.getFreelancerId(), contract.getClientUserId())
                || (approved != null && (!Objects.equals(approved.getJobId(), job.getId())
                    || !Objects.equals(approved.getMilestoneId(), milestone.getId())
                    || !Objects.equals(approved.getFreelancerId(), contract.getFreelancerId())))
                || paid.getStatus() != FundingStatus.SUCCEEDED
                || !Objects.equals(paid.getContractId(), contract.getId())
                || !Objects.equals(paid.getClientUserId(), contract.getClientUserId())
                || paid.getCheckoutOrderId() == null
                || !Objects.equals(paid.getCheckoutOrderId(), job.getCheckoutOrderId())
                || !"USD".equals(milestone.getCurrency())
                || !Objects.equals(paid.getCurrency(), milestone.getCurrency())
                || !same(milestone.getAmount(), contract.getBudgetUsd())
                || !same(milestone.getAmount(), job.getBudgetUsd())
                || !same(milestone.getAmount(), paid.getAmount())
                || disputes.existsByContractIdAndStatusIn(contract.getId(),
                    EnumSet.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW))) throw ineligible();
        return new Eligibility(contract, job, paid);
    }

    private void validateSnapshot(ContractSettlement s, Eligibility e, Milestone m) {
        if (!Objects.equals(s.getContractId(), e.contract().getId())
                || !Objects.equals(s.getJobId(), e.job().getId())
                || !Objects.equals(s.getFundingTransactionId(), e.funding().getId())
                || !Objects.equals(s.getCheckoutOrderId(), e.funding().getCheckoutOrderId())
                || !Objects.equals(s.getFreelancerId(), e.contract().getFreelancerId())
                || !Objects.equals(s.getCurrency(), m.getCurrency())
                || !same(s.getAmount(), m.getAmount())) throw ineligible();
    }

    private boolean matches(ContractSettlement s, PaymentReleaseResult r) {
        return r != null && r.releaseId() != null && r.status() != null
                && Boolean.TRUE.equals(r.simulation()) // Current primitive only supports simulation.
                && Objects.equals(r.releaseReference(), "sim-release-" + r.releaseId())
                && Objects.equals(s.getReleaseKey(), r.releaseKey())
                && Objects.equals(s.getCheckoutOrderId(), r.checkoutOrderId())
                && Objects.equals(s.getFreelancerId(), r.recipientUserId())
                && Objects.equals(s.getCurrency(), r.currency()) && same(s.getAmount(), r.amount());
    }

    private boolean matchesPartner(ContractSettlement s, Eligibility e, PartnerEscrowResult p) {
        if (p == null || !p.simulation() || !"PAID".equals(p.status())
                || !Objects.equals(p.milestoneId(), s.getMilestoneId())
                || !Objects.equals(p.contractId(), s.getContractId())
                || !Objects.equals(p.jobId(), s.getJobId())
                || !Objects.equals(p.clientId(), e.contract().getClientUserId())
                || !Objects.equals(p.freelancerId(), s.getFreelancerId())
                || !Objects.equals(p.releaseKey(), s.getReleaseKey())
                || !same(p.grossUsd(), s.getAmount()) || p.feeUsd() == null
                || p.freelancerUsd() == null || p.usdVndRate() == null || p.payoutVnd() == null) return false;
        BigDecimal expectedFee = s.getAmount().multiply(new BigDecimal("0.03"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        return p.feeUsd().compareTo(expectedFee) == 0
                && p.freelancerUsd().compareTo(s.getAmount().subtract(expectedFee)) == 0
                && p.payoutVnd().compareTo(p.freelancerUsd().multiply(p.usdVndRate())
                        .setScale(0, java.math.RoundingMode.HALF_UP)) == 0;
    }

    private void moneyError(ContractSettlement s, SettlementMoneyStatus state, String error, boolean retry) {
        s.setMoneyStatus(state);
        s.setLastError(error);
        s.setRetryable(retry);
        s.setNextAttemptAt(Instant.now().plusSeconds(30));
        settlements.saveAndFlush(s);
    }

    private boolean same(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.signum() > 0 && a.compareTo(b) == 0;
    }

    private ApplicationException ineligible() {
        return new ApplicationException(ErrorCode.SETTLEMENT_INELIGIBLE);
    }

    private <T> T committed(Supplier<T> work) {
        TransactionTemplate independent = new TransactionTemplate(transactionTemplate.getTransactionManager());
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return independent.execute(status -> work.get());
    }

    private record Eligibility(WorkContract contract, Job job, FundingTransaction funding) {}
}
