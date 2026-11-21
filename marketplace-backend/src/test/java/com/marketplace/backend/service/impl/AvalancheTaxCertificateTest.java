package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.configuration.MisaCertificateProperties;
import com.marketplace.backend.dto.response.misa.MisaCertificateStatusResult;
import com.marketplace.backend.dto.response.misa.MisaPayoutTransactionResult;
import com.marketplace.backend.entity.ContractStatus;
import com.marketplace.backend.entity.ExchangeRateSource;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.Milestone;
import com.marketplace.backend.entity.TaxCertificateRecord;
import com.marketplace.backend.entity.TaxCertificateStatus;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.provider.currency.ExchangeRateProvider;
import com.marketplace.backend.provider.currency.ExchangeRateResult;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.PaymentFlowRepository;
import com.marketplace.backend.repository.PaymentFlowStepRepository;
import com.marketplace.backend.repository.TaxCertificateRecordRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import com.marketplace.backend.service.AvalancheTaxCertificatePreparer;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Owner decision 2026-11-21: Avalanche release -> simulated MISA certificate under Decree 117 (PIT 2% + VAT 5%). */
class AvalancheTaxCertificateTest {
    private final TaxCertificateRecordRepository records = mock(TaxCertificateRecordRepository.class);
    private final WorkContractRepository contracts = mock(WorkContractRepository.class);
    private final MilestoneRepository milestones = mock(MilestoneRepository.class);
    private final ExchangeRateProvider rates = mock(ExchangeRateProvider.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MisaBackendClient misa = mock(MisaBackendClient.class);
    private AvalancheTaxCertificatePreparer preparer;
    private TaxCertificateServiceImpl service;

    @BeforeEach
    void setUp() {
        preparer = new AvalancheTaxCertificatePreparer(contracts, milestones, records, rates);
        MisaCertificateProperties properties = new MisaCertificateProperties();
        service = new TaxCertificateServiceImpl(records, mock(FreelancerPayoutRecordRepository.class), jobs, users, misa,
                mock(NotificationService.class), properties, mock(PaymentFlowRepository.class),
                mock(PaymentFlowStepRepository.class), contracts);
        when(rates.getUsdToVndRate()).thenReturn(new ExchangeRateResult(new BigDecimal("25000.004"),
                ExchangeRateSource.FALLBACK_PLACEHOLDER, Instant.parse("2026-11-21T00:00:00Z")));
        when(records.saveAndFlush(any(TaxCertificateRecord.class))).thenAnswer(i -> {
            TaxCertificateRecord r = i.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(records.save(any(TaxCertificateRecord.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aReleasedAvalancheContractGetsADecree117RecordFromTheEscrowedAmount() {
        WorkContract contract = contract("avalanche-local", ContractStatus.COMPLETED);
        Milestone milestone = new Milestone();
        milestone.setAmount(new BigDecimal("300"));
        when(milestones.findByContractId(contract.getId())).thenReturn(Optional.of(milestone));
        when(records.findByJobId(contract.getJobId())).thenReturn(Optional.empty());

        assertThat(preparer.prepare(contract.getId())).isPresent();

        TaxCertificateRecord r = saved();
        assertThat(r.getTaxScheme()).isEqualTo("VN_DECREE_117_SERVICES");
        assertThat(r.getAmountUsd()).isEqualByComparingTo("300");
        assertThat(r.getUsdToVndRate()).isEqualByComparingTo("25000.00");       // Solana-rail rounding, 2 decimals
        assertThat(r.getTaxableIncomeVnd()).isEqualByComparingTo("7500000");    // 300 x 25,000
        assertThat(r.getTransactionReference()).isEqualTo("avalanche-release:" + contract.getId());
        assertThat(r.getStatus()).isEqualTo(TaxCertificateStatus.PENDING_EXPORT);
        assertThat(r.getFreelancerId()).isEqualTo(contract.getFreelancerId());
    }

    @Test
    void noRecordForAnUnreleasedOrNonAvalancheContract() {
        WorkContract active = contract("avalanche-local", ContractStatus.ACTIVE);
        WorkContract legacy = contract("solana-localnet", ContractStatus.COMPLETED);

        assertThat(preparer.prepare(active.getId())).isEmpty();
        assertThat(preparer.prepare(legacy.getId())).isEmpty();
        verify(records, never()).saveAndFlush(any());
    }

    @Test
    void exportRecordsTheDecree117SchemeAndKeepsTheVatWithheldByMisa() {
        WorkContract contract = contract("avalanche-fuji", ContractStatus.COMPLETED);
        TaxCertificateRecord r = record(contract);
        Job job = new Job();
        job.setId(contract.getJobId());
        job.setTitle("Landing page");
        User freelancer = new User();
        freelancer.setId(contract.getFreelancerId());
        UUID taxpayer = UUID.randomUUID();
        UUID payout = UUID.randomUUID();
        UUID certificate = UUID.randomUUID();
        MisaPayoutTransactionResult misaPayout = new MisaPayoutTransactionResult();
        ReflectionTestUtils.setField(misaPayout, "id", payout);
        MisaCertificateStatusResult created = new MisaCertificateStatusResult();
        created.setId(certificate.toString());
        created.setStatus("DRAFT");
        created.setTaxWithheld(new BigDecimal("150000"));   // 2% of 7,500,000
        created.setVatWithheld(new BigDecimal("375000"));   // 5% of 7,500,000
        when(records.findById(r.getId())).thenReturn(Optional.of(r));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(users.findById(freelancer.getId())).thenReturn(Optional.of(freelancer));
        when(misa.registerTaxpayerForExternal(any(), any(), any(), any(), any(), any())).thenReturn(taxpayer);
        when(misa.recordPayoutTransaction(eq(taxpayer), eq(job.getId()), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(misaPayout);
        when(misa.createWithholdingCertificate(payout)).thenReturn(created);
        when(misa.getCertificateStatus(certificate)).thenReturn(created);

        service.exportForAvalancheRelease(r.getId());

        verify(misa).recordPayoutTransaction(eq(taxpayer), eq(job.getId()), eq(new BigDecimal("300.00")),
                eq(new BigDecimal("25000.00")), eq("avalanche-release:" + contract.getId()), eq("avalanche"), eq(true),
                eq("VN_DECREE_117_SERVICES"));
        assertThat(r.getMisaCertificateId()).isEqualTo(certificate);
        assertThat(r.getTaxWithheldVnd()).isEqualByComparingTo("150000");
        assertThat(r.getVatWithheldVnd()).isEqualByComparingTo("375000");
    }

    @Test
    void exportIsRefusedWhileTheEscrowIsNotReleased() {
        WorkContract contract = contract("avalanche-local", ContractStatus.ACTIVE);
        TaxCertificateRecord r = record(contract);
        when(records.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.exportForAvalancheRelease(r.getId())).isInstanceOf(ApplicationException.class);
        verify(misa, never()).registerTaxpayerForExternal(any(), any(), any(), any(), any(), any());
    }

    private WorkContract contract(String network, ContractStatus status) {
        WorkContract c = new WorkContract();
        c.setId(UUID.randomUUID());
        c.setJobId(UUID.randomUUID());
        c.setClientUserId(UUID.randomUUID());
        c.setFreelancerId(UUID.randomUUID());
        c.setBudgetUsd(new BigDecimal("300"));
        c.setPaymentNetworkId(network);
        c.setStatus(status);
        when(contracts.findById(c.getId())).thenReturn(Optional.of(c));
        when(contracts.findByJobId(c.getJobId())).thenReturn(Optional.of(c));
        return c;
    }

    private TaxCertificateRecord record(WorkContract contract) {
        TaxCertificateRecord r = new TaxCertificateRecord();
        r.setId(UUID.randomUUID());
        r.setJobId(contract.getJobId());
        r.setFreelancerId(contract.getFreelancerId());
        r.setClientUserId(contract.getClientUserId());
        r.setAmountUsd(new BigDecimal("300.00"));
        r.setUsdToVndRate(new BigDecimal("25000.00"));
        r.setRateSource(ExchangeRateSource.FALLBACK_PLACEHOLDER);
        r.setTaxableIncomeVnd(new BigDecimal("7500000"));
        r.setTransactionReference("avalanche-release:" + contract.getId());
        r.setTaxScheme("VN_DECREE_117_SERVICES");
        r.setStatus(TaxCertificateStatus.PENDING_EXPORT);
        return r;
    }

    private TaxCertificateRecord saved() {
        return (TaxCertificateRecord) org.mockito.Mockito.mockingDetails(records).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("saveAndFlush")).reduce((a, b) -> b).orElseThrow()
                .getArgument(0);
    }
}
