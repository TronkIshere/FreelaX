package com.marketplace.backend.service;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentFlowService {
    private static final List<String> STEPS = List.of("USD_ORDER", "USD_RECEIVED",
            "CLIENT_USDC", "ESCROW", "WORK_ACCEPTED", "USDC_RELEASE",
            "USDC_REFUND", "WITHDRAWAL", "VND_PAYOUT", "PLATFORM_FEE", "USD_REFUND");

    private final PaymentFlowRepository flows;
    private final PaymentFlowStepRepository steps;
    private final PaymentFlowEvidenceRepository evidence;
    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final JobRepository jobs;
    private final SolanaCprClient solana;
    private final SolanaCprProperties solanaProperties;

    @Value("${payment-flow.cutover-enabled:false}")
    private boolean cutoverEnabled;

    public boolean cutoverEnabled() { return cutoverEnabled; }

    /** Existing locked quotes keep their original fee-only promise. */
    @Transactional(readOnly = true)
    public boolean usesLegacyFeeOnlyQuote(UUID contractId) {
        return flows.findByContractId(contractId)
                .filter(flow -> flow.getTermsVersion() == 1)
                .map(flow -> steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId()).stream()
                        .filter(step -> "WITHDRAWAL".equals(step.getKind()))
                        .anyMatch(step -> step.getFeeUsdc() != null && step.getVndRate() != null
                                && step.getPayoutVnd() != null
                                && step.getPayoutVnd().compareTo(flow.getEscrowUsdc()
                                    .subtract(step.getFeeUsdc()).multiply(step.getVndRate())
                                    .setScale(0, RoundingMode.HALF_UP)) == 0))
                .orElse(false);
    }

    public record ChainTerms(String network, String mint) { }

    /** Network and mint published with a unified Job; both parties accept them in the fingerprint. */
    public ChainTerms currentChainTerms() {
        SolanaConfigResult config = solana.getConfig();
        if (config == null || !StringUtils.hasText(config.getAcceptedMint())
                || Boolean.TRUE.equals(config.getPaused())
                || !StringUtils.hasText(solanaProperties.getNetwork()))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        return new ChainTerms(solanaProperties.getNetwork(), config.getAcceptedMint());
    }

    /** Backfill metadata for local flows released before WORK_ACCEPTED was recorded. */
    @Scheduled(initialDelayString = "${payment-flow.reconcile-initial-delay-ms:5000}",
            fixedDelayString = "${payment-flow.reconcile-interval-ms:5000}")
    @Transactional
    public void reconcileAcceptedWork() {
        for (PaymentFlowStep candidate : steps.findReleasedWorkPending(PageRequest.of(0, 50))) {
            PaymentFlow flow = flows.findById(candidate.getPaymentFlowId()).orElse(null);
            if (flow == null) continue;
            PaymentFlowStep release = steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), "USDC_RELEASE")
                    .orElse(null);
            if (release != null && "CONFIRMED".equals(release.getStatus()))
                confirmWorkAccepted(flow, release.getReference());
        }
    }

    /** Called only after the escrow gateway has been checked against the stored vault terms. */
    @Transactional
    public void confirmChainSettlement(WorkContract contract, Milestone milestone,
            String chainStatus, String escrowAddress, String chainReference) {
        if (!PaymentFlow.RAIL.equals(contract.getPaymentRail())) return;
        if (!"Released".equals(chainStatus) && !"Refunded".equals(chainStatus))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        PaymentFlow flow = flows.findByMilestoneId(milestone.getId())
                .filter(f -> f.getContractId().equals(contract.getId()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
        PaymentFlowStep escrow = steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), "ESCROW")
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
        if (!"CONFIRMED".equals(escrow.getStatus())
                || !escrowAddress.equals(escrow.getReference()))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        String kind = "Released".equals(chainStatus) ? "USDC_RELEASE" : "USDC_REFUND";
        String incompatible = "Released".equals(chainStatus) ? "USDC_REFUND" : "USDC_RELEASE";
        PaymentFlowStep opposite = steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), incompatible)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
        if ("CONFIRMED".equals(opposite.getStatus()))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        PaymentFlowStep step = steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), kind)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
        if ("CONFIRMED".equals(step.getStatus())) {
            if ("Released".equals(chainStatus)) confirmWorkAccepted(flow, step.getReference());
            return;
        }
        step.setStatus("CONFIRMED");
        step.setAmount(flow.getEscrowUsdc());
        step.setCurrency("USDC");
        step.setProvider("SOLANA_ESCROW");
        step.setReference(chainReference == null || chainReference.isBlank()
                ? escrowAddress + ":" + chainStatus : chainReference);
        step.setEvidenceSource("SOLANA_GATEWAY");
        step.setConfirmedAt(Instant.now());
        steps.saveAndFlush(step);
        evidence.save(new PaymentFlowEvidence(flow, kind, "CONFIRMED", null,
                step.getReference(), "SOLANA_GATEWAY", flow.getEscrowUsdc(), "USDC"));
        if ("Released".equals(chainStatus)) confirmWorkAccepted(flow, step.getReference());
        PaymentFlowStep next = steps.findWithLockByPaymentFlowIdAndKind(flow.getId(),
                "Released".equals(chainStatus) ? "WITHDRAWAL" : "USD_REFUND")
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
        if ("NOT_STARTED".equals(next.getStatus())) next.setStatus("PENDING");
        if ("Refunded".equals(chainStatus)) {
            PaymentFlowStep withdrawal = steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), "WITHDRAWAL")
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
            if ("NOT_STARTED".equals(withdrawal.getStatus())) withdrawal.setStatus("PENDING");
        }
    }

    private void confirmWorkAccepted(PaymentFlow flow, String reference) {
        PaymentFlowStep work = steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), "WORK_ACCEPTED")
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
        if ("CONFIRMED".equals(work.getStatus())) return;
        work.setStatus("CONFIRMED");
        work.setProvider("MARKETPLACE_WORKFLOW");
        work.setReference(reference);
        work.setEvidenceSource("ESCROW_TERMINAL_RELEASE");
        work.setConfirmedAt(Instant.now());
        evidence.save(new PaymentFlowEvidence(flow, "WORK_ACCEPTED", "CONFIRMED", null,
                reference, "ESCROW_TERMINAL_RELEASE", null, null));
    }

    /** One funding deadline per contract: the unified snapshot, else legacy assignment + 48h. */
    @Transactional(readOnly = true)
    public Instant fundingDeadline(WorkContract contract) {
        if (!PaymentFlow.RAIL.equals(contract.getPaymentRail()))
            return contract.getCreatedAt().atZone(java.time.ZoneId.systemDefault())
                    .toInstant().plusSeconds(48 * 3600);
        return flows.findByContractId(contract.getId())
                .map(PaymentFlow::getFundingExpiresAt)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
    }

    /**
     * True when a funding timer may not cancel: a USD order could have money with the provider
     * or in the Client wallet. Only no order, a failed order, or an expired unpaid quote are safe.
     */
    @Transactional(readOnly = true)
    public boolean unifiedFundingStarted(WorkContract contract) {
        if (!PaymentFlow.RAIL.equals(contract.getPaymentRail())) return false;
        PaymentFlow flow = flows.findByContractId(contract.getId()).orElse(null);
        if (flow == null) return true;
        String order = steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId()).stream()
                .filter(s -> "USD_ORDER".equals(s.getKind())).map(PaymentFlowStep::getStatus)
                .findFirst().orElse(null);
        if ("NOT_STARTED".equals(order) || "FAILED".equals(order)) return false;
        return !("AWAITING_CLIENT".equals(order) && flow.getQuoteExpiresAt() != null
                && !Instant.now().isBefore(flow.getQuoteExpiresAt()));
    }

    /** Called in the same DB transaction as assignment. No remote money instruction is issued. */
    @Transactional
    public PaymentFlow createDraft(WorkContract contract, Milestone milestone) {
        if (!PaymentFlow.RAIL.equals(contract.getPaymentRail())
                || contract.getId() == null || milestone.getId() == null
                || !contract.getId().equals(milestone.getContractId())
                || contract.getReviewWindowHours() != PaymentFlow.REVIEW_WINDOW_HOURS)
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        PaymentFlow flow = new PaymentFlow();
        flow.setJobId(contract.getJobId());
        flow.setContractId(contract.getId());
        flow.setMilestoneId(milestone.getId());
        flow.setClientId(contract.getClientUserId());
        flow.setFreelancerId(contract.getFreelancerId());
        flow.setGrossUsd(contract.getBudgetUsd().setScale(2));
        // The draft amount is a local mock 1:1 estimate. A valid quote and mint must be
        // locked before any USD order or token movement is permitted.
        flow.setEscrowUsdc(contract.getBudgetUsd().setScale(6));
        flow.setPlatformFeeUsd(contract.getBudgetUsd().multiply(new BigDecimal("0.03"))
                .setScale(2, RoundingMode.HALF_UP));
        flow.setFundingExpiresAt(Instant.now().plusSeconds(48 * 3600));
        flow.setDeliveryDueAt(contract.getDeliveryDueAt());
        flow.setReviewWindowHours(contract.getReviewWindowHours());
        flow.setMaxRevisions(contract.getMaxRevisions());
        Job job = jobs.findById(contract.getJobId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
        if (!StringUtils.hasText(job.getPaymentMint()) || !StringUtils.hasText(job.getPaymentNetwork()))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        // Accepted with the terms fingerprint; the USD order must find the same chain terms.
        flow.setNetwork(job.getPaymentNetwork());
        flow.setMint(job.getPaymentMint());
        flow.setTermsVersion(0);
        flow = flows.saveAndFlush(flow);
        final PaymentFlow saved = flow;
        steps.saveAll(STEPS.stream().map(kind -> {
            PaymentFlowStep step = new PaymentFlowStep();
            step.setPaymentFlowId(saved.getId());
            step.setKind(kind);
            step.setStatus("NOT_STARTED");
            return step;
        }).toList());
        evidence.save(new PaymentFlowEvidence(flow, "FLOW_CREATED", "CONFIRMED",
                null, null, "MARKETPLACE_DB", null, null));
        return flow;
    }

    @Transactional(readOnly = true)
    public Timeline timeline(UUID actorId, UUID contractId, UUID milestoneId, boolean admin) {
        WorkContract contract = contracts.findById(contractId)
                .filter(c -> admin || actorId.equals(c.getClientUserId())
                        || actorId.equals(c.getFreelancerId()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        milestones.findById(milestoneId)
                .filter(m -> m.getContractId().equals(contractId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        PaymentFlow flow = flows.findByMilestoneId(milestoneId)
                .filter(f -> f.getContractId().equals(contractId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        Job job = jobs.findById(contract.getJobId()).orElseThrow();
        boolean payerVisible = admin || actorId.equals(contract.getClientUserId());
        List<Step> stageViews = steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId()).stream()
                .map(s -> new Step(s.getKind(), s.getStatus(), s.getAmount(), s.getCurrency(),
                        s.getProvider(), s.getReference(), s.getEvidenceSource(),
                        s.getRetryAfter(), s.getConfirmedAt(), s.getTransactionSignature(),
                        s.getVndRate(), s.getPayoutVnd(), s.getFeeUsdc(), s.getQuoteExpiresAt())).toList();
        List<Evidence> events = evidence.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId()).stream()
                .map(e -> new Evidence(e.getKind(), e.getStatus(), e.getReference(),
                        e.getEvidenceSource(), e.getAmount(), e.getCurrency(), e.getCreatedAt())).toList();
        return new Timeline(flow.getId(), flow.getVersion(), flow.getJobId(), flow.getContractId(),
                flow.getMilestoneId(), PaymentFlow.RAIL, flow.getTermsVersion() == 0 ? "DRAFT" : "LOCKED",
                flow.getGrossUsd(), flow.getEscrowUsdc(), flow.getPlatformFeeUsd(),
                // The payer account is the Client's; the Freelancer has no need to see it.
                payerVisible ? flow.getPayerBankCode() : null,
                payerVisible ? masked(flow.getPayerBankAccountNumber()) : null,
                flow.getNetwork(), flow.getMint(), flow.getQuoteSource(), flow.getQuoteExpiresAt(),
                flow.getFundingExpiresAt(), flow.getDeliveryDueAt(), flow.getReviewWindowHours(),
                flow.getMaxRevisions(), job.getStatus().name(), contract.getStatus().name(),
                stageViews, events, true);
    }

    public record Step(String kind, String status, BigDecimal amount, String currency,
            String provider, String reference, String evidenceSource, Instant retryAfter,
            Instant confirmedAt, String transactionSignature, BigDecimal vndRate,
            BigDecimal payoutVnd, BigDecimal feeUsdc, Instant quoteExpiresAt) { }
    public record Evidence(String kind, String status, String reference, String evidenceSource,
            BigDecimal amount, String currency, java.time.LocalDateTime occurredAt) { }
    public record Timeline(UUID paymentFlowId, long version, UUID jobId, UUID contractId,
            UUID milestoneId, String paymentRail, String termsStatus, BigDecimal grossUsd,
            BigDecimal escrowUsdc, BigDecimal platformFeeUsd, String payerBankCode,
            String payerBankMaskedAccount, String network, String mint,
            String quoteSource, Instant quoteExpiresAt, Instant fundingExpiresAt,
            Instant deliveryDueAt, int reviewWindowHours, int maxRevisions,
            String jobStatus, String contractStatus, List<Step> steps,
            List<Evidence> evidence, boolean simulation) { }

    private String masked(String value) {
        if (value == null) return null;
        return "••••" + value.substring(Math.max(0, value.length() - 4));
    }
}
