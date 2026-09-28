package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.job.AssignFreelancerRequest;
import com.marketplace.backend.dto.request.job.CreateJobRequest;
import com.marketplace.backend.dto.request.job.UpdateJobRequest;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.job.CertificateSummaryResponse;
import com.marketplace.backend.dto.response.job.JobApplicationResponse;
import com.marketplace.backend.dto.response.job.JobPaymentStatusResponse;
import com.marketplace.backend.dto.response.job.JobResponse;
import com.marketplace.backend.dto.response.bofa.CheckoutOrderResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobApplicationRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.JobService;
import com.marketplace.backend.service.NotificationService;
import com.marketplace.backend.service.PayoutService;
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

import java.util.List;
import java.util.UUID;
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
    PaymentBackendClient paymentBackendClient;
    MisaBackendClient misaBackendClient;
    NotificationService notificationService;

    PayoutService payoutService;
    FreelancerPayoutRecordRepository freelancerPayoutRecordRepository;

    @Override
    @Transactional
    public JobResponse create(UUID clientUserId, CreateJobRequest request) {
        Job job = new Job();
        job.setClientUserId(clientUserId);
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setBudgetUsd(request.getBudgetUsd());
        job.setStatus(JobStatus.OPEN);

        if (request.getFreelancerId() != null) {
            job.setFreelancerId(validateAndGetFreelancer(request.getFreelancerId()).getId());
        }

        jobRepository.save(job);

        CheckoutOrderResult checkoutOrder = paymentBackendClient.createCheckoutOrder(
                clientUserId,
                job.getId(),
                request.getBudgetUsd(),
                request.getPayerBankCode(),
                request.getPayerBankAccountNumber(),
                request.getPayerBankAccountHolderName()
        );
        job.setCheckoutOrderId(checkoutOrder.getId());

        if (job.getFreelancerId() != null) {
            job.setStatus(JobStatus.IN_PROGRESS);
        }

        jobRepository.save(job);

        return toResponse(job);
    }

    @Override
    @Transactional
    public JobApplicationResponse apply(UUID freelancerId, UUID jobId) {
        Job job = getOrThrow(jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        validateAndGetFreelancer(freelancerId);

        if (jobApplicationRepository.findByJobIdAndFreelancerId(jobId, freelancerId).isPresent()) {
            throw new ApplicationException(ErrorCode.ALREADY_APPLIED, jobId);
        }

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setFreelancerId(freelancerId);
        application.setStatus(JobApplicationStatus.PENDING);
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
        Job job = getOwnedByClientOrThrow(clientUserId, jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        User freelancer = validateAndGetFreelancer(request.getFreelancerId());

        JobApplication acceptedApplication = jobApplicationRepository
                .findByJobIdAndFreelancerId(jobId, freelancer.getId())
                .filter(a -> a.getStatus() == JobApplicationStatus.PENDING)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FREELANCER_NOT_APPLIED, jobId));

        acceptedApplication.setStatus(JobApplicationStatus.ACCEPTED);
        jobApplicationRepository.save(acceptedApplication);

        for (JobApplication other : jobApplicationRepository.findByJobIdAndStatus(jobId, JobApplicationStatus.PENDING)) {
            other.setStatus(JobApplicationStatus.REJECTED);
            jobApplicationRepository.save(other);
        }

        job.setFreelancerId(freelancer.getId());
        job.setStatus(JobStatus.IN_PROGRESS);
        jobRepository.save(job);

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

        if (request.getTitle() != null) {
            job.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            job.setDescription(request.getDescription());
        }
        if (request.getBudgetUsd() != null) {
            job.setBudgetUsd(request.getBudgetUsd());
        }

        jobRepository.save(job);

        return toResponse(job);
    }

    @Override
    @Transactional
    public JobResponse approve(UUID clientUserId, UUID jobId) {
        Job job = getOwnedByClientOrThrow(clientUserId, jobId);

        if (job.getStatus() != JobStatus.IN_PROGRESS || job.getCheckoutOrderId() == null) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        CheckoutOrderResult captured = paymentBackendClient.captureCheckoutOrder(job.getCheckoutOrderId());

        if (!"CAPTURED".equals(captured.getStatus())) {
            throw new ApplicationException(ErrorCode.PAYMENT_BACKEND_CALL_FAILED, "capture:" + captured.getStatus());
        }

        job.setStatus(JobStatus.COMPLETED);
        jobRepository.save(job);

        notificationService.notify(
                clientUserId,
                NotificationType.PAYMENT_SENT,
                "Thanh toán thành công",
                "Bạn đã thanh toán thành công cho công việc \"" + job.getTitle() + "\".",
                job.getId());

        payoutService.settle(job);

        return toResponse(job);
    }

    @Override
    @Transactional
    public JobResponse cancel(UUID clientUserId, UUID jobId) {
        Job job = getOwnedByClientOrThrow(clientUserId, jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);

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
                .estimatedAmountVnd(payoutRecord != null ? payoutRecord.getAmountVndEstimated() : null)
                .usdcToVndRateSource(payoutRecord != null && payoutRecord.getUsdcToVndRateSource() != null
                        ? payoutRecord.getUsdcToVndRateSource().name() : null)
                .taxableAmountVnd(payoutRecord != null ? payoutRecord.getTaxableAmountVnd() : null)
                .taxRateSource(payoutRecord != null ? payoutRecord.getTaxRateSource().name() : null)
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

    @Override
    public List<CertificateSummaryResponse> listCertificatesForFreelancer(UUID freelancerId) {
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
        jobRepository.findByFreelancerIdAndMisaCertificateId(freelancerId, certificateId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.JOB_NOT_FOUND, certificateId));

        return misaBackendClient.getCertificatePdf(certificateId);
    }

    private Job getOrThrow(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.JOB_NOT_FOUND, jobId));
    }

    private Job getOwnedByClientOrThrow(UUID clientUserId, UUID jobId) {
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
                .createdAt(application.getCreatedAt())
                .build();
    }

    private JobResponse toResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .description(job.getDescription())
                .budgetUsd(job.getBudgetUsd())
                .clientUserId(job.getClientUserId())
                .freelancerId(job.getFreelancerId())
                .status(job.getStatus().name())
                .checkoutOrderId(job.getCheckoutOrderId())
                .taxExportStatus(job.getTaxExportStatus() != null ? job.getTaxExportStatus().name() : null)
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
