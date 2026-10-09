package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.response.partner.PartnerStatementResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Compare Marketplace liabilities with the separate Payment Backend mock statement. */
@Service
@RequiredArgsConstructor
public class PartnerReconciliationService {
    private final PaymentBackendClient payment;
    private final FundingTransactionRepository funding;
    private final ContractSettlementRepository settlements;
    private final ContractCancellationRepository cancellations;
    private final ContractDisputeRepository disputes;

    public record Difference(UUID milestoneId, String reason, BigDecimal amountUsd) {}
    public record ReconciliationView(BigDecimal partnerBalanceUsd, BigDecimal ledgerLiabilityUsd,
                                     BigDecimal differenceUsd, boolean matched, int pendingFunding,
                                     List<Difference> differences, Instant statementAt, boolean simulation) {}

    /** Require the independent mock statement to contain the exact cash movements for this escrow. */
    public boolean confirms(UUID milestoneId, String finalKind, BigDecimal grossUsd) {
        if (milestoneId == null || grossUsd == null || grossUsd.signum() <= 0
                || !Set.of("FUND", "RELEASE", "REFUND").contains(finalKind)) return false;
        PartnerStatementResult statement = payment.getPartnerStatement();
        if (statement == null || !statement.simulation() || statement.entries() == null
                || statement.balanceUsd() == null || statement.observedAt() == null) return false;
        int fundingEvents = 0, finalEvents = 0;
        BigDecimal balance = BigDecimal.ZERO, statementTotal = BigDecimal.ZERO;
        for (PartnerStatementResult.Entry entry : statement.entries()) {
            if (entry == null || entry.deltaUsd() == null) return false;
            statementTotal = statementTotal.add(entry.deltaUsd());
            if (!milestoneId.equals(entry.milestoneId())) continue;
            if (entry.kind() == null || entry.eventKey() == null) return false;
            BigDecimal expected;
            String expectedKey;
            if ("FUND".equals(entry.kind())) {
                fundingEvents++;
                expected = grossUsd;
                expectedKey = "fund:" + milestoneId;
            } else if (finalKind.equals(entry.kind()) && !"FUND".equals(finalKind)) {
                finalEvents++;
                expected = grossUsd.negate();
                expectedKey = finalKind.toLowerCase(Locale.ROOT) + ":" + milestoneId;
            } else return false;
            if (!expectedKey.equals(entry.eventKey()) || expected.compareTo(entry.deltaUsd()) != 0) return false;
            balance = balance.add(entry.deltaUsd());
        }
        return fundingEvents == 1 && finalEvents == ("FUND".equals(finalKind) ? 0 : 1)
                && balance.compareTo("FUND".equals(finalKind) ? grossUsd : BigDecimal.ZERO) == 0
                && statementTotal.compareTo(statement.balanceUsd()) == 0;
    }

    public ReconciliationView snapshot() {
        PartnerStatementResult statement = payment.getPartnerStatement();
        if (statement == null || !statement.simulation() || statement.balanceUsd() == null)
            throw new IllegalStateException("Partner mock statement unavailable");
        List<FundingTransaction> rows = funding.findByPaymentMethodIdAndStatus(
                PartnerEscrowFundingService.RAIL, FundingStatus.SUCCEEDED);
        BigDecimal liability = BigDecimal.ZERO.setScale(2);
        Map<UUID, BigDecimal> byMilestone = new HashMap<>();
        for (FundingTransaction row : rows) {
            UUID mid = row.getMilestoneId();
            boolean paid = settlements.findByMilestoneId(mid)
                    .map(s -> s.getMoneyStatus() == SettlementMoneyStatus.SUCCEEDED).orElse(false);
            boolean refunded = cancellations.findByContractId(row.getContractId())
                    .map(c -> c.getStatus() == CancellationStatus.CANCELLED).orElse(false)
                    || disputes.findByContractId(row.getContractId())
                    .map(d -> d.getStatus() == DisputeStatus.RESOLVED_REFUND).orElse(false);
            if (!paid && !refunded) {
                liability = liability.add(row.getAmount());
                byMilestone.merge(mid, row.getAmount(), BigDecimal::add);
            }
        }
        Map<UUID, BigDecimal> statementByMilestone = new HashMap<>();
        for (PartnerStatementResult.Entry entry : statement.entries()) {
            statementByMilestone.merge(entry.milestoneId(), entry.deltaUsd(), BigDecimal::add);
        }
        Set<UUID> all = new HashSet<>(byMilestone.keySet());
        all.addAll(statementByMilestone.keySet());
        List<Difference> differences = all.stream().sorted().map(mid -> {
            BigDecimal amount = statementByMilestone.getOrDefault(mid, BigDecimal.ZERO)
                    .subtract(byMilestone.getOrDefault(mid, BigDecimal.ZERO));
            return amount.signum() == 0 ? null : new Difference(mid, "STATEMENT_LEDGER_MISMATCH", amount);
        }).filter(Objects::nonNull).toList();
        BigDecimal delta = statement.balanceUsd().subtract(liability);
        int pending = funding.findTop50ByPaymentMethodIdAndStatusInOrderByUpdatedAtAsc(
                PartnerEscrowFundingService.RAIL,
                EnumSet.of(FundingStatus.PENDING, FundingStatus.PROCESSING, FundingStatus.UNKNOWN)).size();
        return new ReconciliationView(statement.balanceUsd(), liability, delta,
                delta.signum() == 0 && differences.isEmpty(), pending, differences,
                statement.observedAt(), true);
    }
}
