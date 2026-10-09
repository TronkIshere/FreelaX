package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.response.payment.UnifiedUsdOrderResult;
import com.marketplace.backend.dto.response.payment.UnifiedUsdStatementResult;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.provider.currency.OnRampQuote;
import com.marketplace.backend.provider.currency.OnRampResult;
import com.marketplace.backend.provider.onchain.SolanaOnRampProvider;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** USD statement and on-ramp are separate confirmations of one payment flow. */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedUsdFundingService {
    private final PaymentFlowRepository flows;
    private final PaymentFlowStepRepository steps;
    private final PaymentFlowEvidenceRepository evidence;
    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final WalletRepository wallets;
    private final UserRepository users;
    private final PaymentBackendClient payment;
    private final SolanaCprClient solana;
    private final SolanaCprProperties solanaProperties;
    private final SolanaOnRampProvider onramp;
    private final PaymentFlowService timelines;
    private final TransactionTemplate transactionTemplate;

    public PaymentFlowService.Timeline openUsdOrder(UUID clientId, UUID contractId,
            UUID milestoneId, String key) {
        if (!StringUtils.hasText(key) || key.length() > 100 || !key.equals(key.trim()))
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        UUID flowId = transactionTemplate.execute(tx -> {
            Milestone milestone = milestones.findWithLockById(milestoneId)
                    .filter(m -> m.getContractId().equals(contractId))
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
            WorkContract contract = contracts.findById(contractId)
                    .filter(c -> clientId.equals(c.getClientUserId()))
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
            PaymentFlow flow = flows.findWithLockByMilestoneId(milestoneId)
                    .filter(f -> f.getContractId().equals(contractId)
                            && f.getClientId().equals(clientId))
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
            if (!PaymentFlow.RAIL.equals(contract.getPaymentRail())
                    || contract.getStatus() != ContractStatus.PENDING_FUNDING
                    || milestone.getStatus() != MilestoneStatus.PENDING_FUNDING
                    || !"USD".equals(milestone.getCurrency())
                    || milestone.getAmount().compareTo(flow.getGrossUsd()) != 0
                    || Instant.now().isAfter(flow.getFundingExpiresAt()))
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            // On-ramp can deliver USDC only to a verified Client wallet; refuse before any USD moves.
            if (wallets.findFirstByUserIdOrderByIdAsc(clientId).map(Wallet::getPublicKey)
                    .filter(StringUtils::hasText).isEmpty())
                throw new ApplicationException(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID);
            if (flow.getPayerBankCode() == null) {
                User client = users.findById(clientId)
                        .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID));
                if (client.getBankCode() == null
                        || !StringUtils.hasText(client.getBankAccountNumber())
                        || !StringUtils.hasText(client.getBankAccountHolderName()))
                    throw new ApplicationException(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID);
                flow.setPayerBankCode(client.getBankCode().name());
                flow.setPayerBankAccountNumber(client.getBankAccountNumber());
                flow.setPayerBankAccountHolderName(client.getBankAccountHolderName());
            }
            PaymentFlowStep order = locked(flow.getId(), "USD_ORDER");
            if (order.getIdempotencyKey() != null && !order.getIdempotencyKey().equals(key))
                throw new ApplicationException(ErrorCode.FUNDING_KEY_CONFLICT);
            if ("FAILED".equals(order.getStatus()))
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            if (order.getIdempotencyKey() == null) order.setIdempotencyKey(key);
            if ("NOT_STARTED".equals(order.getStatus())) order.setStatus("PROCESSING");
            return flow.getId();
        });
        PaymentFlow snapshot = flows.findById(flowId).orElseThrow();
        String providerKey = "unified-usd-" + flowId;
        try {
            UnifiedUsdOrderResult order = payment.openUnifiedUsdOrder(flowId, snapshot.getJobId(),
                    snapshot.getContractId(), snapshot.getMilestoneId(), snapshot.getClientId(),
                    snapshot.getGrossUsd(), snapshot.getEscrowUsdc(), snapshot.getPayerBankCode(),
                    snapshot.getPayerBankAccountNumber(), snapshot.getPayerBankAccountHolderName(),
                    providerKey);
            acceptOrder(snapshot, order);
        } catch (RuntimeException ex) {
            markUnknown(flowId, "USD_ORDER");
        }
        return timelines.timeline(clientId, contractId, milestoneId, false);
    }

    public PaymentFlowService.Timeline submitUsdOrder(UUID clientId, UUID contractId,
            UUID milestoneId) {
        WorkContract contract = contracts.findById(contractId)
                .filter(c -> clientId.equals(c.getClientUserId())
                        && PaymentFlow.RAIL.equals(c.getPaymentRail())
                        && c.getStatus() == ContractStatus.PENDING_FUNDING)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        PaymentFlow flow = flows.findByMilestoneId(milestoneId)
                .filter(f -> f.getContractId().equals(contract.getId())
                        && f.getTermsVersion() == 1 && f.getQuoteExpiresAt() != null
                        && Instant.now().isBefore(f.getQuoteExpiresAt())
                        && Instant.now().isBefore(f.getFundingExpiresAt()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_INVALID_STATE));
        PaymentFlowStep current = steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId()).stream()
                .filter(s -> "USD_ORDER".equals(s.getKind())).findFirst()
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        if (!List.of("AWAITING_CLIENT", "PENDING", "UNKNOWN").contains(current.getStatus()))
            throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
        try {
            acceptOrder(flow, payment.submitUnifiedUsdOrder(flow.getId(), "unified-usd-" + flow.getId()));
        } catch (RuntimeException ex) {
            markUnknown(flow.getId(), "USD_ORDER");
        }
        return timelines.timeline(clientId, contractId, milestoneId, false);
    }

    @Scheduled(initialDelayString = "${payment-flow.reconcile-initial-delay-ms:5000}",
            fixedDelayString = "${payment-flow.reconcile-interval-ms:5000}")
    public void reconcile() {
        List<PaymentFlowStep> usd = steps.findTop50ByKindAndStatusInOrderByUpdatedAtAsc(
                "USD_ORDER", List.of("PENDING", "PROCESSING", "UNKNOWN"));
        for (PaymentFlowStep step : usd) {
            try { reconcileUsd(step.getPaymentFlowId()); }
            catch (RuntimeException ex) { log.warn("Unified USD reconciliation will retry for {}", step.getPaymentFlowId()); }
        }
        List<PaymentFlowStep> usdc = steps.findTop50ByKindAndStatusInOrderByUpdatedAtAsc(
                "CLIENT_USDC", List.of("PENDING", "PROCESSING", "UNKNOWN", "FAILED"));
        for (PaymentFlowStep step : usdc) {
            try { advanceOnramp(step.getPaymentFlowId()); }
            catch (RuntimeException ex) { log.warn("Unified on-ramp reconciliation will retry for {}", step.getPaymentFlowId()); }
        }
    }

    public void reconcileUsd(UUID flowId) {
        PaymentFlow flow = flows.findById(flowId).orElseThrow();
        UnifiedUsdOrderResult order;
        UnifiedUsdStatementResult statement;
        try {
            order = payment.getUnifiedUsdOrder(flowId);
            statement = payment.getUnifiedUsdStatement(flowId);
            acceptOrder(flow, order);
        } catch (RuntimeException ex) {
            markUnknown(flowId, "USD_ORDER");
            return;
        }
        if (!"USD_RECEIVED".equals(order.status())) return;
        boolean matched = statement != null && statement.simulation()
                && flowId.equals(statement.paymentFlowId()) && statement.entries() != null
                && statement.entries().stream().anyMatch(s ->
                    flowId.equals(s.paymentFlowId()) && "USD_RECEIVED".equals(s.kind())
                    && "USD".equals(s.currency()) && flow.getGrossUsd().compareTo(s.amount()) == 0
                    && Objects.equals(order.quoteId(), s.reference())
                    && Objects.equals("usd-received:" + flowId, s.eventKey()));
        transactionTemplate.executeWithoutResult(tx -> {
            PaymentFlowStep orderStep = locked(flowId, "USD_ORDER");
            orderStep.setStatus("CONFIRMED");
            orderStep.setConfirmedAt(Instant.now());
            PaymentFlowStep received = locked(flowId, "USD_RECEIVED");
            if ("CONFIRMED".equals(received.getStatus())) return;
            if (!matched) {
                received.setStatus("UNKNOWN");
                return;
            }
            received.setStatus("CONFIRMED");
            received.setAmount(flow.getGrossUsd());
            received.setCurrency("USD");
            received.setProvider("UNIFIED_USD_MOCK");
            received.setReference("usd-received:" + flowId);
            received.setEvidenceSource("PROVIDER_STATEMENT");
            received.setConfirmedAt(Instant.now());
            evidence.save(new PaymentFlowEvidence(flow, "USD_RECEIVED", "CONFIRMED",
                    "unified-usd-" + flowId, received.getReference(), "PROVIDER_STATEMENT",
                    flow.getGrossUsd(), "USD"));
            PaymentFlowStep clientUsdc = locked(flowId, "CLIENT_USDC");
            if ("NOT_STARTED".equals(clientUsdc.getStatus())) clientUsdc.setStatus("PENDING");
        });
        if (matched) advanceOnramp(flowId);
    }

    public void advanceOnramp(UUID flowId) {
        PaymentFlow flow = flows.findById(flowId).orElseThrow();
        String clientWallet = wallets.findFirstByUserIdOrderByIdAsc(flow.getClientId())
                .map(Wallet::getPublicKey)
                .orElse(null);
        if (!StringUtils.hasText(clientWallet)) { markUnknown(flowId, "CLIENT_USDC"); return; }
        String purchaseId = Long.toUnsignedString(flowId.getMostSignificantBits());
        // A failed on-ramp may be resent only while no receipt exists: the receipt PDA is keyed
        // by purchase ID, so a late landing of the earlier transaction cannot issue USDC twice.
        boolean resendFailed;
        try { resendFailed = solana.findMockOnrampReceipt(clientWallet, purchaseId).isEmpty(); }
        catch (RuntimeException ex) { markUnknown(flowId, "CLIENT_USDC"); return; }
        PaymentFlowStep claimed = transactionTemplate.execute(tx -> {
            if (!"CONFIRMED".equals(locked(flowId, "USD_RECEIVED").getStatus())) return null;
            PaymentFlowStep step = locked(flowId, "CLIENT_USDC");
            if ("CONFIRMED".equals(step.getStatus())) return null;
            if ("FAILED".equals(step.getStatus())
                    && (StringUtils.hasText(step.getTransactionSignature())
                        || StringUtils.hasText(step.getAccountAddress()))) {
                if (!resendFailed) return null;
                step.setTransactionSignature(null);
                step.setSubmittedAt(null);
            }
            if ("PROCESSING".equals(step.getStatus()) && step.getRetryAfter() != null
                    && Instant.now().isBefore(step.getRetryAfter())) return null;
            step.setStatus("PROCESSING");
            step.setRetryAfter(Instant.now().plusSeconds(30));
            step.setIdempotencyKey("unified-onramp-" + flowId);
            return step;
        });
        if (claimed == null) return;
        OnRampQuote quote = new OnRampQuote(flow.getGrossUsd().setScale(6),
                BigDecimal.ZERO.setScale(6), flow.getGrossUsd().setScale(6),
                flow.getGrossUsd().movePointRight(6).toBigIntegerExact().toString(), purchaseId);
        OnRampResult result;
        try {
            result = StringUtils.hasText(claimed.getTransactionSignature())
                    ? onramp.resume(quote, clientWallet, claimed.getTransactionSignature(),
                        claimed.getTokenAccount(), claimed.getAccountAddress(), claimed.getSubmittedAt())
                    : onramp.execute(quote, clientWallet);
        }
        catch (RuntimeException ex) { markUnknown(flowId, "CLIENT_USDC"); return; }
        transactionTemplate.executeWithoutResult(tx -> {
            PaymentFlowStep step = locked(flowId, "CLIENT_USDC");
            if ("CONFIRMED".equals(step.getStatus())) return;
            if (StringUtils.hasText(result.transactionSignature()))
                step.setTransactionSignature(result.transactionSignature());
            if (result.status() == OnRampStatus.SUBMITTED && step.getSubmittedAt() == null)
                step.setSubmittedAt(java.time.LocalDateTime.now());
            if (StringUtils.hasText(result.receiptPda()))
                step.setAccountAddress(result.receiptPda());
            if (StringUtils.hasText(result.clientUsdcAta()))
                step.setTokenAccount(result.clientUsdcAta());
            if (result.status() == OnRampStatus.CONFIRMED
                    && result.amountUsdcReceived() != null
                    && result.amountUsdcReceived().compareTo(flow.getEscrowUsdc()) == 0) {
                step.setStatus("CONFIRMED");
                step.setAmount(result.amountUsdcReceived());
                step.setCurrency("USDC");
                step.setProvider("SOLANA_LOCALNET");
                step.setReference(result.receiptPda());
                step.setEvidenceSource("ONRAMP_RECEIPT_AND_ATA");
                step.setConfirmedAt(Instant.now());
                step.setRetryAfter(null);
                evidence.save(new PaymentFlowEvidence(flow, "CLIENT_USDC", "CONFIRMED",
                        step.getIdempotencyKey(), result.receiptPda(), "ONRAMP_RECEIPT_AND_ATA",
                        result.amountUsdcReceived(), "USDC"));
            } else if (result.status() == OnRampStatus.CONFIRMED
                    || (result.status() == OnRampStatus.FAILED
                        && (StringUtils.hasText(result.transactionSignature())
                            || StringUtils.hasText(result.receiptPda())))) {
                step.setStatus("FAILED");
            } else {
                step.setStatus("UNKNOWN");
                step.setReference(result.receiptPda() != null
                        ? result.receiptPda() : result.transactionSignature());
                step.setRetryAfter(Instant.now().plusSeconds(
                        StringUtils.hasText(step.getTransactionSignature()) ? 5 : 30));
            }
        });
    }

    public boolean clientUsdcConfirmed(UUID flowId, String mint, BigDecimal amount) {
        PaymentFlow flow = flows.findById(flowId).orElseThrow();
        return Objects.equals(flow.getMint(), mint)
                && flow.getEscrowUsdc().compareTo(amount) == 0
                && steps.findByPaymentFlowIdOrderByCreatedAtAsc(flowId).stream()
                    .anyMatch(s -> "CLIENT_USDC".equals(s.getKind())
                            && "CONFIRMED".equals(s.getStatus()));
    }

    public void confirmEscrow(PaymentFlow flow, SolanaEscrowResult chain) {
        if (chain == null || !Objects.equals(chain.milestoneId(), flow.getMilestoneId().toString())
                || !Objects.equals(chain.mint(), flow.getMint())
                || !Objects.equals(chain.amount(), flow.getEscrowUsdc().movePointRight(6)
                    .toBigIntegerExact().toString())
                || chain.vaultBalanceBaseUnits() == null
                || new BigInteger(chain.vaultBalanceBaseUnits()).compareTo(new BigInteger(chain.amount())) < 0)
            throw new ApplicationException(ErrorCode.FUNDING_AMOUNT_CHANGED);
        transactionTemplate.executeWithoutResult(tx -> {
            if (!"CONFIRMED".equals(locked(flow.getId(), "CLIENT_USDC").getStatus()))
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            PaymentFlowStep escrow = locked(flow.getId(), "ESCROW");
            if ("CONFIRMED".equals(escrow.getStatus())) return;
            escrow.setStatus("CONFIRMED");
            escrow.setAmount(flow.getEscrowUsdc());
            escrow.setCurrency("USDC");
            escrow.setProvider("SOLANA_LOCALNET");
            escrow.setReference(chain.address());
            escrow.setEvidenceSource("ESCROW_PDA_AND_VAULT");
            escrow.setConfirmedAt(Instant.now());
            evidence.save(new PaymentFlowEvidence(flow, "ESCROW", "CONFIRMED",
                    "unified-escrow-" + flow.getId(), chain.address(),
                    "ESCROW_PDA_AND_VAULT", flow.getEscrowUsdc(), "USDC"));
        });
    }

    private void acceptOrder(PaymentFlow flow, UnifiedUsdOrderResult order) {
        SolanaConfigResult config = solana.getConfig();
        if (order == null || !order.simulation() || !Objects.equals(order.paymentFlowId(), flow.getId())
                || !Objects.equals(order.jobId(), flow.getJobId())
                || !Objects.equals(order.contractId(), flow.getContractId())
                || !Objects.equals(order.milestoneId(), flow.getMilestoneId())
                || !Objects.equals(order.clientId(), flow.getClientId())
                || order.grossUsd() == null || flow.getGrossUsd().compareTo(order.grossUsd()) != 0
                || order.escrowUsdc() == null || flow.getEscrowUsdc().compareTo(order.escrowUsdc()) != 0
                || !Objects.equals(order.payerBankCode(), flow.getPayerBankCode())
                || !Objects.equals(order.payerBankAccountNumber(), flow.getPayerBankAccountNumber())
                || !Objects.equals(order.payerBankAccountHolderName(), flow.getPayerBankAccountHolderName())
                || !Objects.equals(order.fundKey(), "unified-usd-" + flow.getId())
                || !StringUtils.hasText(order.quoteId()) || order.quoteExpiresAt() == null
                || !StringUtils.hasText(config.getAcceptedMint())
                || Boolean.TRUE.equals(config.getPaused()))
            throw new ApplicationException(ErrorCode.FUNDING_AMOUNT_CHANGED);
        transactionTemplate.executeWithoutResult(tx -> {
            PaymentFlow lockedFlow = flows.findWithLockByMilestoneId(flow.getMilestoneId()).orElseThrow();
            if (lockedFlow.getTermsVersion() == 0) {
                // Mint/network were accepted by both parties with the fingerprint; a rotated
                // config needs new terms, not a silent switch. Pre-snapshot drafts keep the old rule.
                if (lockedFlow.getMint() != null
                        && (!Objects.equals(lockedFlow.getMint(), config.getAcceptedMint())
                            || !Objects.equals(lockedFlow.getNetwork(), solanaProperties.getNetwork())))
                    throw new ApplicationException(ErrorCode.FUNDING_AMOUNT_CHANGED);
                lockedFlow.setTermsVersion(1);
                lockedFlow.setNetwork(solanaProperties.getNetwork());
                lockedFlow.setMint(config.getAcceptedMint());
                lockedFlow.setQuoteSource(order.quoteSource());
                lockedFlow.setQuoteExpiresAt(order.quoteExpiresAt());
            } else if (!Objects.equals(lockedFlow.getMint(), config.getAcceptedMint())
                    || !Objects.equals(lockedFlow.getQuoteExpiresAt(), order.quoteExpiresAt())
                    || !Objects.equals(lockedFlow.getQuoteSource(), order.quoteSource())) {
                throw new ApplicationException(ErrorCode.FUNDING_AMOUNT_CHANGED);
            }
            PaymentFlowStep step = locked(flow.getId(), "USD_ORDER");
            if (!"CONFIRMED".equals(step.getStatus())) {
                step.setStatus("AWAITING_CLIENT".equals(order.status()) ? "AWAITING_CLIENT"
                        : "EXPIRED".equals(order.status()) ? "FAILED" : "PENDING");
                step.setAmount(flow.getGrossUsd());
                step.setCurrency("USD");
                step.setProvider("UNIFIED_USD_MOCK");
                step.setReference(order.quoteId());
                step.setEvidenceSource("PROVIDER_ORDER");
            }
        });
    }

    private PaymentFlowStep locked(UUID flowId, String kind) {
        return steps.findWithLockByPaymentFlowIdAndKind(flowId, kind)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
    }

    private void markUnknown(UUID flowId, String kind) {
        transactionTemplate.executeWithoutResult(tx -> {
            PaymentFlowStep step = locked(flowId, kind);
            if (!"CONFIRMED".equals(step.getStatus()) && !"FAILED".equals(step.getStatus()))
                step.setStatus("UNKNOWN");
        });
    }
}
