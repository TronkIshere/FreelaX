package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.job.RequestRevisionRequest;
import com.marketplace.backend.dto.request.job.CreateJobRequest;
import com.marketplace.backend.dto.request.job.SubmitWorkRequest;
import com.marketplace.backend.dto.request.job.UpdateJobRequest;
import com.marketplace.backend.dto.response.bofa.CheckoutOrderResult;
import com.marketplace.backend.dto.response.job.JobSubmissionResponse;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.JobStatus;
import com.marketplace.backend.entity.JobSubmission;
import com.marketplace.backend.entity.JobSubmissionStatus;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.UserType;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobApplicationRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.JobSubmissionRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.repository.AcceptanceCriterionRepository;
import com.marketplace.backend.repository.DeliverableRequirementRepository;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import com.marketplace.backend.service.NotificationService;
import com.marketplace.backend.service.PayoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JobWorkflowServiceImplTest {

    private JobRepository jobRepository;
    private UserRepository userRepository;
    private JobSubmissionRepository submissionRepository;
    private PaymentBackendClient paymentBackendClient;
    private PayoutService payoutService;
    private JobServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jobRepository = mock(JobRepository.class);
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        submissionRepository = mock(JobSubmissionRepository.class);
        paymentBackendClient = mock(PaymentBackendClient.class);
        MisaBackendClient misaBackendClient = mock(MisaBackendClient.class);
        NotificationService notificationService = mock(NotificationService.class);
        payoutService = mock(PayoutService.class);
        FreelancerPayoutRecordRepository payoutRecordRepository = mock(FreelancerPayoutRecordRepository.class);
        WorkContractRepository contractRepository = mock(WorkContractRepository.class);
        MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
        AcceptanceCriterionRepository criterionRepository = mock(AcceptanceCriterionRepository.class);
        DeliverableRequirementRepository deliverableRepository = mock(DeliverableRequirementRepository.class);
        service = new JobServiceImpl(userRepository, jobRepository, applicationRepository, submissionRepository,
                paymentBackendClient, misaBackendClient, notificationService, payoutService, payoutRecordRepository,
                contractRepository, milestoneRepository, criterionRepository, deliverableRepository);
        when(submissionRepository.save(any(JobSubmission.class))).thenAnswer(invocation -> {
            JobSubmission submission = invocation.getArgument(0);
            if (submission.getId() == null) {
                submission.setId(UUID.randomUUID());
                submission.setCreatedAt(LocalDateTime.now());
            }
            return submission;
        });
    }

    @Test
    void assignedFreelancerSubmitsWorkForClientReview() {
        Job job = job(JobStatus.IN_PROGRESS);
        stubUsers(job);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(submissionRepository.countByJobId(job.getId())).thenReturn(0L);
        SubmitWorkRequest request = new SubmitWorkRequest();
        request.setSummary("  Finished API and tests  ");
        request.setDeliverableUrl(" https://example.test/delivery ");

        JobSubmissionResponse response = service.submitWork(job.getFreelancerId(), job.getId(), request);

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUBMITTED_FOR_REVIEW);
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getSummary()).isEqualTo("Finished API and tests");
        assertThat(response.getDeliverableUrl()).isEqualTo("https://example.test/delivery");
        assertThat(response.getStatus()).isEqualTo("SUBMITTED");
        verify(jobRepository).save(job);
    }

    @Test
    void clientRequestsRevisionAndFreelancerCanSubmitAgain() {
        Job job = job(JobStatus.SUBMITTED_FOR_REVIEW);
        stubUsers(job);
        JobSubmission first = submission(job, 1, JobSubmissionStatus.SUBMITTED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(submissionRepository.findFirstByJobIdOrderByVersionDesc(job.getId()))
                .thenReturn(Optional.of(first));
        RequestRevisionRequest revision = new RequestRevisionRequest();
        revision.setFeedback("Please add the missing test evidence");

        service.requestRevision(job.getClientUserId(), job.getId(), revision);

        assertThat(job.getStatus()).isEqualTo(JobStatus.REVISION_REQUESTED);
        assertThat(first.getStatus()).isEqualTo(JobSubmissionStatus.REVISION_REQUESTED);
        assertThat(first.getReviewerFeedback()).isEqualTo("Please add the missing test evidence");

        when(submissionRepository.countByJobId(job.getId())).thenReturn(1L);
        SubmitWorkRequest resubmission = new SubmitWorkRequest();
        resubmission.setSummary("Added the evidence");
        JobSubmissionResponse response = service.submitWork(job.getFreelancerId(), job.getId(), resubmission);

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUBMITTED_FOR_REVIEW);
        assertThat(response.getVersion()).isEqualTo(2);
    }

    @Test
    void clientCannotApproveBeforeWorkIsSubmitted() {
        Job job = job(JobStatus.IN_PROGRESS);
        stubUsers(job);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.approve(job.getClientUserId(), job.getId()))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_JOB_STATUS);
        verify(paymentBackendClient, never()).captureCheckoutOrder(any());
        verify(payoutService, never()).settle(any());
    }

    @Test
    void approvalAfterSubmissionCapturesPaymentAndStartsPayout() {
        Job job = job(JobStatus.SUBMITTED_FOR_REVIEW);
        stubUsers(job);
        JobSubmission submission = submission(job, 1, JobSubmissionStatus.SUBMITTED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(submissionRepository.findFirstByJobIdOrderByVersionDesc(job.getId()))
                .thenReturn(Optional.of(submission));
        CheckoutOrderResult captured = new CheckoutOrderResult();
        captured.setStatus("CAPTURED");
        when(paymentBackendClient.captureCheckoutOrder(job.getCheckoutOrderId())).thenReturn(captured);

        service.approve(job.getClientUserId(), job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(submission.getStatus()).isEqualTo(JobSubmissionStatus.APPROVED);
        assertThat(submission.getReviewedAt()).isNotNull();
        verify(payoutService).settle(job);
    }

    @Test
    void titleAndDescriptionCanChangeWithoutChangingCheckoutBudget() {
        Job job = job(JobStatus.OPEN);
        stubUsers(job);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        UpdateJobRequest request = new UpdateJobRequest();
        request.setTitle("Updated title");
        request.setDescription("Updated description");
        request.setBudgetUsd(new BigDecimal("500.00"));

        service.update(job.getClientUserId(), job.getId(), request);

        assertThat(job.getTitle()).isEqualTo("Updated title");
        assertThat(job.getDescription()).isEqualTo("Updated description");
        assertThat(job.getBudgetUsd()).isEqualByComparingTo("500");
        verify(jobRepository).save(job);
    }

    @Test
    void jobCreationDoesNotChargeBeforeAssignmentAndFunding() {
        UUID clientId = UUID.randomUUID();
        User client = new User();
        client.setId(clientId);
        client.setUserType(UserType.CLIENT);
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            if (job.getId() == null) job.setId(UUID.randomUUID());
            return job;
        });
        CreateJobRequest request = new CreateJobRequest();
        request.setTitle("Demo job");
        request.setDescription("Work");
        request.setBudgetUsd(new BigDecimal("500"));
        service.create(clientId, request);

        verifyNoInteractions(paymentBackendClient);
        verify(jobRepository).save(any(Job.class));
    }

    @Test
    void omittedBudgetIsAllowedForOpenJob() {
        Job job = job(JobStatus.OPEN);
        stubUsers(job);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        UpdateJobRequest request = new UpdateJobRequest();
        request.setTitle("New title");

        service.update(job.getClientUserId(), job.getId(), request);

        assertThat(job.getBudgetUsd()).isEqualByComparingTo("500");
        assertThat(job.getTitle()).isEqualTo("New title");
    }

    @Test
    void changedBudgetIsRejectedAfterCheckoutCreation() {
        Job job = job(JobStatus.OPEN);
        stubUsers(job);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        UpdateJobRequest request = new UpdateJobRequest();
        request.setBudgetUsd(new BigDecimal("600"));

        assertThatThrownBy(() -> service.update(job.getClientUserId(), job.getId(), request))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getErrorCode())
                .isEqualTo(ErrorCode.JOB_BUDGET_IMMUTABLE);
        assertThat(job.getBudgetUsd()).isEqualByComparingTo("500");
        verify(jobRepository, never()).save(job);
    }

    private Job job(JobStatus status) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setClientUserId(UUID.randomUUID());
        job.setFreelancerId(UUID.randomUUID());
        job.setCheckoutOrderId(UUID.randomUUID());
        job.setTitle("Build API");
        job.setBudgetUsd(new BigDecimal("500"));
        job.setStatus(status);
        return job;
    }

    private JobSubmission submission(Job job, int version, JobSubmissionStatus status) {
        JobSubmission submission = new JobSubmission();
        submission.setId(UUID.randomUUID());
        submission.setJobId(job.getId());
        submission.setFreelancerId(job.getFreelancerId());
        submission.setVersion(version);
        submission.setSummary("Delivery");
        submission.setStatus(status);
        return submission;
    }

    private void stubUsers(Job job) {
        User client = new User();
        client.setId(job.getClientUserId());
        client.setUserType(UserType.CLIENT);
        User freelancer = new User();
        freelancer.setId(job.getFreelancerId());
        freelancer.setUserType(UserType.FREELANCER);
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(userRepository.findById(freelancer.getId())).thenReturn(Optional.of(freelancer));
    }
}
