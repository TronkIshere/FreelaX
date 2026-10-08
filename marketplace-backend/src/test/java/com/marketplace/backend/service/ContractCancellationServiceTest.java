package com.marketplace.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.bofa.*;
import com.marketplace.backend.dto.request.cancellation.*;
import com.marketplace.backend.dto.request.funding.FundMilestoneRequest;
import com.marketplace.backend.dto.request.submission.CreateContractSubmissionRequest;
import com.marketplace.backend.dto.request.job.AssignFreelancerRequest;
import com.marketplace.backend.dto.response.bofa.*;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
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
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
        "spring.jpa.show-sql=false"}, showSql = false)
@ContextConfiguration(classes = ContractCancellationServiceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContractCancellationServiceTest {
    @Configuration @EntityScan(basePackageClasses = Job.class)
    @EnableJpaRepositories(basePackageClasses = JobRepository.class)
    @Import({ContractCancellationService.class, SettlementService.class, FundingService.class, ContractSubmissionService.class,
            com.marketplace.backend.service.impl.JobServiceImpl.class})
    static class Config {
        @Bean TransactionTemplate transactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }
    @Autowired ContractCancellationService service;
    @Autowired SettlementService release;
    @Autowired FundingService fundingService;
    @Autowired ContractSubmissionService submissionService;
    @Autowired DeliverableRequirementRepository requirements;
    @Autowired SubmissionEvidenceRepository evidence;
    @Autowired AcceptanceCriterionRepository criteria;
    @Autowired JobApplicationRepository applications;
    @Autowired com.marketplace.backend.service.impl.JobServiceImpl legacyJobs;
    @Autowired ContractCancellationRepository cancellations;
    @Autowired EscrowContractRepository escrowContracts;
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
    @MockitoBean com.marketplace.backend.client.MisaBackendClient misa;
    @MockitoBean com.marketplace.backend.client.SolanaCprClient solana;
    @MockitoBean PayoutService payout;
    @MockitoBean UserRepository users;
    Job job; WorkContract contract; Milestone milestone; FundingTransaction paid;
    Map<String, PaymentRefundResult> ledger; AtomicInteger credits;
    final CreateCancellationRequest intent = new CreateCancellationRequest("MUTUAL", "Hai bên muốn dừng công việc");
    final CancellationDecisionRequest accept = new CancellationDecisionRequest(CancellationDecisionRequest.Decision.ACCEPT);
    final CancellationDecisionRequest reject = new CancellationDecisionRequest(CancellationDecisionRequest.Decision.REJECT);

    @BeforeEach void setup() {
        cancellations.deleteAll(); disputes.deleteAll(); settlements.deleteAll(); evidence.deleteAll(); requirements.deleteAll(); criteria.deleteAll();
        applications.deleteAll(); submissions.deleteAll();
        funding.deleteAll(); milestones.deleteAll(); contracts.deleteAll(); jobs.deleteAll();
        tx.executeWithoutResult(s -> {
            job = new Job(); job.setTitle("Cancellation test"); job.setBudgetUsd(new BigDecimal("500.00"));
            job.setClientUserId(UUID.randomUUID()); job.setFreelancerId(UUID.randomUUID()); job.setCheckoutOrderId(UUID.randomUUID());
            job.setStatus(JobStatus.IN_PROGRESS); jobs.saveAndFlush(job);
            contract = new WorkContract(); contract.setJobId(job.getId()); contract.setClientUserId(job.getClientUserId());
            contract.setFreelancerId(job.getFreelancerId()); contract.setTitleSnapshot(job.getTitle());
            contract.setBudgetUsd(job.getBudgetUsd()); contract.setStatus(ContractStatus.ACTIVE);
            contract.setReviewWindowHours(72); contract.setMaxRevisions(2); contracts.saveAndFlush(contract);
            milestone = new Milestone(); milestone.setContractId(contract.getId()); milestone.setAmount(job.getBudgetUsd());
            milestone.setCurrency("USD"); milestone.setStatus(MilestoneStatus.FUNDED); milestones.saveAndFlush(milestone);
            paid = new FundingTransaction(); paid.setContractId(contract.getId()); paid.setMilestoneId(milestone.getId());
            paid.setClientUserId(client()); paid.setAmount(job.getBudgetUsd()); paid.setCurrency("USD");
            paid.setStatus(FundingStatus.SUCCEEDED); paid.setCheckoutOrderId(job.getCheckoutOrderId());
            paid.setIdempotencyKey("test-funding"); paid.setPayloadHash("test"); paid.setPaymentMethodId("BANK_ACCOUNT_ON_FILE");
            paid.setPayerBankCode("TEST"); paid.setPayerBankAccountNumber("PRIVATE_BANK"); paid.setPayerBankAccountHolderName("Private");
            funding.saveAndFlush(paid);
        });
        ledger = new ConcurrentHashMap<>(); credits = new AtomicInteger();
        when(payment.findRefund(anyString())).thenAnswer(a -> ledger.get(a.getArgument(0)));
        when(payment.createRefund(any())).thenAnswer(a -> {
            CreateRefundRequest r = a.getArgument(0);
            return ledger.computeIfAbsent(r.refundKey(), key -> { credits.incrementAndGet(); return result(r); });
        });
    }
    @Test void preFundingClientCancelsWithoutMoneyAndFutureFundingIsBlocked() {
        preFunding();
        var read = service.request(client(), contract.getId(), intent);
        assertThat(read.cancellationStatus()).isEqualTo("CANCELLED"); assertThat(read.refundStatus()).isNull();
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.CANCELLED);
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.CANCELLED);
        var r = new FundMilestoneRequest(); r.setPaymentMethodId("BANK_ACCOUNT_ON_FILE");
        var amount = new FundMilestoneRequest.ExpectedAmount(); amount.setAmount(new BigDecimal("500")); amount.setCurrency("USD"); r.setExpectedAmount(amount);
        assertThatThrownBy(() -> fundingService.fund(client(), contract.getId(), milestone.getId(), "fund-late", r)).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(payment);
    }
    @Test void freelancerCannotCancelBeforeFunding() {
        preFunding(); assertThatThrownBy(() -> service.request(worker(), contract.getId(), intent)).isInstanceOf(ApplicationException.class);
        assertThat(cancellations.count()).isZero(); verifyNoInteractions(payment);
    }
    @Test void anyPriorFundingAttemptBlocksUncapturedAssumption() {
        tx.executeWithoutResult(s -> {
            contracts.findById(contract.getId()).orElseThrow().setStatus(ContractStatus.PENDING_FUNDING);
            milestones.findById(milestone.getId()).orElseThrow().setStatus(MilestoneStatus.PENDING_FUNDING);
            jobs.findById(job.getId()).orElseThrow().setStatus(JobStatus.AWAITING_PAYMENT);
            funding.findById(paid.getId()).orElseThrow().setStatus(FundingStatus.FAILED);
        });
        assertThatThrownBy(() -> service.request(client(), contract.getId(), intent)).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(payment);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void eitherParticipantMayProposeButOnlyCounterpartCanConsent(boolean freelancerRequests) {
        UUID requester = freelancerRequests ? worker() : client(), counterpart = freelancerRequests ? client() : worker();
        var r = service.request(requester, contract.getId(), intent);
        assertThat(r.cancellationStatus()).isEqualTo("REQUESTED"); verifyNoInteractions(payment);
        assertThatThrownBy(() -> service.decide(requester, contract.getId(), r.cancellationId(), accept)).isInstanceOf(ApplicationException.class);
        service.decide(counterpart, contract.getId(), r.cancellationId(), accept); assertCancelled(); assertThat(credits.get()).isEqualTo(1);
    }
    @Test void requestDuplicatesReuseProposalAndChangedIntentConflicts() {
        var r = propose(); assertThat(propose().cancellationId()).isEqualTo(r.cancellationId());
        assertThat(cancellations.count()).isEqualTo(1);
        assertThatThrownBy(() -> service.request(client(), contract.getId(), new CreateCancellationRequest("OTHER", "different")))
                .isInstanceOf(ApplicationException.class); verifyNoInteractions(payment);
    }
    @Test void refusalPreservesWorkAndDoesNotRefundOrOpenDispute() {
        var r = propose(); service.decide(worker(), contract.getId(), r.cancellationId(), reject);
        assertThat(service.decide(worker(), contract.getId(), r.cancellationId(), reject).cancellationStatus()).isEqualTo("REJECTED");
        assertThat(contracts.findById(contract.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(disputes.count()).isZero(); verifyNoInteractions(payment);
    }
    @Test void consentCommitsStableIdentityAndSendsOnlyAuthoritativeSnapshot() {
        var r = propose(); consent(r.cancellationId());
        var saved = saved(); assertThat(saved.getRefundKey()).isEqualTo("marketplace-refund-" + milestone.getId());
        verify(payment).createRefund(new CreateRefundRequest(paid.getCheckoutOrderId(),
                new CreateRefundRequest.ExpectedAmount("500.00", "USD"), saved.getRefundKey()));
        verify(payment).findRefund(saved.getRefundKey()); verifyNoMoreInteractions(payment); assertCancelled();
    }
    @Test void duplicateConsentAndWorkerNeverRefundAgain() {
        var r = propose(); consent(r.cancellationId()); consent(r.cancellationId()); service.process(r.cancellationId());
        assertThat(credits.get()).isEqualTo(1); verify(payment, times(1)).createRefund(any());
        verify(notifications, times(1)).notify(eq(client()), eq(NotificationType.REFUND_CONFIRMED), anyString(), contains("ledger mô phỏng"), eq(job.getId()));
    }
    @Test void remoteSuccessTimeoutLeavesUnknownThenLookupRecoversWithoutSecondCredit() {
        doAnswer(a -> {
            CreateRefundRequest r = a.getArgument(0); ledger.put(r.refundKey(), result(r)); credits.incrementAndGet();
            throw new ResourceAccessException("timeout PRIVATE_BANK token");
        }).when(payment).createRefund(any());
        var r = propose(); consent(r.cancellationId());
        assertThat(saved().getRefundStatus()).isEqualTo(SettlementMoneyStatus.UNKNOWN);
        assertThat(saved().getLastError()).isEqualTo("REFUND_CREATE_UNRESOLVED");
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.REFUND_PENDING);
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        String key = saved().getRefundKey(); service.process(r.cancellationId()); assertCancelled();
        verify(payment, times(1)).createRefund(any()); verify(payment, times(2)).findRefund(key); assertThat(credits.get()).isEqualTo(1);
    }
    @Test void definitiveAbsenceAfterTimeoutRetriesOriginalIdentity() {
        doThrow(new ResourceAccessException("timeout")).when(payment).createRefund(any());
        var r = propose(); consent(r.cancellationId()); String key = saved().getRefundKey();
        doAnswer(a -> { CreateRefundRequest body = a.getArgument(0); credits.incrementAndGet(); return result(body); }).when(payment).createRefund(any());
        service.process(r.cancellationId()); assertCancelled();
        var capture = org.mockito.ArgumentCaptor.forClass(CreateRefundRequest.class);
        verify(payment, times(2)).createRefund(capture.capture());
        assertThat(capture.getAllValues()).extracting(CreateRefundRequest::refundKey).containsOnly(key);
    }
    @Test void unavailableLookupCannotTriggerRefund() {
        doThrow(new ResourceAccessException("lookup unavailable")).when(payment).findRefund(anyString());
        var r = propose(); consent(r.cancellationId()); service.process(r.cancellationId());
        assertThat(saved().getRefundStatus()).isEqualTo(SettlementMoneyStatus.UNKNOWN);
        verify(payment, never()).createRefund(any());
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","PROCESSING","UNKNOWN","FAILED","FAILED_RETRYABLE"})
    void existingUnresolvedRemoteRecordNeverTriggersSecondRefund(String remoteState) {
        doThrow(new ResourceAccessException("timeout")).when(payment).createRefund(any());
        var proposal = propose(); consent(proposal.cancellationId()); String key = saved().getRefundKey();
        var row = result(new CreateRefundRequest(paid.getCheckoutOrderId(), new CreateRefundRequest.ExpectedAmount("500.00","USD"),key));
        boolean retry = remoteState.equals("FAILED_RETRYABLE");
        ledger.put(key, new PaymentRefundResult(row.refundId(),key,row.checkoutOrderId(),client(),
                retry ? "FAILED" : remoteState,row.amount(),row.currency(),true,row.refundReference(),retry,row.createdAt(),row.updatedAt()));
        service.process(proposal.cancellationId());
        assertThat(saved().getRefundStatus()).isEqualTo(remoteState.equals("FAILED") ? SettlementMoneyStatus.FAILED
                : retry ? SettlementMoneyStatus.FAILED_RETRYABLE : SettlementMoneyStatus.UNKNOWN);
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        verify(payment,times(1)).createRefund(any());
    }
    @ParameterizedTest @ValueSource(ints={400,408,429,500})
    void createHttpErrorsSeparateDefinitiveFailureFromAmbiguity(int status) {
        doThrow(HttpClientErrorException.create(org.springframework.http.HttpStatusCode.valueOf(status),"provider",
                org.springframework.http.HttpHeaders.EMPTY,new byte[0],null)).when(payment).createRefund(any());
        var proposal = propose(); consent(proposal.cancellationId());
        assertThat(saved().getRefundStatus()).isEqualTo(status==408 || status>=500 ? SettlementMoneyStatus.UNKNOWN
                : status==429 ? SettlementMoneyStatus.FAILED_RETRYABLE : SettlementMoneyStatus.FAILED);
        assertThat(saved().isRetryable()).isEqualTo(status!=400);
        assertThat(saved().getStatus()).isEqualTo(CancellationStatus.REFUND_PENDING);
    }
    @Test void remoteSuccessWithLostLocalCommitRecoversAfterRestart() {
        AtomicInteger commits = new AtomicInteger();
        doAnswer(a -> {
            if (commits.getAndIncrement() == 0) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("Lost commit"); }
            });
            return null;
        }).when(notifications).notify(any(), eq(NotificationType.REFUND_CONFIRMED), anyString(), anyString(), any());
        var r = propose(); assertThatThrownBy(() -> consent(r.cancellationId())).isInstanceOf(IllegalStateException.class);
        String key = saved().getRefundKey(); assertThat(saved().getRefundStatus()).isEqualTo(SettlementMoneyStatus.PENDING);
        var restarted = new ContractCancellationService(cancellations, escrowContracts, contracts, milestones, funding, settlements, disputes,
                submissions, jobs, payment, notifications, tx);
        restarted.process(r.cancellationId()); assertCancelled(); assertThat(saved().getRefundKey()).isEqualTo(key);
        assertThat(credits.get()).isEqualTo(1); verify(payment, times(1)).createRefund(any());
    }
    @ParameterizedTest @ValueSource(strings={"UNDER_REVIEW","REVISION","DISPUTED","COMPLETED","CANCELLED"})
    void nonEligibleContractStateCannotRefund(String status) {
        tx.executeWithoutResult(s -> contracts.findById(contract.getId()).orElseThrow().setStatus(ContractStatus.valueOf(status)));
        assertThatThrownBy(this::propose).isInstanceOf(ApplicationException.class); verifyNoInteractions(payment);
    }
    @ParameterizedTest @ValueSource(strings={"SUBMITTED","RELEASE_PENDING","RELEASED","DISPUTED","REFUND_PENDING","REFUNDED"})
    void nonEligibleMilestoneCannotRefund(String status) {
        tx.executeWithoutResult(s -> milestones.findById(milestone.getId()).orElseThrow().setStatus(MilestoneStatus.valueOf(status)));
        assertThatThrownBy(this::propose).isInstanceOf(ApplicationException.class); verifyNoInteractions(payment);
    }
    @ParameterizedTest @ValueSource(strings={"OPEN","UNDER_REVIEW"})
    void activeDisputeBlocksOrdinaryCancellation(String state) {
        tx.executeWithoutResult(s -> { var d = new ContractDispute(); d.setContractId(contract.getId()); d.setSubmissionId(UUID.randomUUID());
            d.setOpenedBy(client()); d.setReasonCode("TEST"); d.setDescription("Dispute"); d.setStatus(DisputeStatus.valueOf(state)); disputes.save(d); });
        assertThatThrownBy(this::propose).isInstanceOf(ApplicationException.class); verifyNoInteractions(payment);
    }
    @ParameterizedTest @ValueSource(strings={"PROCESSING","UNKNOWN","SUCCEEDED"})
    void existingPrimaryMoneyBlocksRefund(String state) {
        approved();
        UUID id = release.prepare(milestone.getId());
        tx.executeWithoutResult(s -> { settlements.findById(id).orElseThrow().setMoneyStatus(SettlementMoneyStatus.valueOf(state));
            contracts.findById(contract.getId()).orElseThrow().setStatus(ContractStatus.ACTIVE);
            milestones.findById(milestone.getId()).orElseThrow().setStatus(MilestoneStatus.FUNDED);
            jobs.findById(job.getId()).orElseThrow().setStatus(JobStatus.IN_PROGRESS); submissions.deleteAll(); });
        assertThatThrownBy(this::propose).isInstanceOf(ApplicationException.class); verifyNoInteractions(payment);
    }
    @Test void priorSubmissionEvenStaleActiveStateBlocksConsent() {
        var r = propose(); tx.executeWithoutResult(s -> addSubmission());
        assertThatThrownBy(() -> consent(r.cancellationId())).isInstanceOf(ApplicationException.class); verifyNoInteractions(payment);
    }
    @Test void approvalWinnerRejectsStaleCancellationAndOnlyReleases() {
        var r = propose(); approved(); UUID id = release.prepare(milestone.getId());
        when(payment.createRelease(any())).thenAnswer(a -> {
            CreateReleaseRequest b = a.getArgument(0); UUID rid = UUID.randomUUID();
            return new PaymentReleaseResult(rid, b.releaseKey(), b.checkoutOrderId(), b.recipientUserId(), "SUCCEEDED",
                    new BigDecimal(b.expectedAmount().amount()), "USD", true, "sim-release-" + rid, false, Instant.now(), Instant.now());
        });
        release.processMoney(id);
        assertThatThrownBy(() -> consent(r.cancellationId())).isInstanceOf(ApplicationException.class);
        assertThat(settlements.findById(id).orElseThrow().getMoneyStatus()).isEqualTo(SettlementMoneyStatus.SUCCEEDED);
        verify(payment, never()).createRefund(any());
    }
    @Test void cancellationWinnerBlocksSettlementAndConcurrentConsentCreditsOnce() throws Exception {
        var r = propose(); var pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Void> call = () -> { start.await(); consent(r.cancellationId()); return null; };
            var one = pool.submit(call); var two = pool.submit(call); start.countDown();
            one.get(15, TimeUnit.SECONDS); two.get(15, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertCancelled(); assertThat(credits.get()).isEqualTo(1);
        assertThatThrownBy(() -> release.prepare(milestone.getId())).isInstanceOf(ApplicationException.class);
        verify(payment, never()).createRelease(any());
    }
    @Test void participantReadsAreSafeAndOutsidersCannotReadOrMutate() throws Exception {
        var r = propose(); var read = service.get(worker(), contract.getId());
        assertThat(read.allowedActions()).containsExactly("ACCEPT","REJECT"); assertThat(service.get(client(), contract.getId()).allowedActions()).isEmpty();
        assertThat(json.writeValueAsString(read)).doesNotContain("PRIVATE_BANK","refundKey","checkoutOrderId","intentHash","payloadHash","payerBank","token");
        UUID outsider = UUID.randomUUID();
        assertThatThrownBy(() -> service.get(outsider, contract.getId())).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> service.request(outsider, contract.getId(), intent)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> service.decide(outsider, contract.getId(), r.cancellationId(), accept)).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(payment);
    }
    @Test void concurrentSubmissionAndCancellationCannotBothClaimWorkflow() throws Exception {
        UUID[] requirement = {null};
        tx.executeWithoutResult(s -> {
            var item = new DeliverableRequirement(); item.setContractId(contract.getId()); item.setJobId(job.getId());
            item.setTitle("Deliverable"); item.setDescription("Evidence"); item.setRequired(true);
            requirement[0] = requirements.saveAndFlush(item).getId();
        });
        var body = new CreateContractSubmissionRequest(); body.setSummary("Submitted document");
        var item = new CreateContractSubmissionRequest.DeliverableInput(); item.setRequirementId(requirement[0]); item.setUrl("https://example.test/work");
        body.setDeliverables(List.of(item));
        var r = propose(); var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var cancel = pool.submit(() -> { start.await(); try { consent(r.cancellationId()); return true; }
                catch (ApplicationException | org.springframework.dao.ConcurrencyFailureException ex) { return false; } });
            var submit = pool.submit(() -> { start.await(); try { submissionService.submit(worker(), contract.getId(), "submit-race", body); return true; }
                catch (ApplicationException | org.springframework.dao.ConcurrencyFailureException ex) { return false; } });
            start.countDown();
            boolean cancelled = cancel.get(15, TimeUnit.SECONDS), submitted = submit.get(15, TimeUnit.SECONDS);
            assertThat(cancelled ^ submitted).isTrue();
            if (cancelled) { assertCancelled(); assertThat(submissions.count()).isZero(); assertThat(credits.get()).isEqualTo(1); }
            else {
                assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.SUBMITTED);
                assertThat(saved().getStatus()).isEqualTo(CancellationStatus.REQUESTED); assertThat(credits.get()).isZero();
            }
        } finally { pool.shutdownNow(); }
        verify(payment, never()).createRelease(any());
    }
    @Test void pendingCancellationDoesNotPreventWorkUntilConsentClaimsMilestone() {
        var r = propose(); assertThat(r.cancellationStatus()).isEqualTo("REQUESTED");
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.FUNDED);
    }
    @Test void legacyOpenCancellationAndAssignmentCannotOverwriteEachOther() throws Exception {
        User c = new User(); c.setId(client()); c.setUserType(UserType.CLIENT);
        User f = new User(); f.setId(worker()); f.setUserType(UserType.FREELANCER);
        when(users.findById(c.getId())).thenReturn(Optional.of(c)); when(users.findById(f.getId())).thenReturn(Optional.of(f));
        tx.executeWithoutResult(s -> {
            funding.deleteAll(); milestones.deleteAll(); contracts.deleteAll();
            var open = jobs.findById(job.getId()).orElseThrow(); open.setStatus(JobStatus.OPEN); open.setFreelancerId(null); open.setCheckoutOrderId(null);
            var d = new DeliverableRequirement(); d.setJobId(job.getId()); d.setTitle("Deliverable"); d.setDescription("Evidence"); requirements.save(d);
            var a = new AcceptanceCriterion(); a.setJobId(job.getId()); a.setDescription("Acceptance"); criteria.save(a);
            var app = new JobApplication(); app.setJobId(job.getId()); app.setFreelancerId(f.getId()); app.setStatus(JobApplicationStatus.PENDING); applications.save(app);
        });
        var assign = new AssignFreelancerRequest(); assign.setFreelancerId(f.getId());
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var cancel = pool.submit(() -> { start.await(); try { legacyJobs.cancel(c.getId(),job.getId()); return true; }
                catch (ApplicationException ex) { return false; } });
            var assignment = pool.submit(() -> { start.await(); try { legacyJobs.assignFreelancer(c.getId(),job.getId(),assign); return true; }
                catch (ApplicationException ex) { return false; } });
            start.countDown();
            boolean cancelled = cancel.get(15,TimeUnit.SECONDS), assigned = assignment.get(15,TimeUnit.SECONDS);
            assertThat(cancelled ^ assigned).isTrue();
            assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(cancelled ? JobStatus.CANCELLED : JobStatus.AWAITING_PAYMENT);
            assertThat(contracts.count()).isEqualTo(assigned ? 1 : 0);
            assertThat(milestones.count()).isEqualTo(assigned ? 1 : 0);
        } finally { pool.shutdownNow(); }
        verifyNoInteractions(payment);
    }
    @Test void changedFundingSnapshotCannotBeRefunded() {
        var r = propose();
        tx.executeWithoutResult(s -> funding.findById(paid.getId()).orElseThrow().setAmount(new BigDecimal("499.00")));
        assertThatThrownBy(() -> consent(r.cancellationId())).isInstanceOf(ApplicationException.class); verifyNoInteractions(payment);
    }
    @Test void malformedProviderIdentityRemainsUnknown() {
        doAnswer(a -> {
            var r = result(a.getArgument(0)); return new PaymentRefundResult(r.refundId(), r.refundKey(), r.checkoutOrderId(),
                    UUID.randomUUID(), r.status(), r.amount(), r.currency(), r.simulation(), r.refundReference(), false, r.createdAt(), r.updatedAt());
        }).when(payment).createRefund(any());
        var r = propose(); consent(r.cancellationId()); assertThat(saved().getRefundStatus()).isEqualTo(SettlementMoneyStatus.UNKNOWN);
        assertThat(contracts.findById(contract.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.ACTIVE);
    }
    private UUID client() { return contract.getClientUserId(); }
    private UUID worker() { return contract.getFreelancerId(); }
    private com.marketplace.backend.dto.response.cancellation.CancellationResponse propose() { return service.request(client(), contract.getId(), intent); }
    private void consent(UUID id) { service.decide(worker(), contract.getId(), id, accept); }
    private ContractCancellation saved() { return cancellations.findByContractId(contract.getId()).orElseThrow(); }
    private void preFunding() { tx.executeWithoutResult(s -> {
        funding.deleteAll(); contracts.findById(contract.getId()).orElseThrow().setStatus(ContractStatus.PENDING_FUNDING);
        milestones.findById(milestone.getId()).orElseThrow().setStatus(MilestoneStatus.PENDING_FUNDING);
        jobs.findById(job.getId()).orElseThrow().setStatus(JobStatus.AWAITING_PAYMENT);
    }); }
    private JobSubmission addSubmission() {
        var v = new JobSubmission(); v.setContractId(contract.getId()); v.setMilestoneId(milestone.getId()); v.setJobId(job.getId());
        v.setFreelancerId(worker()); v.setVersion(1); v.setSummary("Submitted work"); v.setStatus(JobSubmissionStatus.APPROVED);
        v.setReviewedAt(LocalDateTime.now()); return submissions.saveAndFlush(v);
    }
    private void approved() { tx.executeWithoutResult(s -> {
        addSubmission(); contracts.findById(contract.getId()).orElseThrow().setStatus(ContractStatus.UNDER_REVIEW);
        milestones.findById(milestone.getId()).orElseThrow().setStatus(MilestoneStatus.RELEASE_PENDING);
        jobs.findById(job.getId()).orElseThrow().setStatus(JobStatus.SUBMITTED_FOR_REVIEW);
    }); }
    private PaymentRefundResult result(CreateRefundRequest r) {
        UUID id = UUID.randomUUID(); return new PaymentRefundResult(id, r.refundKey(), r.checkoutOrderId(), client(), "SUCCEEDED",
                new BigDecimal(r.expectedAmount().amount()), r.expectedAmount().currency(), true, "sim-refund-" + id, false, Instant.now(), Instant.now());
    }
    private void assertCancelled() {
        assertThat(saved().getStatus()).isEqualTo(CancellationStatus.CANCELLED);
        assertThat(saved().getRefundStatus()).isEqualTo(SettlementMoneyStatus.SUCCEEDED);
        assertThat(milestones.findById(milestone.getId()).orElseThrow().getStatus()).isEqualTo(MilestoneStatus.REFUNDED);
        assertThat(contracts.findById(contract.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.CANCELLED);
    }
}
