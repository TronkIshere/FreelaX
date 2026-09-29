package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.configuration.MisaCertificateProperties;
import com.marketplace.backend.dto.response.misa.MisaCertificateStatusResult;
import com.marketplace.backend.dto.response.misa.MisaPayoutTransactionResult;
import com.marketplace.backend.entity.ExchangeRateSource;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.OffRampStatus;
import com.marketplace.backend.entity.TaxCertificateRecord;
import com.marketplace.backend.entity.TaxCertificateStatus;
import com.marketplace.backend.entity.TaxExportStatus;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.TaxCertificateRecordRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaxCertificateContractTest {
    private final TaxCertificateRecordRepository taxRecords = mock(TaxCertificateRecordRepository.class);
    private final FreelancerPayoutRecordRepository payouts = mock(FreelancerPayoutRecordRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MisaBackendClient misa = mock(MisaBackendClient.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private TaxCertificateServiceImpl service;

    @BeforeEach
    void setUp() {
        MisaCertificateProperties properties = new MisaCertificateProperties();
        properties.setAutoIssue(true);
        properties.setAutoSubmit(true);
        properties.setDigitalCertificateSerial("DEMO-CERT");
        service = new TaxCertificateServiceImpl(taxRecords, payouts, jobs, users, misa, notifications, properties);
    }

    @Test
    void nonCompletedPayoutCannotStartTaxExport() {
        Job job = job();
        for (OffRampStatus status : new OffRampStatus[] {
                OffRampStatus.NOT_STARTED, OffRampStatus.SIMULATED,
                OffRampStatus.COMPLETION_SUBMITTED, OffRampStatus.FAILED }) {
            FreelancerPayoutRecord payout = payout(job, status);
            assertThatThrownBy(() -> service.exportForPayout(job, payout, "signature"))
                    .isInstanceOf(ApplicationException.class);
        }
        verify(misa, never()).registerTaxpayerForExternal(any(), any(), any(), any(), any(), any());
    }

    @Test
    void completedPayoutCreatesIssuesSubmitsAndAcceptsCertificate() {
        Job job = job();
        FreelancerPayoutRecord payout = payout(job, OffRampStatus.COMPLETED);
        User freelancer = new User();
        freelancer.setId(payout.getFreelancerId());
        freelancer.setDisplayName("Demo Freelancer");
        freelancer.setTaxCode("DEMO-TAX-000001");
        freelancer.setIdentityNumber("DEMO-ID-000001");
        freelancer.setNationality("VN");
        freelancer.setTaxAddress("Demo address");
        UUID taxpayerId = UUID.randomUUID();
        UUID misaPayoutId = UUID.randomUUID();
        UUID certificateId = UUID.randomUUID();
        MisaPayoutTransactionResult misaPayout = new MisaPayoutTransactionResult();
        ReflectionTestUtils.setField(misaPayout, "id", misaPayoutId);
        when(taxRecords.findByJobId(job.getId())).thenReturn(Optional.empty());
        when(taxRecords.save(any(TaxCertificateRecord.class))).thenAnswer(invocation -> {
            TaxCertificateRecord record = invocation.getArgument(0);
            if (record.getId() == null) record.setId(UUID.randomUUID());
            return record;
        });
        when(users.findById(freelancer.getId())).thenReturn(Optional.of(freelancer));
        when(payouts.findByJobId(job.getId())).thenReturn(Optional.of(payout));
        when(misa.registerTaxpayerForExternal(eq(freelancer.getId()), any(), any(), any(), any(), any()))
                .thenReturn(taxpayerId);
        when(misa.recordPayoutTransaction(eq(taxpayerId), eq(job.getId()), any(), any(), any(), any()))
                .thenReturn(misaPayout);
        when(misa.createWithholdingCertificate(misaPayoutId)).thenReturn(result(certificateId, "DRAFT"));
        when(misa.getCertificateStatus(certificateId)).thenReturn(
                result(certificateId, "DRAFT"), result(certificateId, "SUBMITTING"),
                result(certificateId, "ACCEPTED"));
        when(misa.issueCertificate(eq(certificateId), any(), any()))
                .thenReturn(result(certificateId, "SUBMITTING"));
        when(misa.submitCertificate(eq(certificateId), any(), any()))
                .thenReturn(result(certificateId, "ACCEPTED"));

        service.exportForPayout(job, payout, "completion-signature");

        assertThat(job.getTaxExportStatus()).isEqualTo(TaxExportStatus.SUCCESS);
        TaxCertificateRecord record = (TaxCertificateRecord) org.mockito.Mockito.mockingDetails(taxRecords)
                .getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> invocation.getArgument(0))
                .findFirst().orElseThrow();
        assertThat(record.getStatus()).isEqualTo(TaxCertificateStatus.ACCEPTED);
        assertThat(record.getAmountUsd()).isEqualByComparingTo("500");
        assertThat(record.getRateObservedAt()).isEqualTo(payout.getTaxRateObservedAt());
        verify(misa).recordPayoutTransaction(eq(taxpayerId), eq(job.getId()),
                eq(new BigDecimal("500")), eq(new BigDecimal("25000")),
                eq("completion-signature"), eq("solana"));
        verify(misa).issueCertificate(eq(certificateId), any(), any());
        verify(misa).submitCertificate(eq(certificateId), any(), any());
    }

    private Job job() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Demo job");
        return job;
    }

    private FreelancerPayoutRecord payout(Job job, OffRampStatus status) {
        FreelancerPayoutRecord payout = new FreelancerPayoutRecord();
        payout.setId(UUID.randomUUID());
        payout.setJobId(job.getId());
        payout.setClientUserId(UUID.randomUUID());
        payout.setFreelancerId(UUID.randomUUID());
        payout.setOffRampStatus(status);
        payout.setAmountUsd(new BigDecimal("500"));
        payout.setTaxUsdToVndRate(new BigDecimal("25000"));
        payout.setTaxRateSource(ExchangeRateSource.FALLBACK_PLACEHOLDER);
        payout.setTaxRateObservedAt(Instant.parse("2026-01-01T00:00:00Z"));
        payout.setTaxableAmountVnd(new BigDecimal("12500000"));
        return payout;
    }

    private MisaCertificateStatusResult result(UUID id, String status) {
        MisaCertificateStatusResult result = new MisaCertificateStatusResult();
        result.setId(id.toString());
        result.setStatus(status);
        return result;
    }
}
