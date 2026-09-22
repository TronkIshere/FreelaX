package com.misa.backend.service.impl;

import com.misa.backend.configuration.MisaProperties;
import com.misa.backend.dto.request.misa.CancelCertificateRequest;
import com.misa.backend.dto.request.misa.CreateWithholdingCertificateRequest;
import com.misa.backend.dto.request.misa.IncorrectRecordNotificationRequest;
import com.misa.backend.dto.request.misa.IssueCertificateRequest;
import com.misa.backend.dto.request.misa.SubmitCertificateRequest;
import com.misa.backend.dto.response.misa.CertificateCancelResponse;
import com.misa.backend.dto.response.misa.CertificateDocumentLinks;
import com.misa.backend.dto.response.misa.CertificateIssueResponse;
import com.misa.backend.dto.response.misa.CertificateLookupResponse;
import com.misa.backend.dto.response.misa.CertificateStatusResponse;
import com.misa.backend.dto.response.misa.CertificateSubmitResponse;
import com.misa.backend.dto.response.misa.FormInfo;
import com.misa.backend.dto.response.misa.IncomeSummary;
import com.misa.backend.dto.response.misa.IncorrectRecordNotificationResponse;
import com.misa.backend.dto.response.misa.SignatureInfo;
import com.misa.backend.dto.response.misa.TaxpayerSummary;
import com.misa.backend.dto.response.misa.WithholdingCertificateResponse;
import com.misa.backend.entity.CertificateStatus;
import com.misa.backend.entity.IncorrectRecordNotification;
import com.misa.backend.entity.PayoutTransaction;
import com.misa.backend.entity.WithholdingCertificate;
import com.misa.backend.exception.ApplicationException;
import com.misa.backend.exception.ErrorCode;
import com.misa.backend.repository.IncorrectRecordNotificationRepository;
import com.misa.backend.repository.PayoutTransactionRepository;
import com.misa.backend.repository.WithholdingCertificateRepository;
import com.misa.backend.service.MisaProviderClient;
import com.misa.backend.service.TaxEngineService;
import com.misa.backend.service.WithholdingCertificateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WithholdingCertificateServiceImpl implements WithholdingCertificateService {

    WithholdingCertificateRepository withholdingCertificateRepository;
    PayoutTransactionRepository payoutTransactionRepository;
    IncorrectRecordNotificationRepository incorrectRecordNotificationRepository;
    TaxEngineService taxEngineService;
    MisaProviderClient misaProviderClient;
    MisaProperties misaProperties;

    @Override
    @Transactional
    public WithholdingCertificateResponse create(CreateWithholdingCertificateRequest request) {
        PayoutTransaction payoutTransaction = payoutTransactionRepository.findById(request.getPayoutTransactionId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYOUT_NOT_FOUND, request.getPayoutTransactionId()));

        if (withholdingCertificateRepository.existsByPayoutTransactionId(payoutTransaction.getId())) {
            throw new ApplicationException(ErrorCode.PAYOUT_ALREADY_HAS_CERTIFICATE);
        }

        WithholdingCertificate certificate = new WithholdingCertificate();
        certificate.setPayoutTransaction(payoutTransaction);
        certificate.setTaxpayer(payoutTransaction.getTaxpayer());
        certificate.setTaxableIncome(payoutTransaction.getAmountVndGross());
        certificate.setTaxWithheld(taxEngineService.calculateTaxWithheld(payoutTransaction.getAmountVndGross()));
        certificate.setStatus(CertificateStatus.DRAFT);

        MisaProviderClient.MisaCertificateAssignment assignment = misaProviderClient.createCertificate(certificate);
        certificate.setSymbol(assignment.symbol());
        certificate.setCertificateNumber(assignment.certificateNumber());
        certificate.setLookupCode(assignment.lookupCode());

        withholdingCertificateRepository.save(certificate);

        return toResponse(certificate);
    }

    @Override
    @Transactional
    public CertificateIssueResponse issue(UUID certificateId, IssueCertificateRequest request) {
        WithholdingCertificate certificate = getCertificateOrThrow(certificateId);

        if (certificate.getStatus() != CertificateStatus.DRAFT) {
            throw new ApplicationException(ErrorCode.INVALID_CERTIFICATE_STATUS);
        }

        String serial = StringUtils.hasText(request.getDigitalCertificateSerial())
                ? request.getDigitalCertificateSerial()
                : misaProperties.getSigning().getCertificateSerial();

        MisaProviderClient.MisaIssueResult result = misaProviderClient.issueCertificate(certificate, serial);

        certificate.setDigitalCertificateSerial(serial);
        certificate.setIssuedAt(result.issuedAt());
        certificate.setStatus(CertificateStatus.SUBMITTING);

        withholdingCertificateRepository.save(certificate);

        return CertificateIssueResponse.builder()
                .id(certificate.getId())
                .status(certificate.getStatus().name())
                .certificateNumber(certificate.getCertificateNumber())
                .issuedAt(certificate.getIssuedAt())
                .signature(SignatureInfo.builder()
                        .status(result.signatureStatus())
                        .certificateSerial(serial)
                        .build())
                .build();
    }

    @Override
    @Transactional
    public CertificateSubmitResponse submit(UUID certificateId, SubmitCertificateRequest request) {
        WithholdingCertificate certificate = getCertificateOrThrow(certificateId);

        if (certificate.getStatus() == CertificateStatus.SUBMITTED || certificate.getStatus() == CertificateStatus.ACCEPTED) {
            throw new ApplicationException(ErrorCode.CERTIFICATE_ALREADY_SUBMITTED);
        }

        if (certificate.getStatus() != CertificateStatus.SUBMITTING) {
            throw new ApplicationException(ErrorCode.INVALID_CERTIFICATE_STATUS);
        }

        MisaProviderClient.MisaSubmitResult result =
                misaProviderClient.submitCertificate(certificate, request.getIdempotencyKey());

        certificate.setSubmissionId(result.submissionId());
        certificate.setTaxAuthorityReference(result.taxAuthorityReference());
        certificate.setSubmittedAt(result.submittedAt());
        certificate.setStatus(CertificateStatus.ACCEPTED);

        withholdingCertificateRepository.save(certificate);

        return CertificateSubmitResponse.builder()
                .submissionId(certificate.getSubmissionId())
                .certificateId(certificate.getId())
                .status(certificate.getStatus().name())
                .submittedAt(certificate.getSubmittedAt())
                .build();
    }

    @Override
    public CertificateStatusResponse getStatus(UUID certificateId) {
        WithholdingCertificate certificate = getCertificateOrThrow(certificateId);

        return CertificateStatusResponse.builder()
                .certificateId(certificate.getId())
                .status(certificate.getStatus().name())
                .submissionId(certificate.getSubmissionId())
                .taxAuthorityReference(certificate.getTaxAuthorityReference())
                .updatedAt(certificate.getUpdatedAt())
                .build();
    }

    @Override
    public CertificateLookupResponse lookup(String lookupCode) {
        WithholdingCertificate certificate = withholdingCertificateRepository.findByLookupCode(lookupCode)
                .orElseThrow(() -> new ApplicationException(ErrorCode.LOOKUP_CODE_NOT_FOUND));

        return CertificateLookupResponse.builder()
                .certificateId(certificate.getId())
                .lookupCode(certificate.getLookupCode())
                .status(certificate.getStatus().name())
                .formNumber(certificate.getFormNumber())
                .symbol(certificate.getSymbol())
                .number(certificate.getCertificateNumber())
                .taxpayer(TaxpayerSummary.builder()
                        .id(certificate.getTaxpayer().getId())
                        .fullName(certificate.getTaxpayer().getFullName())
                        .taxCode(certificate.getTaxpayer().getTaxCode())
                        .build())
                .documents(CertificateDocumentLinks.builder()
                        .pdfUrl("/api/v1/withholding-certificates/" + certificate.getId() + "/pdf")
                        .xmlUrl("/api/v1/withholding-certificates/" + certificate.getId() + "/xml")
                        .build())
                .build();
    }

    @Override
    @Transactional
    public CertificateCancelResponse cancel(UUID certificateId, CancelCertificateRequest request) {
        WithholdingCertificate certificate = getCertificateOrThrow(certificateId);

        if (certificate.getStatus() == CertificateStatus.CANCELLED || certificate.getStatus() == CertificateStatus.REPLACED) {
            throw new ApplicationException(ErrorCode.INVALID_CERTIFICATE_STATUS);
        }

        MisaProviderClient.MisaCancelResult result = misaProviderClient.cancelCertificate(certificate, request.getReason());

        certificate.setStatus(CertificateStatus.CANCELLED);
        certificate.setCancelledAt(result.cancelledAt());
        certificate.setCancelReason(request.getReason());

        withholdingCertificateRepository.save(certificate);

        return CertificateCancelResponse.builder()
                .certificateId(certificate.getId())
                .status(certificate.getStatus().name())
                .cancelledAt(certificate.getCancelledAt())
                .reason(certificate.getCancelReason())
                .build();
    }

    @Override
    public byte[] getPdf(UUID certificateId) {
        WithholdingCertificate certificate = getCertificateOrThrow(certificateId);
        String content = "MOCK PDF - Chung tu khau tru TNCN " + certificate.getSymbol() + certificate.getCertificateNumber();
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String getXml(UUID certificateId) {
        WithholdingCertificate certificate = getCertificateOrThrow(certificateId);

        return "<WithholdingCertificate>"
                + "<FormNumber>" + certificate.getFormNumber() + "</FormNumber>"
                + "<Symbol>" + certificate.getSymbol() + "</Symbol>"
                + "<Number>" + certificate.getCertificateNumber() + "</Number>"
                + "<Taxpayer>"
                + "<FullName>" + certificate.getTaxpayer().getFullName() + "</FullName>"
                + "<TaxCode>" + certificate.getTaxpayer().getTaxCode() + "</TaxCode>"
                + "</Taxpayer>"
                + "<Income>"
                + "<TaxableIncome>" + certificate.getTaxableIncome() + "</TaxableIncome>"
                + "<TaxWithheld>" + certificate.getTaxWithheld() + "</TaxWithheld>"
                + "</Income>"
                + "</WithholdingCertificate>";
    }

    @Override
    @Transactional
    public IncorrectRecordNotificationResponse reportIncorrectRecord(IncorrectRecordNotificationRequest request) {
        WithholdingCertificate certificate = getCertificateOrThrow(request.getCertificateId());

        IncorrectRecordNotification notification = new IncorrectRecordNotification();
        notification.setCertificate(certificate);
        notification.setErrorType(request.getErrorType());
        notification.setDescription(request.getDescription());
        notification.setRequestedAction(request.getRequestedAction());
        notification.setNextAction("REPLACE".equalsIgnoreCase(request.getRequestedAction())
                ? "REPLACEMENT_REQUIRED"
                : "REVIEW_REQUIRED");

        incorrectRecordNotificationRepository.save(notification);

        certificate.setStatus(CertificateStatus.CORRECTION_REQUIRED);
        withholdingCertificateRepository.save(certificate);

        return IncorrectRecordNotificationResponse.builder()
                .notificationId(notification.getId())
                .certificateId(certificate.getId())
                .status("RECEIVED")
                .nextAction(notification.getNextAction())
                .build();
    }

    private WithholdingCertificate getCertificateOrThrow(UUID certificateId) {
        return withholdingCertificateRepository.findById(certificateId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CERTIFICATE_NOT_FOUND, certificateId));
    }

    private WithholdingCertificateResponse toResponse(WithholdingCertificate certificate) {
        return WithholdingCertificateResponse.builder()
                .id(certificate.getId())
                .platformPayoutId(certificate.getPayoutTransaction().getPlatformPayoutId())
                .status(certificate.getStatus().name())
                .form(FormInfo.builder()
                        .formNumber(certificate.getFormNumber())
                        .symbol(certificate.getSymbol())
                        .number(certificate.getCertificateNumber())
                        .build())
                .taxpayer(TaxpayerSummary.builder()
                        .id(certificate.getTaxpayer().getId())
                        .fullName(certificate.getTaxpayer().getFullName())
                        .taxCode(certificate.getTaxpayer().getTaxCode())
                        .build())
                .income(IncomeSummary.builder()
                        .taxableIncome(certificate.getTaxableIncome())
                        .taxWithheld(certificate.getTaxWithheld())
                        .currency(certificate.getCurrency())
                        .build())
                .createdAt(certificate.getCreatedAt())
                .build();
    }
}
