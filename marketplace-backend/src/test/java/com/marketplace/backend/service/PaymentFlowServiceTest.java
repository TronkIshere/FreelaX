package com.marketplace.backend.service;

import com.marketplace.backend.entity.*;
import com.marketplace.backend.repository.*;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentFlowServiceTest {
    private final PaymentFlowRepository flows = mock(PaymentFlowRepository.class);
    private final PaymentFlowStepRepository steps = mock(PaymentFlowStepRepository.class);
    private final PaymentFlowEvidenceRepository evidence = mock(PaymentFlowEvidenceRepository.class);
    private final WorkContractRepository contracts = mock(WorkContractRepository.class);
    private final MilestoneRepository milestones = mock(MilestoneRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final com.marketplace.backend.client.SolanaCprClient solana =
            mock(com.marketplace.backend.client.SolanaCprClient.class);
    private final com.marketplace.backend.configuration.SolanaCprProperties solanaProperties =
            mock(com.marketplace.backend.configuration.SolanaCprProperties.class);
    private final PaymentFlowService service = new PaymentFlowService(flows, steps, evidence,
            contracts, milestones, jobs, solana, solanaProperties);

    private WorkContract contract;
    private Milestone milestone;

    @BeforeEach
    void setup() {
        contract = new WorkContract();
        contract.setId(UUID.randomUUID());
        contract.setJobId(UUID.randomUUID());
        contract.setClientUserId(UUID.randomUUID());
        contract.setFreelancerId(UUID.randomUUID());
        contract.setBudgetUsd(new BigDecimal("100.00"));
        contract.setDeliveryDueAt(Instant.now().plusSeconds(86400));
        contract.setReviewWindowHours(72);
        contract.setMaxRevisions(2);
        contract.setPaymentRail(PaymentFlow.RAIL);
        contract.setStatus(ContractStatus.PENDING_FUNDING);
        milestone = new Milestone();
        milestone.setId(UUID.randomUUID());
        milestone.setContractId(contract.getId());
        Job job = new Job();
        job.setId(contract.getJobId());
        job.setStatus(JobStatus.AWAITING_PAYMENT);
        job.setPaymentNetwork("localnet");
        job.setPaymentMint("accepted-mint");
        when(jobs.findById(contract.getJobId())).thenReturn(Optional.of(job));
        when(flows.saveAndFlush(any(PaymentFlow.class))).thenAnswer(invocation -> {
            PaymentFlow saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void draftKeepsMoneyStepsUnconfirmedAndFeeSeparate() {
        PaymentFlow flow = service.createDraft(contract, milestone);

        assertThat(flow.getGrossUsd()).isEqualByComparingTo("100.00");
        assertThat(flow.getEscrowUsdc()).isEqualByComparingTo("100.000000");
        assertThat(flow.getPlatformFeeUsd()).isEqualByComparingTo("3.00");
        assertThat(flow.getTermsVersion()).isZero();
        assertThat(flow.getMint()).isEqualTo("accepted-mint");
        assertThat(flow.getNetwork()).isEqualTo("localnet");
        assertThat(flow.getQuoteExpiresAt()).isNull();
        verify(steps).saveAll(argThat((Iterable<PaymentFlowStep> rows) -> {
            List<PaymentFlowStep> list = new java.util.ArrayList<>();
            rows.forEach(list::add);
            return list.size() == 11 && list.stream().allMatch(s ->
                    s.getPaymentFlowId().equals(flow.getId()) && "NOT_STARTED".equals(s.getStatus()));
        }));
        verify(evidence).save(argThat(event -> "FLOW_CREATED".equals(event.getKind())
                && flow.getId().equals(event.getPaymentFlowId())));
    }

    @Test
    void timelineIsVisibleOnlyToParticipantAndDoesNotInventConfirmation() {
        PaymentFlow flow = service.createDraft(contract, milestone);
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(milestones.findById(milestone.getId())).thenReturn(Optional.of(milestone));
        when(flows.findByMilestoneId(milestone.getId())).thenReturn(Optional.of(flow));
        Job job = new Job();
        job.setStatus(JobStatus.AWAITING_PAYMENT);
        when(jobs.findById(contract.getJobId())).thenReturn(Optional.of(job));
        when(steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId())).thenReturn(List.of());
        when(evidence.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.timeline(UUID.randomUUID(), contract.getId(),
                milestone.getId(), false)).isInstanceOf(RuntimeException.class);
        PaymentFlowService.Timeline result = service.timeline(contract.getFreelancerId(),
                contract.getId(), milestone.getId(), false);
        assertThat(result.paymentFlowId()).isEqualTo(flow.getId());
        assertThat(result.termsStatus()).isEqualTo("DRAFT");
        assertThat(result.jobStatus()).isEqualTo("AWAITING_PAYMENT");
        assertThat(result.steps()).isEmpty();

        flow.setPayerBankCode("VIETCOMBANK");
        flow.setPayerBankAccountNumber("123456789");
        assertThat(service.timeline(contract.getFreelancerId(), contract.getId(), milestone.getId(), false)
                .payerBankMaskedAccount()).isNull();
        assertThat(service.timeline(contract.getClientUserId(), contract.getId(), milestone.getId(), false)
                .payerBankMaskedAccount()).isEqualTo("••••6789");
        assertThat(service.timeline(UUID.randomUUID(), contract.getId(), milestone.getId(), true)
                .payerBankCode()).isEqualTo("VIETCOMBANK");
    }

    @Test
    void releaseRequiresVerifiedEscrowAndCannotFollowRefund() {
        PaymentFlow flow = service.createDraft(contract, milestone);
        PaymentFlowStep escrow = new PaymentFlowStep();
        escrow.setStatus("PENDING");
        escrow.setReference("escrow-address");
        PaymentFlowStep refund = new PaymentFlowStep();
        refund.setStatus("NOT_STARTED");
        PaymentFlowStep release = new PaymentFlowStep();
        release.setStatus("NOT_STARTED");
        PaymentFlowStep withdrawal = new PaymentFlowStep();
        withdrawal.setStatus("NOT_STARTED");
        PaymentFlowStep accepted = new PaymentFlowStep();
        accepted.setStatus("NOT_STARTED");
        when(flows.findByMilestoneId(milestone.getId())).thenReturn(Optional.of(flow));
        when(steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), "ESCROW"))
                .thenReturn(Optional.of(escrow));
        when(steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), "USDC_REFUND"))
                .thenReturn(Optional.of(refund));
        when(steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), "USDC_RELEASE"))
                .thenReturn(Optional.of(release));
        when(steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), "WITHDRAWAL"))
                .thenReturn(Optional.of(withdrawal));
        when(steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), "WORK_ACCEPTED"))
                .thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> service.confirmChainSettlement(contract, milestone,
                "Released", "escrow-address", "release-tx")).isInstanceOf(RuntimeException.class);
        verify(steps, never()).saveAndFlush(any());

        escrow.setStatus("CONFIRMED");
        refund.setStatus("CONFIRMED");
        assertThatThrownBy(() -> service.confirmChainSettlement(contract, milestone,
                "Released", "escrow-address", "release-tx")).isInstanceOf(RuntimeException.class);
        refund.setStatus("NOT_STARTED");
        service.confirmChainSettlement(contract, milestone, "Released", "escrow-address", "release-tx");
        assertThat(release.getStatus()).isEqualTo("CONFIRMED");
        assertThat(release.getAmount()).isEqualByComparingTo("100.000000");
        assertThat(accepted.getStatus()).isEqualTo("CONFIRMED");
        assertThat(withdrawal.getStatus()).isEqualTo("PENDING");
        verify(evidence).save(argThat(event -> "USDC_RELEASE".equals(event.getKind())
                && "release-tx".equals(event.getReference())));

        service.confirmChainSettlement(contract, milestone, "Released", "escrow-address", "release-tx");
        verify(steps, times(1)).saveAndFlush(release);
        verify(evidence, times(1)).save(argThat(event ->
                "WORK_ACCEPTED".equals(event.getKind())));
    }

    @Test
    void existingReleaseBackfillsWorkAcceptanceOnce() {
        PaymentFlow flow = service.createDraft(contract, milestone);
        PaymentFlowStep work = new PaymentFlowStep();
        work.setPaymentFlowId(flow.getId()); work.setStatus("NOT_STARTED");
        PaymentFlowStep release = new PaymentFlowStep();
        release.setStatus("CONFIRMED"); release.setReference("release-tx");
        when(steps.findReleasedWorkPending(PageRequest.of(0, 50))).thenReturn(List.of(work));
        when(flows.findById(flow.getId())).thenReturn(Optional.of(flow));
        when(steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), "USDC_RELEASE"))
                .thenReturn(Optional.of(release));
        when(steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), "WORK_ACCEPTED"))
                .thenReturn(Optional.of(work));

        service.reconcileAcceptedWork();
        service.reconcileAcceptedWork();

        assertThat(work.getStatus()).isEqualTo("CONFIRMED");
        assertThat(work.getReference()).isEqualTo("release-tx");
        verify(evidence, times(1)).save(argThat(event ->
                "WORK_ACCEPTED".equals(event.getKind())
                    && "release-tx".equals(event.getReference())));
    }

    @Test
    void unifiedDraftRejectsAReviewWindowOutsideTheLockedTerms() {
        contract.setReviewWindowHours(48);

        assertThatThrownBy(() -> service.createDraft(contract, milestone))
                .isInstanceOf(com.marketplace.backend.exception.ApplicationException.class);
        verify(flows, never()).saveAndFlush(any());
    }

    @Test
    void fundingDeadlineComesFromTheUnifiedSnapshotNotContractCreation() {
        PaymentFlow flow = service.createDraft(contract, milestone);
        contract.setCreatedAt(java.time.LocalDateTime.now().minusDays(10));
        when(flows.findByContractId(contract.getId())).thenReturn(Optional.of(flow));

        assertThat(service.fundingDeadline(contract)).isEqualTo(flow.getFundingExpiresAt());
    }

    @Test
    void fundingTimerMayCancelOnlyWhenNoUsdCanBeInFlight() {
        PaymentFlow flow = service.createDraft(contract, milestone);
        PaymentFlowStep order = new PaymentFlowStep();
        order.setKind("USD_ORDER");
        when(flows.findByContractId(contract.getId())).thenReturn(Optional.of(flow));
        when(steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId())).thenReturn(List.of(order));

        for (String status : List.of("NOT_STARTED", "FAILED")) {
            order.setStatus(status);
            assertThat(service.unifiedFundingStarted(contract)).as(status).isFalse();
        }
        for (String status : List.of("PENDING", "PROCESSING", "UNKNOWN", "CONFIRMED")) {
            order.setStatus(status);
            assertThat(service.unifiedFundingStarted(contract)).as(status).isTrue();
        }
        order.setStatus("AWAITING_CLIENT");
        flow.setQuoteExpiresAt(Instant.now().plusSeconds(600));
        assertThat(service.unifiedFundingStarted(contract)).isTrue();
        flow.setQuoteExpiresAt(Instant.now().minusSeconds(1));
        assertThat(service.unifiedFundingStarted(contract)).isFalse();

        contract.setPaymentRail("SOLANA_ESCROW");
        assertThat(service.unifiedFundingStarted(contract)).isFalse();
    }


    @Test
    void draftRequiresTheChainTermsPublishedWithTheJob() {
        Job unpublished = new Job();
        unpublished.setId(contract.getJobId());
        when(jobs.findById(contract.getJobId())).thenReturn(Optional.of(unpublished));

        assertThatThrownBy(() -> service.createDraft(contract, milestone))
                .isInstanceOf(com.marketplace.backend.exception.ApplicationException.class);
    }

    @Test
    void chainTermsRequireAnUnpausedConfiguredMint() {
        var config = new com.marketplace.backend.dto.response.solana.SolanaConfigResult();
        config.setAcceptedMint("accepted-mint");
        when(solana.getConfig()).thenReturn(config);
        when(solanaProperties.getNetwork()).thenReturn("localnet");
        assertThat(service.currentChainTerms()).isEqualTo(new PaymentFlowService.ChainTerms("localnet", "accepted-mint"));

        config.setPaused(true);
        assertThatThrownBy(service::currentChainTerms)
                .isInstanceOf(com.marketplace.backend.exception.ApplicationException.class);
    }

}
