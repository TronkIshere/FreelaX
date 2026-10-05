package com.marketplace.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.dto.request.submission.CreateContractSubmissionRequest;
import com.marketplace.backend.dto.request.submission.ReviewSubmissionRequest;
import com.marketplace.backend.dto.response.submission.ContractSubmissionResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractSubmissionService {
    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final JobRepository jobs;
    private final JobSubmissionRepository submissions;
    private final SubmissionEvidenceRepository evidence;
    private final DeliverableRequirementRepository deliverableRequirements;
    private final AcceptanceCriterionRepository criteria;
    private final FundingTransactionRepository funding;
    private final ContractDisputeRepository disputes;
    private final DisputeAuditRepository disputeAudit;
    private final NotificationService notifications;
    private final ObjectMapper objectMapper;

    @Transactional
    public ContractSubmissionResponse submit(UUID freelancerId, UUID contractId, String key,
                                             CreateContractSubmissionRequest request) {
        if (!StringUtils.hasText(key) || key.length() > 100 || request == null
                || !StringUtils.hasText(request.getSummary())) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
        String idempotencyKey = key.trim();
        List<CreateContractSubmissionRequest.DeliverableInput> deliverables =
                request.getDeliverables() == null ? List.of() : request.getDeliverables();
        List<CreateContractSubmissionRequest.CriterionInput> acceptance =
                request.getAcceptanceEvidence() == null ? List.of() : request.getAcceptanceEvidence();
        if (deliverables.isEmpty() && acceptance.isEmpty()) {
            throw new ApplicationException(ErrorCode.SUBMISSION_EVIDENCE_INVALID, "cần ít nhất một bằng chứng");
        }
        String payloadHash = hash(contractId + ":" + json(request));
        WorkContract contract = participantContract(freelancerId, contractId);
        if (!contract.getFreelancerId().equals(freelancerId)) {
            throw new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND);
        }
        Milestone milestone = milestones.findWithLockById(milestoneId(contractId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND));
        JobSubmission prior = submissions.findByFreelancerIdAndIdempotencyKey(freelancerId, idempotencyKey).orElse(null);
        if (prior != null) {
            if (!payloadHash.equals(prior.getPayloadHash())) {
                throw new ApplicationException(ErrorCode.SUBMISSION_KEY_CONFLICT);
            }
            return response(prior);
        }
        if (!funding.existsByMilestoneIdAndStatusIn(milestone.getId(), EnumSet.of(FundingStatus.SUCCEEDED))) {
            throw new ApplicationException(ErrorCode.SUBMISSION_NOT_FUNDED);
        }
        if ((milestone.getStatus() != MilestoneStatus.FUNDED && milestone.getStatus() != MilestoneStatus.IN_PROGRESS)
                || (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.REVISION)) {
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        }
        Job job = jobs.findById(contract.getJobId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND));
        if (job.getStatus() != JobStatus.IN_PROGRESS && job.getStatus() != JobStatus.REVISION_REQUESTED) {
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        }
        validateEvidence(contractId, deliverables, acceptance);

        int nextVersion = submissions.findFirstByJobIdOrderByVersionDesc(job.getId())
                .map(previous -> previous.getVersion() + 1).orElse(1);
        Instant now = Instant.now();
        JobSubmission submission = new JobSubmission();
        submission.setJobId(job.getId());
        submission.setContractId(contractId);
        submission.setMilestoneId(milestone.getId());
        submission.setFreelancerId(freelancerId);
        submission.setIdempotencyKey(idempotencyKey);
        submission.setPayloadHash(payloadHash);
        submission.setVersion(nextVersion);
        submission.setSummary(request.getSummary().trim());
        submission.setStatus(JobSubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(now);
        submission.setSubmittedLate(contract.getDeliveryDueAt() != null && now.isAfter(contract.getDeliveryDueAt()));
        submission.setReviewDueAt(now.plus(contract.getReviewWindowHours(), ChronoUnit.HOURS));
        submissions.saveAndFlush(submission);

        List<SubmissionEvidence> rows = new ArrayList<>();
        for (var item : deliverables) {
            rows.add(evidenceRow(submission.getId(), item.getRequirementId(), SubmissionEvidence.Kind.DELIVERABLE,
                    item.getDescription(), item.getUrl()));
        }
        for (var item : acceptance) {
            rows.add(evidenceRow(submission.getId(), item.getCriterionId(),
                    SubmissionEvidence.Kind.ACCEPTANCE_CRITERION, item.getNote(), item.getUrl()));
        }
        evidence.saveAll(rows);
        milestone.setStatus(MilestoneStatus.SUBMITTED);
        contract.setStatus(ContractStatus.UNDER_REVIEW);
        job.setStatus(JobStatus.SUBMITTED_FOR_REVIEW);
        notifications.notify(contract.getClientUserId(), NotificationType.WORK_SUBMITTED,
                "Freelancer đã bàn giao", "Bản bàn giao #" + nextVersion + " đang chờ bạn duyệt.", job.getId());
        return response(submission);
    }

    @Transactional(readOnly = true)
    public List<ContractSubmissionResponse> list(UUID actorId, UUID contractId) {
        participantContract(actorId, contractId);
        return submissions.findByContractIdOrderByVersionAsc(contractId).stream().map(this::response).toList();
    }

    @Transactional
    public ContractSubmissionResponse decide(UUID clientId, UUID contractId, UUID submissionId,
                                             ReviewSubmissionRequest request) {
        if (request == null || request.getDecision() == null) throw new ApplicationException(ErrorCode.INVALID_DATA);
        WorkContract contract = participantContract(clientId, contractId);
        if (!contract.getClientUserId().equals(clientId)) {
            throw new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND);
        }
        Milestone milestone = milestones.findWithLockById(milestoneId(contractId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND));
        JobSubmission latest = submissions.findFirstByContractIdOrderByVersionDesc(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND));
        if (!latest.getId().equals(submissionId)) throw new ApplicationException(ErrorCode.SUBMISSION_STALE);
        JobSubmission submission = submissions.findWithLockById(submissionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND));
        if (submission.getStatus() != JobSubmissionStatus.SUBMITTED
                || milestone.getStatus() != MilestoneStatus.SUBMITTED
                || contract.getStatus() != ContractStatus.UNDER_REVIEW) {
            throw new ApplicationException(ErrorCode.SUBMISSION_STALE);
        }
        Job job = jobs.findById(contract.getJobId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND));
        submission.setReviewedAt(LocalDateTime.now());
        if (request.getDecision() == ReviewSubmissionRequest.Decision.APPROVE) {
            submission.setStatus(JobSubmissionStatus.APPROVED);
            submission.setReviewerFeedback(StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null);
            milestone.setStatus(MilestoneStatus.RELEASE_PENDING);
            notifications.notify(contract.getFreelancerId(), NotificationType.WORK_APPROVED,
                    "Bàn giao đã được duyệt", "Client đã duyệt bản bàn giao #" + submission.getVersion() + ".", job.getId());
        } else if (request.getDecision() == ReviewSubmissionRequest.Decision.REQUEST_REVISION) {
            if (contract.getRevisionsUsed() >= contract.getMaxRevisions()) {
                throw new ApplicationException(ErrorCode.SUBMISSION_REVISION_LIMIT);
            }
            if (!StringUtils.hasText(request.getFeedback())) {
                throw new ApplicationException(ErrorCode.SUBMISSION_EVIDENCE_INVALID, "thiếu phản hồi chỉnh sửa");
            }
            List<UUID> criterionIds = request.getCriterionIds() == null ? List.of() : request.getCriterionIds();
            List<UUID> deliverableIds = request.getDeliverableIds() == null ? List.of() : request.getDeliverableIds();
            if (criterionIds.isEmpty() && deliverableIds.isEmpty()) {
                throw new ApplicationException(ErrorCode.SUBMISSION_EVIDENCE_INVALID, "chưa chọn tiêu chí hoặc sản phẩm liên quan");
            }
            validateReferences(contractId, criterionIds, deliverableIds);
            submission.setStatus(JobSubmissionStatus.REVISION_REQUESTED);
            submission.setReviewerFeedback(request.getFeedback().trim());
            submission.setReviewCriterionIdsJson(json(criterionIds));
            submission.setReviewDeliverableIdsJson(json(deliverableIds));
            contract.setRevisionsUsed(contract.getRevisionsUsed() + 1);
            contract.setStatus(ContractStatus.REVISION);
            milestone.setStatus(MilestoneStatus.IN_PROGRESS);
            job.setStatus(JobStatus.REVISION_REQUESTED);
            notifications.notify(contract.getFreelancerId(), NotificationType.REVISION_REQUESTED,
                    "Client yêu cầu chỉnh sửa", "Bản bàn giao #" + submission.getVersion() + " cần chỉnh sửa.", job.getId());
        } else if (request.getDecision() == ReviewSubmissionRequest.Decision.OPEN_DISPUTE) {
            if (!StringUtils.hasText(request.getReasonCode()) || !StringUtils.hasText(request.getDescription())) {
                throw new ApplicationException(ErrorCode.DISPUTE_REASON_REQUIRED);
            }
            if (disputes.existsByContractIdAndStatusIn(contractId,
                    EnumSet.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW))) {
                throw new ApplicationException(ErrorCode.DISPUTE_ALREADY_OPEN);
            }
            ContractDispute dispute = new ContractDispute();
            dispute.setContractId(contractId);
            dispute.setJobId(job.getId());
            dispute.setMilestoneId(milestone.getId());
            dispute.setSubmissionId(submissionId);
            dispute.setOpenedBy(clientId);
            dispute.setReasonCode(request.getReasonCode().trim());
            dispute.setDescription(request.getDescription().trim());
            dispute.setOpenedAt(Instant.now());
            dispute.setStatus(DisputeStatus.OPEN);
            disputes.save(dispute);
            DisputeAudit opened = new DisputeAudit();
            opened.setDisputeId(dispute.getId());
            opened.setActorId(clientId);
            opened.setAction("OPENED");
            opened.setAfterStatus(DisputeStatus.OPEN.name());
            opened.setReason(dispute.getDescription());
            opened.setRequestId(UUID.randomUUID().toString());
            disputeAudit.save(opened);
            submission.setStatus(JobSubmissionStatus.DISPUTED);
            contract.setStatus(ContractStatus.DISPUTED);
            milestone.setStatus(MilestoneStatus.DISPUTED);
            notifications.notify(contract.getFreelancerId(), NotificationType.DISPUTE_OPENED,
                    "Client mở tranh chấp", "Bản bàn giao #" + submission.getVersion() + " đang được tranh chấp.", job.getId());
            submissions.save(submission);
            return response(submission, dispute.getId());
        } else {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
        submissions.save(submission);
        return response(submission);
    }

    /** Called in its own transaction for each candidate; the milestone lock serializes all review decisions. */
    @Transactional
    public AutoReviewOutcome autoReview(UUID submissionId, Instant now) {
        UUID milestoneId = submissions.findReviewMilestoneId(submissionId).orElse(null);
        if (milestoneId == null) return AutoReviewOutcome.SKIPPED;
        Milestone milestone = milestones.findWithLockById(milestoneId).orElse(null);
        if (milestone == null) return AutoReviewOutcome.SKIPPED;
        JobSubmission submission = submissions.findWithLockById(submissionId).orElse(null);
        if (submission == null || !milestoneId.equals(submission.getMilestoneId())
                || !milestone.getContractId().equals(submission.getContractId())) return AutoReviewOutcome.SKIPPED;
        JobSubmission latest = submissions.findFirstByContractIdOrderByVersionDesc(submission.getContractId()).orElse(null);
        if (latest == null || !latest.getId().equals(submissionId)) return AutoReviewOutcome.SKIPPED;
        WorkContract contract = contracts.findById(submission.getContractId()).orElse(null);
        if (submission == null || contract == null || submission.getStatus() != JobSubmissionStatus.SUBMITTED
                || milestone.getStatus() != MilestoneStatus.SUBMITTED
                || contract.getStatus() != ContractStatus.UNDER_REVIEW
                || submission.getReviewDueAt() == null || now.isBefore(submission.getReviewDueAt())
                || disputes.existsByContractIdAndStatusIn(submission.getContractId(),
                    EnumSet.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW))) {
            return AutoReviewOutcome.SKIPPED;
        }
        Job job = jobs.findById(contract.getJobId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND));
        if (milestone.getAmount().compareTo(new BigDecimal("500.00")) > 0) {
            Instant graceDueAt = submission.getReviewDueAt().plus(24, ChronoUnit.HOURS);
            if (now.isBefore(graceDueAt)) {
                if (submission.getReviewGraceDueAt() == null) {
                    submission.setReviewGraceDueAt(graceDueAt);
                    submissions.save(submission);
                    notifications.notify(contract.getClientUserId(), NotificationType.REVIEW_GRACE_STARTED,
                            "Thời gian duyệt được gia hạn", "Bạn có thêm 24 giờ để duyệt bản bàn giao #"
                                    + submission.getVersion() + ".", job.getId());
                    return AutoReviewOutcome.GRACE_STARTED;
                }
                return AutoReviewOutcome.SKIPPED;
            }
            if (submission.getReviewGraceDueAt() == null) submission.setReviewGraceDueAt(graceDueAt);
        }
        submission.setStatus(JobSubmissionStatus.APPROVED);
        submission.setReviewedAutomatically(true);
        submission.setReviewedAt(LocalDateTime.now());
        milestone.setStatus(MilestoneStatus.RELEASE_PENDING);
        submissions.save(submission);
        notifications.notify(contract.getClientUserId(), NotificationType.REVIEW_AUTO_APPROVED,
                "Bàn giao được tự duyệt", "Bản bàn giao #" + submission.getVersion()
                        + " đã được tự duyệt sau thời hạn.", job.getId());
        notifications.notify(contract.getFreelancerId(), NotificationType.REVIEW_AUTO_APPROVED,
                "Bàn giao được tự duyệt", "Bản bàn giao #" + submission.getVersion()
                        + " đã được tự duyệt sau thời hạn.", job.getId());
        return AutoReviewOutcome.APPROVED;
    }

    public enum AutoReviewOutcome { SKIPPED, GRACE_STARTED, APPROVED }

    private WorkContract participantContract(UUID actorId, UUID contractId) {
        return contracts.findById(contractId)
                .filter(c -> c.getClientUserId().equals(actorId) || c.getFreelancerId().equals(actorId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND));
    }

    private UUID milestoneId(UUID contractId) {
        return milestones.findByContractId(contractId)
                .map(Milestone::getId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND));
    }

    private void validateEvidence(UUID contractId,
                                  List<CreateContractSubmissionRequest.DeliverableInput> deliverables,
                                  List<CreateContractSubmissionRequest.CriterionInput> acceptance) {
        Set<UUID> allowedDeliverables = new HashSet<>(deliverableRequirements
                .findByContractIdOrderByOrderAsc(contractId).stream().map(DeliverableRequirement::getId).toList());
        Set<UUID> allowedCriteria = new HashSet<>(criteria
                .findByContractIdOrderByOrderAsc(contractId).stream().map(AcceptanceCriterion::getId).toList());
        Set<UUID> seenDeliverables = new HashSet<>();
        Set<UUID> seenCriteria = new HashSet<>();
        for (var item : deliverables) {
            if (item == null || item.getRequirementId() == null
                    || !allowedDeliverables.contains(item.getRequirementId())
                    || !seenDeliverables.add(item.getRequirementId()) || !https(item.getUrl())) {
                throw new ApplicationException(ErrorCode.SUBMISSION_EVIDENCE_INVALID, "deliverable ID/URL");
            }
        }
        for (var item : acceptance) {
            if (item == null || item.getCriterionId() == null
                    || !allowedCriteria.contains(item.getCriterionId())
                    || !seenCriteria.add(item.getCriterionId())
                    || (!StringUtils.hasText(item.getNote()) && !StringUtils.hasText(item.getUrl()))
                    || (StringUtils.hasText(item.getUrl()) && !https(item.getUrl()))) {
                throw new ApplicationException(ErrorCode.SUBMISSION_EVIDENCE_INVALID, "criterion ID/note/URL");
            }
        }
    }

    private void validateReferences(UUID contractId, List<UUID> criterionIds, List<UUID> deliverableIds) {
        Set<UUID> validCriteria = new HashSet<>(criteria.findByContractIdOrderByOrderAsc(contractId)
                .stream().map(AcceptanceCriterion::getId).toList());
        Set<UUID> validDeliverables = new HashSet<>(deliverableRequirements.findByContractIdOrderByOrderAsc(contractId)
                .stream().map(DeliverableRequirement::getId).toList());
        if (criterionIds.stream().anyMatch(id -> !validCriteria.contains(id))
                || deliverableIds.stream().anyMatch(id -> !validDeliverables.contains(id))
                || new HashSet<>(criterionIds).size() != criterionIds.size()
                || new HashSet<>(deliverableIds).size() != deliverableIds.size()) {
            throw new ApplicationException(ErrorCode.SUBMISSION_EVIDENCE_INVALID, "ID tiêu chí/sản phẩm liên quan");
        }
    }

    private boolean https(String value) {
        if (!StringUtils.hasText(value) || value.length() > 2048) return false;
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasText(uri.getHost())
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private SubmissionEvidence evidenceRow(UUID submissionId, UUID requirementId, SubmissionEvidence.Kind kind,
                                           String description, String url) {
        SubmissionEvidence row = new SubmissionEvidence();
        row.setSubmissionId(submissionId);
        row.setRequirementId(requirementId);
        row.setKind(kind);
        row.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        row.setUrl(StringUtils.hasText(url) ? url.trim() : null);
        return row;
    }

    private ContractSubmissionResponse response(JobSubmission submission) {
        return response(submission, disputes.findFirstByContractIdAndStatusInOrderByCreatedAtDesc(
                submission.getContractId(), EnumSet.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW))
                .filter(dispute -> dispute.getSubmissionId().equals(submission.getId()))
                .map(ContractDispute::getId).orElse(null));
    }

    private ContractSubmissionResponse response(JobSubmission submission, UUID disputeId) {
        List<SubmissionEvidence> rows = evidence.findBySubmissionIdOrderByCreatedAtAsc(submission.getId());
        return ContractSubmissionResponse.builder()
                .id(submission.getId()).contractId(submission.getContractId())
                .milestoneId(submission.getMilestoneId()).freelancerId(submission.getFreelancerId())
                .version(submission.getVersion()).status(submission.getStatus().name())
                .summary(submission.getSummary()).submittedAt(submission.getSubmittedAt())
                .submittedLate(submission.isSubmittedLate()).reviewDueAt(submission.getReviewDueAt())
                .reviewGraceDueAt(submission.getReviewGraceDueAt())
                .reviewedAutomatically(submission.isReviewedAutomatically()).disputeId(disputeId)
                .reviewerFeedback(submission.getReviewerFeedback())
                .reviewCriterionIds(readIds(submission.getReviewCriterionIdsJson()))
                .reviewDeliverableIds(readIds(submission.getReviewDeliverableIdsJson()))
                .deliverables(rows.stream().filter(row -> row.getKind() == SubmissionEvidence.Kind.DELIVERABLE)
                        .map(row -> new ContractSubmissionResponse.Evidence(row.getRequirementId(),
                                row.getDescription(), row.getUrl())).toList())
                .acceptanceEvidence(rows.stream().filter(row -> row.getKind() == SubmissionEvidence.Kind.ACCEPTANCE_CRITERION)
                        .map(row -> new ContractSubmissionResponse.Evidence(row.getRequirementId(),
                                row.getDescription(), row.getUrl())).toList())
                .build();
    }

    private List<UUID> readIds(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<List<UUID>>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid persisted review references", ex);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize submission", ex);
        }
    }

    private String hash(String payload) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
