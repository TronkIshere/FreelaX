package com.misa.backend.service.impl;

import com.misa.backend.configuration.CertificatePdfProperties;
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
import com.misa.backend.dto.response.misa.CertificateRecoveryResponse;
import com.misa.backend.entity.CertificateStatus;
import com.misa.backend.entity.IncorrectRecordNotification;
import com.misa.backend.entity.PayoutTransaction;
import com.misa.backend.entity.Taxpayer;
import com.misa.backend.entity.WithholdingCertificate;
import com.misa.backend.exception.ApplicationException;
import com.misa.backend.exception.ErrorCode;
import com.misa.backend.repository.IncorrectRecordNotificationRepository;
import com.misa.backend.repository.PayoutTransactionRepository;
import com.misa.backend.repository.WithholdingCertificateRepository;
import com.misa.backend.service.MisaProviderClient;
import com.misa.backend.service.TaxEngineService;
import com.misa.backend.service.WithholdingCertificateService;
import com.misa.backend.service.pdf.CertificatePdfData;
import com.misa.backend.service.pdf.CertificatePdfRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.Objects;
import java.util.List;
import java.util.HexFormat;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

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
    CertificatePdfRenderer certificatePdfRenderer;
    CertificatePdfProperties certificatePdfProperties;

    @Override
    @Transactional
    public WithholdingCertificateResponse create(CreateWithholdingCertificateRequest request) {
        if (request == null || request.getPayoutTransactionId() == null) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
        String key = request.getIdempotencyKey() == null
                ? "payout-" + request.getPayoutTransactionId() : request.getIdempotencyKey();
        if (!StringUtils.hasText(key) || key.length() > 100 || !key.equals(key.trim())) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
        // Same payout contenders wait here, then read the authoritative winning certificate.
        PayoutTransaction payoutTransaction = payoutTransactionRepository.findWithLockById(request.getPayoutTransactionId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYOUT_NOT_FOUND, request.getPayoutTransactionId()));
        String hash = fingerprint(payoutTransaction);
        WithholdingCertificate keyed = withholdingCertificateRepository.findByIdempotencyKey(key).orElse(null);
        if (keyed != null && (!keyed.getPayoutTransaction().getId().equals(payoutTransaction.getId())
                || !Objects.equals(keyed.getPayloadHash(), hash))) {
            throw new ApplicationException(ErrorCode.CERTIFICATE_KEY_CONFLICT);
        }
        WithholdingCertificate existing = withholdingCertificateRepository
                .findByPayoutTransactionId(payoutTransaction.getId()).orElse(null);
        if (existing != null) {
            if (!key.equals(certificateKey(existing))) {
                throw new ApplicationException(ErrorCode.CERTIFICATE_IDENTITY_CONFLICT);
            }
            if ((existing.getPayloadHash() != null && !hash.equals(existing.getPayloadHash()))
                    || existing.getTaxableIncome().compareTo(payoutTransaction.getAmountVndGross()) != 0
                    || !existing.getTaxpayer().getId().equals(payoutTransaction.getTaxpayer().getId())) {
                throw new ApplicationException(ErrorCode.CERTIFICATE_KEY_CONFLICT);
            }
            return toResponse(existing);
        }

        WithholdingCertificate certificate = new WithholdingCertificate();
        certificate.setPayoutTransaction(payoutTransaction);
        certificate.setTaxpayer(payoutTransaction.getTaxpayer());
        certificate.setTaxableIncome(payoutTransaction.getAmountVndGross());
        certificate.setTaxWithheld(taxEngineService.calculateTaxWithheld(payoutTransaction.getAmountVndGross()));
        certificate.setStatus(CertificateStatus.DRAFT);
        certificate.setIdempotencyKey(key);
        certificate.setPayloadHash(hash);

        MisaProviderClient.MisaCertificateAssignment assignment = misaProviderClient.createCertificate(certificate);
        certificate.setSymbol(assignment.symbol());
        certificate.setCertificateNumber(assignment.certificateNumber());
        certificate.setLookupCode(assignment.lookupCode());

        withholdingCertificateRepository.saveAndFlush(certificate);

        return toResponse(certificate);
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateRecoveryResponse findByPlatformPayout(String platformPayoutId) {
        WithholdingCertificate c = withholdingCertificateRepository
                .findByPayoutTransactionPlatformPayoutId(platformPayoutId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CERTIFICATE_NOT_FOUND, platformPayoutId));
        PayoutTransaction p = c.getPayoutTransaction();
        return new CertificateRecoveryResponse(c.getId(), p.getId(), p.getPlatformPayoutId(), certificateKey(c),
                c.getStatus().name(), c.getCertificateNumber(), c.getSymbol(), p.getAmountUsdc(),
                p.getExchangeRate(), c.getTaxableIncome(), c.getTaxWithheld(), c.getCurrency(),
                c.getCreatedAt(), c.getIssuedAt(), true); // Current MISA provider is simulation-only.
    }

    private String certificateKey(WithholdingCertificate c) {
        return c.getIdempotencyKey() == null ? "payout-" + c.getPayoutTransaction().getId() : c.getIdempotencyKey();
    }

    private String fingerprint(PayoutTransaction p) {
        List<String> values = List.of(p.getId().toString(), p.getPlatformPayoutId(),
                p.getTaxpayer().getId().toString(), decimal(p.getAmountUsdc()), decimal(p.getExchangeRate()),
                decimal(p.getAmountVndGross()), p.getBlockchain(), p.getTransactionHash(), "VND");
        String canonical = values.stream().map(v -> v.length() + ":" + v).collect(Collectors.joining());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

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
    @Transactional(readOnly = true)
    public byte[] getPdf(UUID certificateId) {
        WithholdingCertificate certificate = getCertificateOrThrow(certificateId);
        return certificatePdfRenderer.render(toPdfData(certificate));
    }

    @Override
    @Transactional(readOnly = true)
    public String getXml(UUID certificateId) {
        WithholdingCertificate certificate = getCertificateOrThrow(certificateId);

        return "<WithholdingCertificate>"
                + "<FormNumber>" + xml(certificate.getFormNumber()) + "</FormNumber>"
                + "<Symbol>" + xml(certificate.getSymbol()) + "</Symbol>"
                + "<Number>" + xml(certificate.getCertificateNumber()) + "</Number>"
                + "<Taxpayer>"
                + "<FullName>" + xml(certificate.getTaxpayer().getFullName()) + "</FullName>"
                + "<TaxCode>" + xml(certificate.getTaxpayer().getTaxCode()) + "</TaxCode>"
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

    private CertificatePdfData toPdfData(WithholdingCertificate certificate) {
        Taxpayer taxpayer = certificate.getTaxpayer();
        PayoutTransaction payout = certificate.getPayoutTransaction();

        return new CertificatePdfData(
                certificate.getFormNumber(),
                certificate.getSymbol(),
                certificate.getCertificateNumber(),
                certificate.getStatus().name(),
                certificate.getLookupCode(),
                certificatePdfProperties.getPayerName(),
                certificatePdfProperties.getPayerTaxCode(),
                certificatePdfProperties.getPayerAddress(),
                certificatePdfProperties.getPayerPhone(),
                taxpayer.getFullName(),
                taxpayer.getTaxCode(),
                taxpayer.getIdentityNumber(),
                taxpayer.getNationality(),
                taxpayer.getAddress(),
                taxpayer.getPhone(),
                certificatePdfProperties.getIncomeType(),
                payout.getPaymentDate(),
                certificate.getTaxableIncome(),
                certificate.getMandatoryInsurance(),
                certificate.getCharityContribution(),
                certificate.getTaxWithheld(),
                certificate.getCurrency(),
                payout.getAmountUsdc(),
                payout.getExchangeRate(),
                payout.getPlatformPayoutId(),
                payout.getTransactionHash(),
                payout.getBlockchain(),
                payout.getDescription(),
                certificate.getCreatedAt(),
                certificate.getIssuedAt(),
                certificate.getDigitalCertificateSerial(),
                certificate.getSubmittedAt(),
                certificate.getTaxAuthorityReference(),
                certificate.getCancelledAt(),
                certificate.getCancelReason()
        );
    }

    private String xml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private WithholdingCertificate getCertificateOrThrow(UUID certificateId) {
        return withholdingCertificateRepository.findById(certificateId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CERTIFICATE_NOT_FOUND, certificateId));
    }

    private WithholdingCertificateResponse toResponse(WithholdingCertificate certificate) {
        return WithholdingCertificateResponse.builder()
                .id(certificate.getId())
                .payoutTransactionId(certificate.getPayoutTransaction().getId())
                .idempotencyKey(certificateKey(certificate))
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
