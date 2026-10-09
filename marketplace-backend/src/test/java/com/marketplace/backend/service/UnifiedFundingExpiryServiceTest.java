package com.marketplace.backend.service;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UnifiedFundingExpiryServiceTest {
    private final WorkContractRepository contracts = mock(WorkContractRepository.class);
    private final MilestoneRepository milestones = mock(MilestoneRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final PaymentFlowRepository flows = mock(PaymentFlowRepository.class);
    private final PaymentFlowStepRepository steps = mock(PaymentFlowStepRepository.class);
    private final PaymentFlowEvidenceRepository evidence = mock(PaymentFlowEvidenceRepository.class);
    private final PaymentFlowService paymentFlows = mock(PaymentFlowService.class);
    private final SolanaCprClient solana = mock(SolanaCprClient.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final UnifiedFundingExpiryService service = new UnifiedFundingExpiryService(contracts,
            milestones, jobs, flows, steps, evidence, paymentFlows, solana, notifications);

    private final UUID admin = UUID.randomUUID();
    private WorkContract contract;
    private Milestone milestone;
    private Job job;
    private Map<String, PaymentFlowStep> rows;

    @BeforeEach
    void setup() {
        contract = new WorkContract();
        contract.setId(UUID.randomUUID());
        contract.setJobId(UUID.randomUUID());
        contract.setClientUserId(UUID.randomUUID());
        contract.setFreelancerId(UUID.randomUUID());
        contract.setPaymentRail(PaymentFlow.RAIL);
        contract.setStatus(ContractStatus.PENDING_FUNDING);
        milestone = new Milestone();
        milestone.setId(UUID.randomUUID());
        milestone.setContractId(contract.getId());
        milestone.setStatus(MilestoneStatus.PENDING_FUNDING);
        job = new Job();
        job.setId(contract.getJobId());
        job.setStatus(JobStatus.AWAITING_PAYMENT);
        PaymentFlow flow = new PaymentFlow();
        flow.setId(UUID.randomUUID());
        flow.setJobId(job.getId());
        flow.setContractId(contract.getId());
        flow.setMilestoneId(milestone.getId());
        flow.setEscrowUsdc(new BigDecimal("100.000000"));
        rows = Map.of("CLIENT_USDC", row("CLIENT_USDC", "CONFIRMED"), "ESCROW", row("ESCROW", "NOT_STARTED"),
                "USDC_RELEASE", row("USDC_RELEASE", "NOT_STARTED"), "USDC_REFUND", row("USDC_REFUND", "NOT_STARTED"),
                "WITHDRAWAL", row("WITHDRAWAL", "NOT_STARTED"), "USD_REFUND", row("USD_REFUND", "NOT_STARTED"));
        rows.get("CLIENT_USDC").setReference("onramp-receipt");
        when(milestones.findWithLockByContractId(contract.getId())).thenReturn(Optional.of(milestone));
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(jobs.findWithLockById(job.getId())).thenReturn(Optional.of(job));
        when(flows.findWithLockByMilestoneId(milestone.getId())).thenReturn(Optional.of(flow));
        for (var entry : rows.entrySet())
            when(steps.findWithLockByPaymentFlowIdAndKind(flow.getId(), entry.getKey()))
                    .thenReturn(Optional.of(entry.getValue()));
        when(paymentFlows.fundingDeadline(contract)).thenReturn(Instant.now().minusSeconds(3600));
        when(solana.findEscrow(milestone.getId().toString())).thenReturn(Optional.empty());
    }

    @Test
    void cancelsExpiredUnfundedContractAndOpensTheUsdRefundExit() {
        service.cancelExpired(admin, contract.getId(), "Quá hạn 48h, đã kiểm tra receipt on-ramp");

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.CANCELLED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        PaymentFlowStep refund = rows.get("USDC_REFUND");
        assertThat(UnifiedFundingExpiryService.unfunded(refund)).isTrue();
        assertThat(refund.getReference()).isEqualTo("onramp-receipt");
        assertThat(rows.get("WITHDRAWAL").getStatus()).isEqualTo("PENDING");
        assertThat(rows.get("USD_REFUND").getStatus()).isEqualTo("PENDING");
        verify(evidence).save(argThat(e -> "FUNDING_EXPIRED_CANCEL".equals(e.getKind())
                && admin.equals(e.getActorId())));
    }

    @Test
    void refusesBeforeTheDeadlineHasSettled() {
        when(paymentFlows.fundingDeadline(contract)).thenReturn(Instant.now().minusSeconds(60));
        assertRefusedWithoutChanges();
    }

    @Test
    void refusesWhenUsdcHasNotReachedTheClientWallet() {
        rows.get("CLIENT_USDC").setStatus("UNKNOWN");
        assertRefusedWithoutChanges();
    }

    @Test
    void refusesWhenAnEscrowAccountExistsOnChain() {
        when(solana.findEscrow(milestone.getId().toString()))
                .thenReturn(Optional.of(mock(SolanaEscrowResult.class)));
        assertRefusedWithoutChanges();
    }

    @Test
    void refusesLegacyRailsAndShortNotes() {
        assertThatThrownBy(() -> service.cancelExpired(admin, contract.getId(), "short"))
                .isInstanceOf(ApplicationException.class);
        contract.setPaymentRail("SOLANA_ESCROW");
        assertRefusedWithoutChanges();
    }

    private void assertRefusedWithoutChanges() {
        assertThatThrownBy(() -> service.cancelExpired(admin, contract.getId(), "Quá hạn, kiểm tra thủ công"))
                .isInstanceOf(ApplicationException.class);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.PENDING_FUNDING);
        assertThat(rows.get("USDC_REFUND").getStatus()).isEqualTo("NOT_STARTED");
        verify(evidence, never()).save(any());
    }

    private PaymentFlowStep row(String kind, String status) {
        PaymentFlowStep step = new PaymentFlowStep(); step.setKind(kind); step.setStatus(status); return step;
    }
}
