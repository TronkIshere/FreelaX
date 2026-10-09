package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.job.CreateJobRequest;
import com.marketplace.backend.dto.request.job.AssignFreelancerRequest;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.job.DiscoverJobResponse;
import com.marketplace.backend.dto.response.job.MyApplicationResponse;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.JobApplication;
import com.marketplace.backend.entity.JobApplicationStatus;
import com.marketplace.backend.entity.JobStatus;
import com.marketplace.backend.entity.PaymentFlow;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.UserType;
import com.marketplace.backend.entity.ContractStatus;
import com.marketplace.backend.entity.MilestoneStatus;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.entity.Milestone;
import com.marketplace.backend.entity.AcceptanceCriterion;
import com.marketplace.backend.entity.DeliverableRequirement;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JobAccessAndDiscoveryServiceImplTest {

    private UserRepository userRepository;
    private JobRepository jobRepository;
    private JobApplicationRepository applicationRepository;
    private PaymentBackendClient paymentBackendClient;
    private NotificationService notificationService;
    private WorkContractRepository contractRepository;
    private MilestoneRepository milestoneRepository;
    private AcceptanceCriterionRepository criterionRepository;
    private DeliverableRequirementRepository deliverableRepository;
    private com.marketplace.backend.service.PaymentFlowService paymentFlowService;
    private JobServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jobRepository = mock(JobRepository.class);
        applicationRepository = mock(JobApplicationRepository.class);
        JobSubmissionRepository submissionRepository = mock(JobSubmissionRepository.class);
        paymentBackendClient = mock(PaymentBackendClient.class);
        MisaBackendClient misaBackendClient = mock(MisaBackendClient.class);
        notificationService = mock(NotificationService.class);
        PayoutService payoutService = mock(PayoutService.class);
        FreelancerPayoutRecordRepository payoutRecordRepository = mock(FreelancerPayoutRecordRepository.class);
        contractRepository = mock(WorkContractRepository.class);
        milestoneRepository = mock(MilestoneRepository.class);
        criterionRepository = mock(AcceptanceCriterionRepository.class);
        deliverableRepository = mock(DeliverableRequirementRepository.class);
        paymentFlowService = mock(com.marketplace.backend.service.PaymentFlowService.class);
        service = new JobServiceImpl(userRepository, jobRepository, applicationRepository, submissionRepository,
                paymentBackendClient, misaBackendClient, notificationService, payoutService, payoutRecordRepository,
                contractRepository, milestoneRepository, criterionRepository, deliverableRepository,
                paymentFlowService);
        when(contractRepository.save(any(WorkContract.class))).thenAnswer(invocation -> {
            WorkContract contract = invocation.getArgument(0);
            if (contract.getId() == null) contract.setId(UUID.randomUUID());
            return contract;
        });
    }

    @Test
    void freelancerCannotCreateJob() {
        User freelancer = user(UserType.FREELANCER);
        when(userRepository.findById(freelancer.getId())).thenReturn(Optional.of(freelancer));

        assertThatThrownBy(() -> service.create(freelancer.getId(), new CreateJobRequest()))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getErrorCode())
                .isEqualTo(ErrorCode.NOT_A_CLIENT);
        verifyNoInteractions(paymentBackendClient);
    }

    @Test
    void discoveryReturnsOpenJobsWithCurrentApplicationState() {
        User freelancer = user(UserType.FREELANCER);
        User client = user(UserType.CLIENT);
        client.setDisplayName("Client Demo");
        Job job = job(client.getId(), JobStatus.OPEN);
        JobApplication application = application(job.getId(), freelancer.getId(), JobApplicationStatus.PENDING);
        when(userRepository.findById(freelancer.getId())).thenReturn(Optional.of(freelancer));
        when(jobRepository.discover(eq(freelancer.getId()), eq("solana"), eq(new BigDecimal("50")),
                eq(new BigDecimal("200")), eq("ALL"), org.mockito.ArgumentMatchers.isNull(), eq(false), eq(List.of("")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job)));
        when(applicationRepository.findByFreelancerIdAndJobIdIn(freelancer.getId(), List.of(job.getId())))
                .thenReturn(List.of(application));
        when(userRepository.findAllById(any())).thenReturn(List.of(client));

        PageResponse<DiscoverJobResponse> result = service.discover(freelancer.getId(), 0, 10,
                " solana ", new BigDecimal("50"), new BigDecimal("200"), "NEWEST", "ALL");

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).isHasApplied()).isTrue();
        assertThat(result.getData().get(0).getApplicationStatus()).isEqualTo("PENDING");
        assertThat(result.getData().get(0).getClient().getDisplayName()).isEqualTo("Client Demo");
    }

    @Test
    void myApplicationsReturnsApplicationWithJobSummary() {
        User freelancer = user(UserType.FREELANCER);
        User client = user(UserType.CLIENT);
        client.setDisplayName("Client Demo");
        Job job = job(client.getId(), JobStatus.OPEN);
        JobApplication application = application(job.getId(), freelancer.getId(), JobApplicationStatus.PENDING);
        when(userRepository.findById(freelancer.getId())).thenReturn(Optional.of(freelancer));
        when(applicationRepository.findMine(eq(freelancer.getId()), eq(JobApplicationStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(application)));
        when(jobRepository.findAllById(List.of(job.getId()))).thenReturn(List.of(job));
        when(userRepository.findAllById(any())).thenReturn(List.of(client));

        PageResponse<MyApplicationResponse> result = service.listMyApplications(
                freelancer.getId(), 0, 10, JobApplicationStatus.PENDING);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getStatus()).isEqualTo("PENDING");
        assertThat(result.getData().get(0).getJob().getId()).isEqualTo(job.getId());
        assertThat(result.getData().get(0).getJob().getClientDisplayName()).isEqualTo("Client Demo");
    }

    @Test
    void cancellingJobClosesPendingApplicationsAndNotifiesFreelancers() {
        User client = user(UserType.CLIENT);
        Job job = job(client.getId(), JobStatus.OPEN);
        JobApplication first = application(job.getId(), UUID.randomUUID(), JobApplicationStatus.PENDING);
        JobApplication second = application(job.getId(), UUID.randomUUID(), JobApplicationStatus.PENDING);
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(jobRepository.findWithLockById(job.getId())).thenReturn(Optional.of(job));
        when(applicationRepository.findByJobIdAndStatus(job.getId(), JobApplicationStatus.PENDING))
                .thenReturn(List.of(first, second));

        service.cancel(client.getId(), job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(List.of(first.getStatus(), second.getStatus()))
                .containsOnly(JobApplicationStatus.CANCELLED);
        verify(applicationRepository).saveAll(List.of(first, second));
        verify(notificationService, times(2)).notify(any(), eq(com.marketplace.backend.entity.NotificationType.JOB_CANCELLED),
                anyString(), anyString(), eq(job.getId()));
    }

    @Test
    void assigningFreelancerCreatesPendingFundingContractAndMilestone() {
        User client = user(UserType.CLIENT);
        User freelancer = user(UserType.FREELANCER);
        Job job = job(client.getId(), JobStatus.OPEN);
        JobApplication application = application(job.getId(), freelancer.getId(), JobApplicationStatus.PENDING);
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(userRepository.findById(freelancer.getId())).thenReturn(Optional.of(freelancer));
        when(jobRepository.findWithLockById(job.getId())).thenReturn(Optional.of(job));
        when(applicationRepository.findByJobIdAndFreelancerId(job.getId(), freelancer.getId()))
                .thenReturn(Optional.of(application));
        when(applicationRepository.findByJobIdAndStatus(job.getId(), JobApplicationStatus.PENDING))
                .thenReturn(List.of());
        AcceptanceCriterion criterion = new AcceptanceCriterion();
        criterion.setDescription("API passes acceptance tests");
        DeliverableRequirement deliverable = new DeliverableRequirement();
        deliverable.setTitle("Source code");
        deliverable.setDescription("Repository and instructions");
        when(criterionRepository.findByJobIdOrderByOrderAsc(job.getId())).thenReturn(List.of(criterion));
        when(deliverableRepository.findByJobIdOrderByOrderAsc(job.getId())).thenReturn(List.of(deliverable));
        AssignFreelancerRequest request = new AssignFreelancerRequest();
        request.setFreelancerId(freelancer.getId());

        service.assignFreelancer(client.getId(), job.getId(), request);

        assertThat(job.getStatus()).isEqualTo(JobStatus.AWAITING_PAYMENT);
        verify(contractRepository).save(org.mockito.ArgumentMatchers.argThat(contract ->
                contract.getStatus() == ContractStatus.PENDING_FUNDING
                        && contract.getBudgetUsd().compareTo(job.getBudgetUsd()) == 0));
        verify(milestoneRepository).save(org.mockito.ArgumentMatchers.argThat(milestone ->
                milestone.getStatus() == MilestoneStatus.PENDING_FUNDING
                        && "USD".equals(milestone.getCurrency())));
    }

    @Test
    void unifiedTermsRequireTheSameAcceptedFingerprintFromBothParticipants() {
        User client = user(UserType.CLIENT);
        User freelancer = user(UserType.FREELANCER);
        Job job = job(client.getId(), JobStatus.OPEN);
        job.setPaymentFlowVersion(1);
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(userRepository.findById(freelancer.getId())).thenReturn(Optional.of(freelancer));
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.findWithLockById(job.getId())).thenReturn(Optional.of(job));
        when(applicationRepository.findByJobIdAndFreelancerId(job.getId(), freelancer.getId()))
                .thenReturn(Optional.empty());
        AcceptanceCriterion criterion = new AcceptanceCriterion();
        criterion.setDescription("Artifact accepted");
        DeliverableRequirement deliverable = new DeliverableRequirement();
        deliverable.setTitle("Artifact");
        deliverable.setDescription("Verifiable artifact");
        when(criterionRepository.findByJobIdOrderByOrderAsc(job.getId())).thenReturn(List.of(criterion));
        when(deliverableRepository.findByJobIdOrderByOrderAsc(job.getId())).thenReturn(List.of(deliverable));
        var preview = service.getByIdForParticipant(client.getId(), job.getId()).getLocalPaymentTerms();
        assertThat(preview.grossUsd()).isEqualByComparingTo("100.00");
        assertThat(preview.escrowUsdc()).isEqualByComparingTo("100.000000");
        assertThat(preview.platformFeeUsdc()).isEqualByComparingTo("3.000000");
        assertThat(preview.estimatedPayoutVnd()).isEqualByComparingTo("2182500");
        assertThat(preview.fullRefundUsd()).isEqualByComparingTo("100.00");
        assertThatThrownBy(() -> service.apply(freelancer.getId(), job.getId(), null))
                .isInstanceOf(ApplicationException.class);
        var application = service.apply(freelancer.getId(), job.getId(), preview.fingerprint());
        assertThat(application.getAcceptedTermsFingerprint()).isEqualTo(preview.fingerprint());
        assertThat(application.getTermsAcceptedAt()).isNotNull();

        JobApplication saved = application(job.getId(), freelancer.getId(), JobApplicationStatus.PENDING);
        saved.setAcceptedTermsFingerprint(preview.fingerprint());
        when(applicationRepository.findByJobIdAndFreelancerId(job.getId(), freelancer.getId()))
                .thenReturn(Optional.of(saved));
        var assign = new AssignFreelancerRequest();
        assign.setFreelancerId(freelancer.getId());
        assign.setAcceptedTermsFingerprint("stale");
        assertThatThrownBy(() -> service.assignFreelancer(client.getId(), job.getId(), assign))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_DATA);
        verify(contractRepository, org.mockito.Mockito.never()).save(any());
        assign.setAcceptedTermsFingerprint(preview.fingerprint());
        service.assignFreelancer(client.getId(), job.getId(), assign);
        verify(contractRepository).save(org.mockito.ArgumentMatchers.argThat(contract ->
                preview.fingerprint().equals(contract.getAcceptedTermsFingerprint())
                        && contract.getClientTermsAcceptedAt() != null));
    }

    @Test
    void completedOldQuoteShowsTheHistoricalPayoutInsteadOfTheNewEstimate() {
        User client = user(UserType.CLIENT);
        Job job = job(client.getId(), JobStatus.COMPLETED);
        job.setPaymentFlowVersion(1);
        WorkContract contract = new WorkContract();
        contract.setId(UUID.randomUUID());
        contract.setPaymentRail(PaymentFlow.RAIL);
        contract.setStatus(ContractStatus.COMPLETED);
        contract.setBudgetUsd(job.getBudgetUsd());
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(contractRepository.findByJobId(job.getId())).thenReturn(Optional.of(contract));
        when(paymentFlowService.usesLegacyFeeOnlyQuote(contract.getId())).thenReturn(true);

        var preview = service.getByIdForParticipant(client.getId(), job.getId()).getLocalPaymentTerms();
        assertThat(preview.legacyPayout()).isTrue();
        assertThat(preview.estimatedTaxableVnd()).isEqualByComparingTo("2500000");
        assertThat(preview.estimatedPayoutVnd()).isEqualByComparingTo("2425000");
    }

    @Test
    void createPersistsExplicitCategoryAndNormalizedJobSkillsWithoutCharging() {
        User client = user(UserType.CLIENT);
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0); saved.setId(UUID.randomUUID()); return saved;
        });
        CreateJobRequest request = createRequest();
        request.setSkills(List.of(" React ", "CSS"));
        var result = service.create(client.getId(), request);
        assertThat(result.getCategory()).isEqualTo(com.marketplace.backend.entity.JobCategory.WEB_FRONTEND);
        assertThat(result.getSkills()).containsExactly("React", "CSS");
        verify(jobRepository).save(org.mockito.ArgumentMatchers.argThat(row ->
                row.getCategory() == com.marketplace.backend.entity.JobCategory.WEB_FRONTEND
                        && Integer.valueOf(0).equals(row.getPaymentFlowVersion())
                        && row.getSkills().equals(List.of("React", "CSS"))));
        verifyNoInteractions(paymentBackendClient);
    }

    @Test
    void cutoverVersionIsCapturedWhenJobIsCreated() {
        User client = user(UserType.CLIENT);
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(paymentFlowService.cutoverEnabled()).thenReturn(true);
        when(paymentFlowService.currentChainTerms())
                .thenReturn(new com.marketplace.backend.service.PaymentFlowService.ChainTerms("localnet", "mock-mint"));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0); saved.setId(UUID.randomUUID()); return saved;
        });
        CreateJobRequest request = createRequest();
        request.setReviewWindowHours(48);

        service.create(client.getId(), request);

        // Unified terms override the editor value and snapshot the chain terms both sides accept.
        verify(jobRepository).save(org.mockito.ArgumentMatchers.argThat(row ->
                Integer.valueOf(1).equals(row.getPaymentFlowVersion())
                        && row.getReviewWindowHours() == 72
                        && "localnet".equals(row.getPaymentNetwork())
                        && "mock-mint".equals(row.getPaymentMint())));
        verifyNoInteractions(paymentBackendClient);
    }

    @Test
    void invalidCategoryAndSkillsAreRejectedBeforePersistence() {
        User client = user(UserType.CLIENT);
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        for (String category : java.util.Arrays.asList(null, "", "UNKNOWN", "Remote", "web_frontend")) {
            CreateJobRequest request = createRequest(); request.setCategory(category);
            assertThatThrownBy(() -> service.create(client.getId(), request)).isInstanceOf(ApplicationException.class);
        }
        List<List<String>> invalid = List.of(List.of(""), List.of("   "), List.of("a"), List.of("x".repeat(41)),
                List.of("React", " react "), java.util.Arrays.asList((String) null),
                java.util.stream.IntStream.range(0, 11).mapToObj(i -> "Skill " + i).toList());
        for (List<String> skills : invalid) {
            CreateJobRequest request = createRequest(); request.setSkills(skills);
            assertThatThrownBy(() -> service.create(client.getId(), request)).isInstanceOf(ApplicationException.class);
        }
        verify(jobRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateChangesOnlySuppliedCategorySkillsAndRejectsInvalidInputBeforeMutation() {
        User client = user(UserType.CLIENT);
        Job row = job(client.getId(), JobStatus.OPEN);
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(jobRepository.findById(row.getId())).thenReturn(Optional.of(row));
        var request = new com.marketplace.backend.dto.request.job.UpdateJobRequest();
        request.setCategory("BACKEND_API"); request.setSkills(List.of(" Spring ", "Java"));
        var result = service.update(client.getId(), row.getId(), request);
        assertThat(result.getCategory()).isEqualTo(com.marketplace.backend.entity.JobCategory.BACKEND_API);
        assertThat(result.getSkills()).containsExactly("Spring", "Java");
        assertThat(row.getTitle()).isEqualTo("Solana job");
        assertThat(row.getBudgetUsd()).isEqualByComparingTo("100");
        request.setCategory("MOBILE_APP"); request.setSkills(List.of("Flutter", "flutter"));
        assertThatThrownBy(() -> service.update(client.getId(), row.getId(), request)).isInstanceOf(ApplicationException.class);
        assertThat(row.getCategory()).isEqualTo(com.marketplace.backend.entity.JobCategory.BACKEND_API);
        assertThat(row.getSkills()).containsExactly("Spring", "Java");
        verify(jobRepository, times(1)).save(row);
    }

    @Test
    void discoveryComposesCategorySkillsBudgetsSearchAndApplicationAndReturnsLegacyDefaults() {
        User freelancer = user(UserType.FREELANCER);
        Job row = job(UUID.randomUUID(), JobStatus.OPEN); row.setCategory(null);
        when(userRepository.findById(freelancer.getId())).thenReturn(Optional.of(freelancer));
        when(jobRepository.discover(eq(freelancer.getId()), eq("API"), eq(new BigDecimal("50")),
                eq(new BigDecimal("200")), eq("NOT_APPLIED"), eq(com.marketplace.backend.entity.JobCategory.BACKEND_API),
                eq(true), eq(List.of("spring", "java")), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(row)));
        var result = service.discover(freelancer.getId(), 0, 10, " API ", new BigDecimal("50"), new BigDecimal("200"),
                "BUDGET_ASC", "NOT_APPLIED", "BACKEND_API", List.of(" Spring ", "Java"));
        assertThat(result.getData().get(0).getCategory()).isEqualTo(com.marketplace.backend.entity.JobCategory.OTHER);
        assertThat(result.getData().get(0).getSkills()).isEmpty();
    }

    @Test
    void discoveryRejectsInvalidCategoryOrSkillsBeforeQuerying() {
        User freelancer = user(UserType.FREELANCER);
        when(userRepository.findById(freelancer.getId())).thenReturn(Optional.of(freelancer));
        assertThatThrownBy(() -> service.discover(freelancer.getId(), 0, 10, null, null, null,
                "NEWEST", "ALL", "REMOTE", List.of())).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> service.discover(freelancer.getId(), 0, 10, null, null, null,
                "NEWEST", "ALL", null, List.of("Java", "java"))).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(jobRepository);
    }

    private CreateJobRequest createRequest() {
        CreateJobRequest request = new CreateJobRequest(); request.setTitle("Work"); request.setDescription("Brief");
        request.setBudgetUsd(new BigDecimal("100")); request.setCategory("WEB_FRONTEND");
        request.setDeliveryDueAt(java.time.Instant.now().plus(java.time.Duration.ofDays(5)));
        return request;
    }

    private User user(UserType type) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUserType(type);
        return user;
    }

    private Job job(UUID clientId, JobStatus status) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setClientUserId(clientId);
        job.setTitle("Solana job");
        job.setDescription("Build payout flow");
        job.setBudgetUsd(new BigDecimal("100"));
        job.setStatus(status);
        job.setCreatedAt(LocalDateTime.now());
        return job;
    }

    private JobApplication application(UUID jobId, UUID freelancerId, JobApplicationStatus status) {
        JobApplication application = new JobApplication();
        application.setId(UUID.randomUUID());
        application.setJobId(jobId);
        application.setFreelancerId(freelancerId);
        application.setStatus(status);
        application.setCreatedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());
        return application;
    }
}
