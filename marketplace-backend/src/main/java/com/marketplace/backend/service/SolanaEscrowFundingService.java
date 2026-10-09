package com.marketplace.backend.service;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.dto.response.solana.SolanaBuildResult;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import com.marketplace.backend.dto.response.solana.MockOnrampReceiptResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SolanaEscrowFundingService {
    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final JobRepository jobs;
    private final WalletRepository wallets;
    private final FundingTransactionRepository simulatedFunding;
    private final EscrowContractRepository escrows;
    private final SolanaCprClient solana;
    private final NotificationService notifications;
    private final PaymentFlowRepository paymentFlows;
    private final UnifiedUsdFundingService unifiedFunding;
    private final PaymentFlowService paymentFlowService;
    private final UnifiedReconciliationService reconciliation;

    public record EscrowView(String paymentRail, String status, String settlementStatus,
            String escrowAddress,
            String clientWallet, String freelancerWallet, String mint, String amountBaseUnits,
            String fundingExpiresAt, String deliveryDueAt, String reviewDueAt, String submissionHash,
            Integer submissionCount, String disputeHash,
            String fundSignature, String releaseSignature, String refundSignature,
            String resolutionSignature,
            String vaultAddress, String vaultBalanceBaseUnits, String vaultBalanceStatus,
            String requestedDeliveryDueAt, boolean extensionUsed) { }

    public record EscrowBuildView(String buildSessionId, String transactionBase64,
            String escrowAddress, String clientWallet, String freelancerWallet,
            String amountBaseUnits, String mint) { }

    @Transactional
    public EscrowBuildView prepare(UUID clientId, UUID contractId, UUID milestoneId,
            String connectedWallet) {
        Milestone milestone = milestones.findWithLockById(milestoneId)
                .filter(m -> m.getContractId().equals(contractId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        WorkContract contract = contracts.findById(contractId)
                .filter(c -> c.getClientUserId().equals(clientId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        Job job = jobs.findById(contract.getJobId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        boolean unified = PaymentFlow.RAIL.equals(contract.getPaymentRail());
        if (milestone.getStatus() != MilestoneStatus.PENDING_FUNDING
                || contract.getStatus() != ContractStatus.PENDING_FUNDING
                || job.getStatus() != JobStatus.AWAITING_PAYMENT
                || (contract.getCreatedAt() != null
                    && !Instant.now().isBefore(paymentFlowService.fundingDeadline(contract)))
                || simulatedFunding.existsByMilestoneIdAndStatusIn(milestoneId,
                    EnumSet.allOf(FundingStatus.class))) {
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        }
        EscrowContract record = escrows.findByContractId(contractId).orElse(null);
        if (record != null && record.getFundSignature() != null) {
            throw new ApplicationException(ErrorCode.FUNDING_IN_PROGRESS);
        }
        String clientWallet = wallet(contract.getClientUserId());
        if (!clientWallet.equals(connectedWallet)) {
            throw new ApplicationException(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID);
        }
        String freelancerWallet = wallet(contract.getFreelancerId());
        if (record != null && (!record.getClientWallet().equals(clientWallet)
                || !record.getFreelancerWallet().equals(freelancerWallet)
                || !record.getMilestoneId().equals(milestoneId))) {
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        }
        if (solana.findEscrow(milestoneId.toString()).isPresent()) {
            throw new ApplicationException(ErrorCode.FUNDING_IN_PROGRESS);
        }
        SolanaConfigResult config = solana.getConfig();
        if (unified) {
            PaymentFlow flow = paymentFlows.findByMilestoneId(milestoneId)
                    .filter(f -> f.getContractId().equals(contractId))
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
            if (!unifiedFunding.clientUsdcConfirmed(flow.getId(), config.getAcceptedMint(), milestone.getAmount()))
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            reconciliation.requireMatched(flow, 1);
            String purchaseId = Long.toUnsignedString(flow.getId().getMostSignificantBits());
            MockOnrampReceiptResult receipt = solana.findMockOnrampReceipt(clientWallet, purchaseId)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
            if (!Objects.equals(receipt.getClient(), clientWallet)
                    || !Objects.equals(receipt.getMint(), config.getAcceptedMint())
                    || !Objects.equals(receipt.getPurchaseId(), purchaseId)
                    || !Objects.equals(receipt.getTokenAmount(), baseUnits(milestone.getAmount()))
                    || !Objects.equals(receipt.getUsdAmountE6(), baseUnits(milestone.getAmount())))
                throw new ApplicationException(ErrorCode.FUNDING_AMOUNT_CHANGED);
        }
        String amount = baseUnits(milestone.getAmount());
        Map<String, Object> request = Map.of(
                "client", clientWallet, "freelancer", freelancerWallet,
                "amount", amount,
                "fundingExpiresAt", Long.toString(paymentFlowService.fundingDeadline(contract).getEpochSecond()),
                "deliveryDueAt", Long.toString(contract.getDeliveryDueAt().getEpochSecond()),
                "reviewWindowHours", contract.getReviewWindowHours(), "maxRevisions", contract.getMaxRevisions(),
                "highValueReviewGrace", !unified, "mode", "build");
        SolanaBuildResult built = solana.buildEscrowFund(milestoneId.toString(), request);
        if (built.derivedAccounts() == null
                || built.derivedAccounts().getMilestoneEscrow() == null
                || !built.requiredSigners().contains(clientWallet)
                || !built.requiredSigners().contains(config.getAdmin())) {
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        }
        if (record == null) {
            record = new EscrowContract();
            record.setContractId(contractId);
            record.setMilestoneId(milestoneId);
            record.setClientWallet(clientWallet);
            record.setFreelancerWallet(freelancerWallet);
            record.setArbiterWallet(config.getAdmin());
            record.setEscrowAddress(built.derivedAccounts().getMilestoneEscrow());
            record.setMint(config.getAcceptedMint());
        }
        record.setFundBuildSession(built.buildSessionId());
        record.setLastChainStatus("AWAITING_SIGNATURE");
        escrows.saveAndFlush(record);
        if (!unified) contract.setPaymentRail("SOLANA_ESCROW");
        return new EscrowBuildView(built.buildSessionId(), built.transactionBase64(),
                record.getEscrowAddress(), clientWallet, freelancerWallet, amount,
                config.getAcceptedMint());
    }

    @Transactional
    public EscrowView submitFunding(UUID clientId, UUID contractId, UUID milestoneId,
            String buildSessionId, String transactionBase64) {
        EscrowContract record = participant(clientId, contractId, milestoneId);
        WorkContract contract = contracts.findById(contractId).orElseThrow();
        if (!clientId.equals(contract.getClientUserId())
                || !Objects.equals(record.getFundBuildSession(), buildSessionId)
                || transactionBase64 == null || transactionBase64.isBlank()) {
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        }
        if (record.getFundSignature() == null) {
            record.setFundSignature(solana.submitEscrowSigned(buildSessionId, transactionBase64));
            record.setLastChainStatus("PENDING_CONFIRMATION");
            escrows.saveAndFlush(record);
        }
        return get(clientId, contractId, milestoneId);
    }

    @Transactional
    public EscrowView get(UUID actorId, UUID contractId, UUID milestoneId) {
        EscrowContract record = participant(actorId, contractId, milestoneId);
        WorkContract contract = contracts.findById(contractId).orElseThrow();
        String expectedFundingExpiry = Long.toString(paymentFlowService.fundingDeadline(contract).getEpochSecond());
        SolanaEscrowResult chain = solana.findEscrow(milestoneId.toString()).orElse(null);
        if (chain == null) {
            return new EscrowView(contract.getPaymentRail() == null ? "SOLANA_ESCROW" : contract.getPaymentRail(), record.getLastChainStatus(),
                    record.getLastChainStatus(),
                    record.getEscrowAddress(), record.getClientWallet(), record.getFreelancerWallet(),
                    record.getMint(), null, expectedFundingExpiry, null, null, null, null, null,
                    record.getFundSignature(),
                    record.getReleaseSignature(), record.getRefundSignature(),
                    record.getResolutionSignature(), null, null,
                    "UNCONFIRMED",
                    null, false);
        }
        Milestone milestone = milestones.findWithLockById(milestoneId).orElseThrow();
        // Unified terms lock one review window; only legacy escrow adds high-value grace.
        boolean reviewGrace = !PaymentFlow.RAIL.equals(contract.getPaymentRail())
                && milestone.getAmount().compareTo(new BigDecimal("500.00")) > 0;
        String expectedReviewSeconds = Long.toString((contract.getReviewWindowHours()
                + (reviewGrace ? 24 : 0)) * 3600L);
        if (!Objects.equals(chain.address(), record.getEscrowAddress())
                || !Objects.equals(chain.milestoneId(), milestoneId.toString())
                || !Objects.equals(chain.client(), record.getClientWallet())
                || !Objects.equals(chain.freelancer(), record.getFreelancerWallet())
                || !Objects.equals(chain.arbiter(), record.getArbiterWallet())
                || !Objects.equals(chain.mint(), record.getMint())
                || !Objects.equals(chain.amount(), baseUnits(milestone.getAmount()))
                || chain.vaultAddress() == null || chain.vaultBalanceBaseUnits() == null
                || (!"Released".equals(chain.status()) && !"Refunded".equals(chain.status())
                    && new java.math.BigInteger(chain.vaultBalanceBaseUnits()).compareTo(
                        new java.math.BigInteger(chain.amount())) < 0)
                || !Objects.equals(chain.fundingExpiresAt(), expectedFundingExpiry)
                || !Objects.equals(chain.originalDeliveryDueAt(),
                    Long.toString(contract.getDeliveryDueAt().getEpochSecond()))
                || !Objects.equals(chain.reviewWindowSeconds(), expectedReviewSeconds)
                || chain.maxRevisions() != contract.getMaxRevisions()) {
            record.setLastChainStatus("MISMATCH");
            throw new ApplicationException(ErrorCode.FUNDING_AMOUNT_CHANGED);
        }
        if (PaymentFlow.RAIL.equals(contract.getPaymentRail()) && "Released".equals(chain.status())
                && contract.getStatus() == ContractStatus.COMPLETED
                && milestone.getStatus() == MilestoneStatus.RELEASED)
            // Idempotent; the chain terminal and vault were checked above against this escrow.
            paymentFlowService.confirmChainSettlement(contract, milestone, "Released",
                    chain.address(), record.getReleaseSignature());
        record.setLastChainStatus("Released".equals(chain.status())
                && contract.getStatus() == ContractStatus.COMPLETED
                ? "RELEASED_RECONCILED" : chain.status());
        if (PaymentFlow.RAIL.equals(contract.getPaymentRail())
                && ("Funded".equals(chain.status()) || "Submitted".equals(chain.status())
                    || "Revision".equals(chain.status()) || "Disputed".equals(chain.status()))) {
            PaymentFlow flow = paymentFlows.findByMilestoneId(milestoneId)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
            unifiedFunding.confirmEscrow(flow, chain);
        }
        if (milestone.getStatus() == MilestoneStatus.PENDING_FUNDING
                && contract.getStatus() == ContractStatus.PENDING_FUNDING
                && ("Funded".equals(chain.status()) || "Submitted".equals(chain.status())
                    || "Revision".equals(chain.status()) || "Disputed".equals(chain.status()))) {
            Job job = jobs.findById(contract.getJobId()).orElseThrow();
            if (job.getStatus() != JobStatus.AWAITING_PAYMENT) {
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            }
            milestone.setStatus(MilestoneStatus.FUNDED);
            contract.setStatus(ContractStatus.ACTIVE);
            job.setStatus(JobStatus.IN_PROGRESS);
            notifications.notify(contract.getFreelancerId(), NotificationType.FUNDING_CONFIRMED,
                    "Milestone đã được ký quỹ on-chain", "Bạn có thể bắt đầu công việc.", job.getId());
        }
        return new EscrowView(contract.getPaymentRail() == null ? "SOLANA_ESCROW" : contract.getPaymentRail(), chain.status(), phase(chain, record), chain.address(),
                chain.client(), chain.freelancer(), chain.mint(), chain.amount(),
                chain.fundingExpiresAt(), chain.deliveryDueAt(), chain.reviewDueAt(), chain.submissionHash(),
                chain.submissionCount(), chain.disputeHash(),
                record.getFundSignature(), record.getReleaseSignature(), record.getRefundSignature(),
                record.getResolutionSignature(),
                chain.vaultAddress(), chain.vaultBalanceBaseUnits(), "CHAIN_STATE_CONFIRMED",
                chain.requestedDeliveryDueAt(), chain.extensionUsed());
    }

    private String phase(SolanaEscrowResult chain, EscrowContract record) {
        if ("Released".equals(chain.status())) return "RELEASED";
        if ("Refunded".equals(chain.status())) return "REFUNDED";
        if (record.isSettlementRetryPending()) return "RETRY_PENDING";
        if ("Disputed".equals(chain.status()) && record.getResolutionSignature() != null)
            return "RESOLUTION_PENDING";
        if ("Submitted".equals(chain.status()) && record.getReleaseSignature() != null)
            return "RELEASE_PENDING";
        if (record.getRefundSignature() != null) return "REFUND_PENDING";
        if ("Submitted".equals(chain.status()) && chain.reviewDueAt() != null
                && !Instant.now().isBefore(Instant.ofEpochSecond(Long.parseLong(chain.reviewDueAt()))))
            return "RELEASE_ELIGIBLE";
        return chain.status().toUpperCase(java.util.Locale.ROOT);
    }

    private EscrowContract participant(UUID actorId, UUID contractId, UUID milestoneId) {
        WorkContract contract = contracts.findById(contractId)
                .filter(c -> c.getClientUserId().equals(actorId) || c.getFreelancerId().equals(actorId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        return escrows.findByContractId(contract.getId())
                .filter(e -> e.getMilestoneId().equals(milestoneId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
    }

    private String wallet(UUID userId) {
        return wallets.findFirstByUserIdOrderByIdAsc(userId)
                .map(Wallet::getPublicKey)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID));
    }

    private String baseUnits(BigDecimal usd) {
        return usd.movePointRight(6).toBigIntegerExact().toString();
    }

}
