package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.dispute.*;
import com.marketplace.backend.dto.request.cancellation.CreateCancellationRequest;
import com.marketplace.backend.dto.request.cancellation.CancellationDecisionRequest;
import com.marketplace.backend.dto.response.bofa.*;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
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
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
        "spring.jpa.show-sql=false"}, showSql = false)
@ContextConfiguration(classes = ContractDisputeServiceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContractDisputeServiceTest {
    @Configuration @EntityScan(basePackageClasses = Job.class)
    @EnableJpaRepositories(basePackageClasses = JobRepository.class)
    @Import({ContractDisputeService.class, ContractCancellationService.class, SettlementService.class})
    static class Config {
        @Bean TransactionTemplate transactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
    }
    @Autowired ContractDisputeService service;
    @Autowired ContractCancellationService refunds;
    @Autowired SettlementService releases;
    @Autowired JobRepository jobs;
    @Autowired WorkContractRepository contracts;
    @Autowired MilestoneRepository milestones;
    @Autowired FundingTransactionRepository funding;
    @Autowired ContractDisputeRepository disputes;
    @Autowired DisputeEvidenceRepository evidence;
    @Autowired DisputeEvidenceBatchRepository evidenceBatches;
    @Autowired DisputeAuditRepository audit;
    @Autowired ContractCancellationRepository cancellations;
    @Autowired ContractSettlementRepository settlements;
    @Autowired JobSubmissionRepository submissions;
    @Autowired TransactionTemplate tx;
    @MockitoBean PaymentBackendClient payment;
    @MockitoBean NotificationService notifications;
    @MockitoBean SettlementDownstreamService downstream;
    Job job; WorkContract contract; Milestone milestone; FundingTransaction paid;
    UUID admin = UUID.randomUUID();
    OpenDisputeRequest reason = new OpenDisputeRequest("QUALITY", "Work is disputed",
            List.of(new DisputeEvidenceInput(DisputeEvidence.Kind.TEXT, "Contract terms differ", null, null)));

    @BeforeEach void setup() {
        audit.deleteAll(); evidence.deleteAll(); evidenceBatches.deleteAll(); disputes.deleteAll(); cancellations.deleteAll();
        settlements.deleteAll(); submissions.deleteAll(); funding.deleteAll();
        milestones.deleteAll(); contracts.deleteAll(); jobs.deleteAll();
        tx.executeWithoutResult(s -> {
            job = new Job(); job.setTitle("Dispute test"); job.setBudgetUsd(new BigDecimal("500.00"));
            job.setClientUserId(UUID.randomUUID()); job.setFreelancerId(UUID.randomUUID());
            job.setCheckoutOrderId(UUID.randomUUID()); job.setStatus(JobStatus.IN_PROGRESS);
            jobs.saveAndFlush(job);
            contract = new WorkContract(); contract.setJobId(job.getId());
            contract.setClientUserId(job.getClientUserId()); contract.setFreelancerId(job.getFreelancerId());
            contract.setTitleSnapshot(job.getTitle()); contract.setBudgetUsd(job.getBudgetUsd());
            contract.setStatus(ContractStatus.ACTIVE); contract.setReviewWindowHours(72); contract.setMaxRevisions(2);
            contracts.saveAndFlush(contract);
            milestone = new Milestone(); milestone.setContractId(contract.getId());
            milestone.setAmount(job.getBudgetUsd()); milestone.setCurrency("USD"); milestone.setStatus(MilestoneStatus.FUNDED);
            milestones.saveAndFlush(milestone);
            paid = new FundingTransaction(); paid.setContractId(contract.getId()); paid.setMilestoneId(milestone.getId());
            paid.setClientUserId(client()); paid.setAmount(job.getBudgetUsd()); paid.setCurrency("USD");
            paid.setStatus(FundingStatus.SUCCEEDED); paid.setCheckoutOrderId(job.getCheckoutOrderId());
            paid.setIdempotencyKey("funding-1"); paid.setPayloadHash("hash"); paid.setPaymentMethodId("BANK_ACCOUNT_ON_FILE");
            paid.setPayerBankCode("TEST"); paid.setPayerBankAccountNumber("PRIVATE_BANK");
            paid.setPayerBankAccountHolderName("Private"); funding.saveAndFlush(paid);
        });
    }
    UUID client() { return job.getClientUserId(); }
    UUID freelancer() { return job.getFreelancerId(); }
    UUID opened() { return service.open(freelancer(), contract.getId(), reason).disputeId(); }
    void claimed(UUID id) { service.claim(admin, id); }
    void reviewState() {
        tx.executeWithoutResult(s -> {
            contract = contracts.findById(contract.getId()).orElseThrow();
            milestone = milestones.findById(milestone.getId()).orElseThrow();
            job = jobs.findById(job.getId()).orElseThrow();
            contract.setStatus(ContractStatus.UNDER_REVIEW); milestone.setStatus(MilestoneStatus.SUBMITTED);
            job.setStatus(JobStatus.SUBMITTED_FOR_REVIEW);
            JobSubmission sub = new JobSubmission(); sub.setJobId(job.getId()); sub.setContractId(contract.getId());
            sub.setMilestoneId(milestone.getId()); sub.setFreelancerId(freelancer()); sub.setVersion(1);
            sub.setSummary("Submitted work"); sub.setStatus(JobSubmissionStatus.SUBMITTED);
            submissions.saveAndFlush(sub);
        });
    }
    @Test void participantCanOpenWithImmutableEvidenceAndOutsiderCannotReadOrOpen() {
        UUID id = opened();
        assertThat(service.get(client(), contract.getId()).disputeId()).isEqualTo(id);
        assertThat(service.get(client(), contract.getId()).evidence()).hasSize(1);
        assertThat(audit.findByDisputeIdOrderByCreatedAtAsc(id)).hasSize(1);
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), contract.getId())).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> service.open(UUID.randomUUID(), contract.getId(), reason)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> service.open(client(), contract.getId(), reason)).isInstanceOf(ApplicationException.class);
        assertThat(contracts.findById(contract.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.DISPUTED);
    }
    @Test void twoThousandCharacterDescriptionPersistsDisputeAndOpeningAudit() {
        String description = "d".repeat(2000);
        var result = service.open(freelancer(), contract.getId(),
                new OpenDisputeRequest("QUALITY", description, List.of()));

        assertThat(result.description()).isEqualTo(description);
        assertThat(disputes.findById(result.disputeId()).orElseThrow().getDescription()).isEqualTo(description);
        assertThat(audit.findByDisputeIdOrderByCreatedAtAsc(result.disputeId())).singleElement()
                .satisfies(row -> {
                    assertThat(row.getAction()).isEqualTo("OPENED");
                    assertThat(row.getActorId()).isEqualTo(freelancer());
                    assertThat(row.getAfterStatus()).isEqualTo("OPEN");
                    assertThat(row.getReason()).isEqualTo(description);
                });
    }
    @Test void oversizedDescriptionIsRejectedBeforeDisputeOrAuditPersistence() {
        assertThatThrownBy(() -> service.open(client(), contract.getId(),
                new OpenDisputeRequest("QUALITY", "d".repeat(2001), List.of())))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DISPUTE_REASON_REQUIRED);

        assertThat(disputes.count()).isZero();
        assertThat(audit.count()).isZero();
        assertThat(evidence.count()).isZero();
        assertThat(contracts.findById(contract.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.FUNDED);
        verifyNoInteractions(notifications);
    }
    @Test void invalidStateAndUnsafeEvidenceCannotOpen() {
        tx.executeWithoutResult(s -> milestones.findById(milestone.getId()).orElseThrow().setStatus(MilestoneStatus.RELEASE_PENDING));
        assertThatThrownBy(this::opened).isInstanceOf(ApplicationException.class);
        tx.executeWithoutResult(s -> milestones.findById(milestone.getId()).orElseThrow().setStatus(MilestoneStatus.FUNDED));
        var bad = new OpenDisputeRequest("QUALITY", "bad link",
                List.of(new DisputeEvidenceInput(DisputeEvidence.Kind.LINK, null, "http://insecure.test", null)));
        assertThatThrownBy(() -> service.open(client(), contract.getId(), bad)).isInstanceOf(ApplicationException.class);
    }
    @Test void claimFreezesEvidenceAndResolutionIsImmutable() {
        UUID id = opened();
        claimed(id);
        assertThatThrownBy(() -> service.addEvidence(client(), contract.getId(), id, "evidence-1", reason.evidence()))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> service.claim(UUID.randomUUID(), id)).isInstanceOf(ApplicationException.class);
        var decision = new ResolveDisputeRequest(ResolveDisputeRequest.Outcome.REFUND_TO_CLIENT, "Evidence reviewed");
        var first = service.resolve(admin, id, "decision-1", decision);
        assertThat(first.status()).isEqualTo(DisputeStatus.DECISION_PENDING_REFUND);
        assertThat(first.refundStatus()).isEqualTo(SettlementMoneyStatus.PENDING);
        assertThat(service.resolve(admin, id, "decision-1", decision).status()).isEqualTo(first.status());
        assertThatThrownBy(() -> service.resolve(admin, id, "decision-2", decision)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> service.resolve(client(), id, "decision-1", decision)).isInstanceOf(ApplicationException.class);
        assertThat(audit.findByDisputeIdOrderByCreatedAtAsc(id).stream().filter(a -> a.getAction().equals("DECIDED"))).hasSize(1);
        verifyNoInteractions(payment);
    }
    @Test void refundUsesStepFiveIdentityOnceAndBlocksRelease() {
        var proposal = refunds.request(client(), contract.getId(),
                new CreateCancellationRequest("MUTUAL", "Request cancellation"));
        refunds.decide(freelancer(), contract.getId(), proposal.cancellationId(),
                new CancellationDecisionRequest(CancellationDecisionRequest.Decision.REJECT));
        UUID id = opened(); claimed(id);
        service.resolve(admin, id, "refund-decision", new ResolveDisputeRequest(
                ResolveDisputeRequest.Outcome.REFUND_TO_CLIENT, "Client should receive full refund"));
        AtomicInteger credits = new AtomicInteger();
        when(payment.createRefund(any())).thenAnswer(a -> {
            var request = (com.marketplace.backend.dto.request.bofa.CreateRefundRequest) a.getArgument(0);
            assertThat(request.refundKey()).isEqualTo("marketplace-refund-" + milestone.getId());
            credits.incrementAndGet(); UUID rid = UUID.randomUUID();
            return new PaymentRefundResult(rid, request.refundKey(), request.checkoutOrderId(), client(), "SUCCEEDED",
                    new BigDecimal(request.expectedAmount().amount()), "USD", true, "sim-refund-" + rid,
                    false, Instant.now(), Instant.now());
        });
        refunds.processDisputeRefund(id);
        refunds.processDisputeRefund(id);
        assertThat(credits.get()).isEqualTo(1);
        assertThat(cancellations.findByContractId(contract.getId()).orElseThrow().getStatus())
                .isEqualTo(CancellationStatus.REJECTED);
        assertThat(disputes.findById(id).orElseThrow().getStatus()).isEqualTo(DisputeStatus.RESOLVED_REFUND);
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.REFUNDED);
        assertThatThrownBy(() -> releases.prepare(milestone.getId())).isInstanceOf(ApplicationException.class);
    }
    @Test void releaseUsesStepFourSettlementOnceAndBlocksRefund() {
        reviewState();
        UUID id = service.open(client(), contract.getId(), reason).disputeId(); claimed(id);
        service.resolve(admin, id, "release-decision", new ResolveDisputeRequest(
                ResolveDisputeRequest.Outcome.RELEASE_TO_FREELANCER, "Work satisfies criteria"));
        UUID settlementId = releases.prepare(milestone.getId());
        assertThat(releases.prepare(milestone.getId())).isEqualTo(settlementId);
        AtomicInteger credits = new AtomicInteger();
        when(payment.createRelease(any())).thenAnswer(a -> {
            var request = (com.marketplace.backend.dto.request.bofa.CreateReleaseRequest) a.getArgument(0);
            credits.incrementAndGet(); UUID rid = UUID.randomUUID();
            return new PaymentReleaseResult(rid, request.releaseKey(), request.checkoutOrderId(), freelancer(),
                    "SUCCEEDED", new BigDecimal(request.expectedAmount().amount()), "USD", true,
                    "sim-release-" + rid, false, Instant.now(), Instant.now());
        });
        releases.processMoney(settlementId);
        releases.processMoney(settlementId);
        assertThat(credits.get()).isEqualTo(1);
        assertThat(disputes.findById(id).orElseThrow().getStatus()).isEqualTo(DisputeStatus.RESOLVED_RELEASE);
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.RELEASED);
        verify(payment, never()).createRefund(any());
    }
    @Test void ambiguousRefundStaysPendingWithSameKey() {
        UUID id = opened(); claimed(id);
        service.resolve(admin, id, "decision-unknown", new ResolveDisputeRequest(
                ResolveDisputeRequest.Outcome.REFUND_TO_CLIENT, "Refund is justified"));
        when(payment.findRefund(anyString())).thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));
        refunds.processDisputeRefund(id);
        ContractDispute d = disputes.findById(id).orElseThrow();
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.DECISION_PENDING_REFUND);
        assertThat(d.getRefundStatus()).isEqualTo(SettlementMoneyStatus.UNKNOWN);
        assertThat(d.getRefundKey()).isEqualTo("marketplace-refund-" + milestone.getId());
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.REFUND_PENDING);
        verify(payment, never()).createRefund(any());
    }
    @Test void activeDisputeBlocksOrdinaryCancellationAndAdminDetailOmitsBankData() {
        UUID id = opened();
        assertThatThrownBy(() -> refunds.request(client(), contract.getId(),
                new CreateCancellationRequest("MUTUAL", "Stop work"))).isInstanceOf(ApplicationException.class);
        var detail = service.detail(id);
        assertThat(detail.fundingStatus()).isEqualTo(FundingStatus.SUCCEEDED);
        assertThat(detail.audit()).hasSize(1);
        assertThat(detail.toString()).doesNotContain("PRIVATE_BANK", "marketplace-refund-");
    }
    @Test void pendingMutualCancellationCannotBeAcceptedAfterDisputeOpens() {
        var proposal = refunds.request(client(), contract.getId(),
                new CreateCancellationRequest("MUTUAL", "Request cancellation"));
        opened();
        assertThat(refunds.get(freelancer(), contract.getId()).allowedActions()).isEmpty();
        assertThatThrownBy(() -> refunds.decide(freelancer(), contract.getId(), proposal.cancellationId(),
                new CancellationDecisionRequest(CancellationDecisionRequest.Decision.ACCEPT)))
                .isInstanceOf(ApplicationException.class);
        verifyNoInteractions(payment);
    }
    @Test void evidenceRetryReusesPersistedBatchAndChangedPayloadConflicts() {
        UUID id = opened();
        List<DisputeEvidenceInput> items = List.of(new DisputeEvidenceInput(
                DisputeEvidence.Kind.LINK, "Delivery URL", "https://example.test/work", null));
        service.addEvidence(client(), contract.getId(), id, "evidence-1", items);
        service.addEvidence(client(), contract.getId(), id, "evidence-1", items);
        assertThat(evidence.findByDisputeIdOrderByCreatedAtAsc(id)).hasSize(2);
        assertThat(evidenceBatches.count()).isEqualTo(1);
        assertThatThrownBy(() -> service.addEvidence(client(), contract.getId(), id, "evidence-1",
                reason.evidence())).isInstanceOf(ApplicationException.class);
    }
    @Test void ambiguousReleaseRemainsDecisionPending() {
        UUID id = opened(); claimed(id);
        service.resolve(admin, id, "release-unknown", new ResolveDisputeRequest(
                ResolveDisputeRequest.Outcome.RELEASE_TO_FREELANCER, "Release is justified"));
        UUID settlementId = releases.prepare(milestone.getId());
        when(payment.findRelease(anyString())).thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));
        releases.processMoney(settlementId);
        assertThat(disputes.findById(id).orElseThrow().getStatus()).isEqualTo(DisputeStatus.DECISION_PENDING_RELEASE);
        assertThat(settlements.findById(settlementId).orElseThrow().getMoneyStatus()).isEqualTo(SettlementMoneyStatus.UNKNOWN);
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.RELEASE_PENDING);
        verify(payment, never()).createRelease(any());
    }
    @Test void competingAdminOutcomesCannotBothWin() throws Exception {
        UUID id = opened(); claimed(id);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> release = pool.submit(() -> {
                await(start);
                try { service.resolve(admin, id, "race-release", new ResolveDisputeRequest(
                        ResolveDisputeRequest.Outcome.RELEASE_TO_FREELANCER, "Release")); }
                catch (RuntimeException ignored) { }
            });
            Future<?> refund = pool.submit(() -> {
                await(start);
                try { service.resolve(admin, id, "race-refund", new ResolveDisputeRequest(
                        ResolveDisputeRequest.Outcome.REFUND_TO_CLIENT, "Refund")); }
                catch (RuntimeException ignored) { }
            });
            start.countDown(); release.get(10, TimeUnit.SECONDS); refund.get(10, TimeUnit.SECONDS);
            ContractDispute d = disputes.findById(id).orElseThrow();
            assertThat(d.getStatus()).isIn(DisputeStatus.DECISION_PENDING_RELEASE, DisputeStatus.DECISION_PENDING_REFUND);
            assertThat(audit.findByDisputeIdOrderByCreatedAtAsc(id).stream().filter(a -> a.getAction().equals("DECIDED"))).hasSize(1);
            assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(
                    d.getStatus() == DisputeStatus.DECISION_PENDING_RELEASE
                            ? MilestoneStatus.RELEASE_PENDING : MilestoneStatus.REFUND_PENDING);
            verifyNoInteractions(payment);
        } finally { pool.shutdownNow(); }
    }
    private void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
