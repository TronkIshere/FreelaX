package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.client.PaypalBackendClient;
import com.marketplace.backend.dto.request.job.CreateJobRequest;
import com.marketplace.backend.dto.request.job.UpdateJobRequest;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.job.JobPaymentStatusResponse;
import com.marketplace.backend.dto.response.job.JobResponse;
import com.marketplace.backend.dto.response.job.PayJobResponse;
import com.marketplace.backend.dto.response.misa.MisaCertificateResult;
import com.marketplace.backend.dto.response.misa.MisaPayoutTransactionResult;
import com.marketplace.backend.dto.response.paypal.CheckoutOrderResult;
import com.marketplace.backend.dto.response.paypal.PayeeStatusResult;
import com.marketplace.backend.dto.response.paypal.PayoutReleaseResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.JobService;
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

import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JobServiceImpl implements JobService {
    UserRepository userRepository;
    JobRepository jobRepository;
    PaypalBackendClient paypalBackendClient;
    MisaBackendClient misaBackendClient;


    @Override
    public PageResponse<JobResponse> listForUser(UUID userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending());
        Page<Job> jobPage = jobRepository.findByClientUserIdOrFreelancerUserId(userId, userId, pageable);

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

        PayeeStatusResult payeeStatus = paypalBackendClient.getPayeeStatus(job.getFreelancerUserId());

        if (!payeeStatus.isRegistered() || !payeeStatus.isActive()) {
            throw new ApplicationException(ErrorCode.FREELANCER_NOT_LINKED_TO_PAYPAL, job.getFreelancerUserId());
        }

        CheckoutOrderResult checkoutOrder = paypalBackendClient.createCheckoutOrder(
                payeeStatus.getPayeeId(),
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

        CheckoutOrderResult captured = paypalBackendClient.captureCheckoutOrder(job.getCheckoutOrderId());

        if (!"CAPTURED".equals(captured.getStatus())) {
            throw new ApplicationException(ErrorCode.PAYPAL_BACKEND_CALL_FAILED, "capture:" + captured.getStatus());
        }

        job.setStatus(JobStatus.IN_PROGRESS);
        jobRepository.save(job);

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

        CheckoutOrderResult checkoutOrder = paypalBackendClient.getCheckoutOrder(job.getCheckoutOrderId());

        String payoutStatus = null;
        if (job.getPayoutReleaseId() != null) {
            PayoutReleaseResult release = paypalBackendClient.getPayoutRelease(job.getPayoutReleaseId());
            payoutStatus = release.getStatus();
        }

        return JobPaymentStatusResponse.builder()
                .jobId(job.getId())
                .checkoutOrderId(job.getCheckoutOrderId())
                .checkoutOrderStatus(checkoutOrder.getStatus())
                .payoutReleaseId(job.getPayoutReleaseId())
                .payoutReleaseStatus(payoutStatus)
                .build();
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
        if (!job.getClientUserId().equals(userId) && !job.getFreelancerUserId().equals(userId)) {
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
                .freelancerUserId(job.getFreelancerUserId())
                .status(job.getStatus().name())
                .checkoutOrderId(job.getCheckoutOrderId())
                .payoutReleaseId(job.getPayoutReleaseId())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public JobResponse create(UUID clientUserId, CreateJobRequest request) {
        User freelancer = userRepository.findById(request.getFreelancerId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.FREELANCER_NOT_FOUND, request.getFreelancerId()));

        if (freelancer.getUserType() != UserType.FREELANCER) {
            throw new ApplicationException(ErrorCode.USER_IS_NOT_FREELANCER, request.getFreelancerId());
        }

        Job job = new Job();
        job.setClientUserId(clientUserId);
        job.setFreelancerId(freelancer.getId());
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setBudgetUsd(request.getBudgetUsd());
        job.setStatus(JobStatus.OPEN);

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

        PayoutReleaseResult release = paypalBackendClient.releasePayout(job.getCheckoutOrderId());

        job.setPayoutReleaseId(release.getId());
        job.setStatus(JobStatus.COMPLETED);
        jobRepository.save(job);

        exportTaxRecordSafely(job);

        return toResponse(job);
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

            MisaPayoutTransactionResult payoutTx = misaBackendClient.recordPayoutTransaction(
                    freelancer.getMisaTaxpayerId(), job.getPayoutReleaseId(), job.getBudgetUsd());

            MisaCertificateResult certificate = misaBackendClient.createWithholdingCertificate(payoutTx.getId());

            job.setMisaPayoutTransactionId(payoutTx.getId());
            job.setMisaCertificateId(certificate.getId());
            job.setTaxExportStatus(TaxExportStatus.SUCCESS);
            jobRepository.save(job);
        } catch (Exception e) {
            job.setTaxExportStatus(TaxExportStatus.FAILED);
            jobRepository.save(job);
            log.error("Xuất chứng từ MISA thất bại cho job {}: {}", job.getId(), e.getMessage(), e);
        }
    }
}
