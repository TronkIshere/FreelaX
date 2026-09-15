package com.misa.backend.service;

import com.misa.backend.entity.WithholdingCertificate;

import java.time.LocalDateTime;

public interface MisaProviderClient {

    MisaCertificateAssignment createCertificate(WithholdingCertificate certificate);

    MisaIssueResult issueCertificate(WithholdingCertificate certificate, String digitalCertificateSerial);

    MisaSubmitResult submitCertificate(WithholdingCertificate certificate, String idempotencyKey);

    MisaCancelResult cancelCertificate(WithholdingCertificate certificate, String reason);

    record MisaCertificateAssignment(String symbol, String certificateNumber, String lookupCode) {
    }

    record MisaIssueResult(String signatureStatus, LocalDateTime issuedAt) {
    }

    record MisaSubmitResult(String submissionId, String taxAuthorityReference, LocalDateTime submittedAt) {
    }

    record MisaCancelResult(LocalDateTime cancelledAt) {
    }
}
