package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.configuration.MisaCertificateProperties;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.misa.MisaCertificateStatusResult;
import com.marketplace.backend.dto.response.misa.MisaPayoutTransactionResult;
import com.marketplace.backend.dto.response.tax.TaxCertificateFile;
import com.marketplace.backend.dto.response.tax.TaxCertificateResponse;
import com.marketplace.backend.entity.ExchangeRateSource;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.PaymentFlow;
import com.marketplace.backend.entity.PaymentFlowStep;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.entity.OffRampStatus;
import com.marketplace.backend.entity.TaxCertificateRecord;
import com.marketplace.backend.entity.TaxCertificateStatus;
import com.marketplace.backend.entity.TaxExportStatus;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.PaymentFlowRepository;
import com.marketplace.backend.repository.PaymentFlowStepRepository;
import com.marketplace.backend.repository.TaxCertificateRecordRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.NotificationService;
import com.marketplace.backend.service.TaxCertificateService;
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
import org.springframework.web.client.HttpClientErrorException;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaxCertificateServiceImpl implements TaxCertificateService {

    private static final String TAX_RECORD_BLOCKCHAIN = "solana";
    private static final Set<TaxCertificateStatus> ISSUED_STATUSES = EnumSet.of(
            TaxCertificateStatus.SIGNED, TaxCertificateStatus.SUBMITTING, TaxCertificateStatus.SUBMITTED,
            TaxCertificateStatus.ACCEPTED, TaxCertificateStatus.REJECTED, TaxCertificateStatus.CORRECTION_REQUIRED,
            TaxCertificateStatus.REPLACED, TaxCertificateStatus.CANCELLED);
    private static final Set<TaxCertificateStatus> SUBMITTED_STATUSES = EnumSet.of(
            TaxCertificateStatus.SUBMITTED, TaxCertificateStatus.ACCEPTED, TaxCertificateStatus.REJECTED);

    TaxCertificateRecordRepository taxCertificateRecordRepository;
    FreelancerPayoutRecordRepository freelancerPayoutRecordRepository;
    JobRepository jobRepository;
    UserRepository userRepository;
    MisaBackendClient misaBackendClient;
    NotificationService notificationService;
    MisaCertificateProperties misaCertificateProperties;
    PaymentFlowRepository paymentFlowRepository;
    PaymentFlowStepRepository paymentFlowStepRepository;

    private static final String UNIFIED_REFERENCE = "unified:";

    @Override
    @Transactional
    public void exportForPayout(Job job, FreelancerPayoutRecord payoutRecord, String transactionReference) {
        requireCompletedPayout(payoutRecord);
        TaxCertificateRecord taxRecord = taxCertificateRecordRepository.findByJobId(job.getId())
                .orElseGet(() -> newRecord(job, payoutRecord, transactionReference));

        if (taxRecord.getMisaCertificateId() != null) {
            return;
        }

        export(taxRecord, job, true);
    }

    @Override
    @Transactional
    public void exportForUnifiedPayout(UUID paymentFlowId) {
        PaymentFlow flow = paymentFlowRepository.findById(paymentFlowId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.TAX_RECORD_INVALID_STATUS, "payment flow"));
        PaymentFlowStep withdrawal = unifiedStep(flow.getId(), "WITHDRAWAL");
        if (!unifiedPayoutConfirmed(flow.getId()) || withdrawal == null || withdrawal.getVndRate() == null)
            throw new ApplicationException(ErrorCode.TAX_RECORD_INVALID_STATUS, "payout not completed");
        Job job = jobRepository.findById(flow.getJobId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.JOB_NOT_FOUND, flow.getJobId()));
        TaxCertificateRecord taxRecord = taxCertificateRecordRepository.findByJobId(job.getId()).orElse(null);
        if (taxRecord == null) {
            taxRecord = new TaxCertificateRecord();
            taxRecord.setJobId(job.getId());
            taxRecord.setFreelancerId(flow.getFreelancerId());
            taxRecord.setClientUserId(flow.getClientId());
            // Income is the Job price released to the Freelancer, at the quote locked on the withdrawal;
            // the FreelaX fee is collected at off-ramp and does not change the income being certified.
            taxRecord.setAmountUsd(flow.getGrossUsd());
            taxRecord.setUsdToVndRate(withdrawal.getVndRate());
            taxRecord.setRateSource(ExchangeRateSource.LOCKED_PAYOUT_QUOTE);
            taxRecord.setRateObservedAt(withdrawal.getConfirmedAt());
            taxRecord.setTaxableIncomeVnd(flow.getGrossUsd().multiply(withdrawal.getVndRate())
                    .setScale(0, RoundingMode.HALF_UP));
            taxRecord.setTransactionReference(UNIFIED_REFERENCE + flow.getId());
            taxRecord = taxCertificateRecordRepository.saveAndFlush(taxRecord);
        } else if (!(UNIFIED_REFERENCE + flow.getId()).equals(taxRecord.getTransactionReference())) {
            throw new ApplicationException(ErrorCode.TAX_RECORD_INVALID_STATUS, "certificate belongs to another payout");
        }
        // One automatic attempt; a failed export waits for the participant's explicit retry.
        if (taxRecord.getMisaCertificateId() != null || taxRecord.getStatus() == TaxCertificateStatus.EXPORT_FAILED)
            return;
        export(taxRecord, job, true);
    }

    private PaymentFlowStep unifiedStep(UUID flowId, String kind) {
        return paymentFlowStepRepository.findByPaymentFlowIdOrderByCreatedAtAsc(flowId).stream()
                .filter(step -> kind.equals(step.getKind())).findFirst().orElse(null);
    }

    private boolean unifiedPayoutConfirmed(UUID flowId) {
        PaymentFlowStep payout = unifiedStep(flowId, "VND_PAYOUT");
        return payout != null && "CONFIRMED".equals(payout.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaxCertificateResponse> listForUser(UUID userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending());
        Page<TaxCertificateRecord> recordPage =
                taxCertificateRecordRepository.findByFreelancerIdOrClientUserId(userId, userId, pageable);

        Map<UUID, String> titles = jobRepository.findAllById(
                        recordPage.getContent().stream().map(TaxCertificateRecord::getJobId).toList())
                .stream()
                .collect(Collectors.toMap(Job::getId, Job::getTitle, (a, b) -> a));

        return PageResponse.<TaxCertificateResponse>builder()
                .currentPage(recordPage.getNumber())
                .pageSize(recordPage.getSize())
                .totalPages(recordPage.getTotalPages())
                .totalElements(recordPage.getTotalElements())
                .data(recordPage.getContent().stream()
                        .map(r -> toResponse(r, titles.get(r.getJobId())))
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TaxCertificateResponse getForParticipant(UUID userId, UUID taxRecordId) {
        TaxCertificateRecord taxRecord = getParticipantOrThrow(userId, taxRecordId);
        return toResponse(taxRecord, jobTitle(taxRecord.getJobId()));
    }

    @Override
    @Transactional(readOnly = true)
    public TaxCertificateResponse getByJobForParticipant(UUID userId, UUID jobId) {
        TaxCertificateRecord taxRecord = taxCertificateRecordRepository.findByJobId(jobId)
                .filter(r -> isParticipant(r, userId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.TAX_RECORD_NOT_FOUND, jobId));
        return toResponse(taxRecord, jobTitle(jobId));
    }

    @Override
    @Transactional
    public TaxCertificateResponse syncForParticipant(UUID userId, UUID taxRecordId) {
        TaxCertificateRecord taxRecord = getParticipantOrThrow(userId, taxRecordId);
        requireCompletedPayoutForRecord(taxRecord);
        if (taxRecord.getMisaCertificateId() == null) {
            throw new ApplicationException(ErrorCode.TAX_RECORD_INVALID_STATUS, taxRecord.getStatus().name());
        }
        refreshAndAdvance(taxRecord);
        return toResponse(taxRecord, jobTitle(taxRecord.getJobId()));
    }

    @Override
    @Transactional
    public TaxCertificateResponse retryExportForParticipant(UUID userId, UUID taxRecordId) {
        TaxCertificateRecord taxRecord = getParticipantOrThrow(userId, taxRecordId);
        if (taxRecord.getStatus() != TaxCertificateStatus.EXPORT_FAILED) {
            throw new ApplicationException(ErrorCode.TAX_RECORD_INVALID_STATUS, taxRecord.getStatus().name());
        }

        Job job = jobRepository.findById(taxRecord.getJobId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.JOB_NOT_FOUND, taxRecord.getJobId()));

        requireCompletedPayoutForRecord(taxRecord);

        export(taxRecord, job, false);
        return toResponse(taxRecord, job.getTitle());
    }

    @Override
    @Transactional(readOnly = true)
    public TaxCertificateFile downloadPdfForParticipant(UUID userId, UUID taxRecordId) {
        TaxCertificateRecord taxRecord = getIssuedDocumentOrThrow(userId, taxRecordId);
        return new TaxCertificateFile(
                documentFileName(taxRecord, "pdf"),
                misaBackendClient.getCertificatePdf(taxRecord.getMisaCertificateId()));
    }

    @Override
    @Transactional(readOnly = true)
    public TaxCertificateFile downloadXmlForParticipant(UUID userId, UUID taxRecordId) {
        TaxCertificateRecord taxRecord = getIssuedDocumentOrThrow(userId, taxRecordId);
        return new TaxCertificateFile(
                documentFileName(taxRecord, "xml"),
                misaBackendClient.getCertificateXml(taxRecord.getMisaCertificateId()));
    }

    @Override
    @Transactional
    public void syncById(UUID taxRecordId) {
        taxCertificateRecordRepository.findById(taxRecordId)
                .filter(r -> r.getMisaCertificateId() != null && r.getStatus().isSyncable()
                        && isPayoutCompleted(r))
                .ifPresent(this::refreshAndAdvance);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findRecordIdsToSync() {
        return taxCertificateRecordRepository.findByStatusIn(TaxCertificateStatus.syncableStatuses()).stream()
                .filter(r -> r.getMisaCertificateId() != null && isPayoutCompleted(r))
                .map(TaxCertificateRecord::getId)
                .toList();
    }

    private TaxCertificateRecord newRecord(Job job, FreelancerPayoutRecord payoutRecord, String transactionReference) {
        TaxCertificateRecord taxRecord = new TaxCertificateRecord();
        taxRecord.setJobId(job.getId());
        taxRecord.setFreelancerId(payoutRecord.getFreelancerId());
        taxRecord.setClientUserId(payoutRecord.getClientUserId());
        taxRecord.setPayoutRecordId(payoutRecord.getId());
        taxRecord.setAmountUsd(payoutRecord.getAmountUsd());
        taxRecord.setUsdToVndRate(payoutRecord.getTaxUsdToVndRate());
        taxRecord.setRateSource(payoutRecord.getTaxRateSource());
        taxRecord.setRateObservedAt(payoutRecord.getTaxRateObservedAt());
        taxRecord.setTaxableIncomeVnd(payoutRecord.getTaxableAmountVnd());
        taxRecord.setTransactionReference(transactionReference);
        taxRecord.setStatus(TaxCertificateStatus.PENDING_EXPORT);
        return taxCertificateRecordRepository.save(taxRecord);
    }

    private void requireCompletedPayout(FreelancerPayoutRecord payoutRecord) {
        if (payoutRecord == null || payoutRecord.getOffRampStatus() != OffRampStatus.COMPLETED) {
            throw new ApplicationException(ErrorCode.TAX_RECORD_INVALID_STATUS, "payout not completed");
        }
    }

    private void requireCompletedPayoutForRecord(TaxCertificateRecord taxRecord) {
        if (!isPayoutCompleted(taxRecord)) {
            throw new ApplicationException(ErrorCode.TAX_RECORD_INVALID_STATUS, "payout not completed");
        }
    }

    private boolean isPayoutCompleted(TaxCertificateRecord taxRecord) {
        if (taxRecord.getPayoutRecordId() != null)
            return freelancerPayoutRecordRepository.findById(taxRecord.getPayoutRecordId())
                    .map(r -> r.getOffRampStatus() == OffRampStatus.COMPLETED)
                    .orElse(false);
        // Unified rail: completed means the partner's VND payout statement is confirmed.
        String reference = taxRecord.getTransactionReference();
        if (reference == null || !reference.startsWith(UNIFIED_REFERENCE)) return false;
        try { return unifiedPayoutConfirmed(UUID.fromString(reference.substring(UNIFIED_REFERENCE.length()))); }
        catch (IllegalArgumentException ex) { return false; }
    }

    private void export(TaxCertificateRecord taxRecord, Job job, boolean notifyOnFailure) {
        try {
            User freelancer = userRepository.findById(taxRecord.getFreelancerId())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FREELANCER_NOT_FOUND, taxRecord.getFreelancerId()));

            if (taxRecord.getMisaTaxpayerId() == null) {
                taxRecord.setMisaTaxpayerId(misaBackendClient.registerTaxpayerForExternal(
                        freelancer.getId(),
                        freelancer.getDisplayName(),
                        freelancer.getTaxCode(),
                        freelancer.getIdentityNumber(),
                        freelancer.getNationality(),
                        freelancer.getTaxAddress()));
                taxCertificateRecordRepository.save(taxRecord);
            }

            if (taxRecord.getMisaPayoutTransactionId() == null) {
                MisaPayoutTransactionResult payoutTx = misaBackendClient.recordPayoutTransaction(
                        taxRecord.getMisaTaxpayerId(),
                        job.getId(),
                        taxRecord.getAmountUsd(),
                        taxRecord.getUsdToVndRate(),
                        taxRecord.getTransactionReference(),
                        TAX_RECORD_BLOCKCHAIN);
                taxRecord.setMisaPayoutTransactionId(payoutTx.getId());
                taxCertificateRecordRepository.save(taxRecord);
            }

            MisaCertificateStatusResult certificate =
                    misaBackendClient.createWithholdingCertificate(taxRecord.getMisaPayoutTransactionId());

            taxRecord.setMisaCertificateId(UUID.fromString(certificate.getId()));
            taxRecord.setStatus(TaxCertificateStatus.DRAFT);
            applyMisaResult(taxRecord, certificate);

            job.setMisaPayoutTransactionId(taxRecord.getMisaPayoutTransactionId());
            job.setMisaCertificateId(taxRecord.getMisaCertificateId());
            job.setTaxExportStatus(TaxExportStatus.SUCCESS);
            jobRepository.save(job);

            freelancerPayoutRecordRepository.findByJobId(job.getId()).ifPresent(payoutRecord -> {
                payoutRecord.setMisaPayoutTransactionId(taxRecord.getMisaPayoutTransactionId());
                payoutRecord.setMisaCertificateId(taxRecord.getMisaCertificateId());
                freelancerPayoutRecordRepository.save(payoutRecord);
            });

            refreshAndAdvance(taxRecord);

        } catch (Exception e) {
            taxRecord.setStatus(TaxCertificateStatus.EXPORT_FAILED);
            taxRecord.setLastError("TAX_EXPORT_FAILED");
            taxCertificateRecordRepository.save(taxRecord);

            job.setTaxExportStatus(TaxExportStatus.FAILED);
            jobRepository.save(job);
            log.warn("Tax export requires reconciliation for job {}", job.getId());

            if (notifyOnFailure) {
                notificationService.notify(
                        taxRecord.getFreelancerId(),
                        NotificationType.TAX_EXPORT_FAILED,
                        "Xuất chứng từ thất bại",
                        "Công việc \"" + job.getTitle() + "\" đã hoàn tất nhưng xuất chứng từ thuế thất bại, "
                                + "hệ thống sẽ cần xử lý lại thủ công.",
                        job.getId());
            }
        }
    }

    private void refreshAndAdvance(TaxCertificateRecord taxRecord) {
        refreshFromMisa(taxRecord);

        if (misaCertificateProperties.isAutoIssue() && taxRecord.getStatus() == TaxCertificateStatus.DRAFT) {
            runStep(taxRecord, "issue", () -> misaBackendClient.issueCertificate(
                    taxRecord.getMisaCertificateId(),
                    misaCertificateProperties.getDigitalCertificateSerial(),
                    misaCertificateProperties.getSignatureMode()));
        }

        if (misaCertificateProperties.isAutoSubmit()
                && (taxRecord.getStatus() == TaxCertificateStatus.SIGNED
                || taxRecord.getStatus() == TaxCertificateStatus.SUBMITTING)) {
            runStep(taxRecord, "submit", () -> misaBackendClient.submitCertificate(
                    taxRecord.getMisaCertificateId(),
                    misaCertificateProperties.getSubmissionMode(),
                    "job-" + taxRecord.getJobId() + "-submit"));
        }
    }

    private void runStep(TaxCertificateRecord taxRecord, String step, MisaCall call) {
        try {
            applyMisaResult(taxRecord, call.execute());
        } catch (HttpClientErrorException.Conflict e) {
            log.info("Chung tu {} da {} truoc do, dong bo lai trang thai", taxRecord.getMisaCertificateId(), step);
        } catch (Exception e) {
            taxRecord.setLastError("TAX_" + step.toUpperCase(java.util.Locale.ROOT) + "_FAILED");
            taxCertificateRecordRepository.save(taxRecord);
            log.warn("Tax step {} requires reconciliation for certificate {}", step, taxRecord.getMisaCertificateId());
            return;
        }
        refreshFromMisa(taxRecord);
    }

    private void refreshFromMisa(TaxCertificateRecord taxRecord) {
        try {
            applyMisaResult(taxRecord, misaBackendClient.getCertificateStatus(taxRecord.getMisaCertificateId()));
        } catch (Exception e) {
            taxRecord.setLastError("TAX_SYNC_FAILED");
            taxCertificateRecordRepository.save(taxRecord);
            log.warn("Tax sync requires reconciliation for certificate {}", taxRecord.getMisaCertificateId());
        }
    }

    private void applyMisaResult(TaxCertificateRecord taxRecord, MisaCertificateStatusResult result) {
        if (result == null) {
            return;
        }

        if (StringUtils.hasText(result.getStatus())) {
            taxRecord.setMisaStatusRaw(result.getStatus());
            TaxCertificateStatus.fromMisa(result.getStatus()).ifPresentOrElse(
                    taxRecord::setStatus,
                    () -> log.warn("Trang thai chung tu tu MISA khong xac dinh: {}", result.getStatus()));
        }
        if (StringUtils.hasText(result.getCertificateNumber())) {
            taxRecord.setCertificateNumber(result.getCertificateNumber());
        }
        if (StringUtils.hasText(result.getSymbol())) {
            taxRecord.setCertificateSymbol(result.getSymbol());
        }
        if (StringUtils.hasText(result.getLookupCode())) {
            taxRecord.setLookupCode(result.getLookupCode());
        }
        if (StringUtils.hasText(result.getSubmissionId())) {
            taxRecord.setSubmissionId(result.getSubmissionId());
        }
        if (StringUtils.hasText(result.getTaxAuthorityReference())) {
            taxRecord.setTaxAuthorityReference(result.getTaxAuthorityReference());
        }
        if (result.getTaxWithheld() != null) {
            taxRecord.setTaxWithheldVnd(result.getTaxWithheld().setScale(0, RoundingMode.HALF_UP));
        }

        LocalDateTime now = LocalDateTime.now();
        if (taxRecord.getIssuedAt() == null && ISSUED_STATUSES.contains(taxRecord.getStatus())) {
            taxRecord.setIssuedAt(now);
        }
        if (taxRecord.getSubmittedAt() == null && SUBMITTED_STATUSES.contains(taxRecord.getStatus())) {
            taxRecord.setSubmittedAt(now);
        }
        taxRecord.setLastSyncedAt(now);
        taxRecord.setLastError(null);
        taxCertificateRecordRepository.save(taxRecord);
    }

    private TaxCertificateRecord getParticipantOrThrow(UUID userId, UUID taxRecordId) {
        return taxCertificateRecordRepository.findById(taxRecordId)
                .filter(r -> isParticipant(r, userId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.TAX_RECORD_NOT_FOUND, taxRecordId));
    }

    private TaxCertificateRecord getIssuedDocumentOrThrow(UUID userId, UUID taxRecordId) {
        TaxCertificateRecord taxRecord = getParticipantOrThrow(userId, taxRecordId);
        if (taxRecord.getMisaCertificateId() == null) {
            throw new ApplicationException(ErrorCode.TAX_RECORD_INVALID_STATUS, taxRecord.getStatus().name());
        }
        return taxRecord;
    }

    private String documentFileName(TaxCertificateRecord taxRecord, String extension) {
        String number = StringUtils.hasText(taxRecord.getCertificateNumber())
                ? taxRecord.getCertificateNumber()
                : taxRecord.getMisaCertificateId().toString();
        String symbol = StringUtils.hasText(taxRecord.getCertificateSymbol())
                ? taxRecord.getCertificateSymbol() + "-"
                : "";
        String safeName = ("chung-tu-khau-tru-" + symbol + number).replaceAll("[^A-Za-z0-9._-]", "_");
        return safeName + "." + extension;
    }

    private boolean isParticipant(TaxCertificateRecord taxRecord, UUID userId) {
        return userId.equals(taxRecord.getFreelancerId()) || userId.equals(taxRecord.getClientUserId());
    }

    private String jobTitle(UUID jobId) {
        return jobRepository.findById(jobId).map(Job::getTitle).orElse(null);
    }

    private TaxCertificateResponse toResponse(TaxCertificateRecord r, String jobTitle) {
        return TaxCertificateResponse.builder()
                .id(r.getId())
                .jobId(r.getJobId())
                .jobTitle(jobTitle)
                .freelancerId(r.getFreelancerId())
                .clientUserId(r.getClientUserId())
                .status(r.getStatus().name())
                .statusLabel(r.getStatus().getLabel())
                .amountUsd(r.getAmountUsd())
                .usdToVndRate(r.getUsdToVndRate())
                .rateSource(r.getRateSource().name())
                .rateObservedAt(r.getRateObservedAt())
                .taxableIncomeVnd(r.getTaxableIncomeVnd())
                .taxWithheldVnd(r.getTaxWithheldVnd())
                .certificateNumber(r.getCertificateNumber())
                .certificateSymbol(r.getCertificateSymbol())
                .lookupCode(r.getLookupCode())
                .misaCertificateId(r.getMisaCertificateId())
                .misaPayoutTransactionId(r.getMisaPayoutTransactionId())
                .transactionReference(r.getTransactionReference())
                .submissionId(r.getSubmissionId())
                .taxAuthorityReference(r.getTaxAuthorityReference())
                .issuedAt(r.getIssuedAt())
                .submittedAt(r.getSubmittedAt())
                .lastSyncedAt(r.getLastSyncedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    @FunctionalInterface
    private interface MisaCall {
        MisaCertificateStatusResult execute();
    }
}
