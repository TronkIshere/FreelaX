package com.marketplace.backend.service;

import com.marketplace.backend.entity.*;
import com.marketplace.backend.provider.currency.*;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.service.impl.PayoutServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettlementDownstreamServiceTest {
    ContractSettlementRepository settlements;
    FreelancerPayoutRecordRepository payouts;
    JobRepository jobs;
    PayoutServiceImpl preparation;
    OnRampProvider onRamp;
    ClientPaymentService client;
    OnChainOffRampService withdrawal;
    VndPayoutService vnd;
    SettlementTaxService tax;
    PlatformTransactionManager manager;
    SettlementDownstreamService service;
    ContractSettlement s;
    FreelancerPayoutRecord p;
    Job job;
    AtomicInteger commits;

    @BeforeEach void setup() {
        settlements = mock(ContractSettlementRepository.class); payouts = mock(FreelancerPayoutRecordRepository.class);
        jobs = mock(JobRepository.class); preparation = mock(PayoutServiceImpl.class); onRamp = mock(OnRampProvider.class);
        client = mock(ClientPaymentService.class); withdrawal = mock(OnChainOffRampService.class); vnd = mock(VndPayoutService.class);
        tax = mock(SettlementTaxService.class); manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(a -> new SimpleTransactionStatus());
        commits = new AtomicInteger(); doAnswer(a -> { commits.incrementAndGet(); return null; }).when(manager).commit(any());
        service = new SettlementDownstreamService(settlements, payouts, jobs, preparation, onRamp, client,
                withdrawal, vnd, tax, new TransactionTemplate(manager));
        s = new ContractSettlement(); s.setId(UUID.randomUUID()); s.setJobId(UUID.randomUUID());
        s.setMoneyStatus(SettlementMoneyStatus.SUCCEEDED); s.setAmount(new BigDecimal("500.00"));
        job = new Job(); job.setId(s.getJobId()); job.setStatus(JobStatus.COMPLETED);
        p = new FreelancerPayoutRecord(); p.setId(UUID.randomUUID()); p.setJobId(job.getId()); p.setContractSettlementId(s.getId());
        p.setAmountUsd(new BigDecimal("500.000000")); p.setOnRampFeeUsd(new BigDecimal("2.500000"));
        p.setAmountUsdNet(new BigDecimal("497.500000")); p.setOnRampUsdAmountE6("497500000"); p.setOnRampPurchaseId("stable-purchase");
        p.setOnRampClientPublicKey("client-public"); p.setFreelancerPublicKey("freelancer-public");
        when(settlements.findWithLockById(s.getId())).thenReturn(Optional.of(s));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(preparation.prepareContractRecord(job, s)).thenReturn(p);
        when(payouts.findById(p.getId())).thenReturn(Optional.of(p));
        when(onRamp.execute(any(), anyString())).thenReturn(OnRampResult.notStarted("offline"));
    }

    @Test void quoteCommitsBeforeFirstRemoteMutationAndIsNotRegeneratedOnRetry() {
        when(onRamp.execute(any(), anyString())).thenAnswer(a -> {
            assertThat(commits.get()).isGreaterThanOrEqualTo(1);
            OnRampQuote q = a.getArgument(0); assertThat(q.purchaseId()).isEqualTo("stable-purchase");
            assertThat(q.usdAmountE6()).isEqualTo("497500000"); return OnRampResult.notStarted("offline");
        });
        service.process(s.getId()); service.process(s.getId());
        verify(preparation, times(1)).prepareContractRecord(job, s);
        verify(onRamp, times(2)).execute(any(), eq("client-public"));
        assertPrimaryUnchanged();
    }

    @Test void onChainFailureNeverReversesMoneyOrJob() {
        when(onRamp.execute(any(), anyString())).thenReturn(OnRampResult.failed(null, null, null, "provider private data"));
        service.process(s.getId());
        assertThat(s.getOnChainStatus()).isEqualTo(SettlementStageStatus.FAILED);
        assertThat(s.getOnChainError()).isEqualTo("ON_RAMP_REJECTED");
        assertThat(s.getOffRampStatus()).isEqualTo(SettlementStageStatus.NOT_STARTED);
        assertPrimaryUnchanged(); verifyNoInteractions(client, withdrawal, vnd);
    }

    @Test void retryableChainFailureUsesSamePersistedQuoteThenSucceeds() {
        when(onRamp.execute(any(), anyString())).thenThrow(new IllegalStateException("offline secret"))
                .thenReturn(OnRampResult.confirmed("sig-onramp", "ata", "receipt", new BigDecimal("497.5")));
        doAnswer(a -> { p.setClientPaymentStatus(ClientPaymentStatus.CONFIRMED); p.setPaymentTransactionSignature("sig-payment"); return null; })
                .when(client).advance(p);
        service.process(s.getId()); assertThat(s.getOnChainStatus()).isEqualTo(SettlementStageStatus.FAILED_RETRYABLE);
        service.process(s.getId()); assertThat(s.getOnChainStatus()).isEqualTo(SettlementStageStatus.SUCCEEDED);
        assertThat(s.getOnChainReference()).isEqualTo("sig-payment"); assertThat(s.getOnChainError()).isNull();
        verify(preparation, times(1)).prepareContractRecord(job, s); assertPrimaryUnchanged();
    }

    @Test void submittedOnRampResumesByReceiptWithoutNewExecute() {
        p.setOnRampStatus(OnRampStatus.SUBMITTED); p.setOnRampTransactionSignature("submitted-signature");
        when(onRamp.resume(any(), anyString(), any(), any(), any(), any())).thenReturn(OnRampResult.submitted("submitted-signature", null, null, "waiting"));
        service.process(s.getId());
        verify(onRamp).resume(any(), eq("client-public"), eq("submitted-signature"), isNull(), isNull(), isNull());
        verify(onRamp, never()).execute(any(), any()); assertThat(s.getOnChainStatus()).isEqualTo(SettlementStageStatus.UNKNOWN);
    }

    @Test void offRampFailureLeavesPrimaryAndConfirmedOnChainIntact() {
        chainSucceeded();
        doAnswer(a -> { p.setOnChainOffRampStatus(OnChainOffRampStatus.FAILED); return null; }).when(withdrawal).advance(p);
        service.process(s.getId());
        assertThat(s.getOffRampStatus()).isEqualTo(SettlementStageStatus.FAILED);
        assertThat(s.getOnChainStatus()).isEqualTo(SettlementStageStatus.SUCCEEDED); assertPrimaryUnchanged();
    }

    @Test void retryableWithdrawalThenCompletionUsesExistingRecord() {
        chainSucceeded();
        doThrow(new IllegalStateException("network secret")).doAnswer(a -> { p.setOnChainOffRampStatus(OnChainOffRampStatus.CONFIRMED); return null; })
                .when(withdrawal).advance(p);
        doAnswer(a -> { p.setOffRampStatus(OffRampStatus.COMPLETED); p.setOffRampReference("offramp-job-stable"); return null; }).when(vnd).advance(p);
        service.process(s.getId()); assertThat(s.getOffRampStatus()).isEqualTo(SettlementStageStatus.FAILED_RETRYABLE);
        service.process(s.getId()); assertThat(s.getOffRampStatus()).isEqualTo(SettlementStageStatus.SUCCEEDED);
        assertThat(s.getOffRampReference()).isEqualTo("offramp-job-stable"); assertPrimaryUnchanged();
    }

    @Test void completedDownstreamStagesNeverRepeatRemoteOperations() {
        chainSucceeded(); s.setOffRampStatus(SettlementStageStatus.SUCCEEDED); s.setTaxStatus(SettlementStageStatus.SUCCEEDED);
        service.process(s.getId()); service.process(s.getId());
        verifyNoInteractions(onRamp, client, withdrawal, vnd, tax); assertThat(s.isRetryable()).isFalse();
    }

    @Test void taxPreparationCommitsBeforeRemoteReconciliation() {
        chainSucceeded(); s.setOffRampStatus(SettlementStageStatus.SUCCEEDED);
        doAnswer(a -> { assertThat(commits.get()).isGreaterThanOrEqualTo(5);
            s.setTaxStatus(SettlementStageStatus.UNKNOWN); return null; }).when(tax).advance(s);
        service.process(s.getId());
        var order = inOrder(tax); order.verify(tax).prepare(s); order.verify(tax).advance(s);
        assertThat(s.isRetryable()).isTrue(); assertPrimaryUnchanged();
    }

    @Test void existingAcceptedCertificateCanBeReportedWithoutProviderMutation() {
        chainSucceeded(); s.setOffRampStatus(SettlementStageStatus.SUCCEEDED);
        UUID id = UUID.randomUUID();
        doAnswer(a -> { s.setTaxStatus(SettlementStageStatus.SUCCEEDED); s.setTaxReference("certificate:" + id); return null; }).when(tax).advance(s);
        service.process(s.getId());
        assertThat(s.getTaxStatus()).isEqualTo(SettlementStageStatus.SUCCEEDED);
        assertThat(s.getTaxReference()).isEqualTo("certificate:" + id); assertPrimaryUnchanged();
    }

    @Test void pendingPrimaryDoesNotStartAnyDownstream() {
        s.setMoneyStatus(SettlementMoneyStatus.UNKNOWN); service.process(s.getId());
        verifyNoInteractions(preparation, payouts, onRamp, client, withdrawal, vnd, tax);
    }

    private void chainSucceeded() {
        s.setPayoutRecordId(p.getId()); s.setOnChainStatus(SettlementStageStatus.SUCCEEDED);
        p.setOnRampStatus(OnRampStatus.CONFIRMED); p.setClientPaymentStatus(ClientPaymentStatus.CONFIRMED);
    }
    private void assertPrimaryUnchanged() {
        assertThat(s.getMoneyStatus()).isEqualTo(SettlementMoneyStatus.SUCCEEDED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }
}
