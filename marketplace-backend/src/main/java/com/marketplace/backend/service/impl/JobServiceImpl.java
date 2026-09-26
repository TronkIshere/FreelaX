package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.job.AssignFreelancerRequest;
import com.marketplace.backend.dto.request.job.CreateJobRequest;
import com.marketplace.backend.dto.request.job.UpdateJobRequest;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.job.JobPaymentStatusResponse;
import com.marketplace.backend.dto.response.job.JobResponse;
import com.marketplace.backend.dto.response.job.PayJobResponse;
import com.marketplace.backend.dto.response.misa.MisaCertificateResult;
import com.marketplace.backend.dto.response.misa.MisaPayoutTransactionResult;
import com.marketplace.backend.dto.response.bofa.CheckoutOrderResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JobServiceImpl implements JobService {

    private static final int USDC_SCALE = 6;
    private static final BigDecimal USD_TO_USDC_PEG_RATE = BigDecimal.ONE;
    private static final BigDecimal PLACEHOLDER_USDC_TO_VND_RATE = new BigDecimal("25000");

    UserRepository userRepository;
    JobRepository jobRepository;
    PaymentBackendClient paymentBackendClient;
    MisaBackendClient misaBackendClient;
    NotificationService notificationService;

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

        return toResponse(job);
    }

    @Override
    @Transactional
    public JobResponse assignFreelancer(UUID clientUserId, UUID jobId, AssignFreelancerRequest request) {
        Job job = getOwnedByClientOrThrow(clientUserId, jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        User freelancer = validateAndGetFreelancer(request.getFreelancerId());
        job.setFreelancerId(freelancer.getId());
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
    public PayJobResponse pay(UUID clientUserId, UUID jobId) {
        Job job = getOwnedByClientOrThrow(clientUserId, jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        if (job.getFreelancerId() == null) {
            throw new ApplicationException(ErrorCode.JOB_FREELANCER_NOT_ASSIGNED, jobId);
        }

        // Freelancer giờ chỉ cần tồn tại đúng loại tài khoản -- không còn phụ thuộc
        // payment-backend/payee nữa (freelancer nhận tiền qua ngân hàng đã đăng ký, xử lý thủ công).
        userRepository.findById(job.getFreelancerId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.FREELANCER_NOT_FOUND, job.getFreelancerId()));

        CheckoutOrderResult checkoutOrder = paymentBackendClient.createCheckoutOrder(
                job.getClientUserId(),
                job.getId(),
                job.getBudgetUsd()
        );

        job.setCheckoutOrderId(checkoutOrder.getId());
        job.setStatus(JobStatus.AWAITING_PAYMENT);
        jobRepository.save(job);

        return PayJobResponse.builder()
                .jobId(job.getId())
                .checkoutOrderId(checkoutOrder.getId())
                .approvalUrl(checkoutOrder.getApprovalUrl())
                .status(job.getStatus().name())
                .build();
    }

    @Override
    @Transactional
    public JobResponse confirmPayment(UUID clientUserId, UUID jobId) {
        Job job = getOwnedByClientOrThrow(clientUserId, jobId);

        if (job.getStatus() != JobStatus.AWAITING_PAYMENT || job.getCheckoutOrderId() == null) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        CheckoutOrderResult captured = paymentBackendClient.captureCheckoutOrder(job.getCheckoutOrderId());

        if (!"CAPTURED".equals(captured.getStatus())) {
            throw new ApplicationException(ErrorCode.PAYMENT_BACKEND_CALL_FAILED, "capture:" + captured.getStatus());
        }

        job.setStatus(JobStatus.IN_PROGRESS);
        jobRepository.save(job);

        notificationService.notify(
                clientUserId,
                NotificationType.PAYMENT_SENT,
                "Thanh toán thành công",
                "Bạn đã thanh toán thành công cho công việc \"" + job.getTitle() + "\".",
                job.getId());

        return toResponse(job);
    }

    @Override
    @Transactional
    public JobResponse approve(UUID clientUserId, UUID jobId) {
        Job job = getOwnedByClientOrThrow(clientUserId, jobId);

        if (job.getStatus() != JobStatus.IN_PROGRESS || job.getCheckoutOrderId() == null) {
            throw new ApplicationException(ErrorCode.INVALID_JOB_STATUS);
        }

        // ĐÃ BỎ HOÀN TOÀN: paymentBackendClient.releasePayout(...) -- không còn chuyển tiền
        // ngược lại payment-backend để trả cho freelancer. Freelancer nhận tiền qua ngân hàng
        // đã đăng ký, xử lý thủ công ngoài hệ thống; marketplace-backend chỉ ghi nhận,
        // xuất chứng từ thuế, và gửi thông báo.
        job.setStatus(JobStatus.COMPLETED);
        jobRepository.save(job);

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

    private void exportTaxRecordSafely(Job job) {
        try {
            User freelancer = userRepository.findById(job.getFreelancerId())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FREELANCER_NOT_FOUND, job.getFreelancerId()));

            if (freelancer.getMisaTaxpayerId() == null) {
                job.setTaxExportStatus(TaxExportStatus.SKIPPED_NO_TAXPAYER);
                jobRepository.save(job);
                log.warn("Job {} approved nhưng freelancer {} chưa liên kết misaTaxpayerId -- bỏ qua xuất chứng từ",
                        job.getId(), freelancer.getId());
                return;
            }

            BigDecimal amountUsdc = convertUsdToUsdc(job.getBudgetUsd());
            BigDecimal usdcToVndRate = getUsdcToVndRatePlaceholder();
            BigDecimal amountVnd = amountUsdc.multiply(usdcToVndRate).setScale(0, RoundingMode.HALF_UP);

            log.warn("Job {}: dang dung ty gia USDC->VND PLACEHOLDER ({}), CHUA phai ty gia thuc",
                    job.getId(), usdcToVndRate);

            // Không còn payoutReleaseId của payment-backend -- dùng job.getId() làm định danh
            // tham chiếu duy nhất gửi sang misa-backend (thay cho platformPayoutId cũ).
            MisaPayoutTransactionResult payoutTx = misaBackendClient.recordPayoutTransaction(
                    freelancer.getMisaTaxpayerId(), job.getId(), amountUsdc, usdcToVndRate);

            MisaCertificateResult certificate = misaBackendClient.createWithholdingCertificate(payoutTx.getId());

            job.setMisaPayoutTransactionId(payoutTx.getId());
            job.setMisaCertificateId(certificate.getId());
            job.setTaxExportStatus(TaxExportStatus.SUCCESS);
            jobRepository.save(job);

            notificationService.notify(
                    freelancer.getId(),
                    NotificationType.PAYMENT_RECEIVED,
                    "Đã nhận được tiền",
                    "Công việc \"" + job.getTitle() + "\" đã hoàn tất. Khoản thu nhập ~" + amountVnd
                            + " VNĐ đã được ghi nhận và xuất chứng từ khấu trừ thuế. Tiền sẽ được chuyển khoản thủ công tới "
                            + freelancer.getBankCode() + " - " + freelancer.getBankAccountNumber() + ".",
                    job.getId());
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

    private BigDecimal convertUsdToUsdc(BigDecimal amountUsd) {
        return amountUsd.multiply(USD_TO_USDC_PEG_RATE).setScale(USDC_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal getUsdcToVndRatePlaceholder() {
        return PLACEHOLDER_USDC_TO_VND_RATE;
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