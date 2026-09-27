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
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JobServiceImpl implements JobService {

    UserRepository userRepository;
    JobRepository jobRepository;
    JobApplicationRepository jobApplicationRepository;
    PaymentBackendClient paymentBackendClient;
    MisaBackendClient misaBackendClient;
    NotificationService notificationService;

    // ----- Quy doi tien te USD -> USDC -> VND (them moi) -----
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

        exportTaxRecordSafely(job);

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

        return JobPaymentStatusResponse.builder()
                .jobId(job.getId())
                .checkoutOrderId(job.getCheckoutOrderId())
                .checkoutOrderStatus(checkoutOrder.getStatus())
                .taxExportStatus(job.getTaxExportStatus() != null ? job.getTaxExportStatus().name() : null)
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

    /**
     * Chuoi quy doi tien te + xuat chung tu thue cho 1 job vua duoc approve():
     *
     *  1) USD (ngan sach job)  -[on-ramp, tru phi gia lap]->  USDC (net)
     *  2) Lay ty gia USDC->VND hien tai (uu tien API song, fallback neu loi)
     *  3) USDC (net)  -[off-ramp, tru phi gia lap]->  VND (net)
     *     => day la SO TIEN FREELANCER THUC SU NHAN DUOC (amountVndActual)
     *
     *  Rieng so gui sang misa-backend de khai thue dung amountUsdcGross
     *  (KHONG tru phi on-ramp) + ty gia hien tai -- tuc "so tien cua cong
     *  viec do quy doi sang VND theo ty gia hien tai", khac voi so thuc
     *  nhan o tren.
     */
    private void exportTaxRecordSafely(Job job) {
        try {
            User freelancer = userRepository.findById(job.getFreelancerId())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FREELANCER_NOT_FOUND, job.getFreelancerId()));

            UUID taxpayerId = misaBackendClient.registerTaxpayerForExternal(
                    freelancer.getId(),
                    freelancer.getDisplayName(),
                    freelancer.getTaxCode(),
                    freelancer.getIdentityNumber(),
                    freelancer.getNationality(),
                    freelancer.getTaxAddress()
            );

            // 1) USD -> USDC (on-ramp, co tru phi gia lap)
            OnRampResult onRamp = onRampProvider.convertUsdToUsdc(job.getBudgetUsd());

            // 2) Ty gia USDC -> VND hien tai
            ExchangeRateResult rate = exchangeRateProvider.getUsdcToVndRate();
            if (rate.source() == ExchangeRateResult.RateSource.FALLBACK_PLACEHOLDER) {
                log.warn("Job {}: khong lay duoc ty gia USDC->VND song, dang dung FALLBACK PLACEHOLDER ({})",
                        job.getId(), rate.rate());
            }

            // 3) USDC (net, da tru phi on-ramp) -> VND (off-ramp, co tru phi gia lap)
            //    => so tien freelancer THUC SU nhan duoc
            OffRampResult offRamp = offRampProvider.convertUsdcToVnd(onRamp.amountUsdcNet(), rate.rate());

            // Gui sang Misa: dung amountUsdcGross (KHONG tru phi on-ramp) + ty gia HIEN TAI
            // -- misa-backend se tu tinh amountVndGross = amountUsdc * exchangeRate (Muc 9.4.3)
            MisaPayoutTransactionResult payoutTx = misaBackendClient.recordPayoutTransaction(
                    taxpayerId, job.getId(), onRamp.amountUsdcGross(), rate.rate());

            MisaCertificateResult certificate = misaBackendClient.createWithholdingCertificate(payoutTx.getId());

            BigDecimal amountVndGross = onRamp.amountUsdcGross()
                    .multiply(rate.rate())
                    .setScale(0, RoundingMode.HALF_UP);

            // Luu lai ban ghi payout day du: ca so khai thue lan so thuc nhan
            FreelancerPayoutRecord payoutRecord = new FreelancerPayoutRecord();
            payoutRecord.setJobId(job.getId());
            payoutRecord.setFreelancerId(freelancer.getId());
            payoutRecord.setClientUserId(job.getClientUserId());
            payoutRecord.setAmountUsd(onRamp.amountUsdSource());
            payoutRecord.setAmountUsdcGross(onRamp.amountUsdcGross());
            payoutRecord.setOnRampFeeUsdc(onRamp.feeUsdc());
            payoutRecord.setAmountUsdcNet(onRamp.amountUsdcNet());
            payoutRecord.setExchangeRateUsed(rate.rate());
            payoutRecord.setRateSource(rate.source() == ExchangeRateResult.RateSource.LIVE_COINGECKO
                    ? FreelancerPayoutRecord.RateSource.LIVE_COINGECKO
                    : FreelancerPayoutRecord.RateSource.FALLBACK_PLACEHOLDER);
            payoutRecord.setAmountVndGross(amountVndGross);
            payoutRecord.setOffRampFeeVnd(offRamp.feeVnd());
            payoutRecord.setAmountVndActual(offRamp.amountVndNet());
            payoutRecord.setMisaPayoutTransactionId(payoutTx.getId());
            payoutRecord.setMisaCertificateId(certificate.getId());
            freelancerPayoutRecordRepository.save(payoutRecord);

            job.setMisaPayoutTransactionId(payoutTx.getId());
            job.setMisaCertificateId(certificate.getId());
            job.setTaxExportStatus(TaxExportStatus.SUCCESS);
            jobRepository.save(job);

            notificationService.notify(
                    freelancer.getId(),
                    NotificationType.PAYMENT_RECEIVED,
                    "Đã nhận được tiền",
                    "Công việc \"" + job.getTitle() + "\" đã hoàn tất. Khoản thu nhập chịu thuế ghi nhận ~"
                            + amountVndGross + " VNĐ. Số tiền thực nhận sau quy đổi USD → USDC → VNĐ là "
                            + offRamp.amountVndNet() + " VNĐ, sẽ được chuyển khoản thủ công tới "
                            + freelancer.getBankCode() + " - " + freelancer.getBankAccountNumber() + ".",
                    job.getId(),
                    offRamp.amountVndNet());

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