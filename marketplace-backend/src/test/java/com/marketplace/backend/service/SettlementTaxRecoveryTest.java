package com.marketplace.backend.service;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.dto.response.misa.*;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.provider.currency.OnRampProvider;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.service.impl.PayoutServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.web.client.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"}, showSql = false)
@ContextConfiguration(classes = SettlementTaxRecoveryTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SettlementTaxRecoveryTest {
    @Configuration @EntityScan(basePackageClasses = Job.class)
    @EnableJpaRepositories(basePackageClasses = JobRepository.class)
    @Import({SettlementTaxService.class, SettlementDownstreamService.class})
    static class Config {
        @Bean TransactionTemplate tx(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
    }
    @Autowired SettlementDownstreamService service;
    @Autowired ContractSettlementRepository settlements;
    @Autowired FreelancerPayoutRecordRepository payouts;
    @Autowired TaxCertificateRecordRepository taxes;
    @Autowired JobRepository jobs;
    @Autowired WorkContractRepository contracts;
    @Autowired MilestoneRepository milestones;
    @Autowired UserRepository users;
    @Autowired TransactionTemplate tx;
    @MockitoBean MisaBackendClient misa;
    @MockitoBean PayoutServiceImpl preparation;
    @MockitoBean OnRampProvider onRamp;
    @MockitoBean ClientPaymentService client;
    @MockitoBean OnChainOffRampService withdrawal;
    @MockitoBean VndPayoutService vnd;
    ContractSettlement settlement;
    FreelancerPayoutRecord payout;
    Job job;
    WorkContract contract;
    Milestone milestone;
    UUID taxpayerId, misaPayoutId;
    Map<UUID, MisaCertificateRecoveryResult> ledger;
    AtomicInteger creates;

    @BeforeEach void setup() {
        taxes.deleteAll(); payouts.deleteAll(); settlements.deleteAll(); milestones.deleteAll(); contracts.deleteAll(); jobs.deleteAll(); users.deleteAll();
        tx.executeWithoutResult(status -> {
            User u = new User(); u.setEmail("tax-test@example.invalid"); u.setPassword("test-only"); u.setDisplayName("Test Freelancer");
            u.setAuthProvider(AuthProvider.LOCAL); u.setUserType(UserType.FREELANCER); u.setEnabled(true);
            u.setTaxCode("TEST-TAX"); u.setIdentityNumber("TEST-ID"); u.setNationality("VN"); users.saveAndFlush(u);
            job = new Job(); job.setTitle("Completed contract tax recovery"); job.setBudgetUsd(new BigDecimal("100.01"));
            job.setClientUserId(UUID.randomUUID()); job.setFreelancerId(u.getId()); job.setStatus(JobStatus.COMPLETED); jobs.saveAndFlush(job);
            contract = new WorkContract(); contract.setJobId(job.getId()); contract.setClientUserId(job.getClientUserId());
            contract.setFreelancerId(u.getId()); contract.setTitleSnapshot(job.getTitle()); contract.setBudgetUsd(job.getBudgetUsd());
            contract.setStatus(ContractStatus.COMPLETED); contract.setReviewWindowHours(72); contract.setMaxRevisions(2); contracts.saveAndFlush(contract);
            milestone = new Milestone(); milestone.setContractId(contract.getId()); milestone.setAmount(job.getBudgetUsd());
            milestone.setCurrency("USD"); milestone.setStatus(MilestoneStatus.RELEASED); milestones.saveAndFlush(milestone);
            settlement = new ContractSettlement(); settlement.setContractId(contract.getId()); settlement.setMilestoneId(milestone.getId());
            settlement.setJobId(job.getId()); settlement.setFreelancerId(u.getId()); settlement.setFundingTransactionId(UUID.randomUUID());
            settlement.setCheckoutOrderId(UUID.randomUUID()); settlement.setAmount(job.getBudgetUsd()); settlement.setCurrency("USD");
            settlement.setReleaseKey("settlement-" + milestone.getId()); settlement.setMoneyStatus(SettlementMoneyStatus.SUCCEEDED);
            settlement.setOnChainStatus(SettlementStageStatus.SUCCEEDED); settlement.setOffRampStatus(SettlementStageStatus.SUCCEEDED);
            settlements.saveAndFlush(settlement);
            payout = new FreelancerPayoutRecord(); payout.setJobId(job.getId()); payout.setFreelancerId(u.getId());
            payout.setClientUserId(job.getClientUserId()); payout.setContractSettlementId(settlement.getId()); payout.setAmountUsd(job.getBudgetUsd());
            payout.setOnRampFeeUsd(BigDecimal.ZERO); payout.setAmountUsdNet(job.getBudgetUsd()); payout.setOnRampUsdAmountE6("100000000");
            payout.setOnRampPurchaseId("100"); payout.setOnRampNetwork("localnet"); payout.setOffRampStatus(OffRampStatus.COMPLETED);
            payout.setTaxUsdToVndRate(new BigDecimal("25000.00")); payout.setTaxableAmountVnd(new BigDecimal("2500250"));
            payout.setTaxRateSource(ExchangeRateSource.FALLBACK_PLACEHOLDER); payout.setTaxRateObservedAt(Instant.now()); payouts.saveAndFlush(payout);
            settlement.setPayoutRecordId(payout.getId()); settlements.saveAndFlush(settlement);
        });
        taxpayerId = UUID.randomUUID(); misaPayoutId = UUID.randomUUID(); ledger = new ConcurrentHashMap<>(); creates = new AtomicInteger();
        when(misa.findCertificateByPlatformPayout(any())).thenAnswer(a -> ledger.get(a.getArgument(0)));
        when(misa.registerTaxpayerForExternal(any(), any(), any(), any(), any(), any())).thenReturn(taxpayerId);
        MisaPayoutTransactionResult p = mock(MisaPayoutTransactionResult.class); when(p.getId()).thenReturn(misaPayoutId);
        when(misa.recordPayoutTransaction(any(), any(), any(), any(), any(), any())).thenReturn(p);
        when(misa.createWithholdingCertificate(any(), anyString())).thenAnswer(a -> {
            // Preparation is committed before the remote effect, even when this transaction later fails.
            assertThat(taxes.findByJobId(job.getId()).orElseThrow().getCertificateCreateKey()).isEqualTo(key());
            var c = ledger.computeIfAbsent(job.getId(), id -> { creates.incrementAndGet(); return result(a.getArgument(1), "DRAFT"); });
            MisaCertificateStatusResult response = new MisaCertificateStatusResult(); response.setId(c.certificateId().toString()); return response;
        });
    }

    @Test void createSuccessStoresReferenceWithoutClaimingAuthorityAccepted() {
        service.process(settlement.getId());
        assertThat(current().getTaxStatus()).isEqualTo(SettlementStageStatus.SUCCEEDED);
        var record = taxes.findByJobId(job.getId()).orElseThrow(); assertThat(record.getStatus()).isEqualTo(TaxCertificateStatus.DRAFT);
        assertThat(record.getMisaCertificateId()).isEqualTo(ledger.get(job.getId()).certificateId());
        assertThat(current().isRetryable()).isFalse(); assertPrimaryUnchanged();
        var order = inOrder(misa); order.verify(misa).findCertificateByPlatformPayout(job.getId());
        order.verify(misa).registerTaxpayerForExternal(any(), any(), any(), any(), any(), any());
        order.verify(misa).recordPayoutTransaction(any(), eq(job.getId()), any(), any(), eq("settlement:" + settlement.getId()), eq("solana"));
        order.verify(misa).createWithholdingCertificate(misaPayoutId, key()); order.verify(misa).findCertificateByPlatformPayout(job.getId());
    }

    @Test void lostCreateResponseIsUnknownThenRestartLooksUpWithoutRecreating() {
        doAnswer(a -> { ledger.put(job.getId(), result(a.getArgument(1), "ACCEPTED")); creates.incrementAndGet();
            throw new ResourceAccessException("timeout private upstream detail"); }).when(misa).createWithholdingCertificate(any(), anyString());
        service.process(settlement.getId()); assertThat(current().getTaxStatus()).isEqualTo(SettlementStageStatus.UNKNOWN);
        assertThat(current().getTaxError()).isEqualTo("TAX_CREATE_UNRESOLVED"); assertPrimaryUnchanged();
        clearInvocations(misa);
        service.process(settlement.getId());
        assertThat(current().getTaxStatus()).isEqualTo(SettlementStageStatus.SUCCEEDED);
        verify(misa).findCertificateByPlatformPayout(job.getId()); verify(misa, never()).createWithholdingCertificate(any(), anyString());
        assertThat(creates.get()).isEqualTo(1); assertThat(taxes.findByJobId(job.getId()).orElseThrow().getCertificateCreateKey()).isEqualTo(key());
        assertPrimaryUnchanged();
    }

    @Test void missingAfterTimeoutRetriesSameCommittedKey() {
        doThrow(new ResourceAccessException("timeout")).when(misa).createWithholdingCertificate(any(), anyString());
        service.process(settlement.getId()); assertThat(current().getTaxStatus()).isEqualTo(SettlementStageStatus.UNKNOWN);
        clearInvocations(misa); service.process(settlement.getId());
        var order = inOrder(misa); order.verify(misa).findCertificateByPlatformPayout(job.getId());
        order.verify(misa).createWithholdingCertificate(misaPayoutId, key()); assertPrimaryUnchanged();
    }

    @Test void lookupUnavailableNeverPostsCreateAndPreservesPrimary() {
        doThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "private provider detail"))
                .when(misa).findCertificateByPlatformPayout(any());
        service.process(settlement.getId()); assertThat(current().getTaxStatus()).isEqualTo(SettlementStageStatus.UNKNOWN);
        verify(misa, never()).createWithholdingCertificate(any(), anyString()); assertPrimaryUnchanged();
    }

    @Test void explicitCreateConflictFailsWithoutReversingMoney() {
        doThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "provider private detail")).when(misa).createWithholdingCertificate(any(), anyString());
        service.process(settlement.getId()); assertThat(current().getTaxStatus()).isEqualTo(SettlementStageStatus.FAILED);
        assertThat(current().isRetryable()).isFalse(); assertThat(current().getTaxError()).isEqualTo("TAX_CREATE_REJECTED"); assertPrimaryUnchanged();
    }

    @Test void successfulTaxDoesNotRunAgain() {
        service.process(settlement.getId()); clearInvocations(misa); service.process(settlement.getId()); verifyNoInteractions(misa);
        assertThat(creates.get()).isEqualTo(1); assertPrimaryUnchanged();
    }

    @Test void remoteSuccessAndLocalCommitFailureRecoversFromPersistedIdentity() {
        doAnswer(a -> {
            ledger.put(job.getId(), result(a.getArgument(1), "DRAFT")); creates.incrementAndGet();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("simulated lost local commit"); }
            });
            MisaCertificateStatusResult c = new MisaCertificateStatusResult(); c.setId(ledger.get(job.getId()).certificateId().toString()); return c;
        }).when(misa).createWithholdingCertificate(any(), anyString());
        assertThatThrownBy(() -> service.process(settlement.getId())).hasMessageContaining("lost local commit");
        assertThat(taxes.findByJobId(job.getId()).orElseThrow().getCertificateCreateKey()).isEqualTo(key());
        assertThat(taxes.findByJobId(job.getId()).orElseThrow().getMisaCertificateId()).isNull();
        clearInvocations(misa); service.process(settlement.getId());
        assertThat(current().getTaxStatus()).isEqualTo(SettlementStageStatus.SUCCEEDED); assertThat(creates.get()).isEqualTo(1);
        verify(misa, never()).createWithholdingCertificate(any(), anyString()); assertPrimaryUnchanged();
    }

    @Test void concurrentSchedulerAttemptsCreateOneCertificate() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Void> call = () -> { start.await(); service.process(settlement.getId()); return null; };
            var a = pool.submit(call); var b = pool.submit(call); start.countDown(); a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
            assertThat(creates.get()).isEqualTo(1); assertThat(taxes.count()).isEqualTo(1); assertPrimaryUnchanged();
        } finally { pool.shutdownNow(); }
    }

    @Test void oldBlockedRowBecomesSchedulerEligibleAndReconciles() {
        tx.executeWithoutResult(status -> { var s = current(); s.setTaxStatus(SettlementStageStatus.FAILED);
            s.setTaxError("TAX_DOWNSTREAM_CONTRACT_BLOCKED"); s.setRetryable(false); s.setNextAttemptAt(Instant.EPOCH); settlements.save(s); });
        assertThat(settlements.findDueIds(Instant.now(), PageRequest.of(0, 50))).contains(settlement.getId());
        service.process(settlement.getId()); assertThat(current().getTaxStatus()).isEqualTo(SettlementStageStatus.SUCCEEDED);
    }

    @Test void mismatchedLookupIsNotAcceptedOrRecreated() {
        ledger.put(job.getId(), result("wrong-key", "DRAFT")); service.process(settlement.getId());
        assertThat(current().getTaxStatus()).isEqualTo(SettlementStageStatus.UNKNOWN);
        verify(misa, never()).createWithholdingCertificate(any(), anyString()); assertPrimaryUnchanged();
    }

    @Test void recoveryHonorsExistingMisaTwoDecimalIncomeAndMarketplaceWholeVndPrecision() {
        tx.executeWithoutResult(status -> {
            TaxCertificateRecord r = new TaxCertificateRecord(); r.setJobId(job.getId()); r.setFreelancerId(payout.getFreelancerId());
            r.setClientUserId(payout.getClientUserId()); r.setPayoutRecordId(payout.getId()); r.setAmountUsd(new BigDecimal("100.01"));
            r.setUsdToVndRate(new BigDecimal("25001.12")); r.setTaxableIncomeVnd(new BigDecimal("2500362"));
            r.setRateSource(ExchangeRateSource.FALLBACK_PLACEHOLDER); r.setCertificateCreateKey(key()); taxes.saveAndFlush(r);
            var p = payouts.findById(payout.getId()).orElseThrow(); p.setAmountUsd(new BigDecimal("100.01"));
            p.setTaxUsdToVndRate(new BigDecimal("25001.12"));
            p.setTaxableAmountVnd(new BigDecimal("2500362")); payouts.save(p);
        });
        ledger.put(job.getId(), new MisaCertificateRecoveryResult(UUID.randomUUID(), misaPayoutId, job.getId().toString(), key(), "DRAFT",
                "TEST1", "DEMO", new BigDecimal("100.01"), new BigDecimal("25001.12"), new BigDecimal("2500362.01"),
                new BigDecimal("250000.10"), "VND", LocalDateTime.now(), null, true));
        service.process(settlement.getId()); assertThat(current().getTaxStatus()).isEqualTo(SettlementStageStatus.SUCCEEDED);
        assertThat(taxes.findByJobId(job.getId()).orElseThrow().getTaxWithheldVnd()).isEqualByComparingTo("250000");
    }

    private MisaCertificateRecoveryResult result(String key, String status) {
        return new MisaCertificateRecoveryResult(UUID.randomUUID(), misaPayoutId, job.getId().toString(), key, status,
                "TEST1", "DEMO", payout.getAmountUsd(), payout.getTaxUsdToVndRate(), payout.getTaxableAmountVnd(),
                new BigDecimal("250000"), "VND", LocalDateTime.now(), null, true);
    }
    private String key() { return "freelax-tax-" + job.getId(); }
    private ContractSettlement current() { return settlements.findById(settlement.getId()).orElseThrow(); }
    private void assertPrimaryUnchanged() {
        assertThat(current().getMoneyStatus()).isEqualTo(SettlementMoneyStatus.SUCCEEDED);
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(contracts.findById(contract.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.COMPLETED);
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.RELEASED);
    }
}
