package com.marketplace.backend.service.impl;

import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.entity.ClientPaymentStatus;
import com.marketplace.backend.entity.ExchangeRateSource;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.OffRampStatus;
import com.marketplace.backend.entity.OnChainOffRampStatus;
import com.marketplace.backend.entity.OnRampStatus;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.provider.currency.ExchangeRateProvider;
import com.marketplace.backend.provider.currency.ExchangeRateResult;
import com.marketplace.backend.provider.currency.OnRampProvider;
import com.marketplace.backend.provider.currency.OnRampQuote;
import com.marketplace.backend.provider.currency.OnRampResult;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.WalletRepository;
import com.marketplace.backend.service.ClientPaymentService;
import com.marketplace.backend.service.NotificationService;
import com.marketplace.backend.service.OnChainOffRampService;
import com.marketplace.backend.service.TaxCertificateService;
import com.marketplace.backend.service.VndPayoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutContractTest {
    private final JobRepository jobs = mock(JobRepository.class);
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final FreelancerPayoutRecordRepository records = mock(FreelancerPayoutRecordRepository.class);
    private final OnRampProvider onRamp = mock(OnRampProvider.class);
    private final ExchangeRateProvider rates = mock(ExchangeRateProvider.class);
    private final TaxCertificateService tax = mock(TaxCertificateService.class);
    private final ClientPaymentService clientPayment = mock(ClientPaymentService.class);
    private final OnChainOffRampService chainOffRamp = mock(OnChainOffRampService.class);
    private final VndPayoutService vnd = mock(VndPayoutService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private PayoutServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PayoutServiceImpl(jobs, wallets, records, onRamp, rates, tax,
                clientPayment, chainOffRamp, vnd, notifications, new SolanaCprProperties());
    }

    @Test
    void grossJobUsdRemainsTaxBaseAfterOnRampFee() {
        Job job = job();
        Instant observedAt = Instant.parse("2026-01-01T00:00:00Z");
        when(records.findByJobId(job.getId())).thenReturn(Optional.empty());
        when(rates.getUsdToVndRate()).thenReturn(new ExchangeRateResult(
                new BigDecimal("25000"), ExchangeRateSource.FALLBACK_PLACEHOLDER, observedAt));
        when(onRamp.quote(job.getId(), job.getBudgetUsd())).thenReturn(new OnRampQuote(
                new BigDecimal("500"), new BigDecimal("2.5"), new BigDecimal("497.5"), "497500000", "1"));
        when(onRamp.network()).thenReturn("localnet");
        when(onRamp.execute(any(), any())).thenReturn(OnRampResult.notStarted("pending"));
        when(records.save(any(FreelancerPayoutRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(wallets.findFirstByUserIdOrderByIdAsc(job.getClientUserId()))
                .thenReturn(Optional.of(wallet(job.getClientUserId(), "client-key")));
        when(wallets.findFirstByUserIdOrderByIdAsc(job.getFreelancerId()))
                .thenReturn(Optional.of(wallet(job.getFreelancerId(), "freelancer-key")));

        service.settle(job);

        FreelancerPayoutRecord record = org.mockito.Mockito.mockingDetails(records).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (FreelancerPayoutRecord) invocation.getArgument(0))
                .findFirst().orElseThrow();
        assertThat(record.getAmountUsd()).isEqualByComparingTo("500");
        assertThat(record.getAmountUsdNet()).isEqualByComparingTo("497.5");
        assertThat(record.getTaxableAmountVnd()).isEqualByComparingTo("12500000");
        assertThat(record.getTaxRateSource()).isEqualTo(ExchangeRateSource.FALLBACK_PLACEHOLDER);
        assertThat(record.getTaxRateObservedAt()).isEqualTo(observedAt);
        verify(tax, never()).exportForPayout(any(), any(), any());
    }

    @Test
    void simulatedPayoutDoesNotExportTax() {
        reconcileWithoutExport(OffRampStatus.SIMULATED);
    }

    @Test
    void completionSubmittedDoesNotExportTax() {
        reconcileWithoutExport(OffRampStatus.COMPLETION_SUBMITTED);
    }

    @Test
    void failedPayoutDoesNotExportTax() {
        reconcileWithoutExport(OffRampStatus.FAILED);
    }

    @Test
    void completedPayoutExportsTax() {
        Job job = job();
        FreelancerPayoutRecord record = payout(job, OffRampStatus.COMPLETED);
        when(records.findById(record.getId())).thenReturn(Optional.of(record));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));

        service.reconcile(record.getId());

        verify(tax).exportForPayout(job, record, "completion-signature");
    }

    @Test
    void transitionFromSubmittedToCompletedExportsTax() {
        Job job = job();
        FreelancerPayoutRecord record = payout(job, OffRampStatus.COMPLETION_SUBMITTED);
        when(records.findById(record.getId())).thenReturn(Optional.of(record));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        doAnswer(invocation -> {
            record.setOffRampStatus(OffRampStatus.COMPLETED);
            return null;
        }).when(vnd).advance(record);

        service.reconcile(record.getId());

        verify(tax).exportForPayout(job, record, "completion-signature");
    }

    private void reconcileWithoutExport(OffRampStatus status) {
        Job job = job();
        FreelancerPayoutRecord record = payout(job, status);
        when(records.findById(record.getId())).thenReturn(Optional.of(record));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));

        service.reconcile(record.getId());

        verify(tax, never()).exportForPayout(any(), any(), any());
    }

    private Job job() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setClientUserId(UUID.randomUUID());
        job.setFreelancerId(UUID.randomUUID());
        job.setBudgetUsd(new BigDecimal("500"));
        return job;
    }

    private FreelancerPayoutRecord payout(Job job, OffRampStatus status) {
        FreelancerPayoutRecord record = new FreelancerPayoutRecord();
        record.setId(UUID.randomUUID());
        record.setJobId(job.getId());
        record.setOnRampStatus(OnRampStatus.CONFIRMED);
        record.setClientPaymentStatus(ClientPaymentStatus.CONFIRMED);
        record.setOnChainOffRampStatus(OnChainOffRampStatus.CONFIRMED);
        record.setOffRampStatus(status);
        record.setOffRampCompletionSignature("completion-signature");
        return record;
    }

    private Wallet wallet(UUID userId, String key) {
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setPublicKey(key);
        return wallet;
    }
}
