package com.misa.backend.service.impl;

import com.misa.backend.entity.WithholdingCertificate;
import com.misa.backend.repository.WithholdingCertificateRepository;
import com.misa.backend.service.MisaProviderClient;
import com.misa.backend.util.CertificateNumberGenerator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MisaProviderClientMockImpl implements MisaProviderClient {

    WithholdingCertificateRepository withholdingCertificateRepository;

    @Override
    public MisaCertificateAssignment createCertificate(WithholdingCertificate certificate) {
        long sequence = withholdingCertificateRepository.countByCertificateNumberIsNotNull() + 1;
        String symbol = CertificateNumberGenerator.generateSymbol();
        String number = CertificateNumberGenerator.generateCertificateNumber(sequence);
        String lookupCode = CertificateNumberGenerator.generateLookupCode();
        return new MisaCertificateAssignment(symbol, number, lookupCode);
    }

    @Override
    public MisaIssueResult issueCertificate(WithholdingCertificate certificate, String digitalCertificateSerial) {
        return new MisaIssueResult("SIGNED", LocalDateTime.now());
    }

    @Override
    public MisaSubmitResult submitCertificate(WithholdingCertificate certificate, String idempotencyKey) {
        String submissionId = "SUB-" + LocalDateTime.now().getYear() + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String taxAuthorityReference = "TX-" + System.currentTimeMillis();
        return new MisaSubmitResult(submissionId, taxAuthorityReference, LocalDateTime.now());
    }

    @Override
    public MisaCancelResult cancelCertificate(WithholdingCertificate certificate, String reason) {
        return new MisaCancelResult(LocalDateTime.now());
    }
}
