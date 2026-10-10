package com.marketplace.backend.service;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.dispute.*;
import com.marketplace.backend.dto.response.dispute.*;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.*;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ContractDisputeService {
    private final ContractDisputeRepository disputes;
    private final DisputeEvidenceRepository evidence;
    private final DisputeEvidenceBatchRepository evidenceBatches;
    private final DisputeAuditRepository audit;
    private final MilestoneRepository milestones;
    private final WorkContractRepository contracts;
    private final JobRepository jobs;
    private final JobSubmissionRepository submissions;
    private final SubmissionEvidenceRepository submissionEvidence;
    private final DeliverableRequirementRepository requirements;
    private final AcceptanceCriterionRepository criteria;
    private final FundingTransactionRepository funding;
    private final EscrowContractRepository escrowContracts;
    private final SolanaCprClient solana;
    private final PaymentBackendClient payment;
    private final ContractSettlementRepository settlements;
    private final ContractCancellationRepository cancellations;
    private final NotificationService notifications;
    private final BusinessDayClock businessDays;
    private final TransactionTemplate transactions;
    private final Environment environment;

    @Value("${partner-mock.e2e-negotiation-seconds:0}")
    private int e2eNegotiationSeconds;

    @Transactional
    public DisputeResponse open(UUID actor, UUID contractId, OpenDisputeRequest request) {
        Milestone m = milestones.findWithLockByContractId(contractId).orElseThrow(this::notFound);
        WorkContract c = participant(actor, contractId);
        validateReason(request);
        List<DisputeEvidenceInput> items = request.evidence() == null ? List.of() : request.evidence();
        validateEvidence(items);
        Job j = jobs.findById(c.getJobId()).orElseThrow(this::notFound);
        if (disputes.findByContractId(contractId).isPresent()) throw new ApplicationException(ErrorCode.DISPUTE_ALREADY_OPEN);
        if (settlements.findByMilestoneId(m.getId()).isPresent()
                || cancellations.findByContractId(contractId).filter(x ->
                x.getStatus() == CancellationStatus.REFUND_PENDING || x.getStatus() == CancellationStatus.CANCELLED).isPresent())
            throw ineligible();
        if (escrowContracts.existsByContractId(contractId)) {
            return openEscrow(actor, c, m, j, request, items);
        }
        FundingTransaction f = funding.findFirstByMilestoneIdOrderByCreatedAtDesc(m.getId()).orElseThrow(this::ineligible);
        if (f.getStatus() != FundingStatus.SUCCEEDED || f.getCheckoutOrderId() == null
                || !Objects.equals(f.getContractId(), contractId) || !Objects.equals(f.getMilestoneId(), m.getId())
                || !Objects.equals(f.getClientUserId(), c.getClientUserId())
                || !Objects.equals(c.getClientUserId(), j.getClientUserId())
                || !Objects.equals(c.getFreelancerId(), j.getFreelancerId())
                || Objects.equals(c.getClientUserId(), c.getFreelancerId())
                || !Objects.equals(f.getCheckoutOrderId(), j.getCheckoutOrderId())
                || !same(f.getAmount(), m.getAmount()) || !same(c.getBudgetUsd(), m.getAmount())
                || !same(j.getBudgetUsd(), m.getAmount()) || !Objects.equals(f.getCurrency(), m.getCurrency())
                || !"USD".equals(m.getCurrency())) throw ineligible();
        JobSubmission latest = submissions.findFirstByContractIdOrderByVersionDesc(contractId).orElse(null);
        boolean beforeSubmission = c.getStatus() == ContractStatus.ACTIVE
                && (m.getStatus() == MilestoneStatus.FUNDED || m.getStatus() == MilestoneStatus.IN_PROGRESS)
                && j.getStatus() == JobStatus.IN_PROGRESS && latest == null;
        boolean review = c.getStatus() == ContractStatus.UNDER_REVIEW && m.getStatus() == MilestoneStatus.SUBMITTED
                && j.getStatus() == JobStatus.SUBMITTED_FOR_REVIEW && latest != null
                && latest.getStatus() == JobSubmissionStatus.SUBMITTED;
        boolean revision = c.getStatus() == ContractStatus.REVISION && m.getStatus() == MilestoneStatus.IN_PROGRESS
                && j.getStatus() == JobStatus.REVISION_REQUESTED && latest != null
                && latest.getStatus() == JobSubmissionStatus.REVISION_REQUESTED;
        if (!beforeSubmission && !review && !revision) throw ineligible();
        if (PartnerEscrowFundingService.RAIL.equals(c.getPaymentRail())) {
            var frozen = payment.freezePartnerEscrow(m.getId());
            if (!"FROZEN".equals(frozen.status())) throw ineligible();
        }
        ContractDispute d = new ContractDispute();
        d.setContractId(contractId); d.setMilestoneId(m.getId()); d.setJobId(j.getId());
        d.setSubmissionId(latest == null ? null : latest.getId()); d.setOpenedBy(actor);
        d.setReasonCode(request.reasonCode().trim()); d.setDescription(request.description().trim());
        d.setOpenedAt(Instant.now()); d.setStatus(DisputeStatus.OPEN);
        if (PartnerEscrowFundingService.RAIL.equals(c.getPaymentRail()))
            d.setNegotiationUntil(e2eNegotiationSeconds > 0
                    && environment.acceptsProfiles(Profiles.of("dev"))
                    && !environment.acceptsProfiles(Profiles.of("prod"))
                    ? d.getOpenedAt().plusSeconds(e2eNegotiationSeconds)
                    : businessDays.add(d.getOpenedAt(), 3));
        disputes.saveAndFlush(d);
        if (review) latest.setStatus(JobSubmissionStatus.DISPUTED);
        c.setStatus(ContractStatus.DISPUTED); m.setStatus(MilestoneStatus.DISPUTED);
        saveEvidence(d.getId(), actor, items);
        log(d, actor, "OPENED", null, d.getStatus().name(), d.getDescription(), null);
        notifications.notify(actor.equals(c.getClientUserId()) ? c.getFreelancerId() : c.getClientUserId(),
                NotificationType.DISPUTE_OPENED, "Tranh chấp đã mở",
                "Đối tác đã mở tranh chấp; các thao tác tài chính thường đang tạm khóa.", j.getId());
        return response(d);
    }

    @Transactional(readOnly = true)
    public DisputeResponse get(UUID actor, UUID contractId) {
        participant(actor, contractId);
        return disputes.findByContractId(contractId).map(this::response).orElse(null);
    }

    /** Each participant must approve the same all-or-nothing outcome during the negotiation window. */
    public DisputeResponse negotiate(UUID actor, UUID contractId, UUID disputeId, NegotiateDisputeRequest request) {
        if (request == null || request.outcome() == null || !StringUtils.hasText(request.reason())
                || request.reason().trim().length() > 2000) throw new ApplicationException(ErrorCode.INVALID_DATA);
        return committed(() -> {
            Milestone m = milestones.findWithLockByContractId(contractId).orElseThrow(this::notFound);
            WorkContract c = participant(actor, contractId);
            ContractDispute d = disputes.findById(disputeId)
                    .filter(row -> contractId.equals(row.getContractId())).orElseThrow(this::notFound);
            if (!PartnerEscrowFundingService.RAIL.equals(c.getPaymentRail())
                    || d.getStatus() != DisputeStatus.OPEN || m.getStatus() != MilestoneStatus.DISPUTED
                    || d.getNegotiationUntil() == null || !Instant.now().isBefore(d.getNegotiationUntil()))
                throw new ApplicationException(ErrorCode.DISPUTE_CONFLICT);
            String reason = request.reason().trim();
            if (d.getNegotiationProposedBy() == null) {
                d.setNegotiationProposedBy(actor);
                d.setNegotiationOutcome(request.outcome().name());
                d.setNegotiationReason(reason);
                d.setNegotiationProposedAt(Instant.now());
                disputes.saveAndFlush(d);
                log(d, actor, "NEGOTIATION_PROPOSED", DisputeStatus.OPEN.name(), DisputeStatus.OPEN.name(), reason, null);
                notifications.notify(actor.equals(c.getClientUserId()) ? c.getFreelancerId() : c.getClientUserId(),
                        NotificationType.DISPUTE_OPENED, "Đề xuất dàn xếp tranh chấp",
                        "Đối tác đã đề xuất giải quyết; xem kết quả và lý do trước khi đồng ý.", c.getJobId());
                return response(d);
            }
            if (actor.equals(d.getNegotiationProposedBy())) {
                if (d.getNegotiationOutcome().equals(request.outcome().name())
                        && d.getNegotiationReason().equals(reason)) return response(d);
                throw new ApplicationException(ErrorCode.DISPUTE_CONFLICT);
            }
            if (!d.getNegotiationOutcome().equals(request.outcome().name())
                    || !d.getNegotiationReason().equals(reason)) throw new ApplicationException(ErrorCode.DISPUTE_CONFLICT);
            Job j = jobs.findById(c.getJobId()).orElseThrow(this::notFound);
            FundingTransaction f = funding.findFirstByMilestoneIdOrderByCreatedAtDesc(m.getId()).orElseThrow(this::ineligible);
            if (c.getStatus() != ContractStatus.DISPUTED || f.getStatus() != FundingStatus.SUCCEEDED
                    || !Objects.equals(f.getContractId(), contractId) || !Objects.equals(f.getMilestoneId(), m.getId())
                    || !Objects.equals(f.getClientUserId(), c.getClientUserId())
                    || !Objects.equals(f.getCheckoutOrderId(), j.getCheckoutOrderId())
                    || !same(f.getAmount(), m.getAmount()) || !same(c.getBudgetUsd(), m.getAmount())
                    || settlements.findByMilestoneId(m.getId()).isPresent()) throw ineligible();
            d.setResolvedBy(actor); d.setDecisionAt(Instant.now()); d.setResolutionReason(reason);
            d.setResolutionKey("negotiated-" + disputeId);
            d.setResolutionHash(hash(request.outcome().name() + ":" + reason));
            if (request.outcome() == ResolveDisputeRequest.Outcome.RELEASE_TO_FREELANCER) {
                d.setStatus(DisputeStatus.DECISION_PENDING_RELEASE);
                m.setStatus(MilestoneStatus.RELEASE_PENDING);
            } else {
                d.setStatus(DisputeStatus.DECISION_PENDING_REFUND);
                d.setFundingTransactionId(f.getId()); d.setCheckoutOrderId(f.getCheckoutOrderId());
                d.setAmount(m.getAmount()); d.setCurrency(m.getCurrency());
                d.setRefundKey("marketplace-refund-" + m.getId());
                d.setRefundStatus(SettlementMoneyStatus.PENDING);
                d.setRetryable(true); d.setNextAttemptAt(Instant.EPOCH);
                m.setStatus(MilestoneStatus.REFUND_PENDING);
            }
            disputes.saveAndFlush(d);
            log(d, actor, "NEGOTIATION_ACCEPTED", DisputeStatus.OPEN.name(), d.getStatus().name(), reason, null);
            notifications.notify(c.getClientUserId(), NotificationType.DISPUTE_DECIDED,
                    "Hai bên đã thống nhất", "Thỏa thuận đã lưu; đang đối soát lệnh chi hoặc hoàn tiền mock.", c.getJobId());
            notifications.notify(c.getFreelancerId(), NotificationType.DISPUTE_DECIDED,
                    "Hai bên đã thống nhất", "Thỏa thuận đã lưu; đang đối soát lệnh chi hoặc hoàn tiền mock.", c.getJobId());
            return response(d);
        });
    }

    @Transactional
    public DisputeResponse addEvidence(UUID actor, UUID contractId, UUID disputeId,
                                       String key, List<DisputeEvidenceInput> items) {
        milestones.findWithLockByContractId(contractId).orElseThrow(this::notFound);
        participant(actor, contractId);
        validateEvidence(items);
        if (items == null || items.isEmpty() || !StringUtils.hasText(key) || key.length() > 100
                || !key.equals(key.trim())) throw new ApplicationException(ErrorCode.DISPUTE_EVIDENCE_INVALID);
        ContractDispute d = disputes.findById(disputeId)
                .filter(x -> x.getContractId().equals(contractId)).orElseThrow(this::notFound);
        String payloadHash = hash(items.stream()
                .map(x -> part(x.kind().name()) + part(x.text()) + part(x.url()) + part(x.sha256())).reduce("", String::concat));
        DisputeEvidenceBatch prior = evidenceBatches.findByDisputeIdAndActorIdAndIdempotencyKey(disputeId, actor, key).orElse(null);
        if (prior != null) {
            if (!payloadHash.equals(prior.getPayloadHash())) throw new ApplicationException(ErrorCode.DISPUTE_CONFLICT);
            return response(d);
        }
        if (d.getStatus() != DisputeStatus.OPEN) throw new ApplicationException(ErrorCode.DISPUTE_CONFLICT);
        DisputeEvidenceBatch batch = new DisputeEvidenceBatch();
        batch.setDisputeId(disputeId); batch.setActorId(actor);
        batch.setIdempotencyKey(key); batch.setPayloadHash(payloadHash);
        evidenceBatches.saveAndFlush(batch);
        saveEvidence(d.getId(), actor, items);
        log(d, actor, "EVIDENCE_ADDED", d.getStatus().name(), d.getStatus().name(), null, key);
        return response(d);
    }

    @Transactional(readOnly = true)
    public Page<DisputeResponse> list(DisputeStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApplicationException(ErrorCode.INVALID_DATA);
        return disputes.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(page, size)).map(this::response);
    }

    @Transactional(readOnly = true)
    public AdminDisputeDetail detail(UUID id) {
        ContractDispute d = disputes.findById(id).orElseThrow(this::notFound);
        WorkContract c = contracts.findById(d.getContractId()).orElseThrow(this::notFound);
        Milestone m = milestones.findByContractId(c.getId()).orElseThrow(this::notFound);
        List<AdminDisputeDetail.Submission> history = submissions.findByContractIdOrderByVersionAsc(c.getId()).stream()
                .map(s -> new AdminDisputeDetail.Submission(s.getId(), s.getVersion(), s.getStatus().name(),
                        s.getSummary(), s.getReviewerFeedback(), s.getSubmittedAt(),
                        s.getReviewedAt() == null ? null : s.getReviewedAt().toInstant(ZoneOffset.UTC),
                        submissionEvidence.findBySubmissionIdOrderByCreatedAtAsc(s.getId()).stream()
                                .map(e -> new AdminDisputeDetail.SubmissionItem(e.getRequirementId(), e.getKind().name(),
                                        e.getDescription(), e.getUrl())).toList())).toList();
        return new AdminDisputeDetail(response(d),
                new AdminDisputeDetail.ContractSnapshot(c.getId(), c.getClientUserId(), c.getFreelancerId(),
                        c.getTitleSnapshot(), c.getDescriptionSnapshot(), c.getBudgetUsd(), m.getCurrency(),
                        c.getDeliveryDueAt(), c.getMaxRevisions(), c.getRevisionsUsed()),
                requirements.findByContractIdOrderByOrderAsc(c.getId()).stream()
                        .map(x -> new AdminDisputeDetail.Requirement(x.getId(), x.getTitle(), x.getDescription(), x.isRequired())).toList(),
                criteria.findByContractIdOrderByOrderAsc(c.getId()).stream()
                        .map(x -> new AdminDisputeDetail.Criterion(x.getId(), x.getDescription(), x.isRequired())).toList(),
                history, funding.findFirstByMilestoneIdOrderByCreatedAtDesc(m.getId()).map(FundingTransaction::getStatus).orElse(null),
                audit.findByDisputeIdOrderByCreatedAtAsc(id).stream()
                        .map(a -> new AdminDisputeDetail.Audit(a.getActorId(), a.getAction(), a.getBeforeStatus(),
                                a.getAfterStatus(), a.getReason(), a.getRequestId(),
                                a.getCreatedAt() == null ? null : a.getCreatedAt().toInstant(ZoneOffset.UTC))).toList());
    }

    public DisputeResponse claim(UUID adminId, UUID id) {
        UUID contractId = disputes.findById(id).map(ContractDispute::getContractId).orElseThrow(this::notFound);
        return committed(() -> claimLocked(adminId, id, contractId));
    }

    private DisputeResponse claimLocked(UUID adminId, UUID id, UUID contractId) {
        milestones.findWithLockByContractId(contractId).orElseThrow(this::notFound);
        ContractDispute d = disputes.findById(id).orElseThrow(this::notFound);
        if (!contractId.equals(d.getContractId())) throw notFound();
        forbidParticipantAdmin(adminId, d);
        if (d.getStatus() == DisputeStatus.UNDER_REVIEW && adminId.equals(d.getClaimedBy())) return response(d);
        if (d.getStatus() != DisputeStatus.OPEN) throw new ApplicationException(ErrorCode.DISPUTE_CONFLICT);
        if (d.getNegotiationUntil() != null && Instant.now().isBefore(d.getNegotiationUntil()))
            throw new ApplicationException(ErrorCode.DISPUTE_CONFLICT, "chưa hết thời hạn tự thương lượng");
        d.setClaimedBy(adminId); d.setClaimedAt(Instant.now()); d.setStatus(DisputeStatus.UNDER_REVIEW);
        if (d.getNegotiationUntil() != null) d.setModerationDueAt(businessDays.add(d.getClaimedAt(), 5));
        log(d, adminId, "CLAIMED", DisputeStatus.OPEN.name(), d.getStatus().name(), null, null);
        return response(d);
    }

    public DisputeResponse resolve(UUID adminId, UUID id, String key, ResolveDisputeRequest request) {
        if (request == null || request.outcome() == null || !StringUtils.hasText(request.reason())
                || request.reason().length() > 2000 || !StringUtils.hasText(key) || key.length() > 100
                || !key.equals(key.trim())) throw new ApplicationException(ErrorCode.INVALID_DATA);
        UUID contractId = disputes.findById(id).map(ContractDispute::getContractId).orElseThrow(this::notFound);
        return committed(() -> resolveLocked(adminId, id, contractId, key, request));
    }

    private DisputeResponse resolveLocked(UUID adminId, UUID id, UUID contractId, String key, ResolveDisputeRequest request) {
        Milestone m = milestones.findWithLockByContractId(contractId).orElseThrow(this::notFound);
        ContractDispute d = disputes.findById(id).orElseThrow(this::notFound);
        if (!contractId.equals(d.getContractId())) throw notFound();
        forbidParticipantAdmin(adminId, d);
        String hash = hash(request.outcome().name() + ":" + request.reason().trim());
        if (d.getResolutionKey() != null) {
            if (key.equals(d.getResolutionKey()) && hash.equals(d.getResolutionHash())
                    && adminId.equals(d.getResolvedBy())) return response(d);
            throw new ApplicationException(ErrorCode.DISPUTE_CONFLICT);
        }
        if (d.getStatus() != DisputeStatus.UNDER_REVIEW || !adminId.equals(d.getClaimedBy())
                || m.getStatus() != MilestoneStatus.DISPUTED) throw ineligible();
        WorkContract c = contracts.findById(d.getContractId()).orElseThrow(this::notFound);
        Job j = jobs.findById(c.getJobId()).orElseThrow(this::notFound);
        if (escrowContracts.existsByContractId(c.getId())) {
            return resolveEscrow(adminId, d, c, m, j, key, request, hash);
        }
        FundingTransaction f = funding.findFirstByMilestoneIdOrderByCreatedAtDesc(m.getId()).orElseThrow(this::ineligible);
        if (c.getStatus() != ContractStatus.DISPUTED || f.getStatus() != FundingStatus.SUCCEEDED
                || (j.getStatus() != JobStatus.IN_PROGRESS && j.getStatus() != JobStatus.REVISION_REQUESTED
                    && j.getStatus() != JobStatus.SUBMITTED_FOR_REVIEW)
                || f.getCheckoutOrderId() == null || !Objects.equals(f.getCheckoutOrderId(), j.getCheckoutOrderId())
                || !Objects.equals(f.getMilestoneId(), m.getId()) || !Objects.equals(f.getContractId(), c.getId())
                || !Objects.equals(f.getClientUserId(), c.getClientUserId())
                || !Objects.equals(c.getFreelancerId(), j.getFreelancerId())
                || !Objects.equals(c.getClientUserId(), j.getClientUserId())
                || !same(f.getAmount(), m.getAmount()) || !same(c.getBudgetUsd(), m.getAmount())
                || !same(j.getBudgetUsd(), m.getAmount()) || !"USD".equals(m.getCurrency())
                || !Objects.equals(f.getCurrency(), m.getCurrency())
                || settlements.findByMilestoneId(m.getId()).isPresent()
                || cancellations.findByContractId(c.getId()).filter(x ->
                    x.getStatus() == CancellationStatus.REFUND_PENDING || x.getStatus() == CancellationStatus.CANCELLED).isPresent())
            throw ineligible();
        d.setMilestoneId(m.getId()); d.setJobId(j.getId());
        d.setResolvedBy(adminId); d.setDecisionAt(Instant.now()); d.setResolutionReason(request.reason().trim());
        d.setResolutionKey(key); d.setResolutionHash(hash);
        if (request.outcome() == ResolveDisputeRequest.Outcome.RELEASE_TO_FREELANCER) {
            d.setStatus(DisputeStatus.DECISION_PENDING_RELEASE);
            m.setStatus(MilestoneStatus.RELEASE_PENDING);
        } else {
            d.setStatus(DisputeStatus.DECISION_PENDING_REFUND);
            d.setFundingTransactionId(f.getId()); d.setCheckoutOrderId(f.getCheckoutOrderId());
            d.setAmount(m.getAmount()); d.setCurrency(m.getCurrency());
            d.setRefundKey("marketplace-refund-" + m.getId());
            d.setRefundStatus(SettlementMoneyStatus.PENDING);
            d.setRetryable(true); d.setNextAttemptAt(Instant.EPOCH);
            m.setStatus(MilestoneStatus.REFUND_PENDING);
        }
        disputes.saveAndFlush(d);
        log(d, adminId, "DECIDED", DisputeStatus.UNDER_REVIEW.name(), d.getStatus().name(), d.getResolutionReason(), key);
        notifications.notify(c.getClientUserId(), NotificationType.DISPUTE_DECIDED, "Admin đã quyết định tranh chấp",
                "Quyết định đã được lưu; kết quả tiền mô phỏng đang chờ xác nhận.", j.getId());
        notifications.notify(c.getFreelancerId(), NotificationType.DISPUTE_DECIDED, "Admin đã quyết định tranh chấp",
                "Quyết định đã được lưu; kết quả tiền mô phỏng đang chờ xác nhận.", j.getId());
        return response(d);
    }

    private void forbidParticipantAdmin(UUID admin, ContractDispute d) {
        WorkContract c = contracts.findById(d.getContractId()).orElseThrow(this::notFound);
        if (admin.equals(c.getClientUserId()) || admin.equals(c.getFreelancerId()))
            throw new ApplicationException(ErrorCode.DISPUTE_CONFLICT);
    }

    private DisputeResponse openEscrow(UUID actor, WorkContract c, Milestone m, Job j,
            OpenDisputeRequest request, List<DisputeEvidenceInput> items) {
        JobSubmission latest = submissions.findFirstByContractIdOrderByVersionDesc(c.getId()).orElse(null);
        String expectedHash = escrowDisputeHash(c.getId(), latest == null ? null : latest.getId(),
                request.reasonCode(), request.description());
        SolanaEscrowResult chain = solana.findEscrow(m.getId().toString()).orElseThrow(this::ineligible);
        EscrowContract escrow = escrowContracts.findByContractId(c.getId()).orElseThrow(this::ineligible);
        if (!"Disputed".equals(chain.status())
                || !expectedHash.equalsIgnoreCase(chain.disputeHash())
                || !Objects.equals(chain.address(), escrow.getEscrowAddress())
                || !Objects.equals(chain.client(), escrow.getClientWallet())
                || !Objects.equals(chain.freelancer(), escrow.getFreelancerWallet())
                || !Objects.equals(chain.arbiter(), escrow.getArbiterWallet())) throw ineligible();
        boolean beforeSubmission = c.getStatus() == ContractStatus.ACTIVE
                && (m.getStatus() == MilestoneStatus.FUNDED || m.getStatus() == MilestoneStatus.IN_PROGRESS)
                && j.getStatus() == JobStatus.IN_PROGRESS && latest == null;
        boolean review = c.getStatus() == ContractStatus.UNDER_REVIEW
                && m.getStatus() == MilestoneStatus.SUBMITTED && latest != null
                && latest.getStatus() == JobSubmissionStatus.SUBMITTED;
        boolean revision = c.getStatus() == ContractStatus.REVISION
                && m.getStatus() == MilestoneStatus.IN_PROGRESS && latest != null
                && latest.getStatus() == JobSubmissionStatus.REVISION_REQUESTED;
        if (!beforeSubmission && !review && !revision) throw ineligible();
        ContractDispute d = new ContractDispute();
        d.setContractId(c.getId()); d.setMilestoneId(m.getId()); d.setJobId(j.getId());
        d.setSubmissionId(latest == null ? null : latest.getId()); d.setOpenedBy(actor);
        d.setReasonCode(request.reasonCode().trim()); d.setDescription(request.description().trim());
        d.setOpenedAt(Instant.now()); d.setStatus(DisputeStatus.OPEN);
        disputes.saveAndFlush(d);
        if (review) latest.setStatus(JobSubmissionStatus.DISPUTED);
        c.setStatus(ContractStatus.DISPUTED); m.setStatus(MilestoneStatus.DISPUTED);
        saveEvidence(d.getId(), actor, items);
        log(d, actor, "OPENED", null, d.getStatus().name(), d.getDescription(), null);
        notifications.notify(actor.equals(c.getClientUserId()) ? c.getFreelancerId() : c.getClientUserId(),
                NotificationType.DISPUTE_OPENED, "Tranh chấp escrow đã mở",
                "Escrow đã khóa on-chain để chờ Admin xử lý.", j.getId());
        return response(d);
    }

    private DisputeResponse resolveEscrow(UUID adminId, ContractDispute d, WorkContract c,
            Milestone m, Job j, String key, ResolveDisputeRequest request, String hash) {
        if (c.getStatus() != ContractStatus.DISPUTED || m.getStatus() != MilestoneStatus.DISPUTED) throw ineligible();
        EscrowContract record = escrowContracts.findByContractId(c.getId()).orElseThrow(this::ineligible);
        SolanaEscrowResult chain = solana.findEscrow(m.getId().toString()).orElseThrow(this::ineligible);
        boolean release = request.outcome() == ResolveDisputeRequest.Outcome.RELEASE_TO_FREELANCER;
        String expectedTerminal = release ? "Released" : "Refunded";
        if (!Objects.equals(chain.address(), record.getEscrowAddress())
                || !Objects.equals(chain.arbiter(), record.getArbiterWallet())
                || !"Disputed".equals(chain.status()) && (!expectedTerminal.equals(chain.status())
                || !hash.equalsIgnoreCase(chain.resolutionHash()))) throw ineligible();
        d.setResolvedBy(adminId); d.setDecisionAt(Instant.now());
        d.setResolutionReason(request.reason().trim()); d.setResolutionKey(key); d.setResolutionHash(hash);
        d.setStatus(release ? DisputeStatus.DECISION_PENDING_RELEASE : DisputeStatus.DECISION_PENDING_REFUND);
        d.setRetryable(false); // The simulated payment scheduler must never handle this escrow.
        m.setStatus(release ? MilestoneStatus.RELEASE_PENDING : MilestoneStatus.REFUND_PENDING);
        disputes.saveAndFlush(d);
        log(d, adminId, "DECIDED", DisputeStatus.UNDER_REVIEW.name(), d.getStatus().name(),
                d.getResolutionReason(), key);
        notifications.notify(c.getClientUserId(), NotificationType.DISPUTE_DECIDED,
                "Admin đã quyết định tranh chấp escrow", "Đang chờ xác nhận giao dịch Solana.", j.getId());
        notifications.notify(c.getFreelancerId(), NotificationType.DISPUTE_DECIDED,
                "Admin đã quyết định tranh chấp escrow", "Đang chờ xác nhận giao dịch Solana.", j.getId());
        return response(d);
    }

    public String escrowDisputeHash(UUID contractId, UUID submissionId,
            String reasonCode, String description) {
        return hash(contractId + ":" + (submissionId == null ? "" : submissionId) + ":"
                + reasonCode.trim() + ":" + description.trim());
    }

    private void validateReason(OpenDisputeRequest r) {
        if (r == null || !StringUtils.hasText(r.reasonCode()) || r.reasonCode().length() > 60
                || !StringUtils.hasText(r.description()) || r.description().length() > 2000)
            throw new ApplicationException(ErrorCode.DISPUTE_REASON_REQUIRED);
    }

    private void validateEvidence(List<DisputeEvidenceInput> items) {
        if (items == null || items.size() > 10) throw new ApplicationException(ErrorCode.DISPUTE_EVIDENCE_INVALID);
        for (DisputeEvidenceInput x : items) {
            if (x == null || x.kind() == null || (x.sha256() != null && !x.sha256().matches("[0-9a-fA-F]{64}")))
                throw new ApplicationException(ErrorCode.DISPUTE_EVIDENCE_INVALID);
            if (x.kind() == DisputeEvidence.Kind.TEXT) {
                if (!StringUtils.hasText(x.text()) || x.text().length() > 2000 || x.url() != null)
                    throw new ApplicationException(ErrorCode.DISPUTE_EVIDENCE_INVALID);
            } else {
                if (!https(x.url()) || (x.text() != null && x.text().length() > 2000))
                    throw new ApplicationException(ErrorCode.DISPUTE_EVIDENCE_INVALID);
            }
        }
    }

    private boolean https(String value) {
        if (value == null || value.length() > 2048) return false;
        try {
            URI u = URI.create(value);
            return "https".equalsIgnoreCase(u.getScheme()) && StringUtils.hasText(u.getHost())
                    && u.getUserInfo() == null;
        } catch (IllegalArgumentException ex) { return false; }
    }

    private void saveEvidence(UUID id, UUID actor, List<DisputeEvidenceInput> items) {
        for (DisputeEvidenceInput x : items) {
            DisputeEvidence e = new DisputeEvidence();
            e.setDisputeId(id); e.setActorId(actor); e.setKind(x.kind());
            e.setText(x.text() == null ? null : x.text().trim()); e.setUrl(x.url());
            e.setSha256(x.sha256() == null ? null : x.sha256().toLowerCase(Locale.ROOT));
            evidence.save(e);
        }
    }

    private void log(ContractDispute d, UUID actor, String action, String before, String after, String reason, String requestId) {
        DisputeAudit row = new DisputeAudit();
        row.setDisputeId(d.getId()); row.setActorId(actor); row.setAction(action);
        row.setBeforeStatus(before); row.setAfterStatus(after); row.setReason(reason);
        row.setRequestId(UUID.randomUUID().toString());
        row.setIdempotencyKey(requestId);
        audit.save(row);
    }

    private DisputeResponse response(ContractDispute d) {
        return DisputeResponse.from(d, evidence.findByDisputeIdOrderByCreatedAtAsc(d.getId()));
    }
    private WorkContract participant(UUID actor, UUID id) {
        return contracts.findById(id).filter(c -> actor.equals(c.getClientUserId()) || actor.equals(c.getFreelancerId()))
                .orElseThrow(this::notFound);
    }
    private ApplicationException notFound() { return new ApplicationException(ErrorCode.DISPUTE_NOT_FOUND); }
    private ApplicationException ineligible() { return new ApplicationException(ErrorCode.DISPUTE_INELIGIBLE); }
    private boolean same(BigDecimal a, BigDecimal b) { return a != null && b != null && a.signum() > 0 && a.compareTo(b) == 0; }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private String part(String value) { return value == null ? "-1:" : value.length() + ":" + value; }
    private <T> T committed(Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactions.getTransactionManager());
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> work.get());
    }
}
