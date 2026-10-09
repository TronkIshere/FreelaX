package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.request.solana.PublishRateRequest;
import com.marketplace.backend.dto.request.solana.RequestOfframpRequest;
import com.marketplace.backend.dto.response.payment.UnifiedFiatExitResult;
import com.marketplace.backend.dto.response.payment.UnifiedFiatExitStatementResult;
import com.marketplace.backend.dto.response.solana.*;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A signed USDC withdrawal must precede either mock VND payout or mock USD refund. */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedExitService {
    private static final BigDecimal VND_RATE = new BigDecimal("25000.00");
    private static final int RATE_READ_ATTEMPTS = 6;
    private static final long RATE_READ_DELAY_MS = 500;
    private final PaymentFlowRepository flows;
    private final PaymentFlowStepRepository steps;
    private final PaymentFlowEvidenceRepository evidence;
    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final EscrowContractRepository escrows;
    private final SolanaCprClient solana;
    private final SolanaCprProperties properties;
    private final PaymentBackendClient payment;
    private final PaymentFlowService timelines;
    private final TransactionTemplate transactions;
    private final UnifiedReconciliationService reconciliation;

    public record BuildView(UUID paymentFlowId, String kind, String buildSessionId,
            String transactionBase64, String wallet, String withdrawalId,
            BigDecimal grossUsdc, BigDecimal feeUsdc, BigDecimal payoutVnd,
            Instant quoteExpiresAt, boolean simulation) { }

    public BuildView prepare(UUID actorId, UUID contractId, UUID milestoneId) {
        PaymentFlow flow = flow(actorId, contractId, milestoneId);
        String kind = outcome(flow);
        UUID expectedActor = "PAYOUT".equals(kind) ? flow.getFreelancerId() : flow.getClientId();
        if (!expectedActor.equals(actorId)) throw new ApplicationException(ErrorCode.FUNDING_NOT_FOUND);
        // USD→USDC, vault and terminal transfer must each be matched before USDC leaves the wallet.
        reconciliation.requireMatched(flow, 3);
        String wallet = wallets.findFirstByUserIdOrderByIdAsc(actorId)
                .map(Wallet::getPublicKey).filter(StringUtils::hasText)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID));
        String beneficiary;
        if ("REFUND".equals(kind)) {
            beneficiary = StringUtils.hasText(flow.getPayerBankCode())
                    && StringUtils.hasText(flow.getPayerBankAccountNumber())
                    && StringUtils.hasText(flow.getPayerBankAccountHolderName())
                    ? flow.getPayerBankCode() + ":" + flow.getPayerBankAccountNumber()
                        + ":" + flow.getPayerBankAccountHolderName() : null;
        } else {
            User user = users.findById(actorId)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
            beneficiary = user.getBankCode() == null || !StringUtils.hasText(user.getBankAccountNumber())
                    || !StringUtils.hasText(user.getBankAccountHolderName()) ? null
                    : user.getBankCode().name() + ":" + user.getBankAccountNumber()
                        + ":" + user.getBankAccountHolderName();
        }
        if (!StringUtils.hasText(beneficiary) || beneficiary.length() > 200)
            throw new ApplicationException(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID);
        SolanaConfigResult rateConfig = solana.getConfig();
        long maxAge;
        try { maxAge = Long.parseLong(rateConfig.getMaxRateAgeSeconds()); }
        catch (RuntimeException ex) { throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE); }
        if (maxAge < 60 || !StringUtils.hasText(rateConfig.getRateAuthority())
                || Boolean.TRUE.equals(rateConfig.getPaused()))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        long quoteSeconds = Math.min(900, maxAge - 5);
        String frozenBeneficiary = beneficiary;
        PaymentFlowStep snapshot = transactions.execute(tx -> {
            PaymentFlowStep terminal = locked(flow.getId(), "PAYOUT".equals(kind) ? "USDC_RELEASE" : "USDC_REFUND");
            if (!"CONFIRMED".equals(terminal.getStatus()))
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            PaymentFlowStep step = locked(flow.getId(), "WITHDRAWAL");
            if ("CONFIRMED".equals(step.getStatus()) || "FAILED".equals(step.getStatus())
                    || StringUtils.hasText(step.getTransactionSignature()))
                throw new ApplicationException(ErrorCode.FUNDING_IN_PROGRESS);
            if (step.getBeneficiary() != null && !step.getBeneficiary().equals(frozenBeneficiary))
                throw new ApplicationException(ErrorCode.FUNDING_KEY_CONFLICT);
            if (step.getBeneficiary() == null) step.setBeneficiary(frozenBeneficiary);
            if (step.getQuoteExpiresAt() == null || !Instant.now().isBefore(step.getQuoteExpiresAt())) {
                step.setRateId(Long.toUnsignedString(flow.getId().getLeastSignificantBits()
                        ^ Instant.now().toEpochMilli()));
                step.setQuoteExpiresAt(Instant.now().plusSeconds(quoteSeconds));
                step.setAccountAddress(null);
                step.setBuildSessionId(null);
            }
            BigDecimal fee = "PAYOUT".equals(kind)
                    ? flow.getPlatformFeeUsd().setScale(6) : BigDecimal.ZERO.setScale(6);
            step.setFeeUsdc(fee);
            step.setVndRate(VND_RATE);
            step.setPayoutVnd("PAYOUT".equals(kind)
                    ? flow.getEscrowUsdc().subtract(fee).multiply(VND_RATE)
                        .setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(0));
            step.setIdempotencyKey("unified-exit-" + flow.getId());
            step.setStatus("PROCESSING");
            return step;
        });
        String rateId = snapshot.getRateId();
        SolanaRateResult rate = solana.findRate(rateId).orElse(null);
        if (rate == null) {
            String source = flow.getId() + ":" + rateId + ":LOCAL_MOCK_25000";
            solana.publishRate(PublishRateRequest.builder()
                    .rateAuthority(rateConfig.getRateAuthority()).rateId(rateId)
                    .usdcUsdE6("1000000").usdVndE6("25000000000")
                    .observedAt(Long.toString(Instant.now().getEpochSecond()))
                    .expiresAt(Long.toString(snapshot.getQuoteExpiresAt().getEpochSecond()))
                    .sourceHash(sha256(source)).mode("send")
                    .commitment(properties.getCommitment()).skipPreflight(false).build());
            // A freshly confirmed snapshot can lag the RPC read path; wait briefly, then let
            // the caller retry with the same rate ID instead of publishing a second one.
            for (int attempt = 0; rate == null && attempt < RATE_READ_ATTEMPTS; attempt++) {
                if (attempt > 0) pause(RATE_READ_DELAY_MS);
                rate = solana.findRate(rateId).orElse(null);
            }
        }
        if (rate == null) throw new ApplicationException(ErrorCode.FUNDING_IN_PROGRESS);
        SolanaConfigResult config = solana.getConfig();
        if (!Objects.equals(rate.getPublisher(), config.getRateAuthority())
                || !Objects.equals(rate.getRateId(), rateId)
                || !Objects.equals(rate.getUsdcUsdE6(), "1000000")
                || !Objects.equals(rate.getUsdVndE6(), "25000000000")
                || rate.getExpiresAt() == null
                || Long.parseLong(rate.getExpiresAt()) != snapshot.getQuoteExpiresAt().getEpochSecond()
                || !Instant.now().isBefore(snapshot.getQuoteExpiresAt()))
            throw new ApplicationException(ErrorCode.FUNDING_AMOUNT_CHANGED);
        String rateAddress = rate.getAddress();
        transactions.executeWithoutResult(tx -> {
            PaymentFlowStep step = locked(flow.getId(), "WITHDRAWAL");
            if (!Objects.equals(step.getRateId(), rateId))
                throw new ApplicationException(ErrorCode.FUNDING_KEY_CONFLICT);
            step.setAccountAddress(rateAddress);
        });
        String withdrawalId = withdrawalId(flow.getId());
        SolanaBuildResult built = solana.buildUnifiedOfframp(RequestOfframpRequest.builder()
                .freelancer(wallet).withdrawalId(withdrawalId).rateId(rateId)
                .tokenAmount(baseUnits(flow.getEscrowUsdc())).mode("build")
                .commitment(properties.getCommitment()).skipPreflight(false).build());
        if (built.requiredSigners() == null || !built.requiredSigners().contains(wallet)
                || built.derivedAccounts() == null
                || !StringUtils.hasText(built.derivedAccounts().getWithdrawalRecord()))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        transactions.executeWithoutResult(tx -> {
            PaymentFlowStep step = locked(flow.getId(), "WITHDRAWAL");
            if (!Objects.equals(step.getRateId(), rateId) || step.getTransactionSignature() != null)
                throw new ApplicationException(ErrorCode.FUNDING_KEY_CONFLICT);
            step.setBuildSessionId(built.buildSessionId());
            step.setTokenAccount(built.derivedAccounts().getWithdrawalRecord());
        });
        return new BuildView(flow.getId(), kind, built.buildSessionId(),
                built.transactionBase64(), wallet, withdrawalId, flow.getEscrowUsdc(),
                snapshot.getFeeUsdc(), snapshot.getPayoutVnd(), snapshot.getQuoteExpiresAt(), true);
    }

    public PaymentFlowService.Timeline submit(UUID actorId, UUID contractId,
            UUID milestoneId, String buildSessionId, String signedBase64) {
        PaymentFlow flow = flow(actorId, contractId, milestoneId);
        String kind = outcome(flow);
        if (!("PAYOUT".equals(kind) ? actorId.equals(flow.getFreelancerId())
                : actorId.equals(flow.getClientId())) || !StringUtils.hasText(signedBase64))
            throw new ApplicationException(ErrorCode.FUNDING_NOT_FOUND);
        reconciliation.requireMatched(flow, 3);
        PaymentFlowStep step = step(flow.getId(), "WITHDRAWAL");
        if (!Objects.equals(step.getBuildSessionId(), buildSessionId)
                || step.getQuoteExpiresAt() == null || !Instant.now().isBefore(step.getQuoteExpiresAt()))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        if (step.getTransactionSignature() == null) {
            // A lost local commit is recovered by reading the deterministic WithdrawalRecord.
            if (verifiedWithdrawal(flow, step, actorId) == null) {
                String signature = solana.submitEscrowSigned(buildSessionId, signedBase64);
                transactions.executeWithoutResult(tx -> {
                    PaymentFlowStep locked = locked(flow.getId(), "WITHDRAWAL");
                    locked.setTransactionSignature(signature);
                    locked.setStatus("UNKNOWN");
                });
            }
        }
        reconcileWithdrawal(flow.getId());
        return timelines.timeline(actorId, contractId, milestoneId, false);
    }

    @Scheduled(initialDelayString = "${payment-flow.reconcile-initial-delay-ms:5000}",
            fixedDelayString = "${payment-flow.reconcile-interval-ms:5000}")
    public void reconcile() {
        for (PaymentFlowStep step : steps.findTop50ByKindAndStatusInOrderByUpdatedAtAsc(
                "WITHDRAWAL", List.of("PROCESSING", "UNKNOWN", "PENDING"))) {
            if (abandonedQuote(step) && "PENDING".equals(step.getStatus())) continue;
            try { reconcileWithdrawal(step.getPaymentFlowId()); }
            catch (RuntimeException ex) {
                log.warn("Unified withdrawal needs reconciliation for {}: {}", step.getPaymentFlowId(), ex.getMessage());
            }
            if (abandonedQuote(step)) releaseAbandonedQuote(step.getPaymentFlowId());
        }
        for (String kind : List.of("VND_PAYOUT", "USD_REFUND")) {
            for (PaymentFlowStep step : steps.findTop50ByKindAndStatusInOrderByUpdatedAtAsc(
                    kind, List.of("PENDING", "PROCESSING", "UNKNOWN"))) {
                try { reconcileFiat(step.getPaymentFlowId()); }
                catch (RuntimeException ex) { log.warn("Unified fiat exit needs reconciliation for {}", step.getPaymentFlowId()); }
            }
        }
    }

    /**
     * Prepared but never signed, and the quote is past expiry: the program rejects an expired
     * rate snapshot, so no WithdrawalRecord can still appear. Nothing is in flight.
     */
    private boolean abandonedQuote(PaymentFlowStep step) {
        return !StringUtils.hasText(step.getTransactionSignature()) && step.getQuoteExpiresAt() != null
                && Instant.now().isAfter(step.getQuoteExpiresAt().plusSeconds(120));
    }

    /** Back to PENDING after a final chain check, so the owner prepares a fresh quote. */
    private void releaseAbandonedQuote(UUID flowId) {
        transactions.executeWithoutResult(tx -> {
            PaymentFlowStep locked = locked(flowId, "WITHDRAWAL");
            if ("PROCESSING".equals(locked.getStatus()) || "UNKNOWN".equals(locked.getStatus())) {
                if (abandonedQuote(locked)) {
                    locked.setStatus("PENDING");
                    locked.setRetryAfter(null);
                }
            }
        });
    }

    public void reconcileWithdrawal(UUID flowId) {
        PaymentFlow flow = flows.findById(flowId).orElseThrow();
        PaymentFlowStep step = step(flowId, "WITHDRAWAL");
        if ("CONFIRMED".equals(step.getStatus()) || step.getRateId() == null) return;
        String kind = outcome(flow);
        UUID owner = "PAYOUT".equals(kind) ? flow.getFreelancerId() : flow.getClientId();
        SolanaWithdrawalResult withdrawal = verifiedWithdrawal(flow, step, owner);
        if (withdrawal == null) return;
        transactions.executeWithoutResult(tx -> {
            PaymentFlowStep locked = locked(flowId, "WITHDRAWAL");
            if ("CONFIRMED".equals(locked.getStatus())) return;
            locked.setStatus("CONFIRMED");
            locked.setAmount(flow.getEscrowUsdc());
            locked.setCurrency("USDC");
            locked.setProvider("SOLANA_LOCALNET");
            locked.setReference(withdrawal.getAddress());
            locked.setEvidenceSource("WITHDRAWAL_PDA_AND_TREASURY");
            locked.setConfirmedAt(Instant.now());
            evidence.save(new PaymentFlowEvidence(flow, "WITHDRAWAL", "CONFIRMED",
                    locked.getIdempotencyKey(), withdrawal.getAddress(),
                    "WITHDRAWAL_PDA_AND_TREASURY", flow.getEscrowUsdc(), "USDC"));
            PaymentFlowStep fiat = locked(flowId, "PAYOUT".equals(kind) ? "VND_PAYOUT" : "USD_REFUND");
            if ("NOT_STARTED".equals(fiat.getStatus())) fiat.setStatus("PENDING");
            if ("PAYOUT".equals(kind)) {
                PaymentFlowStep fee = locked(flowId, "PLATFORM_FEE");
                if ("NOT_STARTED".equals(fee.getStatus())) fee.setStatus("PENDING");
            }
        });
        reconcileFiat(flowId);
    }

    public void reconcileFiat(UUID flowId) {
        PaymentFlow flow = flows.findById(flowId).orElseThrow();
        PaymentFlowStep withdrawal = step(flowId, "WITHDRAWAL");
        if (!"CONFIRMED".equals(withdrawal.getStatus()) || !StringUtils.hasText(withdrawal.getReference())) return;
        String kind = outcome(flow);
        PaymentFlowStep terminal = step(flowId, "PAYOUT".equals(kind) ? "USDC_RELEASE" : "USDC_REFUND");
        if (!"CONFIRMED".equals(terminal.getStatus())) return;
        UUID owner = "PAYOUT".equals(kind) ? flow.getFreelancerId() : flow.getClientId();
        if (verifiedWithdrawal(flow, withdrawal, owner) == null) return;
        UnifiedFiatExitResult exit;
        try { exit = payment.getUnifiedFiatExit(flowId); }
        catch (HttpClientErrorException.NotFound ex) {
            reconciliation.requireMatched(flow, 3);
            exit = payment.requestUnifiedFiatExit(flowId, flow.getJobId(), flow.getContractId(),
                    flow.getMilestoneId(), kind, withdrawal.getIdempotencyKey(),
                    withdrawal.getReference(), withdrawal.getBeneficiary(),
                    flow.getEscrowUsdc(), flow.getGrossUsd());
        }
        if (!matches(flow, withdrawal, kind, exit)) { markUnknown(flowId, kind); return; }
        if (!"CONFIRMED".equals(exit.status())) return;
        UnifiedFiatExitStatementResult statement = payment.getUnifiedFiatExitStatement(flowId);
        String payoutKind = "PAYOUT".equals(kind) ? "VND_PAYOUT" : "USD_REFUND";
        BigDecimal payoutAmount = "PAYOUT".equals(kind) ? withdrawal.getPayoutVnd() : flow.getGrossUsd();
        boolean payoutMatched = statementMatches(statement, flowId, payoutKind,
                payoutAmount, "PAYOUT".equals(kind) ? "VND" : "USD", withdrawal.getReference());
        boolean feeMatched = "REFUND".equals(kind) || statementMatches(statement, flowId,
                "PLATFORM_FEE", withdrawal.getFeeUsdc(), "USDC", withdrawal.getReference());
        if (!payoutMatched || !feeMatched) { markUnknown(flowId, kind); return; }
        transactions.executeWithoutResult(tx -> {
            PaymentFlowStep payout = locked(flowId, payoutKind);
            if (!"CONFIRMED".equals(payout.getStatus())) {
                payout.setStatus("CONFIRMED"); payout.setAmount(payoutAmount);
                payout.setCurrency("PAYOUT".equals(kind) ? "VND" : "USD");
                payout.setProvider("UNIFIED_FIAT_MOCK");
                payout.setReference(withdrawal.getReference());
                payout.setEvidenceSource("PROVIDER_STATEMENT");
                payout.setConfirmedAt(Instant.now());
                evidence.save(new PaymentFlowEvidence(flow, payoutKind, "CONFIRMED",
                        withdrawal.getIdempotencyKey(), withdrawal.getReference(),
                        "PROVIDER_STATEMENT", payoutAmount, payout.getCurrency()));
            }
            if ("PAYOUT".equals(kind)) {
                PaymentFlowStep fee = locked(flowId, "PLATFORM_FEE");
                if (!"CONFIRMED".equals(fee.getStatus())) {
                    fee.setStatus("CONFIRMED"); fee.setAmount(withdrawal.getFeeUsdc());
                    fee.setCurrency("USDC"); fee.setProvider("UNIFIED_FIAT_MOCK");
                    fee.setReference(withdrawal.getReference());
                    fee.setEvidenceSource("WITHDRAWAL_AND_PROVIDER_STATEMENT");
                    fee.setConfirmedAt(Instant.now());
                    evidence.save(new PaymentFlowEvidence(flow, "PLATFORM_FEE", "CONFIRMED",
                            withdrawal.getIdempotencyKey(), withdrawal.getReference(),
                            "WITHDRAWAL_AND_PROVIDER_STATEMENT", withdrawal.getFeeUsdc(), "USDC"));
                }
            }
        });
    }

    private SolanaWithdrawalResult verifiedWithdrawal(PaymentFlow flow, PaymentFlowStep step, UUID ownerId) {
        String wallet = wallets.findFirstByUserIdOrderByIdAsc(ownerId).map(Wallet::getPublicKey).orElse(null);
        if (!StringUtils.hasText(wallet)) return null;
        SolanaWithdrawalResult row = solana.findWithdrawal(wallet, withdrawalId(flow.getId())).orElse(null);
        if (row == null) return null;
        SolanaConfigResult config = solana.getConfig();
        if (!Objects.equals(row.getFreelancer(), wallet)
                || !Objects.equals(row.getWithdrawalId(), withdrawalId(flow.getId()))
                || !Objects.equals(row.getTokenAmount(), baseUnits(flow.getEscrowUsdc()))
                || !Objects.equals(row.getMint(), flow.getMint())
                || !Objects.equals(row.getMint(), config.getAcceptedMint())
                || !Objects.equals(row.getRateSnapshot(), step.getAccountAddress())
                || step.getTokenAccount() != null && !Objects.equals(row.getAddress(), step.getTokenAccount())
                || !List.of("Pending", "Completed").contains(row.getStatus())
                || !StringUtils.hasText(row.getTreasury())
                || new BigInteger(row.getFiatAmountVnd()).compareTo(
                    flow.getEscrowUsdc().multiply(VND_RATE).setScale(0).toBigIntegerExact()) != 0)
            throw new ApplicationException(ErrorCode.FUNDING_AMOUNT_CHANGED);
        return row;
    }

    private boolean matches(PaymentFlow flow, PaymentFlowStep step, String kind, UnifiedFiatExitResult exit) {
        return exit != null && exit.simulation() && flow.getId().equals(exit.paymentFlowId())
                && flow.getJobId().equals(exit.jobId()) && flow.getContractId().equals(exit.contractId())
                && flow.getMilestoneId().equals(exit.milestoneId())
                && kind.equals(exit.kind()) && Objects.equals(step.getIdempotencyKey(), exit.idempotencyKey())
                && Objects.equals(step.getReference(), exit.withdrawalReference())
                && Objects.equals(step.getBeneficiary(), exit.beneficiary())
                && exit.grossUsdc() != null && flow.getEscrowUsdc().compareTo(exit.grossUsdc()) == 0
                && exit.grossUsd() != null && flow.getGrossUsd().compareTo(exit.grossUsd()) == 0
                && exit.feeUsdc() != null && step.getFeeUsdc().compareTo(exit.feeUsdc()) == 0
                && exit.vndRate() != null && step.getVndRate().compareTo(exit.vndRate()) == 0
                && exit.payoutVnd() != null && step.getPayoutVnd().compareTo(exit.payoutVnd()) == 0;
    }

    private boolean statementMatches(UnifiedFiatExitStatementResult statement, UUID flowId,
            String kind, BigDecimal amount, String currency, String reference) {
        return statement != null && statement.simulation() && flowId.equals(statement.paymentFlowId())
                && statement.entries() != null && statement.entries().stream().anyMatch(e ->
                    flowId.equals(e.paymentFlowId()) && kind.equals(e.kind())
                    && amount.compareTo(e.amount()) == 0 && currency.equals(e.currency())
                    && reference.equals(e.reference())
                    && ("unified-exit:" + flowId + ":" + kind).equals(e.eventKey()));
    }

    private void markUnknown(UUID flowId, String kind) {
        transactions.executeWithoutResult(tx -> {
            PaymentFlowStep target = locked(flowId, "PAYOUT".equals(kind) ? "VND_PAYOUT" : "USD_REFUND");
            if (!"CONFIRMED".equals(target.getStatus())) {
                target.setStatus("UNKNOWN");
                target.setRetryAfter(Instant.now().plusSeconds(5));
            }
        });
    }

    private String outcome(PaymentFlow flow) {
        boolean released = "CONFIRMED".equals(step(flow.getId(), "USDC_RELEASE").getStatus());
        PaymentFlowStep refundStep = step(flow.getId(), "USDC_REFUND");
        boolean refunded = "CONFIRMED".equals(refundStep.getStatus());
        if (released == refunded) throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        WorkContract contract = contracts.findById(flow.getContractId()).orElseThrow();
        Milestone milestone = milestones.findById(flow.getMilestoneId()).orElseThrow();
        if (UnifiedFundingExpiryService.unfunded(refundStep)) {
            // Funding expired with USDC still in the Client wallet; no vault may exist.
            if (contract.getStatus() != ContractStatus.CANCELLED
                    || milestone.getStatus() != MilestoneStatus.CANCELLED
                    || solana.findEscrow(flow.getMilestoneId().toString()).isPresent())
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            return "REFUND";
        }
        EscrowContract escrow = escrows.findByContractId(flow.getContractId()).orElseThrow();
        if (released ? contract.getStatus() != ContractStatus.COMPLETED
                || milestone.getStatus() != MilestoneStatus.RELEASED
                : contract.getStatus() != ContractStatus.CANCELLED
                || milestone.getStatus() != MilestoneStatus.REFUNDED)
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        if (!Objects.equals(escrow.getMint(), flow.getMint()))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        SolanaEscrowResult chain = solana.findEscrow(flow.getMilestoneId().toString())
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
        if (!Objects.equals(chain.address(), escrow.getEscrowAddress())
                || !Objects.equals(chain.mint(), flow.getMint())
                || !Objects.equals(chain.amount(), baseUnits(flow.getEscrowUsdc()))
                || !Objects.equals(chain.status(), released ? "Released" : "Refunded")
                || !Objects.equals(chain.vaultBalanceBaseUnits(), "0"))
            throw new ApplicationException(ErrorCode.FUNDING_AMOUNT_CHANGED);
        return released ? "PAYOUT" : "REFUND";
    }

    private PaymentFlow flow(UUID actorId, UUID contractId, UUID milestoneId) {
        WorkContract contract = contracts.findById(contractId)
                .filter(c -> PaymentFlow.RAIL.equals(c.getPaymentRail())
                        && (actorId.equals(c.getClientUserId()) || actorId.equals(c.getFreelancerId())))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        return flows.findByMilestoneId(milestoneId)
                .filter(f -> f.getContractId().equals(contractId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
    }

    private PaymentFlowStep locked(UUID flowId, String kind) {
        return steps.findWithLockByPaymentFlowIdAndKind(flowId, kind)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
    }

    private PaymentFlowStep step(UUID flowId, String kind) {
        return steps.findByPaymentFlowIdOrderByCreatedAtAsc(flowId).stream()
                .filter(row -> kind.equals(row.getKind())).findFirst()
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
    }

    private String withdrawalId(UUID flowId) { return Long.toUnsignedString(flowId.getMostSignificantBits()); }
    private String baseUnits(BigDecimal amount) { return amount.movePointRight(6).toBigIntegerExact().toString(); }
    private String sha256(String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
