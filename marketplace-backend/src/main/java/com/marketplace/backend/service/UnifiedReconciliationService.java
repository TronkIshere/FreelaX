package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.dto.response.payment.UnifiedFiatExitResult;
import com.marketplace.backend.dto.response.payment.UnifiedFiatExitStatementResult;
import com.marketplace.backend.dto.response.payment.UnifiedUsdOrderResult;
import com.marketplace.backend.dto.response.payment.UnifiedUsdStatementResult;
import com.marketplace.backend.dto.response.solana.MockOnrampReceiptResult;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import com.marketplace.backend.dto.response.solana.SolanaWithdrawalResult;
import com.marketplace.backend.entity.PaymentFlow;
import com.marketplace.backend.entity.PaymentFlowEvidence;
import com.marketplace.backend.entity.PaymentFlowStep;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.PaymentFlowEvidenceRepository;
import com.marketplace.backend.repository.PaymentFlowRepository;
import com.marketplace.backend.repository.PaymentFlowStepRepository;
import com.marketplace.backend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Read-only audit of four money boundaries. UNKNOWN never authorizes a payment. */
@Service
@RequiredArgsConstructor
public class UnifiedReconciliationService {
    private final PaymentFlowRepository flows;
    private final PaymentFlowStepRepository steps;
    private final PaymentFlowEvidenceRepository evidence;
    private final WalletRepository wallets;
    private final PaymentBackendClient payment;
    private final SolanaCprClient solana;

    public record Boundary(String code, String status, String evidenceSource,
            Instant observedAt, boolean blocksNextAction) { }
    public record Review(String boundary, String decision, String note, UUID adminId,
            java.time.LocalDateTime reviewedAt) { }
    public record Case(UUID paymentFlowId, UUID jobId, UUID contractId, UUID milestoneId,
            BigDecimal grossUsd, BigDecimal escrowUsdc, Boundary usdToClientUsdc,
            Boundary clientUsdcToVault, Boundary vaultToRecipient,
            Boundary withdrawalToFiat, List<Review> reviews, boolean simulation) { }

    public static final List<String> BOUNDARIES = List.of("USD_TO_CLIENT_USDC",
            "CLIENT_USDC_TO_VAULT", "VAULT_TO_RECIPIENT", "WITHDRAWAL_TO_FIAT");
    private static final List<String> DECISIONS = List.of("ACKNOWLEDGED", "ESCALATED", "CLEARED_BY_EVIDENCE");

    public List<Case> recent() {
        return flows.findTop50ByOrderByCreatedAtDesc().stream().map(this::inspect).toList();
    }

    public Case inspect(PaymentFlow flow) {
        List<PaymentFlowStep> rows = steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId());
        List<Review> reviews = evidence.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId()).stream()
                .filter(e -> "ADMIN_RECONCILIATION_REVIEW".equals(e.getKind()))
                .map(e -> new Review(e.getReference(), e.getStatus(), e.getNote(), e.getActorId(),
                        e.getCreatedAt())).toList();
        return new Case(flow.getId(), flow.getJobId(), flow.getContractId(), flow.getMilestoneId(),
                flow.getGrossUsd(), flow.getEscrowUsdc(), usd(flow, rows), vault(flow, rows),
                terminal(flow, rows), fiat(flow, rows), reviews, true);
    }

    /**
     * The next money instruction may start only when every earlier boundary is MATCHED by
     * provider/chain evidence. PENDING, UNKNOWN and MISMATCH all block; an Admin review cannot
     * replace that evidence.
     */
    public void requireMatched(PaymentFlow flow, int boundaryCount) {
        Case current = inspect(flow);
        List<Boundary> ordered = List.of(current.usdToClientUsdc(), current.clientUsdcToVault(),
                current.vaultToRecipient(), current.withdrawalToFiat());
        for (int i = 0; i < boundaryCount; i++)
            if (!"MATCHED".equals(ordered.get(i).status()))
                throw new ApplicationException(ErrorCode.PAYMENT_RECONCILIATION_BLOCKED);
    }

    /** Audited Admin decision on a case. It is append-only and changes no money state. */
    @Transactional
    public Case review(UUID adminId, UUID paymentFlowId, String boundary, String decision, String note) {
        if (!BOUNDARIES.contains(boundary) || !DECISIONS.contains(decision)
                || !StringUtils.hasText(note) || note.trim().length() < 10 || note.length() > 450)
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        PaymentFlow flow = flows.findById(paymentFlowId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        Case current = inspect(flow);
        Boundary target = List.of(current.usdToClientUsdc(), current.clientUsdcToVault(),
                current.vaultToRecipient(), current.withdrawalToFiat()).get(BOUNDARIES.indexOf(boundary));
        // "Cleared" is only a record that fresh evidence now matches, never an override.
        if ("CLEARED_BY_EVIDENCE".equals(decision) && !"MATCHED".equals(target.status()))
            throw new ApplicationException(ErrorCode.PAYMENT_RECONCILIATION_BLOCKED);
        evidence.save(PaymentFlowEvidence.adminReview(flow, adminId, boundary, decision,
                note.trim() + " [" + target.code() + "]"));
        return inspect(flow);
    }

    private Boundary usd(PaymentFlow flow, List<PaymentFlowStep> rows) {
        if (!confirmed(rows, "USD_RECEIVED") || !confirmed(rows, "CLIENT_USDC"))
            return boundary("USD_OR_ONRAMP_PENDING", "PENDING", "PROVIDER_STATEMENT_AND_ONRAMP_RECEIPT", true);
        try {
            UnifiedUsdOrderResult order = payment.getUnifiedUsdOrder(flow.getId());
            UnifiedUsdStatementResult statement = payment.getUnifiedUsdStatement(flow.getId());
            String wallet = wallet(flow.getClientId());
            MockOnrampReceiptResult receipt = solana.findMockOnrampReceipt(wallet,
                    Long.toUnsignedString(flow.getId().getMostSignificantBits())).orElse(null);
            boolean money = order != null && order.simulation() && flow.getId().equals(order.paymentFlowId())
                    && order.grossUsd() != null && flow.getGrossUsd().compareTo(order.grossUsd()) == 0
                    && Objects.equals(order.payerBankCode(), flow.getPayerBankCode())
                    && Objects.equals(order.payerBankAccountNumber(), flow.getPayerBankAccountNumber())
                    && statement != null && statement.simulation() && flow.getId().equals(statement.paymentFlowId())
                    && statement.entries() != null && statement.entries().stream().anyMatch(e ->
                        "USD_RECEIVED".equals(e.kind()) && flow.getId().equals(e.paymentFlowId())
                        && "USD".equals(e.currency()) && e.amount() != null
                        && flow.getGrossUsd().compareTo(e.amount()) == 0
                        && ("usd-received:" + flow.getId()).equals(e.eventKey())
                        && Objects.equals(order.quoteId(), e.reference()));
            boolean token = receipt != null && Objects.equals(receipt.getClient(), wallet)
                    && Objects.equals(receipt.getMint(), flow.getMint())
                    && Objects.equals(receipt.getTokenAmount(), units(flow.getEscrowUsdc()))
                    && Objects.equals(receipt.getUsdAmountE6(), units(flow.getGrossUsd()));
            return money && token ? boundary("USD_USDC_MATCH", "MATCHED",
                    "PROVIDER_STATEMENT_AND_ONRAMP_RECEIPT", false)
                    : boundary("USD_USDC_MISMATCH", "MISMATCH",
                        "PROVIDER_STATEMENT_AND_ONRAMP_RECEIPT", true);
        } catch (RuntimeException ex) {
            return boundary("USD_USDC_LOOKUP_UNKNOWN", "UNKNOWN",
                    "PROVIDER_STATEMENT_AND_ONRAMP_RECEIPT", true);
        }
    }

    private Boundary vault(PaymentFlow flow, List<PaymentFlowStep> rows) {
        if (unfunded(rows)) return neverFunded(flow, rows, "VAULT_NEVER_FUNDED");
        if (!confirmed(rows, "CLIENT_USDC") || !confirmed(rows, "ESCROW"))
            return boundary("VAULT_PENDING", "PENDING", "ESCROW_PDA_AND_VAULT", true);
        try {
            SolanaEscrowResult chain = solana.findEscrow(flow.getMilestoneId().toString()).orElse(null);
            PaymentFlowStep escrow = row(rows, "ESCROW");
            boolean match = chain != null && Objects.equals(chain.address(), escrow.getReference())
                    && Objects.equals(chain.milestoneId(), flow.getMilestoneId().toString())
                    && Objects.equals(chain.client(), wallet(flow.getClientId()))
                    && Objects.equals(chain.freelancer(), wallet(flow.getFreelancerId()))
                    && Objects.equals(chain.mint(), flow.getMint())
                    && Objects.equals(chain.amount(), units(flow.getEscrowUsdc()))
                    && (("Released".equals(chain.status()) || "Refunded".equals(chain.status()))
                        && Objects.equals(chain.vaultBalanceBaseUnits(), "0")
                        || !("Released".equals(chain.status()) || "Refunded".equals(chain.status()))
                            && chain.vaultBalanceBaseUnits() != null
                            && new BigInteger(chain.vaultBalanceBaseUnits()).compareTo(
                                    new BigInteger(chain.amount())) >= 0);
            return match ? boundary("VAULT_MATCH", "MATCHED", "ESCROW_PDA_AND_VAULT", false)
                    : boundary("VAULT_MISMATCH", "MISMATCH", "ESCROW_PDA_AND_VAULT", true);
        } catch (RuntimeException ex) {
            return boundary("VAULT_LOOKUP_UNKNOWN", "UNKNOWN", "ESCROW_PDA_AND_VAULT", true);
        }
    }

    private Boundary terminal(PaymentFlow flow, List<PaymentFlowStep> rows) {
        boolean release = confirmed(rows, "USDC_RELEASE"), refund = confirmed(rows, "USDC_REFUND");
        if (!release && !refund) return boundary("TERMINAL_PENDING", "PENDING", "ESCROW_PDA", true);
        if (release && refund) return boundary("TERMINAL_CONFLICT", "MISMATCH", "MARKETPLACE_DB", true);
        if (unfunded(rows)) return neverFunded(flow, rows, "UNFUNDED_USDC_WITH_CLIENT");
        try {
            SolanaEscrowResult chain = solana.findEscrow(flow.getMilestoneId().toString()).orElse(null);
            if (chain == null || !Objects.equals(chain.status(), release ? "Released" : "Refunded")
                    || !Objects.equals(chain.amount(), units(flow.getEscrowUsdc()))
                    || !Objects.equals(chain.vaultBalanceBaseUnits(), "0")
                    || !Objects.equals(chain.client(), wallet(flow.getClientId()))
                    || !Objects.equals(chain.freelancer(), wallet(flow.getFreelancerId())))
                return boundary("TERMINAL_CHAIN_MISMATCH", "MISMATCH", "ESCROW_PDA", true);
            // settle/refund_mutual transfer the full amount to the recorded participant's ATA in
            // the same instruction that writes the terminal status, so terminal + empty vault
            // proves the recipient credit even after transaction history is pruned.
            return boundary("RECIPIENT_BY_ESCROW_INVARIANT", "MATCHED",
                    "ESCROW_PDA_TERMINAL_AND_EMPTY_VAULT", false);
        } catch (RuntimeException ex) {
            return boundary("TERMINAL_LOOKUP_UNKNOWN", "UNKNOWN", "ESCROW_PDA", true);
        }
    }

    private Boundary fiat(PaymentFlow flow, List<PaymentFlowStep> rows) {
        if (!confirmed(rows, "WITHDRAWAL"))
            return boundary("WITHDRAWAL_PENDING", "PENDING", "WITHDRAWAL_PDA", true);
        boolean payout = confirmed(rows, "USDC_RELEASE");
        try {
            PaymentFlowStep withdrawal = row(rows, "WITHDRAWAL");
            String owner = wallet(payout ? flow.getFreelancerId() : flow.getClientId());
            SolanaWithdrawalResult chain = solana.findWithdrawal(owner,
                    Long.toUnsignedString(flow.getId().getMostSignificantBits())).orElse(null);
            UnifiedFiatExitResult exit = payment.getUnifiedFiatExit(flow.getId());
            UnifiedFiatExitStatementResult statement = payment.getUnifiedFiatExitStatement(flow.getId());
            boolean chainMatch = chain != null && Objects.equals(chain.getAddress(), withdrawal.getReference())
                    && Objects.equals(chain.getFreelancer(), owner)
                    && Objects.equals(chain.getTokenAmount(), units(flow.getEscrowUsdc()))
                    && Objects.equals(chain.getMint(), flow.getMint());
            boolean exitMatch = exit != null && exit.simulation() && Objects.equals(exit.paymentFlowId(), flow.getId())
                    && Objects.equals(exit.kind(), payout ? "PAYOUT" : "REFUND")
                    && Objects.equals(exit.withdrawalReference(), withdrawal.getReference())
                    && Objects.equals(exit.beneficiary(), withdrawal.getBeneficiary())
                    && exit.grossUsdc() != null && flow.getEscrowUsdc().compareTo(exit.grossUsdc()) == 0
                    && exit.feeUsdc() != null && withdrawal.getFeeUsdc() != null
                    && withdrawal.getFeeUsdc().compareTo(exit.feeUsdc()) == 0
                    && exit.payoutVnd() != null && withdrawal.getPayoutVnd() != null
                    && withdrawal.getPayoutVnd().compareTo(exit.payoutVnd()) == 0;
            if (!chainMatch || !exitMatch)
                return boundary("WITHDRAWAL_OR_EXIT_MISMATCH", "MISMATCH",
                        "WITHDRAWAL_AND_PROVIDER_ORDER", true);
            if (!"CONFIRMED".equals(exit.status()))
                return boundary("FIAT_PROVIDER_PENDING", "PENDING", "PROVIDER_ORDER", true);
            boolean payoutMatch = entry(statement, flow.getId(), payout ? "VND_PAYOUT" : "USD_REFUND",
                    payout ? withdrawal.getPayoutVnd() : flow.getGrossUsd(),
                    payout ? "VND" : "USD", withdrawal.getReference());
            boolean feeMatch = !payout || entry(statement, flow.getId(), "PLATFORM_FEE",
                    withdrawal.getFeeUsdc(), "USDC", withdrawal.getReference());
            return payoutMatch && feeMatch
                    ? boundary("FIAT_AND_FEE_MATCH", "MATCHED", "WITHDRAWAL_AND_PROVIDER_STATEMENT", false)
                    : boundary("FIAT_OR_FEE_MISMATCH", "MISMATCH",
                        "WITHDRAWAL_AND_PROVIDER_STATEMENT", true);
        } catch (RuntimeException ex) {
            return boundary("FIAT_LOOKUP_UNKNOWN", "UNKNOWN", "WITHDRAWAL_AND_PROVIDER_STATEMENT", true);
        }
    }

    private boolean entry(UnifiedFiatExitStatementResult statement, UUID flowId, String kind,
            BigDecimal amount, String currency, String reference) {
        return amount != null && statement != null && statement.simulation()
                && Objects.equals(statement.paymentFlowId(), flowId) && statement.entries() != null
                && statement.entries().stream().anyMatch(e -> Objects.equals(e.paymentFlowId(), flowId)
                    && kind.equals(e.kind()) && e.amount() != null && amount.compareTo(e.amount()) == 0
                    && currency.equals(e.currency()) && Objects.equals(reference, e.reference())
                    && ("unified-exit:" + flowId + ":" + kind).equals(e.eventKey()));
    }

    private boolean unfunded(List<PaymentFlowStep> rows) {
        return rows.stream().anyMatch(step -> "USDC_REFUND".equals(step.getKind())
                && UnifiedFundingExpiryService.unfunded(step));
    }

    /** Expired-funding refund: USD→USDC matched earlier and no escrow account may exist on chain. */
    private Boundary neverFunded(PaymentFlow flow, List<PaymentFlowStep> rows, String code) {
        if (confirmed(rows, "ESCROW"))
            return boundary("UNFUNDED_WITH_CONFIRMED_ESCROW", "MISMATCH", "MARKETPLACE_DB", true);
        try {
            return solana.findEscrow(flow.getMilestoneId().toString()).isEmpty()
                    ? boundary(code, "MATCHED", "ESCROW_PDA_ABSENT", false)
                    : boundary("UNFUNDED_BUT_ESCROW_EXISTS", "MISMATCH", "ESCROW_PDA", true);
        } catch (RuntimeException ex) {
            return boundary("ESCROW_LOOKUP_UNKNOWN", "UNKNOWN", "ESCROW_PDA", true);
        }
    }

    private PaymentFlowStep row(List<PaymentFlowStep> rows, String kind) {
        return rows.stream().filter(step -> kind.equals(step.getKind())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing step " + kind));
    }
    private boolean confirmed(List<PaymentFlowStep> rows, String kind) {
        return rows.stream().anyMatch(step -> kind.equals(step.getKind())
                && "CONFIRMED".equals(step.getStatus()));
    }
    private String wallet(UUID userId) {
        return wallets.findFirstByUserIdOrderByIdAsc(userId).map(Wallet::getPublicKey)
                .orElseThrow(() -> new IllegalStateException("Wallet unavailable"));
    }
    private String units(BigDecimal amount) { return amount.movePointRight(6).toBigIntegerExact().toString(); }
    private Boundary boundary(String code, String status, String source, boolean blocked) {
        return new Boundary(code, status, source, Instant.now(), blocked);
    }
}
