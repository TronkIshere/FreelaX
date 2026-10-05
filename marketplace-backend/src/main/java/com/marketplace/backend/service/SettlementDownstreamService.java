package com.marketplace.backend.service;

import com.marketplace.backend.entity.*;
import com.marketplace.backend.provider.currency.*;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.service.impl.PayoutServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class SettlementDownstreamService {
    private final ContractSettlementRepository settlements;
    private final FreelancerPayoutRecordRepository payouts;
    private final JobRepository jobs;
    private final PayoutServiceImpl payoutPreparation;
    private final OnRampProvider onRamp;
    private final ClientPaymentService clientPayment;
    private final OnChainOffRampService withdrawal;
    private final VndPayoutService vndPayout;
    private final SettlementTaxService tax;
    private final TransactionTemplate transactionTemplate;

    public void process(UUID settlementId) {
        locked(settlementId, s -> {
            if (s.getTaxStatus() == SettlementStageStatus.FAILED
                    && "TAX_DOWNSTREAM_CONTRACT_BLOCKED".equals(s.getTaxError())) {
                s.setTaxStatus(SettlementStageStatus.NOT_STARTED); s.setTaxError(null);
            }
        });
        try {
            locked(settlementId, s -> {
                if (!s.getOnChainStatus().canAdvance() || s.getPayoutRecordId() != null) return;
                FreelancerPayoutRecord p = payoutPreparation.prepareContractRecord(
                        jobs.findById(s.getJobId()).orElseThrow(), s);
                s.setPayoutRecordId(p.getId());
                s.setOnChainError(null);
            });
        } catch (RuntimeException ex) {
            // Preparation may mark its transaction rollback-only; report in a fresh transaction.
            locked(settlementId, s -> {
                if (s.getPayoutRecordId() == null && s.getOnChainStatus().canAdvance()) {
                    boolean conflict = ex instanceof com.marketplace.backend.exception.ApplicationException;
                    s.setOnChainStatus(conflict ? SettlementStageStatus.FAILED : SettlementStageStatus.FAILED_RETRYABLE);
                    s.setOnChainError(conflict ? "DOWNSTREAM_RECORD_CONFLICT" : "ON_CHAIN_PREPARATION_UNAVAILABLE");
                }
            });
        }
        // Separate commits: immutable quote survives a remote success followed by local rollback.
        locked(settlementId, this::advanceOnChain);
        locked(settlementId, this::advanceOffRamp);
        try {
            locked(settlementId, s -> { if (taxRunnable(s)) tax.prepare(s); });
        } catch (RuntimeException ex) {
            locked(settlementId, s -> {
                if (taxRunnable(s)) {
                    s.setTaxStatus(SettlementStageStatus.FAILED_RETRYABLE);
                    s.setTaxError("TAX_PREPARATION_UNAVAILABLE");
                }
            });
            return;
        }
        locked(settlementId, s -> { if (taxRunnable(s)) tax.advance(s); });
    }

    private void advanceOnChain(ContractSettlement s) {
        if (!s.getOnChainStatus().canAdvance() || s.getPayoutRecordId() == null) return;
        FreelancerPayoutRecord p = payouts.findById(s.getPayoutRecordId()).orElseThrow();
        s.setOnChainStatus(SettlementStageStatus.PROCESSING);
        try {
            if (p.getOnRampStatus() == OnRampStatus.FAILED || p.getClientPaymentStatus() == ClientPaymentStatus.FAILED) {
                s.setOnChainStatus(SettlementStageStatus.FAILED);
                s.setOnChainError("ON_CHAIN_REJECTED");
                return;
            }
            if (p.getOnRampClientPublicKey() == null || p.getFreelancerPublicKey() == null) {
                s.setOnChainStatus(SettlementStageStatus.FAILED_RETRYABLE);
                s.setOnChainError("ON_CHAIN_WALLET_CONFIGURATION_REQUIRED");
                return;
            }
            if (p.getOnRampStatus() != OnRampStatus.CONFIRMED) {
                OnRampQuote quote = new OnRampQuote(p.getAmountUsd(), p.getOnRampFeeUsd(),
                        p.getAmountUsdNet(), p.getOnRampUsdAmountE6(), p.getOnRampPurchaseId());
                OnRampResult result = p.getOnRampStatus() == OnRampStatus.SUBMITTED
                        ? onRamp.resume(quote, p.getOnRampClientPublicKey(), p.getOnRampTransactionSignature(),
                            p.getOnRampClientUsdcAta(), p.getOnRampReceiptPda(), p.getOnRampSubmittedAt())
                        : onRamp.execute(quote, p.getOnRampClientPublicKey());
                p.setOnRampStatus(result.status());
                if (result.transactionSignature() != null) p.setOnRampTransactionSignature(result.transactionSignature());
                if (result.clientUsdcAta() != null) p.setOnRampClientUsdcAta(result.clientUsdcAta());
                if (result.receiptPda() != null) p.setOnRampReceiptPda(result.receiptPda());
                if (result.status() == OnRampStatus.SUBMITTED && p.getOnRampSubmittedAt() == null)
                    p.setOnRampSubmittedAt(LocalDateTime.now());
                if (result.status() == OnRampStatus.CONFIRMED) {
                    p.setAmountUsdcReceived(result.amountUsdcReceived());
                    p.setOnRampConfirmedAt(LocalDateTime.now());
                }
                if (result.status() == OnRampStatus.FAILED) {
                    s.setOnChainStatus(SettlementStageStatus.FAILED);
                    s.setOnChainError("ON_RAMP_REJECTED");
                } else if (result.status() != OnRampStatus.CONFIRMED) {
                    s.setOnChainStatus(SettlementStageStatus.UNKNOWN);
                    s.setOnChainError("ON_RAMP_AWAITING_RECONCILIATION");
                }
            }
            if (p.getOnRampStatus() == OnRampStatus.CONFIRMED) {
                if (p.getClientPaymentStatus() != ClientPaymentStatus.CONFIRMED) clientPayment.advance(p);
                if (p.getClientPaymentStatus() == ClientPaymentStatus.CONFIRMED) {
                    s.setOnChainStatus(SettlementStageStatus.SUCCEEDED);
                    s.setOnChainReference(p.getPaymentTransactionSignature() != null
                            ? p.getPaymentTransactionSignature() : "invoice:" + p.getInvoiceId());
                    s.setOnChainError(null);
                } else if (p.getClientPaymentStatus() == ClientPaymentStatus.FAILED) {
                    s.setOnChainStatus(SettlementStageStatus.FAILED);
                    s.setOnChainError("ON_CHAIN_PAYMENT_REJECTED");
                } else {
                    s.setOnChainStatus(SettlementStageStatus.UNKNOWN);
                    s.setOnChainError("ON_CHAIN_AWAITING_RECONCILIATION");
                }
            }
            payouts.save(p);
        } catch (RuntimeException ex) {
            // Same persisted purchase/rate/invoice IDs; next run reads chain state before mutation.
            s.setOnChainStatus(SettlementStageStatus.FAILED_RETRYABLE);
            s.setOnChainError("ON_CHAIN_RECONCILIATION_REQUIRED");
        }
    }

    private void advanceOffRamp(ContractSettlement s) {
        if (s.getOnChainStatus() != SettlementStageStatus.SUCCEEDED || !s.getOffRampStatus().canAdvance()) return;
        FreelancerPayoutRecord p = payouts.findById(s.getPayoutRecordId()).orElseThrow();
        try {
            if (p.getOnChainOffRampStatus() != OnChainOffRampStatus.CONFIRMED
                    && p.getOnChainOffRampStatus() != OnChainOffRampStatus.FAILED) withdrawal.advance(p);
            if (p.getOnChainOffRampStatus() == OnChainOffRampStatus.CONFIRMED
                    && p.getOffRampStatus() != OffRampStatus.COMPLETED && p.getOffRampStatus() != OffRampStatus.FAILED)
                vndPayout.advance(p);
            if (p.getOffRampStatus() == OffRampStatus.COMPLETED) {
                s.setOffRampStatus(SettlementStageStatus.SUCCEEDED);
                s.setOffRampReference(p.getOffRampReference() != null ? p.getOffRampReference() : "withdrawal:" + p.getWithdrawalId());
                s.setOffRampError(null);
            } else if (p.getOnChainOffRampStatus() == OnChainOffRampStatus.FAILED || p.getOffRampStatus() == OffRampStatus.FAILED) {
                s.setOffRampStatus(SettlementStageStatus.FAILED);
                s.setOffRampError("OFF_RAMP_REJECTED");
            } else {
                s.setOffRampStatus(SettlementStageStatus.UNKNOWN);
                s.setOffRampError("OFF_RAMP_AWAITING_RECONCILIATION");
            }
            payouts.save(p);
        } catch (RuntimeException ex) {
            s.setOffRampStatus(SettlementStageStatus.FAILED_RETRYABLE);
            s.setOffRampError("OFF_RAMP_RECONCILIATION_REQUIRED");
        }
    }

    private boolean taxRunnable(ContractSettlement s) {
        return s.getOffRampStatus() == SettlementStageStatus.SUCCEEDED && s.getTaxStatus().canAdvance();
    }

    private void locked(UUID id, Consumer<ContractSettlement> work) {
        TransactionTemplate independent = new TransactionTemplate(transactionTemplate.getTransactionManager());
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        independent.executeWithoutResult(status -> {
            ContractSettlement s = settlements.findWithLockById(id).orElse(null);
            if (s == null || s.getMoneyStatus() != SettlementMoneyStatus.SUCCEEDED) return;
            work.accept(s);
            boolean onChainRunnable = s.getOnChainStatus().canAdvance();
            boolean offRampRunnable = s.getOnChainStatus() == SettlementStageStatus.SUCCEEDED && s.getOffRampStatus().canAdvance();
            s.setRetryable(onChainRunnable || offRampRunnable || taxRunnable(s));
            s.setNextAttemptAt(Instant.now().plusSeconds(30));
            settlements.saveAndFlush(s);
        });
    }
}
