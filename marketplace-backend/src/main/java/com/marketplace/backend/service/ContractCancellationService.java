package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.bofa.CreateRefundRequest;
import com.marketplace.backend.dto.request.cancellation.*;
import com.marketplace.backend.dto.response.bofa.PaymentRefundResult;
import com.marketplace.backend.dto.response.cancellation.CancellationResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.*;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service @RequiredArgsConstructor @Slf4j
public class ContractCancellationService {
    private final ContractCancellationRepository cancellations;
    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final FundingTransactionRepository funding;
    private final ContractSettlementRepository settlements;
    private final ContractDisputeRepository disputes;
    private final JobSubmissionRepository submissions;
    private final JobRepository jobs;
    private final PaymentBackendClient payment;
    private final NotificationService notifications;
    private final TransactionTemplate transactions;

    public CancellationResponse request(UUID actor, UUID contractId, CreateCancellationRequest request) {
        if (request == null || !StringUtils.hasText(request.reasonCode()) || request.reasonCode().length() > 60
                || !StringUtils.hasText(request.description()) || request.description().length() > 2000)
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        String code = request.reasonCode().trim(), reason = request.description().trim();
        String hash = hash(actor + ":" + code.length() + ":" + code + ":" + reason.length() + ":" + reason);
        return committed(() -> {
            // No consistent read before this lock: avoid a stale funding/submission snapshot after waiting.
            Milestone m = milestones.findWithLockByContractId(contractId).orElseThrow(this::notFound);
            WorkContract c = participant(actor, contractId);
            ContractCancellation prior = cancellations.findByContractId(contractId).orElse(null);
            if (prior != null) {
                if (!hash.equals(prior.getIntentHash())) throw new ApplicationException(ErrorCode.CANCELLATION_CONFLICT);
                return CancellationResponse.from(prior, actor,
                        disputes.findByContractId(contractId).isEmpty());
            }
            Job job = jobs.findById(c.getJobId()).orElseThrow(this::notFound);
            boolean preFunding = c.getStatus() == ContractStatus.PENDING_FUNDING && m.getStatus() == MilestoneStatus.PENDING_FUNDING;
            commonEligibility(c, m, job);
            FundingTransaction paid = funding.findFirstByMilestoneIdOrderByCreatedAtDesc(m.getId()).orElse(null);
            if (preFunding) {
                // Any prior funding attempt needs reconciliation first; never assume FAILED means uncaptured.
                if (!actor.equals(c.getClientUserId()) || paid != null || job.getStatus() != JobStatus.AWAITING_PAYMENT) throw ineligible();
            } else { fundedEligibility(c, m, job, paid); }
            ContractCancellation row = new ContractCancellation(); row.setContractId(c.getId()); row.setMilestoneId(m.getId());
            row.setJobId(job.getId()); row.setRequestedBy(actor); row.setReasonCode(code); row.setReason(reason); row.setIntentHash(hash);
            row.setAmount(m.getAmount()); row.setCurrency(m.getCurrency()); row.setSimulation(true);
            row.setStatus(preFunding ? CancellationStatus.CANCELLED : CancellationStatus.REQUESTED);
            if (paid != null) { row.setFundingTransactionId(paid.getId()); row.setCheckoutOrderId(paid.getCheckoutOrderId()); }
            cancellations.saveAndFlush(row);
            if (preFunding) {
                c.setStatus(ContractStatus.CANCELLED); m.setStatus(MilestoneStatus.CANCELLED); job.setStatus(JobStatus.CANCELLED);
                notifyBoth(c, NotificationType.JOB_CANCELLED, "Hợp đồng đã hủy", "Hủy trước funding; không có hoàn tiền.");
            } else {
                notifications.notify(other(c, actor), NotificationType.CANCELLATION_REQUESTED,
                        "Đề nghị hủy hợp đồng", "Đối tác đề nghị hủy; chưa có hoàn tiền. Bạn cần đồng ý hoặc từ chối.", job.getId());
            }
            return CancellationResponse.from(row, actor);
        });
    }

    public CancellationResponse decide(UUID actor, UUID contractId, UUID cancellationId, CancellationDecisionRequest request) {
        if (request == null || request.decision() == null) throw new ApplicationException(ErrorCode.INVALID_DATA);
        boolean accept = request.decision() == CancellationDecisionRequest.Decision.ACCEPT;
        UUID id = committed(() -> {
            Milestone m = milestones.findWithLockByContractId(contractId).orElseThrow(this::notFound);
            WorkContract c = participant(actor, contractId);
            ContractCancellation row = cancellations.findWithLockById(cancellationId)
                    .filter(r -> r.getContractId().equals(contractId)).orElseThrow(this::notFound);
            if (actor.equals(row.getRequestedBy())) throw new ApplicationException(ErrorCode.CANCELLATION_CONFLICT);
            if (row.getStatus() != CancellationStatus.REQUESTED) {
                if (actor.equals(row.getDecidedBy()) && ((accept && (row.getStatus() == CancellationStatus.REFUND_PENDING
                        || row.getStatus() == CancellationStatus.CANCELLED)) || (!accept && row.getStatus() == CancellationStatus.REJECTED))) return row.getId();
                throw new ApplicationException(ErrorCode.CANCELLATION_CONFLICT);
            }
            if (!accept) {
                row.setStatus(CancellationStatus.REJECTED); row.setDecidedBy(actor); row.setDecidedAt(Instant.now());
                notifications.notify(row.getRequestedBy(), NotificationType.CANCELLATION_REJECTED, "Đề nghị hủy bị từ chối",
                        "Hợp đồng tiếp tục; không có hoàn tiền. Tranh chấp cần quy trình riêng.", c.getJobId());
            } else {
                Job job = jobs.findById(c.getJobId()).orElseThrow(this::notFound);
                commonEligibility(c, m, job);
                FundingTransaction paid = funding.findFirstByMilestoneIdOrderByCreatedAtDesc(m.getId()).orElseThrow(this::ineligible);
                fundedEligibility(c, m, job, paid);
                if (!Objects.equals(row.getFundingTransactionId(), paid.getId()) || !Objects.equals(row.getCheckoutOrderId(), paid.getCheckoutOrderId())
                        || !same(row.getAmount(), m.getAmount()) || !Objects.equals(row.getCurrency(), m.getCurrency())) throw ineligible();
                row.setDecidedBy(actor); row.setDecidedAt(Instant.now()); row.setStatus(CancellationStatus.REFUND_PENDING);
                row.setRefundKey("marketplace-refund-" + m.getId()); row.setRefundStatus(SettlementMoneyStatus.PENDING);
                row.setRetryable(true); row.setNextAttemptAt(Instant.EPOCH); m.setStatus(MilestoneStatus.REFUND_PENDING);
                notifyBoth(c, NotificationType.REFUND_PENDING, "Đang hoàn tiền mô phỏng",
                        "Hai bên đã đồng ý hủy; hoàn tiền ledger mô phỏng đang chờ xác nhận.");
            }
            cancellations.saveAndFlush(row); return row.getId();
        });
        // Consent and immutable refund identity survive remote success + lost local commit.
        if (accept) process(id);
        return get(actor, contractId);
    }

    public CancellationResponse get(UUID actor, UUID contractId) {
        return committed(() -> { participant(actor, contractId);
            return cancellations.findByContractId(contractId)
                    .map(r -> CancellationResponse.from(r, actor,
                            disputes.findByContractId(contractId).isEmpty()))
                    .orElse(null); });
    }

    public void process(UUID id) {
        // Resolve the immutable lock address outside the financial transaction so that a
        // MySQL repeatable-read snapshot is not established before waiting on the milestone.
        UUID mid = cancellations.findMilestoneId(id).orElseThrow(this::notFound);
        committed(() -> {
            Milestone m = milestones.findWithLockById(mid).orElseThrow(this::notFound);
            ContractCancellation row = cancellations.findWithLockById(id).orElseThrow(this::notFound);
            if (row.getStatus() != CancellationStatus.REFUND_PENDING || !row.isRetryable()
                    || row.getRefundStatus() == SettlementMoneyStatus.SUCCEEDED) return null;
            WorkContract c = contracts.findById(row.getContractId()).orElseThrow(this::notFound);
            Job job = jobs.findById(row.getJobId()).orElseThrow(this::notFound);
            FundingTransaction paid = funding.findById(row.getFundingTransactionId()).orElseThrow(this::ineligible);
            if (m.getStatus() != MilestoneStatus.REFUND_PENDING || c.getStatus() != ContractStatus.ACTIVE
                    || job.getStatus() != JobStatus.IN_PROGRESS || paid.getStatus() != FundingStatus.SUCCEEDED
                    || !Objects.equals(paid.getContractId(), c.getId()) || !Objects.equals(paid.getMilestoneId(), mid)
                    || !Objects.equals(paid.getClientUserId(), c.getClientUserId())
                    || !Objects.equals(c.getJobId(), row.getJobId()) || !Objects.equals(m.getContractId(), c.getId())
                    || !Objects.equals(job.getClientUserId(), c.getClientUserId())
                    || !Objects.equals(job.getFreelancerId(), c.getFreelancerId())
                    || !Objects.equals(row.getCheckoutOrderId(), job.getCheckoutOrderId())
                    || !row.getCheckoutOrderId().equals(paid.getCheckoutOrderId()) || !same(row.getAmount(), paid.getAmount())
                    || !same(row.getAmount(), c.getBudgetUsd()) || !same(row.getAmount(), job.getBudgetUsd())
                    || !same(row.getAmount(), m.getAmount()) || !row.getCurrency().equals(m.getCurrency())
                    || !row.getCurrency().equals(paid.getCurrency()) || submissions.countByJobId(job.getId()) != 0
                    || settlements.findByMilestoneId(mid).isPresent()
                    || disputes.existsByContractIdAndStatusIn(c.getId(), EnumSet.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW))) {
                error(row, SettlementMoneyStatus.FAILED, "REFUND_WORKFLOW_CONFLICT", false); return null;
            }
            RefundAttempt attempt = refundAttempt(row.getRefundKey(), row.getCheckoutOrderId(), row.getAmount(),
                    row.getCurrency(), c.getClientUserId());
            if (attempt.status() != SettlementMoneyStatus.SUCCEEDED) {
                error(row, attempt.status(), attempt.error(), attempt.retry()); return null;
            }
            PaymentRefundResult result = attempt.result();
            row.setRefundStatus(SettlementMoneyStatus.SUCCEEDED); row.setPaymentRefundId(result.refundId());
            row.setRefundReference(result.refundReference()); row.setStatus(CancellationStatus.CANCELLED);
            row.setRetryable(false); row.setLastError(null); row.setNextAttemptAt(null);
            m.setStatus(MilestoneStatus.REFUNDED); c.setStatus(ContractStatus.CANCELLED); job.setStatus(JobStatus.CANCELLED);
            cancellations.saveAndFlush(row);
            notifyBoth(c, NotificationType.REFUND_CONFIRMED, "Đã hoàn tiền ledger mô phỏng",
                    "Hợp đồng đã hủy; số tiền được khôi phục vào ledger mô phỏng của Client. Đây không phải chuyển tiền ngân hàng.");
            return null;
        });
    }

    @Scheduled(initialDelayString = "${cancellation.initial-delay-ms:30000}", fixedDelayString = "${cancellation.interval-ms:30000}")
    public void reconcile() {
        for (UUID id : cancellations.findDueIds(Instant.now(), PageRequest.of(0, 50))) {
            try { process(id); } catch (RuntimeException ex) { log.warn("Refund reconciliation requires another attempt: {}", id); }
        }
        for (UUID id : disputes.findDueRefundIds(Instant.now(), PageRequest.of(0, 50))) {
            try { processDisputeRefund(id); } catch (RuntimeException ex) { log.warn("Dispute refund reconciliation requires another attempt: {}", id); }
        }
    }
    /** Admin dispute refund uses the same Step 5 Payment lookup, create and validation path. */
    public void processDisputeRefund(UUID id) {
        UUID mid = disputes.findById(id).map(ContractDispute::getMilestoneId).orElseThrow(this::notFound);
        committed(() -> {
            Milestone m = milestones.findWithLockById(mid).orElseThrow(this::notFound);
            ContractDispute d = disputes.findById(id).orElseThrow(this::notFound);
            if (d.getStatus() != DisputeStatus.DECISION_PENDING_REFUND || !d.isRetryable()
                    || d.getRefundStatus() == SettlementMoneyStatus.SUCCEEDED) return null;
            WorkContract c = contracts.findById(d.getContractId()).orElseThrow(this::notFound);
            Job job = jobs.findById(c.getJobId()).orElseThrow(this::notFound);
            FundingTransaction paid = funding.findById(d.getFundingTransactionId()).orElseThrow(this::ineligible);
            if (m.getStatus() != MilestoneStatus.REFUND_PENDING || c.getStatus() != ContractStatus.DISPUTED
                    || (job.getStatus() != JobStatus.IN_PROGRESS
                        && job.getStatus() != JobStatus.REVISION_REQUESTED
                        && job.getStatus() != JobStatus.SUBMITTED_FOR_REVIEW)
                    || paid.getStatus() != FundingStatus.SUCCEEDED
                    || d.getDecisionAt() == null || d.getResolvedBy() == null
                    || !StringUtils.hasText(d.getResolutionKey()) || !StringUtils.hasText(d.getRefundKey())
                    || !d.getRefundKey().equals("marketplace-refund-" + m.getId())
                    || !Objects.equals(m.getContractId(), c.getId()) || !Objects.equals(d.getMilestoneId(), m.getId())
                    || !Objects.equals(d.getJobId(), job.getId()) || !Objects.equals(c.getJobId(), job.getId())
                    || !Objects.equals(job.getClientUserId(), c.getClientUserId())
                    || !Objects.equals(job.getFreelancerId(), c.getFreelancerId())
                    || !Objects.equals(paid.getContractId(), c.getId()) || !Objects.equals(paid.getMilestoneId(), m.getId())
                    || !Objects.equals(paid.getClientUserId(), c.getClientUserId())
                    || !Objects.equals(d.getCheckoutOrderId(), paid.getCheckoutOrderId())
                    || !Objects.equals(d.getCheckoutOrderId(), job.getCheckoutOrderId())
                    || !same(d.getAmount(), m.getAmount()) || !same(d.getAmount(), c.getBudgetUsd())
                    || !same(d.getAmount(), job.getBudgetUsd()) || !same(d.getAmount(), paid.getAmount())
                    || !Objects.equals(d.getCurrency(), m.getCurrency())
                    || !Objects.equals(d.getCurrency(), paid.getCurrency())
                    || settlements.findByMilestoneId(m.getId()).isPresent()) {
                disputeError(d, SettlementMoneyStatus.FAILED, "REFUND_WORKFLOW_CONFLICT", false); return null;
            }
            RefundAttempt attempt = refundAttempt(d.getRefundKey(), d.getCheckoutOrderId(), d.getAmount(),
                    d.getCurrency(), c.getClientUserId());
            if (attempt.status() != SettlementMoneyStatus.SUCCEEDED) {
                disputeError(d, attempt.status(), attempt.error(), attempt.retry()); return null;
            }
            d.setRefundStatus(SettlementMoneyStatus.SUCCEEDED);
            d.setPaymentRefundId(attempt.result().refundId());
            d.setRefundReference(attempt.result().refundReference());
            d.setStatus(DisputeStatus.RESOLVED_REFUND); d.setResolvedAt(Instant.now());
            d.setRetryable(false); d.setNextAttemptAt(null); d.setLastError(null);
            m.setStatus(MilestoneStatus.REFUNDED); c.setStatus(ContractStatus.CANCELLED); job.setStatus(JobStatus.CANCELLED);
            disputes.saveAndFlush(d);
            notifyBoth(c, NotificationType.REFUND_CONFIRMED, "Đã hoàn tiền ledger mô phỏng",
                    "Tranh chấp đã xử lý; số tiền được khôi phục vào ledger mô phỏng của Client. Đây không phải chuyển tiền ngân hàng.");
            return null;
        });
    }
    private void commonEligibility(WorkContract c, Milestone m, Job job) {
        if (!Objects.equals(c.getJobId(), job.getId()) || !Objects.equals(c.getClientUserId(), job.getClientUserId())
                || !Objects.equals(c.getFreelancerId(), job.getFreelancerId()) || !same(m.getAmount(), c.getBudgetUsd())
                || !same(m.getAmount(), job.getBudgetUsd()) || !"USD".equals(m.getCurrency())
                || settlements.findByMilestoneId(m.getId()).isPresent()
                || disputes.existsByContractIdAndStatusIn(c.getId(), EnumSet.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW))) throw ineligible();
    }
    private void fundedEligibility(WorkContract c, Milestone m, Job job, FundingTransaction p) {
        if (c.getStatus() != ContractStatus.ACTIVE || (m.getStatus() != MilestoneStatus.FUNDED && m.getStatus() != MilestoneStatus.IN_PROGRESS)
                || job.getStatus() != JobStatus.IN_PROGRESS || submissions.countByJobId(job.getId()) != 0
                || p == null || p.getStatus() != FundingStatus.SUCCEEDED || p.getCheckoutOrderId() == null
                || !p.getContractId().equals(c.getId()) || !p.getMilestoneId().equals(m.getId())
                || !p.getClientUserId().equals(c.getClientUserId())
                || !p.getCheckoutOrderId().equals(job.getCheckoutOrderId()) || !same(p.getAmount(), m.getAmount())
                || !p.getCurrency().equals(m.getCurrency())) throw ineligible();
    }
    private boolean matches(String key, UUID checkout, BigDecimal amount, String currency, UUID payer, PaymentRefundResult p) {
        return p != null && p.refundId() != null && p.status() != null && Boolean.TRUE.equals(p.simulation())
                && ("sim-refund-" + p.refundId()).equals(p.refundReference()) && key.equals(p.refundKey())
                && checkout.equals(p.checkoutOrderId()) && payer.equals(p.payerUserId())
                && currency.equals(p.currency()) && same(amount, p.amount());
    }
    private RefundAttempt refundAttempt(String key, UUID checkout, BigDecimal amount, String currency, UUID payer) {
        PaymentRefundResult result;
        try { result = payment.findRefund(key); }
        catch (RestClientException ex) { return new RefundAttempt(null, SettlementMoneyStatus.UNKNOWN, "REFUND_LOOKUP_UNRESOLVED", true); }
        if (result == null) {
            try { result = payment.createRefund(new CreateRefundRequest(checkout,
                    new CreateRefundRequest.ExpectedAmount(amount.toPlainString(), currency), key)); }
            catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                boolean uncertain = status >= 500 || status == 408;
                return new RefundAttempt(null, uncertain ? SettlementMoneyStatus.UNKNOWN
                        : status == 429 ? SettlementMoneyStatus.FAILED_RETRYABLE : SettlementMoneyStatus.FAILED,
                        uncertain ? "REFUND_CREATE_UNRESOLVED" : "REFUND_CREATE_REJECTED", uncertain || status == 429);
            } catch (RestClientException ex) {
                return new RefundAttempt(null, SettlementMoneyStatus.UNKNOWN, "REFUND_CREATE_UNRESOLVED", true);
            }
        }
        if (!matches(key, checkout, amount, currency, payer, result))
            return new RefundAttempt(null, SettlementMoneyStatus.UNKNOWN, "REFUND_RESPONSE_MISMATCH", true);
        if (!"SUCCEEDED".equals(result.status())) {
            SettlementMoneyStatus state = "FAILED".equals(result.status())
                    ? result.retryable() ? SettlementMoneyStatus.FAILED_RETRYABLE : SettlementMoneyStatus.FAILED
                    : SettlementMoneyStatus.UNKNOWN;
            return new RefundAttempt(null, state, "REFUND_AWAITING_RECONCILIATION",
                    !"FAILED".equals(result.status()) || result.retryable());
        }
        return new RefundAttempt(result, SettlementMoneyStatus.SUCCEEDED, null, false);
    }
    private record RefundAttempt(PaymentRefundResult result, SettlementMoneyStatus status, String error, boolean retry) {}
    private void disputeError(ContractDispute d, SettlementMoneyStatus status, String code, boolean retry) {
        d.setRefundStatus(status); d.setLastError(code); d.setRetryable(retry);
        d.setNextAttemptAt(Instant.now().plusSeconds(30));
        disputes.saveAndFlush(d);
    }
    private void error(ContractCancellation r, SettlementMoneyStatus status, String code, boolean retry) {
        r.setRefundStatus(status); r.setLastError(code); r.setRetryable(retry); r.setNextAttemptAt(Instant.now().plusSeconds(30));
        cancellations.saveAndFlush(r);
    }
    private WorkContract participant(UUID actor, UUID id) {
        return contracts.findById(id).filter(c -> actor.equals(c.getClientUserId()) || actor.equals(c.getFreelancerId())).orElseThrow(this::notFound);
    }
    private UUID other(WorkContract c, UUID actor) { return actor.equals(c.getClientUserId()) ? c.getFreelancerId() : c.getClientUserId(); }
    private void notifyBoth(WorkContract c, NotificationType type, String title, String message) {
        notifications.notify(c.getClientUserId(), type, title, message, c.getJobId());
        notifications.notify(c.getFreelancerId(), type, title, message, c.getJobId());
    }
    private ApplicationException notFound() { return new ApplicationException(ErrorCode.CANCELLATION_NOT_FOUND); }
    private ApplicationException ineligible() { return new ApplicationException(ErrorCode.CANCELLATION_INELIGIBLE); }
    private boolean same(BigDecimal a, BigDecimal b) { return a != null && b != null && a.signum() > 0 && a.compareTo(b) == 0; }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private <T> T committed(Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactions.getTransactionManager());
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); return tx.execute(status -> work.get());
    }
}
