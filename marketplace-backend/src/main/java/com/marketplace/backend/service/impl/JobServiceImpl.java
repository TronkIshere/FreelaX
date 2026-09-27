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
import com.marketplace.backend.dto.response.misa.MisaCertificateResult;
import com.marketplace.backend.dto.response.misa.MisaPayoutTransactionResult;
import com.marketplace.backend.dto.response.bofa.CheckoutOrderResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.provider.currency.ExchangeRateProvider;
import com.marketplace.backend.provider.currency.ExchangeRateResult;
import com.marketplace.backend.provider.currency.OffRampProvider;
import com.marketplace.backend.provider.currency.OffRampResult;
import com.marketplace.backend.provider.currency.OnRampProvider;
import com.marketplace.backend.provider.currency.OnRampResult;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobApplicationRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.JobService;
import com.marketplace.backend.service.NotificationService;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JobServiceImpl implements JobService {

    private static final String TAX_RECORD_BLOCKCHAIN = "solana";
    private static final Locale VI_LOCALE = Locale.forLanguageTag("vi-VN");

    UserRepository userRepository;
    JobRepository jobRepository;
    JobApplicationRepository jobApplicationRepository;
    PaymentBackendClient paymentBackendClient;
    MisaBackendClient misaBackendClient;
    NotificationService notificationService;

    OnRampProvider onRampProvider;
    OffRampProvider offRampProvider;
    ExchangeRateProvider exchangeRateProvider;
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

        settlePayoutAndExportTax(job);

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
                .taxableAmountVnd(payoutRecord != null ? payoutRecord.getTaxableAmountVnd() : null)
                .amountVndActual(payoutRecord != null ? payoutRecord.getAmountVndActual() : null)
                .onRampTransactionSignature(payoutRecord != null ? payoutRecord.getOnRampTransactionSignature() : null)
                .build();
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

    private void settlePayoutAndExportTax(Job job) {
        User freelancer;
        FreelancerPayoutRecord payoutRecord;
        try {
            freelancer = userRepository.findById(job.getFreelancerId())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FREELANCER_NOT_FOUND, job.getFreelancerId()));
            payoutRecord = executePayout(job, freelancer);
        } catch (Exception e) {
            job.setTaxExportStatus(TaxExportStatus.FAILED);
            jobRepository.save(job);
            log.error("Chuyển đổi USD -> USDC -> VND thất bại cho job {}: {}", job.getId(), e.getMessage(), e);

            notificationService.notify(
                    job.getFreelancerId(),
                    NotificationType.PAYOUT_FAILED,
                    "Chuyển đổi thanh toán thất bại",
                    "Công việc \"" + job.getTitle() + "\" đã hoàn tất nhưng quá trình chuyển đổi USD → USDC → VNĐ "
                            + "thất bại, hệ thống sẽ cần xử lý lại thủ công.",
                    job.getId());
            return;
        }

        notificationService.notify(
                freelancer.getId(),
                NotificationType.PAYMENT_RECEIVED,
                "Đã nhận được tiền",
                "Công việc \"" + job.getTitle() + "\" đã hoàn tất. Thu nhập chịu thuế ghi nhận "
                        + formatVnd(payoutRecord.getTaxableAmountVnd()) + " VNĐ (quy đổi từ "
                        + payoutRecord.getAmountUsd().stripTrailingZeros().toPlainString() + " USD theo tỷ giá "
                        + formatVnd(payoutRecord.getTaxUsdToVndRate()) + " VNĐ/USD). Số tiền thực nhận sau quy đổi "
                        + "USD → USDC (Solana) → VNĐ là " + formatVnd(payoutRecord.getAmountVndActual())
                        + " VNĐ, sẽ được chuyển khoản thủ công tới "
                        + freelancer.getBankCode() + " - " + freelancer.getBankAccountNumber() + ".",
                job.getId(),
                payoutRecord.getAmountVndActual());

        exportTaxRecordSafely(job, freelancer, payoutRecord);
    }

    private FreelancerPayoutRecord executePayout(Job job, User freelancer) {
        ExchangeRateResult taxRate = exchangeRateProvider.getUsdToVndRate();
        if (taxRate.source() == ExchangeRateSource.FALLBACK_PLACEHOLDER) {
            log.warn("Job {}: không lấy được tỷ giá USD->VND sống, số khai thuế đang dùng FALLBACK PLACEHOLDER ({})",
                    job.getId(), taxRate.rate());
        }
        BigDecimal taxableAmountVnd = job.getBudgetUsd()
                .multiply(taxRate.rate())
                .setScale(0, RoundingMode.HALF_UP);

        OnRampResult onRamp = onRampProvider.convertUsdToUsdc(job.getId(), job.getBudgetUsd());

        OffRampResult offRamp = offRampProvider.convertUsdcToVnd(job.getId(), onRamp.amountUsdcReceived());
        if (offRamp.rateSource() == ExchangeRateSource.FALLBACK_PLACEHOLDER) {
            log.warn("Job {}: không lấy được tỷ giá USDC->VND sống, off-ramp đang dùng FALLBACK PLACEHOLDER ({})",
                    job.getId(), offRamp.usdcToVndRate());
        }

        FreelancerPayoutRecord payoutRecord = new FreelancerPayoutRecord();
        payoutRecord.setJobId(job.getId());
        payoutRecord.setFreelancerId(freelancer.getId());
        payoutRecord.setClientUserId(job.getClientUserId());

        payoutRecord.setAmountUsd(onRamp.amountUsdSource());
        payoutRecord.setOnRampFeeUsd(onRamp.feeUsd());
        payoutRecord.setAmountUsdNet(onRamp.amountUsdNet());
        payoutRecord.setAmountUsdcReceived(onRamp.amountUsdcReceived());
        payoutRecord.setOnRampPurchaseId(onRamp.purchaseId());
        payoutRecord.setOnRampTransactionSignature(onRamp.transactionSignature());
        payoutRecord.setOnRampClientUsdcAta(onRamp.clientUsdcAta());
        payoutRecord.setOnRampReceiptPda(onRamp.receiptPda());

        payoutRecord.setUsdcToVndRate(offRamp.usdcToVndRate());
        payoutRecord.setUsdcToVndRateSource(offRamp.rateSource());
        payoutRecord.setAmountVndBeforeOffRampFee(offRamp.amountVndGross());
        payoutRecord.setOffRampFeeVnd(offRamp.feeVnd());
        payoutRecord.setAmountVndActual(offRamp.amountVndNet());
        payoutRecord.setOffRampReference(offRamp.payoutReference());

        payoutRecord.setTaxUsdToVndRate(taxRate.rate());
        payoutRecord.setTaxRateSource(taxRate.source());
        payoutRecord.setTaxableAmountVnd(taxableAmountVnd);

        return freelancerPayoutRecordRepository.save(payoutRecord);
    }

    private void exportTaxRecordSafely(Job job, User freelancer, FreelancerPayoutRecord payoutRecord) {
        try {
            UUID taxpayerId = misaBackendClient.registerTaxpayerForExternal(
                    freelancer.getId(),
                    freelancer.getDisplayName(),
                    freelancer.getTaxCode(),
                    freelancer.getIdentityNumber(),
                    freelancer.getNationality(),
                    freelancer.getTaxAddress()
            );

            MisaPayoutTransactionResult payoutTx = misaBackendClient.recordPayoutTransaction(
                    taxpayerId,
                    job.getId(),
                    payoutRecord.getAmountUsd(),
                    payoutRecord.getTaxUsdToVndRate(),
                    payoutRecord.getOnRampTransactionSignature(),
                    TAX_RECORD_BLOCKCHAIN);

            MisaCertificateResult certificate = misaBackendClient.createWithholdingCertificate(payoutTx.getId());

            payoutRecord.setMisaPayoutTransactionId(payoutTx.getId());
            payoutRecord.setMisaCertificateId(certificate.getId());
            freelancerPayoutRecordRepository.save(payoutRecord);

            job.setMisaPayoutTransactionId(payoutTx.getId());
            job.setMisaCertificateId(certificate.getId());
            job.setTaxExportStatus(TaxExportStatus.SUCCESS);
            jobRepository.save(job);

        } catch (Exception e) {
            job.setTaxExportStatus(TaxExportStatus.FAILED);
            jobRepository.save(job);
            log.error("Xuất chứng từ MISA thất bại cho job {}: {}", job.getId(), e.getMessage(), e);

            notificationService.notify(
                    job.getFreelancerId(),
                    NotificationType.TAX_EXPORT_FAILED,
                    "Xuất chứng từ thất bại",
                    "Công việc \"" + job.getTitle() + "\" đã hoàn tất nhưng xuất chứng từ thuế thất bại, "
                            + "hệ thống sẽ cần xử lý lại thủ công.",
                    job.getId());
        }
    }

    private String formatVnd(BigDecimal amount) {
        return String.format(VI_LOCALE, "%,d", amount.setScale(0, RoundingMode.HALF_UP).longValueExact());
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