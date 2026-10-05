package com.marketplace.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.dto.request.submission.CreateContractSubmissionRequest;
import com.marketplace.backend.dto.request.submission.ReviewSubmissionRequest;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContractSubmissionServiceTest {
    private WorkContractRepository contracts;
    private MilestoneRepository milestones;
    private JobRepository jobs;
    private JobSubmissionRepository submissions;
    private SubmissionEvidenceRepository evidence;
    private DeliverableRequirementRepository requirements;
    private AcceptanceCriterionRepository criteria;
    private FundingTransactionRepository funding;
    private ContractDisputeRepository disputes;
    private ContractSubmissionService service;
    private WorkContract contract;
    private Milestone milestone;
    private Job job;
    private DeliverableRequirement deliverable;
    private AcceptanceCriterion criterion;
    private JobSubmission saved;
    private List<SubmissionEvidence> savedEvidence;

    @BeforeEach
    void setUp() {
        contracts = mock(WorkContractRepository.class);
        milestones = mock(MilestoneRepository.class);
        jobs = mock(JobRepository.class);
        submissions = mock(JobSubmissionRepository.class);
        evidence = mock(SubmissionEvidenceRepository.class);
        requirements = mock(DeliverableRequirementRepository.class);
        criteria = mock(AcceptanceCriterionRepository.class);
        funding = mock(FundingTransactionRepository.class);
        disputes = mock(ContractDisputeRepository.class);
        savedEvidence = new ArrayList<>();
        service = new ContractSubmissionService(contracts, milestones, jobs, submissions, evidence,
                requirements, criteria, funding, disputes, mock(NotificationService.class), new ObjectMapper());

        contract = new WorkContract();
        contract.setId(UUID.randomUUID());
        contract.setJobId(UUID.randomUUID());
        contract.setClientUserId(UUID.randomUUID());
        contract.setFreelancerId(UUID.randomUUID());
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setDeliveryDueAt(Instant.now().minusSeconds(60));
        contract.setReviewWindowHours(72);
        contract.setMaxRevisions(2);
        milestone = new Milestone();
        milestone.setId(UUID.randomUUID());
        milestone.setContractId(contract.getId());
        milestone.setStatus(MilestoneStatus.FUNDED);
        milestone.setAmount(new BigDecimal("500.00"));
        job = new Job();
        job.setId(contract.getJobId());
        job.setStatus(JobStatus.IN_PROGRESS);
        deliverable = new DeliverableRequirement();
        deliverable.setId(UUID.randomUUID());
        deliverable.setContractId(contract.getId());
        criterion = new AcceptanceCriterion();
        criterion.setId(UUID.randomUUID());
        criterion.setContractId(contract.getId());

        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(milestones.findByContractId(contract.getId())).thenReturn(Optional.of(milestone));
        when(milestones.findWithLockById(milestone.getId())).thenReturn(Optional.of(milestone));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(requirements.findByContractIdOrderByOrderAsc(contract.getId())).thenReturn(List.of(deliverable));
        when(criteria.findByContractIdOrderByOrderAsc(contract.getId())).thenReturn(List.of(criterion));
        when(funding.existsByMilestoneIdAndStatusIn(eq(milestone.getId()), any())).thenReturn(true);
        when(submissions.saveAndFlush(any(JobSubmission.class))).thenAnswer(invocation -> {
            saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(submissions.findWithLockById(any())).thenAnswer(invocation -> Optional.ofNullable(saved));
        when(submissions.findReviewMilestoneId(any())).thenAnswer(invocation ->
                saved == null ? Optional.empty() : Optional.of(milestone.getId()));
        when(submissions.findFirstByContractIdOrderByVersionDesc(contract.getId()))
                .thenAnswer(invocation -> Optional.ofNullable(saved));
        when(evidence.saveAll(any())).thenAnswer(invocation -> {
            savedEvidence.addAll(invocation.getArgument(0));
            return savedEvidence;
        });
        when(evidence.findBySubmissionIdOrderByCreatedAtAsc(any())).thenAnswer(invocation -> savedEvidence);
    }

    @Test
    void fundedLateSubmissionStoresEvidenceAndStartsReviewWindow() {
        var result = service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request());

        assertThat(result.getStatus()).isEqualTo("SUBMITTED");
        assertThat(result.isSubmittedLate()).isTrue();
        assertThat(result.getReviewDueAt()).isEqualTo(result.getSubmittedAt().plusSeconds(72 * 3600));
        assertThat(result.getDeliverables()).hasSize(1);
        assertThat(result.getAcceptanceEvidence()).hasSize(1);
        assertThat(savedEvidence).hasSize(2);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.SUBMITTED);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.UNDER_REVIEW);
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUBMITTED_FOR_REVIEW);
    }

    @Test
    void sameKeyReplaysSameSubmissionAndChangedPayloadConflicts() {
        var first = service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request());
        when(submissions.findByFreelancerIdAndIdempotencyKey(contract.getFreelancerId(), "submit-1"))
                .thenReturn(Optional.of(saved));

        var replay = service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request());
        assertThat(replay.getId()).isEqualTo(first.getId());
        verify(submissions, times(1)).saveAndFlush(any());

        var changed = request();
        changed.setSummary("Different work");
        assertThatThrownBy(() -> service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", changed))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_KEY_CONFLICT);
    }

    @Test
    void noFundingOrWrongActorCannotSubmit() {
        when(funding.existsByMilestoneIdAndStatusIn(eq(milestone.getId()), any())).thenReturn(false);
        assertThatThrownBy(() -> service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request()))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_NOT_FUNDED);
        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), contract.getId(), "submit-2", request()))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND);
        verify(submissions, never()).saveAndFlush(any());
    }

    @Test
    void evidenceMustReferenceThisContractAndUseHttps() {
        var request = request();
        request.getDeliverables().get(0).setRequirementId(UUID.randomUUID());
        assertThatThrownBy(() -> service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_EVIDENCE_INVALID);
        request.getDeliverables().get(0).setRequirementId(deliverable.getId());
        request.getDeliverables().get(0).setUrl("http://example.test/work");
        assertThatThrownBy(() -> service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_EVIDENCE_INVALID);
        verify(submissions, never()).saveAndFlush(any());
    }

    @Test
    void staleReviewLosesAndOnlyOneDecisionCanApply() {
        service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request());
        when(submissions.findFirstByContractIdOrderByVersionDesc(contract.getId())).thenReturn(Optional.of(saved));
        ReviewSubmissionRequest decision = new ReviewSubmissionRequest();
        decision.setDecision(ReviewSubmissionRequest.Decision.APPROVE);

        assertThatThrownBy(() -> service.decide(contract.getClientUserId(), contract.getId(), UUID.randomUUID(), decision))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_STALE);
        var approved = service.decide(contract.getClientUserId(), contract.getId(), saved.getId(), decision);
        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.RELEASE_PENDING);
        assertThatThrownBy(() -> service.decide(contract.getClientUserId(), contract.getId(), saved.getId(), decision))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_STALE);
    }

    @Test
    void revisionCountsOnceAndResubmissionGetsNextVersion() {
        service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request());
        JobSubmission first = saved;
        when(submissions.findFirstByContractIdOrderByVersionDesc(contract.getId())).thenReturn(Optional.of(first));
        ReviewSubmissionRequest decision = new ReviewSubmissionRequest();
        decision.setDecision(ReviewSubmissionRequest.Decision.REQUEST_REVISION);
        decision.setFeedback("Missing mobile proof");
        decision.setCriterionIds(List.of(criterion.getId()));

        var reviewed = service.decide(contract.getClientUserId(), contract.getId(), first.getId(), decision);
        assertThat(reviewed.getStatus()).isEqualTo("REVISION_REQUESTED");
        assertThat(reviewed.getReviewCriterionIds()).containsExactly(criterion.getId());
        assertThat(contract.getRevisionsUsed()).isEqualTo(1);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.IN_PROGRESS);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.REVISION);

        when(submissions.findFirstByJobIdOrderByVersionDesc(job.getId())).thenReturn(Optional.of(first));
        var second = service.submit(contract.getFreelancerId(), contract.getId(), "submit-2", request());
        assertThat(second.getVersion()).isEqualTo(2);
        assertThat(contract.getRevisionsUsed()).isEqualTo(1);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.UNDER_REVIEW);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.SUBMITTED);
    }

    @Test
    void revisionLimitCannotBeBypassed() {
        service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request());
        contract.setRevisionsUsed(2);
        when(submissions.findFirstByContractIdOrderByVersionDesc(contract.getId())).thenReturn(Optional.of(saved));
        ReviewSubmissionRequest decision = new ReviewSubmissionRequest();
        decision.setDecision(ReviewSubmissionRequest.Decision.REQUEST_REVISION);
        decision.setFeedback("One more change");
        decision.setCriterionIds(List.of(criterion.getId()));

        assertThatThrownBy(() -> service.decide(contract.getClientUserId(), contract.getId(), saved.getId(), decision))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_REVISION_LIMIT);
        assertThat(saved.getStatus()).isEqualTo(JobSubmissionStatus.SUBMITTED);
    }

    @Test
    void thirdRevisionIsRejectedButApprovalRemainsAvailable() {
        service.submit(contract.getFreelancerId(), contract.getId(), "submit-3", request());
        contract.setRevisionsUsed(2);
        ReviewSubmissionRequest revision = new ReviewSubmissionRequest();
        revision.setDecision(ReviewSubmissionRequest.Decision.REQUEST_REVISION);
        revision.setFeedback("Another change");
        revision.setDeliverableIds(List.of(deliverable.getId()));
        assertThatThrownBy(() -> service.decide(contract.getClientUserId(), contract.getId(), saved.getId(), revision))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_REVISION_LIMIT);

        ReviewSubmissionRequest approve = new ReviewSubmissionRequest();
        approve.setDecision(ReviewSubmissionRequest.Decision.APPROVE);
        assertThat(service.decide(contract.getClientUserId(), contract.getId(), saved.getId(), approve).getStatus())
                .isEqualTo("APPROVED");
    }

    @Test
    void disputeIsAvailableAfterRevisionLimitAndPreventsAutoApproval() {
        service.submit(contract.getFreelancerId(), contract.getId(), "submit-3", request());
        contract.setRevisionsUsed(2);
        ReviewSubmissionRequest disputeRequest = new ReviewSubmissionRequest();
        disputeRequest.setDecision(ReviewSubmissionRequest.Decision.OPEN_DISPUTE);
        disputeRequest.setReasonCode("QUALITY");
        disputeRequest.setDescription("Acceptance criteria were not met");
        ContractDispute dispute = new ContractDispute();
        dispute.setId(UUID.randomUUID());
        dispute.setSubmissionId(saved.getId());
        when(disputes.save(any(ContractDispute.class))).thenAnswer(invocation -> {
            ContractDispute value = invocation.getArgument(0);
            value.setId(dispute.getId());
            return value;
        });
        var result = service.decide(contract.getClientUserId(), contract.getId(), saved.getId(), disputeRequest);
        assertThat(result.getStatus()).isEqualTo("DISPUTED");
        assertThat(result.getDisputeId()).isEqualTo(dispute.getId());
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.DISPUTED);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.DISPUTED);
        assertThat(service.autoReview(saved.getId(), saved.getReviewDueAt().plusSeconds(1)))
                .isEqualTo(ContractSubmissionService.AutoReviewOutcome.SKIPPED);
    }

    @Test
    void atFiveHundredAutoApprovesAtDueTimeAndManualReviewThenLoses() {
        service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request());
        Instant due = saved.getReviewDueAt();
        assertThat(service.autoReview(saved.getId(), due.minusSeconds(1)))
                .isEqualTo(ContractSubmissionService.AutoReviewOutcome.SKIPPED);
        assertThat(service.autoReview(saved.getId(), due))
                .isEqualTo(ContractSubmissionService.AutoReviewOutcome.APPROVED);
        assertThat(saved.isReviewedAutomatically()).isTrue();
        assertThat(saved.getReviewGraceDueAt()).isNull();
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.RELEASE_PENDING);
        assertThat(service.autoReview(saved.getId(), due.plusSeconds(1)))
                .isEqualTo(ContractSubmissionService.AutoReviewOutcome.SKIPPED);
        ReviewSubmissionRequest approve = new ReviewSubmissionRequest();
        approve.setDecision(ReviewSubmissionRequest.Decision.APPROVE);
        assertThatThrownBy(() -> service.decide(contract.getClientUserId(), contract.getId(), saved.getId(), approve))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_STALE);
    }

    @Test
    void aboveFiveHundredGrantsOneGracePeriodThenAutoApproves() {
        milestone.setAmount(new BigDecimal("500.01"));
        service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request());
        Instant due = saved.getReviewDueAt();
        assertThat(service.autoReview(saved.getId(), due))
                .isEqualTo(ContractSubmissionService.AutoReviewOutcome.GRACE_STARTED);
        assertThat(saved.getReviewGraceDueAt()).isEqualTo(due.plusSeconds(24 * 3600));
        assertThat(service.autoReview(saved.getId(), due.plusSeconds(3600)))
                .isEqualTo(ContractSubmissionService.AutoReviewOutcome.SKIPPED);
        assertThat(service.autoReview(saved.getId(), due.plusSeconds(24 * 3600)))
                .isEqualTo(ContractSubmissionService.AutoReviewOutcome.APPROVED);
        assertThat(saved.isReviewedAutomatically()).isTrue();
    }

    @Test
    void manualApprovalDuringGracePreventsWorkerDecision() {
        milestone.setAmount(new BigDecimal("750.00"));
        service.submit(contract.getFreelancerId(), contract.getId(), "submit-1", request());
        Instant due = saved.getReviewDueAt();
        service.autoReview(saved.getId(), due);
        ReviewSubmissionRequest approve = new ReviewSubmissionRequest();
        approve.setDecision(ReviewSubmissionRequest.Decision.APPROVE);
        service.decide(contract.getClientUserId(), contract.getId(), saved.getId(), approve);
        assertThat(service.autoReview(saved.getId(), due.plusSeconds(24 * 3600)))
                .isEqualTo(ContractSubmissionService.AutoReviewOutcome.SKIPPED);
        assertThat(saved.isReviewedAutomatically()).isFalse();
    }

    private CreateContractSubmissionRequest request() {
        CreateContractSubmissionRequest input = new CreateContractSubmissionRequest();
        input.setSummary("  Finished work  ");
        var deliverableInput = new CreateContractSubmissionRequest.DeliverableInput();
        deliverableInput.setRequirementId(deliverable.getId());
        deliverableInput.setUrl("https://example.test/work");
        deliverableInput.setDescription("Source code");
        var criterionInput = new CreateContractSubmissionRequest.CriterionInput();
        criterionInput.setCriterionId(criterion.getId());
        criterionInput.setNote("All breakpoints checked");
        input.setDeliverables(List.of(deliverableInput));
        input.setAcceptanceEvidence(List.of(criterionInput));
        return input;
    }
}
