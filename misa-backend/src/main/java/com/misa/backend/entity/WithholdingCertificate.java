package com.misa.backend.entity;

import com.misa.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "withholding_certificate")
public class WithholdingCertificate extends AbstractEntity<UUID> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_transaction_id", nullable = false, unique = true)
    PayoutTransaction payoutTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxpayer_id", nullable = false)
    Taxpayer taxpayer;

    @Column(name = "form_number", nullable = false)
    String formNumber = "03/TNCN";

    @Column(name = "symbol", nullable = false)
    String symbol;

    @Column(name = "certificate_number")
    String certificateNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    CertificateStatus status = CertificateStatus.DRAFT;

    @Column(name = "taxable_income", nullable = false, precision = 20, scale = 2)
    BigDecimal taxableIncome;

    @Column(name = "mandatory_insurance", nullable = false, precision = 20, scale = 2)
    BigDecimal mandatoryInsurance = BigDecimal.ZERO;

    @Column(name = "charity_contribution", nullable = false, precision = 20, scale = 2)
    BigDecimal charityContribution = BigDecimal.ZERO;

    @Column(name = "tax_withheld", nullable = false, precision = 20, scale = 2)
    BigDecimal taxWithheld;

    @Column(name = "currency", nullable = false)
    String currency = "VND";

    @Column(name = "lookup_code", unique = true)
    String lookupCode;

    @Column(name = "submission_id")
    String submissionId;

    @Column(name = "tax_authority_reference")
    String taxAuthorityReference;

    @Column(name = "digital_certificate_serial")
    String digitalCertificateSerial;

    @Column(name = "issued_at")
    LocalDateTime issuedAt;

    @Column(name = "submitted_at")
    LocalDateTime submittedAt;

    @Column(name = "cancelled_at")
    LocalDateTime cancelledAt;

    @Column(name = "cancel_reason")
    String cancelReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_of_id")
    WithholdingCertificate replacementOf;
}
