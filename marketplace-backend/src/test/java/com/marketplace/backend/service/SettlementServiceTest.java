package com.marketplace.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.bofa.CreateReleaseRequest;
import com.marketplace.backend.dto.request.submission.ReviewSubmissionRequest;
import com.marketplace.backend.dto.response.bofa.PaymentReleaseResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
        "spring.jpa.show-sql=false"}, showSql = false)
@ContextConfiguration(classes = SettlementServiceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SettlementServiceTest {
    @Configuration
    @EntityScan(basePackageClasses = Job.class)
    @EnableJpaRepositories(basePackageClasses = JobRepository.class)
    @Import({SettlementService.class, ContractSubmissionService.class})
    static class Config {
        @Bean TransactionTemplate transactions(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }

    @Autowired SettlementService service;
    @Autowired ContractSubmissionService reviews;
    @Autowired ContractSettlementRepository settlements;
    @Autowired WorkContractRepository contracts;
    @Autowired MilestoneRepository milestones;
    @Autowired JobRepository jobs;
    @Autowired JobSubmissionRepository submissions;
    @Autowired FundingTransactionRepository funding;
    @Autowired ContractDisputeRepository disputes;
    @Autowired TransactionTemplate tx;
    @Autowired ObjectMapper json;
    @MockitoBean PaymentBackendClient payment;
    @MockitoBean NotificationService notifications;
    @MockitoBean SettlementDownstreamService downstream;
    Job job;
    WorkContract contract;
    Milestone milestone;
    FundingTransaction paid;
    JobSubmission submission;
    Map<String, PaymentReleaseResult> ledger;
    AtomicInteger credits;

    @BeforeEach void setup() {
        disputes.deleteAll(); settlements.deleteAll(); submissions.deleteAll();
        funding.deleteAll(); milestones.deleteAll(); contracts.deleteAll(); jobs.deleteAll();
        UUID client = UUID.randomUUID(), freelancer = UUID.randomUUID(), checkout = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            job = new Job(); job.setTitle("Settlement test"); job.setBudgetUsd(new BigDecimal("500.00"));
            job.setClientUserId(client); job.setFreelancerId(freelancer); job.setCheckoutOrderId(checkout);
            job.setStatus(JobStatus.SUBMITTED_FOR_REVIEW); jobs.saveAndFlush(job);
            contract = new WorkContract(); contract.setJobId(job.getId()); contract.setClientUserId(client);
            contract.setFreelancerId(freelancer); contract.setTitleSnapshot(job.getTitle());
            contract.setBudgetUsd(job.getBudgetUsd()); contract.setStatus(ContractStatus.UNDER_REVIEW);
            contract.setReviewWindowHours(72); contract.setMaxRevisions(2); contracts.saveAndFlush(contract);
            milestone = new Milestone(); milestone.setContractId(contract.getId()); milestone.setAmount(job.getBudgetUsd());
            milestone.setCurrency("USD"); milestone.setStatus(MilestoneStatus.RELEASE_PENDING); milestones.saveAndFlush(milestone);
            paid = new FundingTransaction(); paid.setContractId(contract.getId()); paid.setMilestoneId(milestone.getId());
            paid.setClientUserId(client); paid.setAmount(job.getBudgetUsd()); paid.setCurrency("USD");
            paid.setStatus(FundingStatus.SUCCEEDED); paid.setCheckoutOrderId(checkout);
            paid.setIdempotencyKey("funding-test"); paid.setPayloadHash("test"); paid.setPaymentMethodId("BANK_ACCOUNT_ON_FILE");
            paid.setPayerBankCode("TEST"); paid.setPayerBankAccountNumber("PRIVATE_BANK"); paid.setPayerBankAccountHolderName("Private");
            funding.saveAndFlush(paid);
            submission = new JobSubmission(); submission.setContractId(contract.getId()); submission.setMilestoneId(milestone.getId());
            submission.setJobId(job.getId()); submission.setFreelancerId(freelancer); submission.setVersion(1);
            submission.setSummary("Real approved document"); submission.setStatus(JobSubmissionStatus.APPROVED);
            submission.setReviewedAt(LocalDateTime.now()); submissions.saveAndFlush(submission);
        });
        ledger = new ConcurrentHashMap<>(); credits = new AtomicInteger();
        when(payment.findRelease(anyString())).thenAnswer(a -> ledger.get(a.getArgument(0)));
        when(payment.createRelease(any())).thenAnswer(a -> {
            CreateReleaseRequest r = a.getArgument(0);
            // Emulate the remote ledger, independent of Marketplace rollback.
            return ledger.computeIfAbsent(r.releaseKey(), key -> {
                credits.incrementAndGet();
                return result(r, "SUCCEEDED", false);
            });
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"milestone", "funding", "dispute", "dispute-review", "submission", "approval-time",
            "freelancer", "client", "checkout", "funding-contract", "funding-client", "amount", "currency", "submission-milestone"})
    void authoritativeIneligibleDataCannotReachPayment(String violation) {
        tx.executeWithoutResult(status -> {
            switch (violation) {
                case "milestone" -> milestones.findById(milestone.getId()).orElseThrow().setStatus(MilestoneStatus.SUBMITTED);
                case "funding" -> funding.findById(paid.getId()).orElseThrow().setStatus(FundingStatus.UNKNOWN);
                case "dispute", "dispute-review" -> {
                    ContractDispute d = new ContractDispute(); d.setContractId(contract.getId()); d.setSubmissionId(submission.getId());
                    d.setOpenedBy(contract.getClientUserId()); d.setReasonCode("TEST"); d.setDescription("Open dispute");
                    d.setStatus(violation.equals("dispute") ? DisputeStatus.OPEN : DisputeStatus.UNDER_REVIEW); disputes.save(d);
                }
                case "submission" -> submissions.findById(submission.getId()).orElseThrow().setStatus(JobSubmissionStatus.SUBMITTED);
                case "approval-time" -> submissions.findById(submission.getId()).orElseThrow().setReviewedAt(null);
                case "freelancer" -> jobs.findById(job.getId()).orElseThrow().setFreelancerId(UUID.randomUUID());
                case "client" -> jobs.findById(job.getId()).orElseThrow().setClientUserId(UUID.randomUUID());
                case "checkout" -> funding.findById(paid.getId()).orElseThrow().setCheckoutOrderId(UUID.randomUUID());
                case "funding-contract" -> funding.findById(paid.getId()).orElseThrow().setContractId(UUID.randomUUID());
                case "funding-client" -> funding.findById(paid.getId()).orElseThrow().setClientUserId(UUID.randomUUID());
                case "amount" -> funding.findById(paid.getId()).orElseThrow().setAmount(new BigDecimal("499.99"));
                case "currency" -> funding.findById(paid.getId()).orElseThrow().setCurrency("VND");
                case "submission-milestone" -> submissions.findById(submission.getId()).orElseThrow().setMilestoneId(UUID.randomUUID());
                default -> throw new AssertionError();
            }
        });
        assertThatThrownBy(() -> service.prepare(milestone.getId())).isInstanceOf(ApplicationException.class);
        assertThat(settlements.count()).isZero(); verifyNoInteractions(payment);
    }

    @Test void preparationCommitsOnceBeforeCallingPaymentAndReusesStableKey() {
        UUID id = service.prepare(milestone.getId());
        assertThat(service.prepare(milestone.getId())).isEqualTo(id);
        assertThat(settlements.count()).isEqualTo(1);
        assertThat(saved(id).getReleaseKey()).isEqualTo("marketplace-settlement-" + milestone.getId());
        verifyNoInteractions(payment);
    }

    @Test void outerTransactionRollbackCannotEraseTheReleaseIdentity() {
        UUID[] id = {null};
        tx.executeWithoutResult(status -> {
            id[0] = service.prepare(milestone.getId());
            status.setRollbackOnly();
        });
        assertThat(saved(id[0]).getReleaseKey()).isEqualTo("marketplace-settlement-" + milestone.getId());
        service.processMoney(id[0]); assertCompleted(id[0]);
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "PROCESSING", "UNKNOWN", "FAILED"})
    void respectsProviderStateAndNeverPostsAgainWhenReleaseAlreadyExists(String remoteState) {
        UUID id = service.prepare(milestone.getId()); ContractSettlement snapshot = saved(id);
        var r = new CreateReleaseRequest(snapshot.getCheckoutOrderId(), snapshot.getFreelancerId(),
                new CreateReleaseRequest.ExpectedAmount("500.00", "USD"), snapshot.getReleaseKey());
        ledger.put(snapshot.getReleaseKey(), result(r, remoteState, false));
        service.processMoney(id);
        SettlementMoneyStatus expected = switch (remoteState) {
            case "FAILED" -> SettlementMoneyStatus.FAILED;
            case "UNKNOWN" -> SettlementMoneyStatus.UNKNOWN;
            default -> SettlementMoneyStatus.PROCESSING;
        };
        assertThat(saved(id).getMoneyStatus()).isEqualTo(expected);
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.RELEASE_PENDING);
        verify(payment, never()).createRelease(any());
    }

    @Test void releaseSendsExactImmutablePayloadWithoutCaptureOrDebit() {
        UUID id = service.prepare(milestone.getId());
        service.processMoney(id);
        verify(payment).createRelease(new CreateReleaseRequest(paid.getCheckoutOrderId(), contract.getFreelancerId(),
                new CreateReleaseRequest.ExpectedAmount("500.00", "USD"), saved(id).getReleaseKey()));
        verify(payment).findRelease(saved(id).getReleaseKey());
        verifyNoMoreInteractions(payment); // Includes no checkout/capture call.
        assertCompleted(id);
    }

    @Test void duplicateInvocationSkipsConfirmedMoneyAndNotification() {
        UUID id = service.prepare(milestone.getId()); service.processMoney(id); service.processMoney(id);
        assertThat(credits.get()).isEqualTo(1); verify(payment, times(1)).createRelease(any());
        verify(notifications, times(1)).notify(eq(contract.getFreelancerId()), eq(NotificationType.RELEASE_CONFIRMED),
                anyString(), contains("mô phỏng"), eq(job.getId()));
    }

    @Test void timeoutLeavesUnknownAndReconcilesOriginalKeyWithoutNewRelease() {
        UUID id = service.prepare(milestone.getId());
        doAnswer(a -> {
            var r = (CreateReleaseRequest) a.getArgument(0);
            ledger.put(r.releaseKey(), result(r, "SUCCEEDED", false)); credits.incrementAndGet();
            throw new ResourceAccessException("timeout PRIVATE_BANK access-token");
        }).when(payment).createRelease(any());
        service.processMoney(id);
        assertThat(saved(id).getMoneyStatus()).isEqualTo(SettlementMoneyStatus.UNKNOWN);
        assertThat(saved(id).getLastError()).isEqualTo("PAYMENT_OUTCOME_UNKNOWN");
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.RELEASE_PENDING);
        due(id); service.processMoney(id); assertCompleted(id);
        verify(payment, times(1)).createRelease(any()); verify(payment, times(2)).findRelease(saved(id).getReleaseKey());
        assertThat(credits.get()).isEqualTo(1);
    }

    @Test void remoteSuccessSurvivesMarketplaceCommitFailureAndFreshServiceReconciles() {
        UUID id = service.prepare(milestone.getId()); String key = saved(id).getReleaseKey();
        AtomicInteger commits = new AtomicInteger();
        doAnswer(a -> {
            if (commits.getAndIncrement() == 0) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("Simulated lost commit"); }
            });
            return null;
        }).when(notifications).notify(any(), eq(NotificationType.RELEASE_CONFIRMED), anyString(), anyString(), any());
        assertThatThrownBy(() -> service.processMoney(id)).isInstanceOf(IllegalStateException.class);
        assertThat(saved(id).getMoneyStatus()).isEqualTo(SettlementMoneyStatus.PENDING);
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.SUBMITTED_FOR_REVIEW);
        SettlementService restarted = new SettlementService(settlements, milestones, contracts, jobs, submissions,
                funding, disputes, payment, notifications, tx, downstream);
        restarted.processMoney(id); assertCompleted(id); assertThat(saved(id).getReleaseKey()).isEqualTo(key);
        assertThat(credits.get()).isEqualTo(1); verify(payment, times(1)).createRelease(any());
    }

    @Test void concurrentPreparationAndWorkersCreateOneRowAndOneCredit() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<UUID> prepare = () -> { start.await(); return service.prepare(milestone.getId()); };
            Future<UUID> first = workers.submit(prepare), second = workers.submit(prepare); start.countDown();
            UUID id = first.get(10, TimeUnit.SECONDS); assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(id);
            Future<?> a = workers.submit(() -> service.processMoney(id)), b = workers.submit(() -> service.processMoney(id));
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
            assertThat(settlements.count()).isEqualTo(1); assertThat(credits.get()).isEqualTo(1); assertCompleted(id);
        } finally { workers.shutdownNow(); }
    }

    @Test void fundingTamperAfterPrepareCannotReleaseSnapshot() {
        UUID id = service.prepare(milestone.getId());
        tx.executeWithoutResult(s -> funding.findById(paid.getId()).orElseThrow().setAmount(new BigDecimal("1.00")));
        assertThatThrownBy(() -> service.processMoney(id)).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(payment);
    }

    @ParameterizedTest @ValueSource(ints = {400, 401, 409, 429, 500, 408})
    void distinguishesDefinitiveHttpFailureFromAmbiguousOutcome(int status) {
        UUID id = service.prepare(milestone.getId());
        doThrow(HttpClientErrorException.create(HttpStatus.valueOf(status),
                "unsafe provider body", null, new byte[0], null)).when(payment).createRelease(any());
        service.processMoney(id);
        SettlementMoneyStatus expected = status == 429 ? SettlementMoneyStatus.FAILED_RETRYABLE
                : status >= 500 || status == 408 ? SettlementMoneyStatus.UNKNOWN : SettlementMoneyStatus.FAILED;
        assertThat(saved(id).getMoneyStatus()).isEqualTo(expected);
        assertThat(saved(id).isRetryable()).isEqualTo(expected != SettlementMoneyStatus.FAILED);
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.SUBMITTED_FOR_REVIEW);
    }

    @Test void malformedRemoteIdentityDoesNotCompleteBusinessState() {
        UUID id = service.prepare(milestone.getId());
        doAnswer(a -> {
            var r = (CreateReleaseRequest) a.getArgument(0);
            return result(new CreateReleaseRequest(r.checkoutOrderId(), UUID.randomUUID(), r.expectedAmount(), r.releaseKey()), "SUCCEEDED", false);
        }).when(payment).createRelease(any());
        service.processMoney(id); assertThat(saved(id).getMoneyStatus()).isEqualTo(SettlementMoneyStatus.UNKNOWN);
        assertThat(saved(id).getLastError()).isEqualTo("PAYMENT_RESPONSE_MISMATCH");
    }

    @Test void failedLookupDoesNotProveAnEarlierReleaseFailed() {
        UUID id = service.prepare(milestone.getId());
        doThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "private error", null, new byte[0], null))
                .when(payment).findRelease(anyString());
        service.processMoney(id);
        assertThat(saved(id).getMoneyStatus()).isEqualTo(SettlementMoneyStatus.UNKNOWN);
        assertThat(saved(id).getLastError()).isEqualTo("PAYMENT_LOOKUP_UNAVAILABLE");
        verify(payment, never()).createRelease(any());
    }

    @Test void workerBackoffExcludesUnresolvedSettlementUntilDue() {
        UUID id = service.prepare(milestone.getId());
        when(payment.findRelease(anyString())).thenThrow(new ResourceAccessException("offline")); service.processMoney(id);
        service.processMoney(id); verify(payment, times(1)).findRelease(anyString());
        assertThat(settlements.findDueIds(Instant.now(), PageRequest.of(0, 50))).doesNotContain(id);
        assertThat(milestones.findUnsettledReleaseIds(PageRequest.of(0, 50))).isEmpty();
    }

    @Test void publicReadAuthorizesBothParticipantsAndOmitsPrivatePayload() throws Exception {
        UUID id = service.prepare(milestone.getId()); service.processMoney(id);
        edit(id, s -> { s.setOnChainStatus(SettlementStageStatus.FAILED_RETRYABLE); s.setOnChainError("ON_CHAIN_RECONCILIATION_REQUIRED");
            s.setOffRampStatus(SettlementStageStatus.SUCCEEDED); s.setTaxStatus(SettlementStageStatus.FAILED); s.setTaxError("TAX_DOWNSTREAM_CONTRACT_BLOCKED"); });
        var read = service.get(contract.getClientUserId(), contract.getId());
        assertThat(service.get(contract.getFreelancerId(), contract.getId())).isEqualTo(read);
        assertThat(read.moneyStatus()).isEqualTo(SettlementMoneyStatus.SUCCEEDED);
        assertThat(read.onChainStatus()).isEqualTo(SettlementStageStatus.FAILED_RETRYABLE);
        assertThat(read.offRampStatus()).isEqualTo(SettlementStageStatus.SUCCEEDED);
        assertThat(read.taxStatus()).isEqualTo(SettlementStageStatus.FAILED);
        assertThat(json.writeValueAsString(read)).doesNotContain("PRIVATE_BANK", "releaseKey", "fundingTransactionId", "recipientUserId", "accessToken", "payerBank", "privateKey");
    }

    @Test void unrelatedUserCannotReadSettlement() {
        service.prepare(milestone.getId());
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), contract.getId())).isInstanceOf(ApplicationException.class);
    }

    @Test void authorizedReadBeforeWorkerReturnsNull() { assertThat(service.get(contract.getClientUserId(), contract.getId())).isNull(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void manualAndAutomaticApprovalUseSamePersistentReleasePath(boolean automatic) {
        tx.executeWithoutResult(s -> {
            milestones.findById(milestone.getId()).orElseThrow().setStatus(MilestoneStatus.SUBMITTED);
            var work = submissions.findById(submission.getId()).orElseThrow(); work.setStatus(JobSubmissionStatus.SUBMITTED);
            work.setReviewedAt(null); work.setReviewDueAt(Instant.now().minusSeconds(30));
        });
        if (automatic) assertThat(reviews.autoReview(submission.getId(), Instant.now())).isEqualTo(ContractSubmissionService.AutoReviewOutcome.APPROVED);
        else {
            var request = new ReviewSubmissionRequest(); request.setDecision(ReviewSubmissionRequest.Decision.APPROVE);
            reviews.decide(contract.getClientUserId(), contract.getId(), submission.getId(), request);
        }
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.RELEASE_PENDING);
        UUID id = service.prepare(milestone.getId()); service.processMoney(id); assertCompleted(id);
        assertThat(submissions.findById(submission.getId()).orElseThrow().isReviewedAutomatically()).isEqualTo(automatic);
    }

    private ContractSettlement saved(UUID id) { return settlements.findById(id).orElseThrow(); }
    private void edit(UUID id, Consumer<ContractSettlement> change) { tx.executeWithoutResult(s -> change.accept(settlements.findById(id).orElseThrow())); }
    private void due(UUID id) { edit(id, s -> s.setNextAttemptAt(Instant.now().minusSeconds(1))); }
    private void assertCompleted(UUID id) {
        assertThat(saved(id).getMoneyStatus()).isEqualTo(SettlementMoneyStatus.SUCCEEDED);
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.RELEASED);
        assertThat(contracts.findById(contract.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.COMPLETED);
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.COMPLETED);
    }
    private PaymentReleaseResult result(CreateReleaseRequest r, String status, boolean retry) {
        UUID releaseId = UUID.randomUUID();
        return new PaymentReleaseResult(releaseId, r.releaseKey(), r.checkoutOrderId(), r.recipientUserId(),
                status, new BigDecimal(r.expectedAmount().amount()), r.expectedAmount().currency(), true,
                "sim-release-" + releaseId, retry, Instant.now(), Instant.now());
    }
}
