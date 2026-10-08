package com.marketplace.backend.service;

import com.marketplace.backend.client.SolanaCprClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.dto.request.dispute.OpenDisputeRequest;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SolanaEscrowTimeoutService {
    private final EscrowContractRepository escrows;
    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final JobRepository jobs;
    private final JobSubmissionRepository submissions;
    private final EscrowActionIntentRepository intents;
    private final ContractDisputeRepository disputes;
    private final DisputeAuditRepository disputeAudit;
    private final SolanaCprClient solana;
    private final SolanaEscrowFundingService fundingService;
    private final ContractSubmissionService submissionService;
    private final ContractDisputeService disputeService;
    private final ObjectMapper objectMapper;
    private final NotificationService notifications;

    @Transactional
    public void process(UUID escrowRecordId) {
        EscrowContract candidate = escrows.findById(escrowRecordId).orElse(null);
        if (candidate == null) return;
        Milestone milestone = milestones.findWithLockById(candidate.getMilestoneId()).orElse(null);
        if (milestone == null) return;
        EscrowContract record = escrows.findWithLockById(escrowRecordId).orElse(null);
        if (record == null || !record.getMilestoneId().equals(milestone.getId())) return;
        SolanaEscrowResult chain = solana.findEscrow(record.getMilestoneId().toString()).orElse(null);
        WorkContract contract = contracts.findById(record.getContractId()).orElse(null);
        if (contract == null) return;
        if (chain == null) {
            reconcileUnfunded(record, contract);
            return;
        }
        if (!Objects.equals(chain.address(), record.getEscrowAddress())
                || !Objects.equals(chain.client(), record.getClientWallet())
                || !Objects.equals(chain.freelancer(), record.getFreelancerWallet())
                || !Objects.equals(chain.arbiter(), record.getArbiterWallet())
                || !Objects.equals(chain.mint(), record.getMint())) return;
        record.setLastChainStatus(chain.status());
        if (contract.getStatus() == ContractStatus.PENDING_FUNDING) {
            fundingService.get(contract.getClientUserId(), contract.getId(), record.getMilestoneId());
            return;
        }
        if (!milestone.getContractId().equals(contract.getId())) return;
        if (!Objects.equals(chain.milestoneId(), record.getMilestoneId().toString())
                || !Objects.equals(chain.amount(), milestone.getAmount().movePointRight(6)
                    .toBigIntegerExact().toString())
                || chain.vaultBalanceBaseUnits() == null
                || (!"Released".equals(chain.status()) && !"Refunded".equals(chain.status())
                    && new java.math.BigInteger(chain.vaultBalanceBaseUnits()).compareTo(
                        new java.math.BigInteger(chain.amount())) < 0)) return;
        if (("Submitted".equals(chain.status()) || "Released".equals(chain.status()))
                && (contract.getStatus() == ContractStatus.ACTIVE
                    || contract.getStatus() == ContractStatus.REVISION)
                && (milestone.getStatus() == MilestoneStatus.FUNDED
                    || milestone.getStatus() == MilestoneStatus.IN_PROGRESS)) {
            reconcileSubmission(record, contract, chain);
        }
        if (contract.getStatus() == ContractStatus.DISPUTED) {
            reconcileDispute(record, contract, milestone, chain);
            return;
        }
        if ("Released".equals(chain.status())) {
            finalizeRelease(record, contract, milestone, chain);
            return;
        }
        if ("Refunded".equals(chain.status())) {
            finalizeMutualRefund(record, contract, milestone);
            return;
        }
        if (record.getRefundSignature() != null && record.getRefundSubmittedAt() != null
                && !record.isSettlementRetryPending()) {
            var refundTx = solana.getTransactionStatus(record.getRefundSignature());
            if (refundTx.hasError() || !refundTx.isFound()
                    && Instant.now().isAfter(record.getRefundSubmittedAt().plusSeconds(15 * 60)))
                record.setSettlementRetryPending(true);
        }
        if ("Submitted".equals(chain.status()) && record.getReleaseSignature() != null
                && record.getReleaseSubmittedAt() != null && !record.isSettlementRetryPending()) {
            var releaseTx = solana.getTransactionStatus(record.getReleaseSignature());
            if (releaseTx.hasError() || !releaseTx.isFound()
                    && Instant.now().isAfter(record.getReleaseSubmittedAt().plusSeconds(15 * 60)))
                record.setSettlementRetryPending(true);
        }
        if ("Revision".equals(chain.status())
                && contract.getStatus() == ContractStatus.UNDER_REVIEW
                && milestone.getStatus() == MilestoneStatus.SUBMITTED) {
            reconcileReviewAction(record, contract, "request-revision", chain.revisionHash());
        }
        if ("Disputed".equals(chain.status())
                && contract.getStatus() == ContractStatus.UNDER_REVIEW
                && milestone.getStatus() == MilestoneStatus.SUBMITTED) {
            reconcileReviewAction(record, contract, "review-dispute", chain.disputeHash());
        }
        if ("Disputed".equals(chain.status())
                && contract.getStatus() != ContractStatus.DISPUTED) {
            reconcileDirectDispute(record, contract, milestone, chain);
        }
        if (!"Submitted".equals(chain.status())
                || contract.getStatus() != ContractStatus.UNDER_REVIEW
                || milestone.getStatus() != MilestoneStatus.SUBMITTED
                || chain.reviewDueAt() == null
                || Instant.now().isBefore(Instant.ofEpochSecond(Long.parseLong(chain.reviewDueAt())))) return;
        JobSubmission latest = submissions.findFirstByContractIdOrderByVersionDesc(contract.getId()).orElse(null);
        if (latest == null || latest.getStatus() != JobSubmissionStatus.SUBMITTED
                || !Objects.equals(latest.getPayloadHash(), chain.submissionHash())) return;
        if (record.getClaimSignature() != null) {
            var transaction = solana.getTransactionStatus(record.getClaimSignature());
            if (!transaction.hasError() && (transaction.isFound()
                    || record.getClaimSubmittedAt() == null
                    || Instant.now().isBefore(record.getClaimSubmittedAt().plusSeconds(15 * 60)))) return;
            record.setSettlementRetryPending(true);
            record.setClaimSignature(null);
        }
        try {
            var result = solana.sendEscrowAction(record.getMilestoneId().toString(), "claim",
                    Map.of("actor", record.getArbiterWallet(), "mode", "send"));
            record.setClaimSignature(result.getSignature());
            record.setClaimSubmittedAt(Instant.now());
            record.setSettlementRetryPending(false);
            record.setReleaseSignature(result.getSignature());
            record.setReleaseSubmittedAt(Instant.now());
        } catch (RuntimeException exception) {
            record.setSettlementRetryPending(true);
        }
    }

    private void reconcileUnfunded(EscrowContract record, WorkContract contract) {
        if (contract.getStatus() != ContractStatus.PENDING_FUNDING
                || contract.getCreatedAt() == null) return;
        Instant assigned = contract.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();
        Instant now = Instant.now();
        if (contract.getFundingReminderSentAt() == null && !now.isBefore(assigned.plusSeconds(24 * 3600))) {
            contract.setFundingReminderSentAt(now);
            notifications.notify(contract.getClientUserId(), NotificationType.FUNDING_REMINDER,
                    "Nhắc ký quỹ Milestone", "Còn dưới 24 giờ để ký quỹ on-chain.", contract.getJobId());
        }
        // The instruction itself rejects funding after 48 hours. Wait for any earlier
        // blockhash to expire before closing a locally unresolved build/signature.
        if (now.isBefore(assigned.plusSeconds(48 * 3600 + 15 * 60))) return;
        if (record.getFundSignature() != null) {
            var tx = solana.getTransactionStatus(record.getFundSignature());
            if (tx.isFound() && !tx.hasError()) return;
        }
        Milestone milestone = milestones.findWithLockById(record.getMilestoneId()).orElse(null);
        Job job = jobs.findById(contract.getJobId()).orElse(null);
        if (milestone == null || job == null || milestone.getStatus() != MilestoneStatus.PENDING_FUNDING
                || job.getStatus() != JobStatus.AWAITING_PAYMENT) return;
        milestone.setStatus(MilestoneStatus.CANCELLED);
        contract.setStatus(ContractStatus.CANCELLED);
        job.setStatus(JobStatus.CANCELLED);
        record.setLastChainStatus("EXPIRED_UNFUNDED");
        notifications.notify(contract.getClientUserId(), NotificationType.JOB_CANCELLED,
                "Hết hạn ký quỹ", "Milestone không được kích hoạt vì chưa có tiền trong vault.", job.getId());
        notifications.notify(contract.getFreelancerId(), NotificationType.JOB_CANCELLED,
                "Hết hạn ký quỹ", "Milestone không được kích hoạt vì chưa có tiền trong vault.", job.getId());
    }

    private void reconcileSubmission(EscrowContract record, WorkContract contract,
            SolanaEscrowResult chain) {
        EscrowActionIntent intent = intents
                .findFirstByMilestoneIdAndActionAndPayloadHashOrderByCreatedAtDesc(
                        record.getMilestoneId(), "submit", chain.submissionHash())
                .orElse(null);
        if (intent == null || intent.getPayloadJson() == null) return;
        try {
            var input = objectMapper.readValue(intent.getPayloadJson(),
                    SolanaEscrowWorkflowService.ActionInput.class);
            if (input.submission() == null) return;
            submissionService.submit(contract.getFreelancerId(), contract.getId(),
                    "escrow-reconcile-" + intent.getId(), input.submission());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Stored escrow submission payload is unreadable", exception);
        }
    }

    private void reconcileReviewAction(EscrowContract record, WorkContract contract,
            String action, String hash) {
        if (hash == null) return;
        EscrowActionIntent intent = intents
                .findFirstByMilestoneIdAndActionAndPayloadHashOrderByCreatedAtDesc(
                        record.getMilestoneId(), action, hash)
                .orElse(null);
        JobSubmission latest = submissions.findFirstByContractIdOrderByVersionDesc(contract.getId())
                .orElse(null);
        if (intent == null || intent.getPayloadJson() == null || latest == null
                || latest.getStatus() != JobSubmissionStatus.SUBMITTED) return;
        try {
            var input = objectMapper.readValue(intent.getPayloadJson(),
                    SolanaEscrowWorkflowService.ActionInput.class);
            if (input.review() == null) return;
            submissionService.decide(contract.getClientUserId(), contract.getId(),
                    latest.getId(), input.review());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Stored escrow review payload is unreadable", exception);
        }
    }

    private void reconcileDirectDispute(EscrowContract record, WorkContract contract,
            Milestone milestone, SolanaEscrowResult chain) {
        if (chain.disputedAt() == null || chain.disputedBy() == null
                || Instant.now().isBefore(Instant.ofEpochSecond(Long.parseLong(chain.disputedAt()))
                    .plusSeconds(60))
                || disputes.findByContractId(contract.getId()).isPresent()) return;
        UUID actor = chain.disputedBy().equals(record.getClientWallet())
                ? contract.getClientUserId() : chain.disputedBy().equals(record.getFreelancerWallet())
                    ? contract.getFreelancerId() : null;
        if (actor == null) return;
        EscrowActionIntent intent = intents
                .findFirstByMilestoneIdAndActionAndPayloadHashOrderByCreatedAtDesc(
                        record.getMilestoneId(), "open-dispute", chain.disputeHash())
                .orElse(null);
        if (intent != null && intent.getPayloadJson() != null
                && actor.equals(intent.getActorId())) {
            try {
                var input = objectMapper.readValue(intent.getPayloadJson(),
                        SolanaEscrowWorkflowService.ActionInput.class);
                if (input.reasonCode() != null && input.description() != null) {
                    disputeService.open(actor, contract.getId(),
                            new OpenDisputeRequest(input.reasonCode(), input.description(), java.util.List.of()));
                    return;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("Stored escrow dispute payload is unreadable", exception);
            }
        }
        Job job = jobs.findById(contract.getJobId()).orElse(null);
        if (job == null) return;
        JobSubmission latest = submissions.findFirstByContractIdOrderByVersionDesc(contract.getId())
                .orElse(null);
        ContractDispute dispute = new ContractDispute();
        dispute.setContractId(contract.getId()); dispute.setMilestoneId(milestone.getId());
        dispute.setJobId(job.getId()); dispute.setSubmissionId(latest == null ? null : latest.getId());
        dispute.setOpenedBy(actor); dispute.setReasonCode("ONCHAIN_ONLY");
        dispute.setDescription("Tranh chấp đã mở trên Solana; nội dung ngoài chain chưa được gửi. Hash: "
                + chain.disputeHash());
        dispute.setOpenedAt(Instant.ofEpochSecond(Long.parseLong(chain.disputedAt())));
        dispute.setStatus(DisputeStatus.OPEN);
        disputes.saveAndFlush(dispute);
        if (latest != null && latest.getStatus() == JobSubmissionStatus.SUBMITTED)
            latest.setStatus(JobSubmissionStatus.DISPUTED);
        contract.setStatus(ContractStatus.DISPUTED);
        milestone.setStatus(MilestoneStatus.DISPUTED);
        DisputeAudit audit = new DisputeAudit();
        audit.setDisputeId(dispute.getId()); audit.setActorId(actor);
        audit.setAction("RECONCILED"); audit.setAfterStatus(DisputeStatus.OPEN.name());
        audit.setReason(dispute.getDescription()); audit.setRequestId(UUID.randomUUID().toString());
        disputeAudit.save(audit);
        notifications.notify(actor.equals(contract.getClientUserId())
                        ? contract.getFreelancerId() : contract.getClientUserId(),
                NotificationType.DISPUTE_OPENED, "Tranh chấp on-chain đã ghi nhận",
                "Bổ sung bằng chứng để Admin xử lý.", job.getId());
    }

    private void finalizeMutualRefund(EscrowContract record, WorkContract contract,
            Milestone milestone) {
        if (contract.getStatus() == ContractStatus.CANCELLED
                && milestone.getStatus() == MilestoneStatus.REFUNDED) {
            record.setLastChainStatus("REFUNDED_RECONCILED");
            record.setSettlementRetryPending(false);
            return;
        }
        if (contract.getStatus() != ContractStatus.ACTIVE
                && contract.getStatus() != ContractStatus.UNDER_REVIEW
                && contract.getStatus() != ContractStatus.REVISION) return;
        if (milestone.getStatus() != MilestoneStatus.FUNDED
                && milestone.getStatus() != MilestoneStatus.IN_PROGRESS
                && milestone.getStatus() != MilestoneStatus.SUBMITTED) return;
        Job job = jobs.findById(contract.getJobId()).orElse(null);
        if (job == null) return;
        milestone.setStatus(MilestoneStatus.REFUNDED);
        contract.setStatus(ContractStatus.CANCELLED);
        job.setStatus(JobStatus.CANCELLED);
        record.setLastChainStatus("REFUNDED_RECONCILED");
        record.setSettlementRetryPending(false);
        notifications.notify(contract.getClientUserId(), NotificationType.REFUND_CONFIRMED,
                "Escrow đã hoàn tiền on-chain", "Vault đã hoàn tiền cho Client.", job.getId());
        notifications.notify(contract.getFreelancerId(), NotificationType.REFUND_CONFIRMED,
                "Escrow đã hoàn tiền on-chain", "Vault đã hoàn tiền cho Client.", job.getId());
    }

    private void reconcileDispute(EscrowContract record, WorkContract contract,
            Milestone milestone, SolanaEscrowResult chain) {
        ContractDispute dispute = disputes.findByContractId(contract.getId()).orElse(null);
        if (dispute == null || dispute.getResolutionHash() == null) return;
        boolean release = dispute.getStatus() == DisputeStatus.DECISION_PENDING_RELEASE;
        boolean refund = dispute.getStatus() == DisputeStatus.DECISION_PENDING_REFUND;
        if (!release && !refund) return;
        String target = release ? "Released" : "Refunded";
        if (target.equals(chain.status())
                && dispute.getResolutionHash().equalsIgnoreCase(chain.resolutionHash())) {
            Job job = jobs.findById(contract.getJobId()).orElse(null);
            if (job == null) return;
            dispute.setStatus(release ? DisputeStatus.RESOLVED_RELEASE : DisputeStatus.RESOLVED_REFUND);
            dispute.setResolvedAt(Instant.now());
            milestone.setStatus(release ? MilestoneStatus.RELEASED : MilestoneStatus.REFUNDED);
            contract.setStatus(release ? ContractStatus.COMPLETED : ContractStatus.CANCELLED);
            job.setStatus(release ? JobStatus.COMPLETED : JobStatus.CANCELLED);
            record.setLastChainStatus(release ? "RELEASED_RECONCILED" : "REFUNDED_RECONCILED");
            record.setSettlementRetryPending(false);
            if (release && record.getReleasedAt() == null) record.setReleasedAt(Instant.now());
            notifications.notify(contract.getClientUserId(), NotificationType.DISPUTE_DECIDED,
                    "Tranh chấp escrow đã quyết toán", "Giao dịch Solana đã được xác nhận.", job.getId());
            notifications.notify(contract.getFreelancerId(), NotificationType.DISPUTE_DECIDED,
                    "Tranh chấp escrow đã quyết toán", "Giao dịch Solana đã được xác nhận.", job.getId());
            return;
        }
        if (!"Disputed".equals(chain.status())) return;
        if (record.getResolutionSignature() != null) {
            var status = solana.getTransactionStatus(record.getResolutionSignature());
            if (!status.hasError() && (status.isFound()
                    || record.getResolutionSubmittedAt() == null
                    || Instant.now().isBefore(record.getResolutionSubmittedAt().plusSeconds(15 * 60)))) return;
            record.setSettlementRetryPending(true);
            record.setResolutionSignature(null);
        }
        try {
            var submitted = solana.sendEscrowAction(record.getMilestoneId().toString(),
                    release ? "resolve-release" : "resolve-refund",
                    Map.of("actor", record.getArbiterWallet(), "hash", dispute.getResolutionHash(),
                            "mode", "send"));
            record.setResolutionSignature(submitted.getSignature());
            record.setResolutionSubmittedAt(Instant.now());
            record.setSettlementRetryPending(false);
            if (release) record.setReleaseSignature(submitted.getSignature());
            if (release) record.setReleaseSubmittedAt(Instant.now());
        } catch (RuntimeException exception) {
            record.setSettlementRetryPending(true);
        }
    }

    private void finalizeRelease(EscrowContract record, WorkContract contract,
            Milestone milestone, SolanaEscrowResult chain) {
        if (contract.getStatus() == ContractStatus.COMPLETED
                && milestone.getStatus() == MilestoneStatus.RELEASED) {
            record.setLastChainStatus("RELEASED_RECONCILED");
            record.setSettlementRetryPending(false);
            if (record.getReleasedAt() == null) record.setReleasedAt(Instant.now());
            return;
        }
        if (contract.getStatus() != ContractStatus.UNDER_REVIEW
                || (milestone.getStatus() != MilestoneStatus.SUBMITTED
                    && milestone.getStatus() != MilestoneStatus.RELEASE_PENDING)) return;
        JobSubmission latest = submissions.findFirstByContractIdOrderByVersionDesc(contract.getId()).orElse(null);
        if (latest == null || (latest.getStatus() != JobSubmissionStatus.SUBMITTED
                    && latest.getStatus() != JobSubmissionStatus.APPROVED)
                || !Objects.equals(latest.getPayloadHash(), chain.submissionHash())
                || chain.submissionCount() != latest.getVersion()) return;
        Job job = jobs.findById(contract.getJobId()).orElse(null);
        if (job == null || job.getStatus() != JobStatus.SUBMITTED_FOR_REVIEW) return;
        if (latest.getStatus() == JobSubmissionStatus.SUBMITTED) {
            latest.setStatus(JobSubmissionStatus.APPROVED);
            latest.setReviewedAt(LocalDateTime.now());
            latest.setReviewedAutomatically(chain.reviewDueAt() != null
                    && !Instant.now().isBefore(Instant.ofEpochSecond(Long.parseLong(chain.reviewDueAt()))));
        }
        milestone.setStatus(MilestoneStatus.RELEASED);
        contract.setStatus(ContractStatus.COMPLETED);
        job.setStatus(JobStatus.COMPLETED);
        record.setLastChainStatus("RELEASED_RECONCILED");
        record.setSettlementRetryPending(false);
        if (record.getReleasedAt() == null) record.setReleasedAt(Instant.now());
        notifications.notify(contract.getFreelancerId(), NotificationType.RELEASE_CONFIRMED,
                "Escrow đã giải ngân on-chain", "Milestone đã được giải ngân từ vault Solana.", job.getId());
        notifications.notify(contract.getClientUserId(), NotificationType.REVIEW_AUTO_APPROVED,
                "Escrow đã giải ngân on-chain", "Milestone đã được giải ngân sau khi chain xác nhận.", job.getId());
    }
}
