package com.payment.backend.service;

import com.payment.backend.entity.UnifiedMockStatement;
import com.payment.backend.entity.UnifiedUsdOrderMock;
import com.payment.backend.exception.ApplicationException;
import com.payment.backend.exception.ErrorCode;
import com.payment.backend.repository.UnifiedMockStatementRepository;
import com.payment.backend.repository.UnifiedUsdOrderMockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Separate local provider ledger for USD receipt. No real bank transfer occurs. */
@Service
@RequiredArgsConstructor
public class UnifiedUsdOrderMockService {
    private final UnifiedUsdOrderMockRepository orders;
    private final UnifiedMockStatementRepository statements;

    public record OpenRequest(UUID paymentFlowId, UUID jobId, UUID contractId,
            UUID milestoneId, UUID clientId, BigDecimal grossUsd, BigDecimal escrowUsdc,
            String payerBankCode, String payerBankAccountNumber,
            String payerBankAccountHolderName, String fundKey) { }
    public record OrderView(UUID paymentFlowId, UUID jobId, UUID contractId, UUID milestoneId,
            UUID clientId, BigDecimal grossUsd, BigDecimal escrowUsdc,
            String payerBankCode, String payerBankAccountNumber,
            String payerBankAccountHolderName, String fundKey,
            String quoteId, String quoteSource, Instant quoteExpiresAt, String status,
            Instant updatedAt, boolean simulation) { }
    public record StatementRow(String eventKey, UUID paymentFlowId, String kind,
            BigDecimal amount, String currency, String reference, Instant occurredAt) { }
    public record StatementView(UUID paymentFlowId, List<StatementRow> entries,
            Instant observedAt, boolean simulation) { }

    @Transactional
    public OrderView open(OpenRequest request) {
        if (request == null || request.paymentFlowId() == null || request.jobId() == null
                || request.contractId() == null || request.milestoneId() == null
                || request.clientId() == null || request.grossUsd() == null
                || request.grossUsd().signum() <= 0 || request.grossUsd().scale() > 2
                || request.escrowUsdc() == null || request.escrowUsdc().scale() > 6
                || request.escrowUsdc().compareTo(request.grossUsd()) != 0
                || !StringUtils.hasText(request.payerBankCode()) || request.payerBankCode().length() > 30
                || !StringUtils.hasText(request.payerBankAccountNumber())
                || request.payerBankAccountNumber().length() > 50
                || !StringUtils.hasText(request.payerBankAccountHolderName())
                || request.payerBankAccountHolderName().length() > 100
                || !StringUtils.hasText(request.fundKey()) || request.fundKey().length() > 100
                || !request.fundKey().equals(request.fundKey().trim()))
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        UnifiedUsdOrderMock prior = orders.findWithLockByPaymentFlowId(request.paymentFlowId()).orElse(null);
        if (prior != null) {
            if (!Objects.equals(prior.getJobId(), request.jobId())
                    || !Objects.equals(prior.getContractId(), request.contractId())
                    || !Objects.equals(prior.getMilestoneId(), request.milestoneId())
                    || !Objects.equals(prior.getClientId(), request.clientId())
                    || prior.getGrossUsd().compareTo(request.grossUsd()) != 0
                    || prior.getEscrowUsdc().compareTo(request.escrowUsdc()) != 0
                    || !Objects.equals(prior.getPayerBankCode(), request.payerBankCode())
                    || !Objects.equals(prior.getPayerBankAccountNumber(), request.payerBankAccountNumber())
                    || !Objects.equals(prior.getPayerBankAccountHolderName(), request.payerBankAccountHolderName())
                    || !Objects.equals(prior.getFundKey(), request.fundKey()))
                throw new ApplicationException(ErrorCode.INVALID_TRANSACTION_STATUS);
            return view(prior);
        }
        Instant now = Instant.now();
        UnifiedUsdOrderMock row = new UnifiedUsdOrderMock();
        row.setPaymentFlowId(request.paymentFlowId());
        row.setJobId(request.jobId());
        row.setContractId(request.contractId());
        row.setMilestoneId(request.milestoneId());
        row.setClientId(request.clientId());
        row.setGrossUsd(request.grossUsd().setScale(2));
        row.setEscrowUsdc(request.escrowUsdc().setScale(6));
        row.setPayerBankCode(request.payerBankCode());
        row.setPayerBankAccountNumber(request.payerBankAccountNumber());
        row.setPayerBankAccountHolderName(request.payerBankAccountHolderName());
        row.setFundKey(request.fundKey());
        row.setQuoteId("local-usd-usdc-" + request.paymentFlowId());
        row.setQuoteExpiresAt(now.plusSeconds(15 * 60));
        row.setStatus("AWAITING_CLIENT");
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return view(orders.saveAndFlush(row));
    }

    /** Mock payment intent: the scheduler, not this API, writes the USD receipt. */
    @Transactional
    public OrderView submit(UUID paymentFlowId, String fundKey) {
        UnifiedUsdOrderMock row = orders.findWithLockByPaymentFlowId(paymentFlowId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "USD order"));
        if (!Objects.equals(row.getFundKey(), fundKey))
            throw new ApplicationException(ErrorCode.INVALID_TRANSACTION_STATUS);
        if ("AWAITING_CLIENT".equals(row.getStatus())) {
            if (!Instant.now().isBefore(row.getQuoteExpiresAt())) {
                row.setStatus("EXPIRED");
            } else {
                row.setStatus("PENDING");
            }
            row.setUpdatedAt(Instant.now());
        }
        return view(row);
    }

    /** Invoked by the mock provider scheduler, never by a Marketplace payment command. */
    @Transactional
    public OrderView confirmUsd(UUID paymentFlowId) {
        UnifiedUsdOrderMock row = orders.findWithLockByPaymentFlowId(paymentFlowId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "USD order"));
        if ("PENDING".equals(row.getStatus())) {
            Instant now = Instant.now();
            String eventKey = "usd-received:" + paymentFlowId;
            UnifiedMockStatement event = new UnifiedMockStatement();
            event.setEventKey(eventKey);
            event.setPaymentFlowId(paymentFlowId);
            event.setKind("USD_RECEIVED");
            event.setAmount(row.getGrossUsd());
            event.setCurrency("USD");
            event.setReference(row.getQuoteId());
            event.setOccurredAt(now);
            statements.saveAndFlush(event);
            row.setStatus("USD_RECEIVED");
            row.setUpdatedAt(now);
        }
        return view(row);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID paymentFlowId) {
        return view(orders.findById(paymentFlowId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "USD order")));
    }

    @Transactional(readOnly = true)
    public StatementView statement(UUID paymentFlowId) {
        return new StatementView(paymentFlowId,
                statements.findByPaymentFlowIdOrderByOccurredAtAsc(paymentFlowId).stream()
                        .map(s -> new StatementRow(s.getEventKey(), s.getPaymentFlowId(), s.getKind(),
                                s.getAmount(), s.getCurrency(), s.getReference(), s.getOccurredAt()))
                        .toList(), Instant.now(), true);
    }

    private OrderView view(UnifiedUsdOrderMock row) {
        return new OrderView(row.getPaymentFlowId(), row.getJobId(), row.getContractId(),
                row.getMilestoneId(), row.getClientId(), row.getGrossUsd(), row.getEscrowUsdc(),
                row.getPayerBankCode(), row.getPayerBankAccountNumber(),
                row.getPayerBankAccountHolderName(), row.getFundKey(), row.getQuoteId(),
                "LOCAL_MOCK_USD_USDC_1_TO_1",
                row.getQuoteExpiresAt(), row.getStatus(), row.getUpdatedAt(), true);
    }
}
