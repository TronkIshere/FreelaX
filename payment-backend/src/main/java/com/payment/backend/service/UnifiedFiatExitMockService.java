package com.payment.backend.service;

import com.payment.backend.entity.UnifiedFiatExitMock;
import com.payment.backend.entity.UnifiedMockStatement;
import com.payment.backend.exception.ApplicationException;
import com.payment.backend.exception.ErrorCode;
import com.payment.backend.repository.UnifiedFiatExitMockRepository;
import com.payment.backend.repository.UnifiedMockStatementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Local mock bank settlement; it issues a separate statement after an immutable exit command. */
@Service
@RequiredArgsConstructor
public class UnifiedFiatExitMockService {
    private static final BigDecimal VND_RATE = new BigDecimal("25000.00");
    private static final BigDecimal FEE_RATE = new BigDecimal("0.03");
    private static final BigDecimal WITHHOLDING_RATE = new BigDecimal("0.10");
    private final UnifiedFiatExitMockRepository exits;
    private final UnifiedMockStatementRepository statements;

    public record ExitRequest(UUID paymentFlowId, UUID jobId, UUID contractId, UUID milestoneId,
            String kind, String idempotencyKey, String withdrawalReference, String beneficiary,
            BigDecimal grossUsdc, BigDecimal grossUsd, BigDecimal expectedPayoutVnd) { }
    public record ExitView(UUID paymentFlowId, UUID jobId, UUID contractId, UUID milestoneId,
            String kind, String idempotencyKey, String withdrawalReference, String beneficiary,
            BigDecimal grossUsdc, BigDecimal feeUsdc, BigDecimal grossUsd, BigDecimal vndRate,
            BigDecimal taxableVnd, BigDecimal taxWithheldVnd, BigDecimal payoutVnd,
            String status, Instant updatedAt, boolean simulation) { }
    public record StatementRow(String eventKey, UUID paymentFlowId, String kind,
            BigDecimal amount, String currency, String reference, Instant occurredAt) { }
    public record StatementView(UUID paymentFlowId, List<StatementRow> entries,
            Instant observedAt, boolean simulation) { }

    @Transactional
    public ExitView request(ExitRequest input) {
        if (input == null || input.paymentFlowId() == null || input.jobId() == null
                || input.contractId() == null || input.milestoneId() == null
                || !List.of("PAYOUT", "REFUND").contains(input.kind())
                || !StringUtils.hasText(input.idempotencyKey()) || input.idempotencyKey().length() > 100
                || !StringUtils.hasText(input.withdrawalReference())
                || !StringUtils.hasText(input.beneficiary()) || input.beneficiary().length() > 200
                || input.grossUsdc() == null || input.grossUsdc().signum() <= 0
                || input.grossUsdc().scale() > 6 || input.grossUsd() == null
                || input.grossUsd().signum() <= 0 || input.grossUsd().scale() > 2
                || input.grossUsdc().compareTo(input.grossUsd()) != 0
                || input.expectedPayoutVnd() == null || input.expectedPayoutVnd().scale() > 0)
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        BigDecimal fee = "PAYOUT".equals(input.kind())
                ? input.grossUsd().multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP).setScale(6)
                : BigDecimal.ZERO.setScale(6);
        BigDecimal taxableVnd = input.grossUsdc().subtract(fee).multiply(VND_RATE)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal netPayout = taxableVnd.subtract(taxableVnd.multiply(WITHHOLDING_RATE)
                .setScale(0, RoundingMode.HALF_UP));
        // Existing locked quotes may still be in flight. Preserve their fee-only amount,
        // while every newly prepared quote uses the tax-inclusive amount.
        boolean validPayout = "REFUND".equals(input.kind())
                ? input.expectedPayoutVnd().signum() == 0
                : input.expectedPayoutVnd().compareTo(netPayout) == 0
                    || input.expectedPayoutVnd().compareTo(taxableVnd) == 0;
        if (!validPayout) throw new ApplicationException(ErrorCode.INVALID_DATA);
        UnifiedFiatExitMock prior = exits.findWithLockByPaymentFlowId(input.paymentFlowId()).orElse(null);
        if (prior != null) {
            if (!Objects.equals(prior.getJobId(), input.jobId())
                    || !Objects.equals(prior.getContractId(), input.contractId())
                    || !Objects.equals(prior.getMilestoneId(), input.milestoneId())
                    || !Objects.equals(prior.getKind(), input.kind())
                    || !Objects.equals(prior.getIdempotencyKey(), input.idempotencyKey())
                    || !Objects.equals(prior.getWithdrawalReference(), input.withdrawalReference())
                    || !Objects.equals(prior.getBeneficiary(), input.beneficiary())
                    || prior.getGrossUsdc().compareTo(input.grossUsdc()) != 0
                    || prior.getGrossUsd().compareTo(input.grossUsd()) != 0
                    || prior.getPayoutVnd().compareTo(input.expectedPayoutVnd()) != 0)
                throw new ApplicationException(ErrorCode.INVALID_TRANSACTION_STATUS);
            return view(prior);
        }
        UnifiedFiatExitMock row = new UnifiedFiatExitMock();
        row.setPaymentFlowId(input.paymentFlowId());
        row.setJobId(input.jobId());
        row.setContractId(input.contractId());
        row.setMilestoneId(input.milestoneId());
        row.setKind(input.kind());
        row.setIdempotencyKey(input.idempotencyKey());
        row.setWithdrawalReference(input.withdrawalReference());
        row.setBeneficiary(input.beneficiary());
        row.setGrossUsdc(input.grossUsdc().setScale(6));
        row.setGrossUsd(input.grossUsd().setScale(2));
        row.setFeeUsdc(fee);
        row.setVndRate(VND_RATE);
        row.setPayoutVnd(input.expectedPayoutVnd());
        row.setStatus("PENDING");
        row.setCreatedAt(Instant.now());
        row.setUpdatedAt(row.getCreatedAt());
        return view(exits.saveAndFlush(row));
    }

    /** Scheduler simulates a bank confirmation. A read API alone cannot pay or refund. */
    @Transactional
    public ExitView settle(UUID flowId) {
        UnifiedFiatExitMock row = exits.findWithLockByPaymentFlowId(flowId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "Fiat exit"));
        if ("PENDING".equals(row.getStatus())) {
            Instant now = Instant.now();
            if ("PAYOUT".equals(row.getKind())) {
                statement(row, "VND_PAYOUT", row.getPayoutVnd(), "VND", now);
                statement(row, "PLATFORM_FEE", row.getFeeUsdc(), "USDC", now);
                BigDecimal tax = taxableVnd(row).subtract(row.getPayoutVnd());
                if (tax.signum() > 0) statement(row, "TAX_WITHHELD", tax, "VND", now);
            } else {
                statement(row, "USD_REFUND", row.getGrossUsd(), "USD", now);
            }
            row.setStatus("CONFIRMED");
            row.setUpdatedAt(now);
        }
        return view(row);
    }

    @Transactional(readOnly = true)
    public ExitView get(UUID flowId) {
        return view(exits.findById(flowId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "Fiat exit")));
    }

    @Transactional(readOnly = true)
    public StatementView statement(UUID flowId) {
        return new StatementView(flowId, statements.findByPaymentFlowIdOrderByOccurredAtAsc(flowId)
                .stream().filter(s -> List.of("VND_PAYOUT", "PLATFORM_FEE", "TAX_WITHHELD", "USD_REFUND")
                        .contains(s.getKind()))
                .map(s -> new StatementRow(s.getEventKey(), s.getPaymentFlowId(), s.getKind(),
                        s.getAmount(), s.getCurrency(), s.getReference(), s.getOccurredAt()))
                .toList(), Instant.now(), true);
    }

    private void statement(UnifiedFiatExitMock row, String kind, BigDecimal amount,
            String currency, Instant at) {
        UnifiedMockStatement event = new UnifiedMockStatement();
        event.setEventKey("unified-exit:" + row.getPaymentFlowId() + ":" + kind);
        event.setPaymentFlowId(row.getPaymentFlowId());
        event.setKind(kind);
        event.setAmount(amount);
        event.setCurrency(currency);
        event.setReference(row.getWithdrawalReference());
        event.setOccurredAt(at);
        statements.saveAndFlush(event);
    }

    private ExitView view(UnifiedFiatExitMock row) {
        BigDecimal taxable = "PAYOUT".equals(row.getKind()) ? taxableVnd(row) : BigDecimal.ZERO;
        BigDecimal tax = taxable.subtract(row.getPayoutVnd());
        return new ExitView(row.getPaymentFlowId(), row.getJobId(), row.getContractId(),
                row.getMilestoneId(), row.getKind(), row.getIdempotencyKey(),
                row.getWithdrawalReference(), row.getBeneficiary(), row.getGrossUsdc(),
                row.getFeeUsdc(), row.getGrossUsd(), row.getVndRate(), taxable, tax, row.getPayoutVnd(),
                row.getStatus(), row.getUpdatedAt(), true);
    }

    private BigDecimal taxableVnd(UnifiedFiatExitMock row) {
        return row.getGrossUsdc().subtract(row.getFeeUsdc()).multiply(row.getVndRate())
                .setScale(0, RoundingMode.HALF_UP);
    }
}
