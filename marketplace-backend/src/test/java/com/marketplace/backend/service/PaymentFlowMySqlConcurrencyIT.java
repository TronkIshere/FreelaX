package com.marketplace.backend.service;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Races on real MySQL 8 with the Flyway schema (H2 does not reproduce InnoDB row locks).
 * Run with -Dfreelax.mysql.url=jdbc:mysql://127.0.0.1:3399/db -Dfreelax.mysql.password=...
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "freelax.mysql.url", matches = ".+")
@Import({PaymentFlowService.class, UnifiedFundingExpiryService.class})
class PaymentFlowMySqlConcurrencyIT {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("freelax.mysql.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("freelax.mysql.user", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("freelax.mysql.password"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired PaymentFlowService flowsService;
    @Autowired UnifiedFundingExpiryService expiry;
    @Autowired JobRepository jobs;
    @Autowired WorkContractRepository contracts;
    @Autowired MilestoneRepository milestones;
    @Autowired PaymentFlowRepository flows;
    @Autowired PaymentFlowStepRepository steps;
    @Autowired PaymentFlowEvidenceRepository evidence;
    @MockitoBean SolanaCprClient solana;
    @MockitoBean SolanaCprProperties solanaProperties;
    @MockitoBean NotificationService notifications;

    @Test
    void onlyOnePaymentFlowCanBeCreatedPerMilestone() throws Exception {
        Fixture f = fixture(ContractStatus.PENDING_FUNDING);
        List<Throwable> errors = race(2, () -> flowsService.createDraft(f.contract, f.milestone));
        assertThat(flows.findByMilestoneId(f.milestone.getId())).isPresent();
        assertThat(errors).hasSize(1);
        assertThat(flows.findAll().stream().filter(x -> x.getMilestoneId().equals(f.milestone.getId()))).hasSize(1);
    }

    @Test
    void opposingChainOutcomesCannotBothBeConfirmed() throws Exception {
        Fixture f = fixture(ContractStatus.ACTIVE);
        PaymentFlow flow = flowsService.createDraft(f.contract, f.milestone);
        PaymentFlowStep escrow = step(flow, "ESCROW");
        escrow.setStatus("CONFIRMED");
        escrow.setReference("escrow-pda");
        steps.saveAndFlush(escrow);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> release = pool.submit(() -> { await(start); flowsService.confirmChainSettlement(
                f.contract, f.milestone, "Released", "escrow-pda", "release-sig"); return null; });
        Future<?> refund = pool.submit(() -> { await(start); flowsService.confirmChainSettlement(
                f.contract, f.milestone, "Refunded", "escrow-pda", "refund-sig"); return null; });
        start.countDown();
        int failures = 0;
        for (Future<?> outcome : List.of(release, refund))
            try { outcome.get(30, TimeUnit.SECONDS); } catch (ExecutionException ex) { failures++; }
        pool.shutdown();
        long confirmed = steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId()).stream()
                .filter(s -> List.of("USDC_RELEASE", "USDC_REFUND").contains(s.getKind())
                        && "CONFIRMED".equals(s.getStatus())).count();
        assertThat(confirmed).isEqualTo(1);
        assertThat(failures).isEqualTo(1);
    }

    @Test
    void expiredFundingIsCancelledExactlyOnceUnderConcurrentAdmins() throws Exception {
        Fixture f = fixture(ContractStatus.PENDING_FUNDING);
        PaymentFlow flow = flowsService.createDraft(f.contract, f.milestone);
        flow.setFundingExpiresAt(Instant.now().minusSeconds(3600));
        flows.saveAndFlush(flow);
        PaymentFlowStep usdc = step(flow, "CLIENT_USDC");
        usdc.setStatus("CONFIRMED");
        usdc.setReference("receipt-pda");
        steps.saveAndFlush(usdc);
        when(solana.findEscrow(any())).thenReturn(Optional.empty());

        List<Throwable> errors = race(4, () -> expiry.cancelExpired(UUID.randomUUID(), f.contract.getId(),
                "Concurrent Admin cancellation drill"));

        assertThat(errors).hasSize(3);
        assertThat(evidence.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId()).stream()
                .filter(e -> "FUNDING_EXPIRED_CANCEL".equals(e.getKind()))).hasSize(1);
        assertThat(contracts.findById(f.contract.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.CANCELLED);
    }

    private PaymentFlowStep step(PaymentFlow flow, String kind) {
        return steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId()).stream()
                .filter(s -> kind.equals(s.getKind())).findFirst().orElseThrow();
    }

    private record Fixture(Job job, WorkContract contract, Milestone milestone) { }

    private Fixture fixture(ContractStatus status) {
        Job job = new Job();
        job.setClientUserId(UUID.randomUUID());
        job.setTitle("MySQL concurrency " + UUID.randomUUID());
        job.setBudgetUsd(new BigDecimal("10.00"));
        job.setCategory(JobCategory.require("WEB_FRONTEND"));
        job.setDeliveryDueAt(Instant.now().plusSeconds(86400));
        job.setStatus(status == ContractStatus.PENDING_FUNDING ? JobStatus.AWAITING_PAYMENT : JobStatus.IN_PROGRESS);
        job.setPaymentFlowVersion(1);
        job.setPaymentNetwork("localnet");
        job.setPaymentMint("mint");
        job.setReviewWindowHours(72);
        job.setMaxRevisions(2);
        job = jobs.saveAndFlush(job);
        WorkContract contract = new WorkContract();
        contract.setJobId(job.getId());
        contract.setClientUserId(job.getClientUserId());
        contract.setFreelancerId(UUID.randomUUID());
        contract.setTitleSnapshot(job.getTitle());
        contract.setBudgetUsd(job.getBudgetUsd());
        contract.setDeliveryDueAt(Instant.now().plusSeconds(86400));
        contract.setReviewWindowHours(72);
        contract.setMaxRevisions(2);
        contract.setRevisionsUsed(0);
        contract.setStatus(status);
        contract.setPaymentRail(PaymentFlow.RAIL);
        contract = contracts.saveAndFlush(contract);
        Milestone milestone = new Milestone();
        milestone.setContractId(contract.getId());
        milestone.setAmount(job.getBudgetUsd());
        milestone.setCurrency("USD");
        milestone.setStatus(status == ContractStatus.PENDING_FUNDING ? MilestoneStatus.PENDING_FUNDING : MilestoneStatus.FUNDED);
        milestone = milestones.saveAndFlush(milestone);
        return new Fixture(job, contract, milestone);
    }

    private List<Throwable> race(int threads, Callable<?> task) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) futures.add(pool.submit(() -> { await(start); return task.call(); }));
        start.countDown();
        List<Throwable> errors = new ArrayList<>();
        for (Future<?> future : futures) {
            try { future.get(30, TimeUnit.SECONDS); }
            catch (ExecutionException ex) { errors.add(ex.getCause()); }
            catch (TimeoutException ex) { errors.add(ex); }
        }
        pool.shutdown();
        return errors;
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
