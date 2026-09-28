package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.job.CreateJobRequest;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.job.DiscoverJobResponse;
import com.marketplace.backend.dto.response.job.MyApplicationResponse;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.JobApplication;
import com.marketplace.backend.entity.JobApplicationStatus;
import com.marketplace.backend.entity.JobStatus;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.UserType;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobApplicationRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.JobSubmissionRepository;
import com.marketplace.backend.repository.UserRepository;
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
        service = new JobServiceImpl(userRepository, jobRepository, applicationRepository, submissionRepository,
                paymentBackendClient, misaBackendClient, notificationService, payoutService, payoutRecordRepository);
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
                eq(new BigDecimal("200")), eq("ALL"), any(Pageable.class)))
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
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
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
