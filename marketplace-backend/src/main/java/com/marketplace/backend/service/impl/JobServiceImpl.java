package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.job.AssignFreelancerRequest;
import com.marketplace.backend.dto.request.job.CreateJobRequest;
import com.marketplace.backend.dto.request.job.RequestRevisionRequest;
import com.marketplace.backend.dto.request.job.SubmitWorkRequest;
import com.marketplace.backend.dto.request.job.UpdateJobRequest;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.job.CertificateSummaryResponse;
import com.marketplace.backend.dto.response.job.ContractSummaryResponse;
import com.marketplace.backend.dto.response.job.DiscoverJobResponse;
import com.marketplace.backend.dto.response.job.JobApplicationResponse;
import com.marketplace.backend.dto.response.job.JobClientSummaryResponse;
import com.marketplace.backend.dto.response.job.JobPaymentStatusResponse;
import com.marketplace.backend.dto.response.job.JobResponse;
import com.marketplace.backend.dto.response.job.JobSubmissionResponse;
import com.marketplace.backend.dto.response.job.MyApplicationJobResponse;
import com.marketplace.backend.dto.response.job.MyApplicationResponse;
import com.marketplace.backend.dto.response.job.RequirementResponse;
import com.marketplace.backend.dto.response.job.UnifiedTermsPreviewResponse;
import com.marketplace.backend.dto.response.bofa.CheckoutOrderResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobApplicationRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.JobSubmissionRepository;
import com.marketplace.backend.repository.AcceptanceCriterionRepository;
import com.marketplace.backend.repository.DeliverableRequirementRepository;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.JobService;
import com.marketplace.backend.service.JobSkills;
import com.marketplace.backend.service.NotificationService;
import com.marketplace.backend.service.PayoutService;
import com.marketplace.backend.service.PaymentFlowService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JobServiceImpl implements JobService {

    private static final String DEVNET = "devnet";

    UserRepository userRepository;
    JobRepository jobRepository;
    JobApplicationRepository jobApplicationRepository;
    JobSubmissionRepository jobSubmissionRepository;
    PaymentBackendClient paymentBackendClient;
    MisaBackendClient misaBackendClient;
    NotificationService notificationService;

    PayoutService payoutService;
    FreelancerPayoutRecordRepository freelancerPayoutRecordRepository;
    WorkContractRepository workContractRepository;
    MilestoneRepository milestoneRepository;
    AcceptanceCriterionRepository acceptanceCriterionRepository;
    DeliverableRequirementRepository deliverableRequirementRepository;
    PaymentFlowService paymentFlowService;

    @Override
    @Transactional
    public JobResponse create(UUID clientUserId, CreateJobRequest request) {
        requireUserType(clientUserId, UserType.CLIENT);
        JobCategory category = JobCategory.require(request.getCategory());
        List<String> skills = JobSkills.normalize(request.getSkills());
        if (request.getDeliveryDueAt() != null
                && request.getDeliveryDueAt().isBefore(Instant.now().plus(Duration.ofHours(24)))) {
            throw new ApplicationException(ErrorCode.JOB_DEADLINE_TOO_SOON);
        }
        Job job = new Job();
        job.setClientUserId(clientUserId);
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setCategory(category);
        job.setSkills(skills);
        job.setBudgetUsd(request.getBudgetUsd());
        job.setPaymentFlowVersion(paymentFlowService.cutoverEnabled() ? 1 : 0);
        if (Integer.valueOf(1).equals(job.getPaymentFlowVersion())) {
            PaymentFlowService.ChainTerms chain = paymentFlowService.currentChainTerms();
            job.setPaymentNetwork(chain.network());
            job.setPaymentMint(chain.mint());
        }
        job.setStatus(JobStatus.OPEN);
        job.setDeliveryDueAt(request.getDeliveryDueAt());
        // Unified terms publish one 72h review window with no grace; legacy rails keep the editor value.
        job.setReviewWindowHours(Integer.valueOf(1).equals(job.getPaymentFlowVersion())
                ? PaymentFlow.REVIEW_WINDOW_HOURS : request.getReviewWindowHours());
        job.setMaxRevisions(request.getMaxRevisions());
        jobRepository.save(job);

        saveJobRequirements(job.getId(), request);

        return toResponse(job);
    }

    @Override
    @Transactional
    public JobApplicationResponse apply(UUID freelancerId, UUID jobId, String acceptedTermsFingerprint) {
        requireUserType(freelancerId, UserType.FREELANCER);
        Job job = getOrThrow(jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }
        requireUnifiedTermsAcceptance(job, acceptedTermsFingerprint);

        JobApplication prior = jobApplicationRepository.findByJobIdAndFreelancerId(jobId, freelancerId).orElse(null);
        if (prior != null) {
            if (Integer.valueOf(1).equals(job.getPaymentFlowVersion())
                    && prior.getStatus() == JobApplicationStatus.PENDING
                    && !acceptedTermsFingerprint.equals(prior.getAcceptedTermsFingerprint())) {
                prior.setAcceptedTermsFingerprint(acceptedTermsFingerprint);
                prior.setTermsAcceptedAt(Instant.now());
                return toApplicationResponse(jobApplicationRepository.save(prior));
            }
            throw new ApplicationException(ErrorCode.ALREADY_APPLIED, jobId);
        }

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setFreelancerId(freelancerId);
        application.setStatus(JobApplicationStatus.PENDING);
        if (Integer.valueOf(1).equals(job.getPaymentFlowVersion())) {
            application.setAcceptedTermsFingerprint(acceptedTermsFingerprint);
            application.setTermsAcceptedAt(Instant.now());
        }
        jobApplicationRepository.save(application);

        return toApplicationResponse(application);
    }

    @Override
    public List<JobApplicationResponse> listApplications(UUID clientUserId, UUID jobId) {
        getOwnedByClientOrThrow(clientUserId, jobId);

        return jobApplicationRepository.findByJobId(jobId).stream()
                .map(this::toApplicationResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public JobResponse assignFreelancer(UUID clientUserId, UUID jobId, AssignFreelancerRequest request) {
        Job job = getOwnedByClientWithLockOrThrow(clientUserId, jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }
        if (safeDeliverables(jobId).isEmpty() || safeCriteria(jobId).isEmpty()) {
            throw new ApplicationException(ErrorCode.JOB_REQUIREMENTS_MISSING);
        }

        User freelancer = validateAndGetFreelancer(request.getFreelancerId());

        JobApplication acceptedApplication = jobApplicationRepository
                .findByJobIdAndFreelancerId(jobId, freelancer.getId())
                .filter(a -> a.getStatus() == JobApplicationStatus.PENDING)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FREELANCER_NOT_APPLIED, jobId));
        if (Integer.valueOf(1).equals(job.getPaymentFlowVersion())) {
            requireUnifiedTermsAcceptance(job, request.getAcceptedTermsFingerprint());
            if (!request.getAcceptedTermsFingerprint().equals(acceptedApplication.getAcceptedTermsFingerprint())) {
                throw new ApplicationException(ErrorCode.INVALID_DATA);
            }
        }

        acceptedApplication.setStatus(JobApplicationStatus.ACCEPTED);
        jobApplicationRepository.save(acceptedApplication);

        for (JobApplication other : jobApplicationRepository.findByJobIdAndStatus(jobId, JobApplicationStatus.PENDING)) {
            other.setStatus(JobApplicationStatus.REJECTED);
            jobApplicationRepository.save(other);
        }

        job.setFreelancerId(freelancer.getId());
        job.setStatus(JobStatus.AWAITING_PAYMENT);
        jobRepository.save(job);

        WorkContract contract = new WorkContract();
        contract.setJobId(job.getId());
        contract.setClientUserId(job.getClientUserId());
        contract.setFreelancerId(freelancer.getId());
        contract.setTitleSnapshot(job.getTitle());
        contract.setDescriptionSnapshot(job.getDescription());
        contract.setBudgetUsd(job.getBudgetUsd());
        contract.setDeliveryDueAt(job.getDeliveryDueAt());
        contract.setReviewWindowHours(job.getReviewWindowHours());
        contract.setMaxRevisions(job.getMaxRevisions());
        contract.setRevisionsUsed(0);
        contract.setStatus(ContractStatus.PENDING_FUNDING);
        if (Integer.valueOf(1).equals(job.getPaymentFlowVersion()))
            contract.setPaymentRail(PaymentFlow.RAIL);
        if (Integer.valueOf(1).equals(job.getPaymentFlowVersion())) {
            contract.setAcceptedTermsFingerprint(request.getAcceptedTermsFingerprint());
            contract.setClientTermsAcceptedAt(Instant.now());
        }
        workContractRepository.save(contract);

        Milestone milestone = new Milestone();
        milestone.setContractId(contract.getId());
        milestone.setAmount(job.getBudgetUsd());
        milestone.setCurrency("USD");
        milestone.setStatus(MilestoneStatus.PENDING_FUNDING);
        milestoneRepository.save(milestone);
        if (PaymentFlow.RAIL.equals(contract.getPaymentRail())) {
            paymentFlowService.createDraft(contract, milestone);
        }
        snapshotRequirements(job.getId(), contract.getId());

        notificationService.notify(
                freelancer.getId(),
                NotificationType.JOB_ASSIGNED,
                "Bạn được giao 1 công việc mới",
                "Bạn vừa được gán vào công việc \"" + job.getTitle() + "\".",
                job.getId());

        return toResponse(job);
    }

    private User validateAndGetFreelancer(UUID freelancerId) {
        User freelancer = userRepository.findById(freelancerId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FREELANCER_NOT_FOUND, freelancerId));

        if (freelancer.getUserType() != UserType.FREELANCER) {
            throw new ApplicationException(ErrorCode.USER_IS_NOT_FREELANCER, freelancerId);
        }

        return freelancer;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<JobResponse> listForUser(UUID userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending());
        Page<Job> jobPage = jobRepository.findByClientUserIdOrFreelancerId(userId, userId, pageable);

        return PageResponse.<JobResponse>builder()
                .currentPage(jobPage.getNumber())
                .pageSize(jobPage.getSize())
                .totalPages(jobPage.getTotalPages())
                .totalElements(jobPage.getTotalElements())
                .data(jobPage.getContent().stream().map(this::toResponse).collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DiscoverJobResponse> discover(UUID freelancerId, int page, int size, String keyword,
                                                       BigDecimal minBudgetUsd, BigDecimal maxBudgetUsd,
                                                       String sort, String application, String category, List<String> skills) {
        requireUserType(freelancerId, UserType.FREELANCER);
        validateBudgetRange(minBudgetUsd, maxBudgetUsd);
        String applicationFilter = normalizeApplicationFilter(application);
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size), discoverySort(sort));
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        JobCategory categoryFilter = category == null || category.isEmpty() ? null : JobCategory.require(category);
        List<String> skillFilter = JobSkills.normalize(skills).stream().map(skill -> skill.toLowerCase(Locale.ROOT)).toList();
        Page<Job> jobs = jobRepository.discover(freelancerId, normalizedKeyword, minBudgetUsd,
                maxBudgetUsd, applicationFilter, categoryFilter, !skillFilter.isEmpty(),
                skillFilter.isEmpty() ? List.of("") : skillFilter, pageable);

        List<UUID> jobIds = jobs.getContent().stream().map(Job::getId).toList();
        Map<UUID, JobApplication> applications = jobIds.isEmpty() ? Map.of()
                : jobApplicationRepository.findByFreelancerIdAndJobIdIn(freelancerId, jobIds).stream()
                .collect(Collectors.toMap(JobApplication::getJobId, Function.identity()));
        Map<UUID, User> clients = usersById(jobs.getContent().stream().map(Job::getClientUserId).toList());

        return PageResponse.<DiscoverJobResponse>builder()
                .currentPage(jobs.getNumber())
                .pageSize(jobs.getSize())
                .totalPages(jobs.getTotalPages())
                .totalElements(jobs.getTotalElements())
                .data(jobs.getContent().stream()
                        .map(job -> toDiscoverResponse(job, clients.get(job.getClientUserId()), applications.get(job.getId())))
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MyApplicationResponse> listMyApplications(UUID freelancerId, int page, int size,
                                                                   JobApplicationStatus status) {
        requireUserType(freelancerId, UserType.FREELANCER);
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size), Sort.by("createdAt").descending());
        Page<JobApplication> applications = jobApplicationRepository.findMine(freelancerId, status, pageable);
        Map<UUID, Job> jobs = jobRepository.findAllById(applications.getContent().stream()
                        .map(JobApplication::getJobId).toList()).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        Map<UUID, User> clients = usersById(jobs.values().stream().map(Job::getClientUserId).toList());

        return PageResponse.<MyApplicationResponse>builder()
                .currentPage(applications.getNumber())
                .pageSize(applications.getSize())
                .totalPages(applications.getTotalPages())
                .totalElements(applications.getTotalElements())
                .data(applications.getContent().stream()
                        .map(item -> toMyApplicationResponse(item, jobs.get(item.getJobId()), clients))
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public JobResponse getByIdForParticipant(UUID userId, UUID jobId) {
        return toResponse(getParticipantOrThrow(userId, jobId));
    }

    @Override
    @Transactional
    public JobResponse update(UUID clientUserId, UUID jobId, UpdateJobRequest request) {
        Job job = getOwnedByClientOrThrow(clientUserId, jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }
        // Applicants agreed to published terms; require a new Job for changes after an application.
        if (!jobApplicationRepository.findByJobId(jobId).isEmpty()) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        if (request.getBudgetUsd() != null && job.getCheckoutOrderId() != null
                && request.getBudgetUsd().compareTo(job.getBudgetUsd()) != 0) {
            throw new ApplicationException(ErrorCode.JOB_BUDGET_IMMUTABLE);
        }

        JobCategory category = request.getCategory() == null ? null : JobCategory.require(request.getCategory());
        List<String> skills = request.getSkills() == null ? null : JobSkills.normalize(request.getSkills());
        if (category != null) job.setCategory(category);
        if (skills != null) job.setSkills(skills);
        if (request.getTitle() != null) {
            job.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            job.setDescription(request.getDescription());
        }
        if (request.getBudgetUsd() != null) {
            if (job.getCheckoutOrderId() == null) {
                job.setBudgetUsd(request.getBudgetUsd());
            }
        }

        jobRepository.save(job);

        return toResponse(job);
    }

    @Override
    @Transactional
    public JobResponse approve(UUID clientUserId, UUID jobId) {
        Job job = getOwnedByClientOrThrow(clientUserId, jobId);
        rejectLegacyContractWorkflow(jobId);

        if (job.getStatus() != JobStatus.SUBMITTED_FOR_REVIEW || job.getCheckoutOrderId() == null) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }
        JobSubmission submission = latestSubmission(jobId);
        if (submission.getStatus() != JobSubmissionStatus.SUBMITTED) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        CheckoutOrderResult captured = paymentBackendClient.captureCheckoutOrder(job.getCheckoutOrderId());

        if (!"CAPTURED".equals(captured.getStatus())) {
            throw new ApplicationException(ErrorCode.PAYMENT_BACKEND_CALL_FAILED, "capture:" + captured.getStatus());
        }

        job.setStatus(JobStatus.COMPLETED);
        jobRepository.save(job);

        submission.setStatus(JobSubmissionStatus.APPROVED);
        submission.setReviewedAt(LocalDateTime.now());
        jobSubmissionRepository.save(submission);

        notificationService.notify(
                clientUserId,
                NotificationType.PAYMENT_SENT,
                "Thanh toán thành công",
                "Bạn đã thanh toán thành công cho công việc \"" + job.getTitle() + "\".",
                job.getId());

        notificationService.notify(
                job.getFreelancerId(),
                NotificationType.WORK_APPROVED,
                "Bàn giao đã được duyệt",
                "Client đã duyệt bàn giao cho công việc \"" + job.getTitle() + "\".",
                job.getId());

        payoutService.settle(job);

        return toResponse(job);
    }

    @Override
    @Transactional
    public JobSubmissionResponse submitWork(UUID freelancerId, UUID jobId, SubmitWorkRequest request) {
        requireUserType(freelancerId, UserType.FREELANCER);
        Job job = getOrThrow(jobId);
        if (job.getFreelancerId() == null || !job.getFreelancerId().equals(freelancerId)) {
            throw new ApplicationException(ErrorCode.JOB_NOT_FOUND, jobId);
        }
        rejectLegacyContractWorkflow(jobId);
        if (job.getStatus() != JobStatus.IN_PROGRESS && job.getStatus() != JobStatus.REVISION_REQUESTED) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        JobSubmission submission = new JobSubmission();
        submission.setJobId(jobId);
        submission.setFreelancerId(freelancerId);
        submission.setVersion(Math.toIntExact(jobSubmissionRepository.countByJobId(jobId) + 1));
        submission.setSummary(request.getSummary().trim());
        submission.setDeliverableUrl(StringUtils.hasText(request.getDeliverableUrl())
                ? request.getDeliverableUrl().trim() : null);
        submission.setStatus(JobSubmissionStatus.SUBMITTED);
        jobSubmissionRepository.save(submission);

        job.setStatus(JobStatus.SUBMITTED_FOR_REVIEW);
        jobRepository.save(job);

        notificationService.notify(
                job.getClientUserId(),
                NotificationType.WORK_SUBMITTED,
                "Freelancer đã bàn giao công việc",
                "Freelancer đã gửi bản bàn giao #" + submission.getVersion()
                        + " cho công việc \"" + job.getTitle() + "\".",
                job.getId());
        return toSubmissionResponse(submission);
    }

    @Override
    @Transactional
    public JobSubmissionResponse requestRevision(UUID clientUserId, UUID jobId, RequestRevisionRequest request) {
        Job job = getOwnedByClientOrThrow(clientUserId, jobId);
        rejectLegacyContractWorkflow(jobId);
        if (job.getStatus() != JobStatus.SUBMITTED_FOR_REVIEW) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }
        JobSubmission submission = latestSubmission(jobId);
        if (submission.getStatus() != JobSubmissionStatus.SUBMITTED) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        submission.setStatus(JobSubmissionStatus.REVISION_REQUESTED);
        submission.setReviewerFeedback(request.getFeedback().trim());
        submission.setReviewedAt(LocalDateTime.now());
        jobSubmissionRepository.save(submission);

        job.setStatus(JobStatus.REVISION_REQUESTED);
        jobRepository.save(job);

        notificationService.notify(
                job.getFreelancerId(),
                NotificationType.REVISION_REQUESTED,
                "Client yêu cầu chỉnh sửa",
                "Client yêu cầu chỉnh sửa bản bàn giao cho công việc \"" + job.getTitle() + "\".",
                job.getId());
        return toSubmissionResponse(submission);
    }

    @Override
    public List<JobSubmissionResponse> listSubmissions(UUID userId, UUID jobId) {
        getParticipantOrThrow(userId, jobId);
        return jobSubmissionRepository.findByJobIdOrderByVersionAsc(jobId).stream()
                .map(this::toSubmissionResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public JobResponse cancel(UUID clientUserId, UUID jobId) {
        Job job = getOwnedByClientWithLockOrThrow(clientUserId, jobId);
        if (workContractRepository.findByJobId(jobId).isPresent()) {
            throw new ApplicationException(ErrorCode.CONTRACT_API_REQUIRED);
        }

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);

        List<JobApplication> pendingApplications = jobApplicationRepository
                .findByJobIdAndStatus(jobId, JobApplicationStatus.PENDING);
        pendingApplications.forEach(application -> application.setStatus(JobApplicationStatus.CANCELLED));
        jobApplicationRepository.saveAll(pendingApplications);
        pendingApplications.forEach(application -> notificationService.notify(
                application.getFreelancerId(),
                NotificationType.JOB_CANCELLED,
                "Công việc đã bị hủy",
                "Công việc \"" + job.getTitle() + "\" đã bị Client hủy. Đơn ứng tuyển của bạn đã được đóng.",
                job.getId()));

        return toResponse(job);
    }

    @Override
    public JobPaymentStatusResponse getPaymentStatus(UUID userId, UUID jobId) {
        Job job = getParticipantOrThrow(userId, jobId);

        if (job.getCheckoutOrderId() == null) {
            throw new ApplicationException(ErrorCode.JOB_NOT_PAID);
        }

        CheckoutOrderResult checkoutOrder = paymentBackendClient.getCheckoutOrder(job.getCheckoutOrderId());

        FreelancerPayoutRecord payoutRecord = freelancerPayoutRecordRepository.findByJobId(job.getId()).orElse(null);

        return JobPaymentStatusResponse.builder()
                .jobId(job.getId())
                .checkoutOrderId(job.getCheckoutOrderId())
                .checkoutOrderStatus(checkoutOrder.getStatus())
                .taxExportStatus(job.getTaxExportStatus() != null ? job.getTaxExportStatus().name() : null)
                .simulation(payoutRecord != null ? payoutRecord.isSimulated() : null)
                .network(payoutRecord != null ? payoutRecord.getOnRampNetwork() : null)
                .onRampStatus(payoutRecord != null ? payoutRecord.getOnRampStatus().name() : null)
                .offRampStatus(payoutRecord != null ? payoutRecord.getOffRampStatus().name() : null)
                .onRampClientPublicKey(payoutRecord != null ? payoutRecord.getOnRampClientPublicKey() : null)
                .onRampPurchaseId(payoutRecord != null ? payoutRecord.getOnRampPurchaseId() : null)
                .onRampTransactionSignature(payoutRecord != null ? payoutRecord.getOnRampTransactionSignature() : null)
                .onRampReceiptPda(payoutRecord != null ? payoutRecord.getOnRampReceiptPda() : null)
                .explorerUrl(payoutRecord != null ? explorerUrl(payoutRecord) : null)
                .amountUsdcReceived(payoutRecord != null ? payoutRecord.getAmountUsdcReceived() : null)
                .clientPaymentStatus(payoutRecord != null && payoutRecord.getClientPaymentStatus() != null
                        ? payoutRecord.getClientPaymentStatus().name() : null)
                .freelancerPublicKey(payoutRecord != null ? payoutRecord.getFreelancerPublicKey() : null)
                .rateId(payoutRecord != null ? payoutRecord.getRateId() : null)
                .rateTransactionSignature(payoutRecord != null ? payoutRecord.getRateTransactionSignature() : null)
                .rateSnapshotPda(payoutRecord != null ? payoutRecord.getRateSnapshotPda() : null)
                .invoiceId(payoutRecord != null ? payoutRecord.getInvoiceId() : null)
                .invoicePda(payoutRecord != null ? payoutRecord.getInvoicePda() : null)
                .paymentMint(payoutRecord != null ? payoutRecord.getPaymentMint() : null)
                .invoiceTransactionSignature(payoutRecord != null ? payoutRecord.getInvoiceTransactionSignature() : null)
                .paymentTransactionSignature(payoutRecord != null ? payoutRecord.getPaymentTransactionSignature() : null)
                .paymentExplorerUrl(payoutRecord != null
                        ? explorerUrl(payoutRecord.getOnRampNetwork(), payoutRecord.getPaymentTransactionSignature()) : null)
                .clientPaymentSubmittedAt(payoutRecord != null ? payoutRecord.getClientPaymentSubmittedAt() : null)
                .clientPaymentConfirmedAt(payoutRecord != null ? payoutRecord.getClientPaymentConfirmedAt() : null)
                .clientPaymentError(payoutRecord != null ? payoutRecord.getClientPaymentError() : null)
                .onChainOffRampStatus(payoutRecord != null && payoutRecord.getOnChainOffRampStatus() != null
                        ? payoutRecord.getOnChainOffRampStatus().name() : null)
                .withdrawalId(payoutRecord != null ? payoutRecord.getWithdrawalId() : null)
                .withdrawalPda(payoutRecord != null ? payoutRecord.getWithdrawalPda() : null)
                .treasuryPublicKey(payoutRecord != null ? payoutRecord.getTreasuryPublicKey() : null)
                .treasuryUsdcAta(payoutRecord != null ? payoutRecord.getTreasuryUsdcAta() : null)
                .withdrawalTokenAmount(payoutRecord != null ? payoutRecord.getWithdrawalTokenAmount() : null)
                .withdrawalFiatAmountVnd(payoutRecord != null ? payoutRecord.getWithdrawalFiatAmountVnd() : null)
                .withdrawalTransactionSignature(payoutRecord != null
                        ? payoutRecord.getWithdrawalTransactionSignature() : null)
                .withdrawalExplorerUrl(payoutRecord != null
                        ? explorerUrl(payoutRecord.getOnRampNetwork(), payoutRecord.getWithdrawalTransactionSignature()) : null)
                .withdrawalSubmittedAt(payoutRecord != null ? payoutRecord.getWithdrawalSubmittedAt() : null)
                .withdrawalConfirmedAt(payoutRecord != null ? payoutRecord.getWithdrawalConfirmedAt() : null)
                .onChainOffRampError(payoutRecord != null ? payoutRecord.getOnChainOffRampError() : null)
                .payoutBankCode(payoutRecord != null && payoutRecord.getPayoutBankCode() != null
                        ? payoutRecord.getPayoutBankCode().name() : null)
                .payoutBankAccountNumber(payoutRecord != null
                        ? maskBankAccount(payoutRecord.getPayoutBankAccountNumber()) : null)
                .payoutBankAccountHolderName(payoutRecord != null
                        ? payoutRecord.getPayoutBankAccountHolderName() : null)
                .offRampReference(payoutRecord != null ? payoutRecord.getOffRampReference() : null)
                .amountVndBeforeOffRampFee(payoutRecord != null
                        ? payoutRecord.getAmountVndBeforeOffRampFee() : null)
                .offRampFeeVnd(payoutRecord != null ? payoutRecord.getOffRampFeeVnd() : null)
                .simulatedPayoutAt(payoutRecord != null ? payoutRecord.getSimulatedPayoutAt() : null)
                .offRampCompletionSignature(payoutRecord != null
                        ? payoutRecord.getOffRampCompletionSignature() : null)
                .offRampCompletionExplorerUrl(payoutRecord != null
                        ? explorerUrl(payoutRecord.getOnRampNetwork(), payoutRecord.getOffRampCompletionSignature()) : null)
                .offRampCompletionSubmittedAt(payoutRecord != null
                        ? payoutRecord.getOffRampCompletionSubmittedAt() : null)
                .offRampCompletedAt(payoutRecord != null ? payoutRecord.getOffRampCompletedAt() : null)
                .offRampError(payoutRecord != null ? payoutRecord.getOffRampError() : null)
                .estimatedAmountVnd(payoutRecord != null ? payoutRecord.getAmountVndEstimated() : null)
                .usdcToVndRateSource(payoutRecord != null && payoutRecord.getUsdcToVndRateSource() != null
                        ? payoutRecord.getUsdcToVndRateSource().name() : null)
                .usdcToVndRate(payoutRecord != null ? payoutRecord.getUsdcToVndRate() : null)
                .usdcToVndRateObservedAt(payoutRecord != null ? payoutRecord.getUsdcToVndRateObservedAt() : null)
                .taxableAmountVnd(payoutRecord != null ? payoutRecord.getTaxableAmountVnd() : null)
                .taxRateSource(payoutRecord != null ? payoutRecord.getTaxRateSource().name() : null)
                .taxUsdToVndRate(payoutRecord != null ? payoutRecord.getTaxUsdToVndRate() : null)
                .taxRateObservedAt(payoutRecord != null ? payoutRecord.getTaxRateObservedAt() : null)
                .build();
    }

    private String explorerUrl(FreelancerPayoutRecord payoutRecord) {
        return explorerUrl(payoutRecord.getOnRampNetwork(), payoutRecord.getOnRampTransactionSignature());
    }

    private String explorerUrl(String network, String signature) {
        if (!DEVNET.equalsIgnoreCase(network) || signature == null) {
            return null;
        }
        return "https://explorer.solana.com/tx/" + signature + "?cluster=devnet";
    }

    private String maskBankAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }

    @Override
    public List<CertificateSummaryResponse> listCertificatesForFreelancer(UUID freelancerId) {
        requireUserType(freelancerId, UserType.FREELANCER);
        return jobRepository.findByFreelancerIdAndMisaCertificateIdIsNotNull(freelancerId).stream()
                .map(job -> CertificateSummaryResponse.builder()
                        .certificateId(job.getMisaCertificateId())
                        .jobId(job.getId())
                        .jobTitle(job.getTitle())
                        .createdAt(job.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public byte[] downloadCertificatePdf(UUID freelancerId, UUID certificateId) {
        requireUserType(freelancerId, UserType.FREELANCER);
        jobRepository.findByFreelancerIdAndMisaCertificateId(freelancerId, certificateId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.JOB_NOT_FOUND, certificateId));

        return misaBackendClient.getCertificatePdf(certificateId);
    }

    private Job getOrThrow(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.JOB_NOT_FOUND, jobId));
    }

    private Job getOwnedByClientWithLockOrThrow(UUID clientUserId, UUID jobId) {
        // Assignment and legacy OPEN cancellation must not overwrite each other's winner.
        Job job = jobRepository.findWithLockById(jobId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.JOB_NOT_FOUND, jobId));
        requireUserType(clientUserId, UserType.CLIENT);
        if (!job.getClientUserId().equals(clientUserId)) {
            throw new ApplicationException(ErrorCode.JOB_NOT_FOUND, jobId);
        }
        return job;
    }

    private Job getOwnedByClientOrThrow(UUID clientUserId, UUID jobId) {
        requireUserType(clientUserId, UserType.CLIENT);
        Job job = getOrThrow(jobId);
        if (!job.getClientUserId().equals(clientUserId)) {
            throw new ApplicationException(ErrorCode.JOB_NOT_FOUND, jobId);
        }
        return job;
    }

    private Job getParticipantOrThrow(UUID userId, UUID jobId) {
        Job job = getOrThrow(jobId);
        boolean isClient = job.getClientUserId().equals(userId);
        boolean isFreelancer = job.getFreelancerId() != null && job.getFreelancerId().equals(userId);
        if (!isClient && !isFreelancer) {
            throw new ApplicationException(ErrorCode.JOB_NOT_FOUND, jobId);
        }
        return job;
    }

    private JobApplicationResponse toApplicationResponse(JobApplication application) {
        return JobApplicationResponse.builder()
                .id(application.getId())
                .jobId(application.getJobId())
                .freelancerId(application.getFreelancerId())
                .status(application.getStatus().name())
                .acceptedTermsFingerprint(application.getAcceptedTermsFingerprint())
                .termsAcceptedAt(application.getTermsAcceptedAt())
                .createdAt(application.getCreatedAt())
                .build();
    }

    private DiscoverJobResponse toDiscoverResponse(Job job, User client, JobApplication application) {
        return DiscoverJobResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .description(job.getDescription())
                .category(job.getCategory() == null ? JobCategory.OTHER : job.getCategory())
                .skills(job.getSkills() == null ? List.of() : List.copyOf(job.getSkills()))
                .budgetUsd(job.getBudgetUsd())
                .localPaymentTerms(unifiedTermsPreview(job))
                .status(job.getStatus().name())
                .client(JobClientSummaryResponse.builder()
                        .id(job.getClientUserId())
                        .displayName(client != null ? client.getDisplayName() : null)
                        .build())
                .hasApplied(application != null)
                .applicationId(application != null ? application.getId() : null)
                .applicationStatus(application != null ? application.getStatus().name() : null)
                .deliveryDueAt(job.getDeliveryDueAt())
                .reviewWindowHours(job.getReviewWindowHours())
                .maxRevisions(job.getMaxRevisions())
                .deliverables(deliverablesForJob(job.getId()))
                .acceptanceCriteria(criteriaForJob(job.getId()))
                .createdAt(job.getCreatedAt())
                .build();
    }

    private MyApplicationResponse toMyApplicationResponse(JobApplication application, Job job,
                                                           Map<UUID, User> clients) {
        if (job == null) {
            throw new ApplicationException(ErrorCode.JOB_NOT_FOUND, application.getJobId());
        }
        User client = clients.get(job.getClientUserId());
        return MyApplicationResponse.builder()
                .id(application.getId())
                .status(application.getStatus().name())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .job(MyApplicationJobResponse.builder()
                        .id(job.getId())
                        .title(job.getTitle())
                        .description(job.getDescription())
                        .category(job.getCategory() == null ? JobCategory.OTHER : job.getCategory())
                        .skills(job.getSkills() == null ? List.of() : List.copyOf(job.getSkills()))
                        .budgetUsd(job.getBudgetUsd())
                        .status(job.getStatus().name())
                        .clientDisplayName(client != null ? client.getDisplayName() : null)
                        .createdAt(job.getCreatedAt())
                        .build())
                .build();
    }

    private Map<UUID, User> usersById(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private User requireUserType(UUID userId, UserType expectedType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_EXISTED));
        if (user.getUserType() != expectedType) {
            throw new ApplicationException(expectedType == UserType.CLIENT
                    ? ErrorCode.NOT_A_CLIENT : ErrorCode.NOT_A_FREELANCER);
        }
        return user;
    }

    private void validateBudgetRange(BigDecimal minBudgetUsd, BigDecimal maxBudgetUsd) {
        if ((minBudgetUsd != null && minBudgetUsd.signum() < 0)
                || (maxBudgetUsd != null && maxBudgetUsd.signum() < 0)
                || (minBudgetUsd != null && maxBudgetUsd != null
                && minBudgetUsd.compareTo(maxBudgetUsd) > 0)) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
    }

    private String normalizeApplicationFilter(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "ALL";
        if (!List.of("ALL", "APPLIED", "NOT_APPLIED").contains(normalized)) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
        return normalized;
    }

    private Sort discoverySort(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "NEWEST";
        return switch (normalized) {
            case "NEWEST" -> Sort.by("createdAt").descending();
            case "BUDGET_ASC" -> Sort.by("budgetUsd").ascending();
            case "BUDGET_DESC" -> Sort.by("budgetUsd").descending();
            default -> throw new ApplicationException(ErrorCode.INVALID_DATA);
        };
    }

    private int safePage(int page) {
        return Math.max(page, 0);
    }

    private int safeSize(int size) {
        return size <= 0 ? 10 : Math.min(size, 100);
    }

    private JobSubmission latestSubmission(UUID jobId) {
        return jobSubmissionRepository.findFirstByJobIdOrderByVersionDesc(jobId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.JOB_SUBMISSION_NOT_FOUND, jobId));
    }

    private void rejectLegacyContractWorkflow(UUID jobId) {
        if (workContractRepository.findByJobId(jobId).isPresent()) {
            throw new ApplicationException(ErrorCode.CONTRACT_API_REQUIRED);
        }
    }

    private JobSubmissionResponse toSubmissionResponse(JobSubmission submission) {
        return JobSubmissionResponse.builder()
                .id(submission.getId())
                .jobId(submission.getJobId())
                .freelancerId(submission.getFreelancerId())
                .version(submission.getVersion())
                .summary(submission.getSummary())
                .deliverableUrl(submission.getDeliverableUrl())
                .status(submission.getStatus().name())
                .reviewerFeedback(submission.getReviewerFeedback())
                .reviewedAt(submission.getReviewedAt())
                .createdAt(submission.getCreatedAt())
                .updatedAt(submission.getUpdatedAt())
                .build();
    }

    private JobResponse toResponse(Job job) {
        List<RequirementResponse> deliverables = deliverablesForJob(job.getId());
        List<RequirementResponse> acceptanceCriteria = criteriaForJob(job.getId());
        return JobResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .description(job.getDescription())
                .category(job.getCategory() == null ? JobCategory.OTHER : job.getCategory())
                .skills(job.getSkills() == null ? List.of() : List.copyOf(job.getSkills()))
                .budgetUsd(job.getBudgetUsd())
                .localPaymentTerms(unifiedTermsPreview(job))
                .clientUserId(job.getClientUserId())
                .freelancerId(job.getFreelancerId())
                .status(job.getStatus().name())
                .checkoutOrderId(job.getCheckoutOrderId())
                .taxExportStatus(job.getTaxExportStatus() != null ? job.getTaxExportStatus().name() : null)
                .deliveryDueAt(job.getDeliveryDueAt())
                .reviewWindowHours(job.getReviewWindowHours())
                .maxRevisions(job.getMaxRevisions())
                .deliverables(deliverables)
                .acceptanceCriteria(acceptanceCriteria)
                .contract(contractSummary(job.getId()))
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private UnifiedTermsPreviewResponse unifiedTermsPreview(Job job) {
        if (!Integer.valueOf(1).equals(job.getPaymentFlowVersion())) return null;
        boolean legacyPayout = workContractRepository.findByJobId(job.getId())
                .filter(contract -> PaymentFlow.RAIL.equals(contract.getPaymentRail()))
                .map(contract -> paymentFlowService.usesLegacyFeeOnlyQuote(contract.getId()))
                .orElse(false);
        BigDecimal grossUsd = job.getBudgetUsd().setScale(2, RoundingMode.HALF_UP);
        BigDecimal escrowUsdc = grossUsd.setScale(6);
        BigDecimal feeUsdc = grossUsd.multiply(new BigDecimal("0.03"))
                .setScale(2, RoundingMode.HALF_UP).setScale(6);
        BigDecimal rate = new BigDecimal("25000.00");
        BigDecimal taxableVnd = (legacyPayout ? escrowUsdc : escrowUsdc.subtract(feeUsdc)).multiply(rate)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal taxVnd = taxableVnd.multiply(new BigDecimal("0.10"))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal payoutVnd = legacyPayout
                ? escrowUsdc.subtract(feeUsdc).multiply(rate).setScale(0, RoundingMode.HALF_UP)
                : taxableVnd.subtract(taxVnd);
        return new UnifiedTermsPreviewResponse(PaymentFlow.RAIL, 1, termsFingerprint(job),
                grossUsd, escrowUsdc, feeUsdc, rate, taxableVnd, taxVnd, payoutVnd, grossUsd, 48,
                job.getReviewWindowHours(), job.getMaxRevisions(),
                job.getPaymentNetwork(), job.getPaymentMint(), true, legacyPayout);
    }

    private void requireUnifiedTermsAcceptance(Job job, String acceptedFingerprint) {
        if (Integer.valueOf(1).equals(job.getPaymentFlowVersion())
                && !termsFingerprint(job).equals(acceptedFingerprint)) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
    }

    private String termsFingerprint(Job job) {
        StringBuilder canonical = new StringBuilder("UNIFIED_USDC_PAYOUT|terms-v2|mock-usdc-1:1|vnd-25000|fee-3pct|tax-after-fee-10pct|refund-full|funding-48h");
        appendTerm(canonical, job.getId());
        appendTerm(canonical, job.getClientUserId());
        appendTerm(canonical, job.getTitle());
        appendTerm(canonical, job.getDescription());
        appendTerm(canonical, job.getBudgetUsd().setScale(2, RoundingMode.HALF_UP).toPlainString());
        appendTerm(canonical, job.getDeliveryDueAt());
        appendTerm(canonical, job.getReviewWindowHours());
        appendTerm(canonical, job.getMaxRevisions());
        // Jobs published before chain terms were snapshotted keep their original fingerprint.
        if (job.getPaymentMint() != null) {
            appendTerm(canonical, job.getPaymentNetwork());
            appendTerm(canonical, job.getPaymentMint());
        }
        safeDeliverables(job.getId()).forEach(item -> {
            appendTerm(canonical, item.getOrder());
            appendTerm(canonical, item.getTitle());
            appendTerm(canonical, item.getDescription());
            appendTerm(canonical, item.isRequired());
        });
        safeCriteria(job.getId()).forEach(item -> {
            appendTerm(canonical, item.getOrder());
            appendTerm(canonical, item.getDescription());
            appendTerm(canonical, item.isRequired());
        });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void appendTerm(StringBuilder canonical, Object value) {
        String text = String.valueOf(value);
        canonical.append('|').append(text.length()).append(':').append(text);
    }

    private void saveJobRequirements(UUID jobId, CreateJobRequest request) {
        if (request.getDeliverables() != null) {
            for (int index = 0; index < request.getDeliverables().size(); index++) {
                var input = request.getDeliverables().get(index);
                DeliverableRequirement item = new DeliverableRequirement();
                item.setJobId(jobId);
                item.setOrder(index);
                item.setTitle(input.getTitle().trim());
                item.setDescription(input.getDescription().trim());
                item.setRequired(input.isRequired());
                deliverableRequirementRepository.save(item);
            }
        }
        if (request.getAcceptanceCriteria() != null) {
            for (int index = 0; index < request.getAcceptanceCriteria().size(); index++) {
                var input = request.getAcceptanceCriteria().get(index);
                AcceptanceCriterion item = new AcceptanceCriterion();
                item.setJobId(jobId);
                item.setOrder(index);
                item.setDescription(input.getDescription().trim());
                item.setRequired(input.isRequired());
                acceptanceCriterionRepository.save(item);
            }
        }
    }

    private void snapshotRequirements(UUID jobId, UUID contractId) {
        safeDeliverables(jobId).forEach(source -> {
            DeliverableRequirement snapshot = new DeliverableRequirement();
            snapshot.setContractId(contractId);
            snapshot.setOrder(source.getOrder());
            snapshot.setTitle(source.getTitle());
            snapshot.setDescription(source.getDescription());
            snapshot.setRequired(source.isRequired());
            deliverableRequirementRepository.save(snapshot);
        });
        safeCriteria(jobId).forEach(source -> {
            AcceptanceCriterion snapshot = new AcceptanceCriterion();
            snapshot.setContractId(contractId);
            snapshot.setOrder(source.getOrder());
            snapshot.setDescription(source.getDescription());
            snapshot.setRequired(source.isRequired());
            acceptanceCriterionRepository.save(snapshot);
        });
    }

    private List<DeliverableRequirement> safeDeliverables(UUID jobId) {
        List<DeliverableRequirement> items = deliverableRequirementRepository.findByJobIdOrderByOrderAsc(jobId);
        return items != null ? items : List.of();
    }

    private List<AcceptanceCriterion> safeCriteria(UUID jobId) {
        List<AcceptanceCriterion> items = acceptanceCriterionRepository.findByJobIdOrderByOrderAsc(jobId);
        return items != null ? items : List.of();
    }

    private List<RequirementResponse> deliverablesForJob(UUID jobId) {
        return safeDeliverables(jobId).stream().map(item -> RequirementResponse.builder()
                .id(item.getId()).title(item.getTitle()).description(item.getDescription())
                .required(item.isRequired()).order(item.getOrder()).build()).toList();
    }

    private List<RequirementResponse> criteriaForJob(UUID jobId) {
        return safeCriteria(jobId).stream().map(item -> RequirementResponse.builder()
                .id(item.getId()).description(item.getDescription())
                .required(item.isRequired()).order(item.getOrder()).build()).toList();
    }

    private ContractSummaryResponse contractSummary(UUID jobId) {
        WorkContract contract = workContractRepository.findByJobId(jobId).orElse(null);
        if (contract == null) return null;
        Milestone milestone = milestoneRepository.findByContractId(contract.getId()).orElse(null);
        List<DeliverableRequirement> deliverables = deliverableRequirementRepository
                .findByContractIdOrderByOrderAsc(contract.getId());
        List<AcceptanceCriterion> criteria = acceptanceCriterionRepository
                .findByContractIdOrderByOrderAsc(contract.getId());
        return ContractSummaryResponse.builder()
                .id(contract.getId()).status(contract.getStatus().name())
                .paymentRail(contract.getPaymentRail() == null ? "SIMULATED" : contract.getPaymentRail())
                .milestoneId(milestone != null ? milestone.getId() : null)
                .milestoneStatus(milestone != null ? milestone.getStatus().name() : null)
                .amount(milestone != null ? milestone.getAmount() : contract.getBudgetUsd())
                .currency(milestone != null ? milestone.getCurrency() : "USD")
                .deliveryDueAt(contract.getDeliveryDueAt())
                .reviewWindowHours(contract.getReviewWindowHours())
                .maxRevisions(contract.getMaxRevisions())
                .revisionsUsed(contract.getRevisionsUsed())
                .deliverables(deliverables == null ? List.of() : deliverables.stream().map(item -> RequirementResponse.builder()
                        .id(item.getId()).title(item.getTitle()).description(item.getDescription())
                        .required(item.isRequired()).order(item.getOrder()).build()).toList())
                .acceptanceCriteria(criteria == null ? List.of() : criteria.stream().map(item -> RequirementResponse.builder()
                        .id(item.getId()).description(item.getDescription())
                        .required(item.isRequired()).order(item.getOrder()).build()).toList())
                .build();
    }
}
