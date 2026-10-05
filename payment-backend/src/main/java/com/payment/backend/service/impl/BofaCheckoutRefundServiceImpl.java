package com.payment.backend.service.impl;

import com.payment.backend.dto.request.bofa.CreateCheckoutRefundRequest;
import com.payment.backend.dto.response.bofa.BofaCheckoutRefundResponse;
import com.payment.backend.entity.*;
import com.payment.backend.exception.*;
import com.payment.backend.repository.*;
import com.payment.backend.service.BofaCheckoutRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service @RequiredArgsConstructor
public class BofaCheckoutRefundServiceImpl implements BofaCheckoutRefundService {
    private final BofaCheckoutOrderRepository orders;
    private final BofaCheckoutRefundRepository refunds;
    private final BofaPayoutReleaseRepository releases;
    private final BofaAccountBalanceRepository balances;
    private final TransactionTemplate transactions;

    @Override public BofaCheckoutRefundResponse refund(CreateCheckoutRefundRequest request) {
        if (request == null || request.checkoutOrderId() == null || request.expectedAmount() == null
                || request.expectedAmount().amount() == null || request.expectedAmount().amount().signum() <= 0
                || request.expectedAmount().amount().scale() > 2
                || request.expectedAmount().amount().precision() - request.expectedAmount().amount().scale() > 17
                || !StringUtils.hasText(request.expectedAmount().currency()) || request.expectedAmount().currency().length() != 3
                || !StringUtils.hasText(request.refundKey()) || request.refundKey().length() > 100
                || !request.refundKey().equals(request.refundKey().trim())) throw new ApplicationException(ErrorCode.INVALID_DATA);
        BigDecimal amount = request.expectedAmount().amount().setScale(2);
        String hash = hash(request.checkoutOrderId() + ":" + amount.toPlainString() + ":" + request.expectedAmount().currency());
        try { return transactions.execute(tx -> execute(request, amount, hash)); }
        catch (DataIntegrityViolationException ex) {
            // Read after rollback; a global key collision must never produce another credit.
            if (refunds.findByRefundKey(request.refundKey()).isPresent()) throw new ApplicationException(ErrorCode.REFUND_KEY_CONFLICT);
            throw ex;
        }
    }

    private BofaCheckoutRefundResponse execute(CreateCheckoutRefundRequest request, BigDecimal amount, String hash) {
        // This is exactly the checkout lock used by capture and primary release.
        BofaCheckoutOrder order = orders.findWithLockById(request.checkoutOrderId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.CHECKOUT_ORDER_NOT_FOUND, request.checkoutOrderId()));
        BofaCheckoutRefund prior = refunds.findByRefundKey(request.refundKey()).orElse(null);
        if (prior != null) {
            if (!prior.getPayloadHash().equals(hash)) throw new ApplicationException(ErrorCode.REFUND_KEY_CONFLICT);
            return response(prior);
        }
        if (refunds.findByCheckoutOrderId(order.getId()).isPresent()) throw new ApplicationException(ErrorCode.CHECKOUT_ALREADY_REFUNDED);
        if (releases.findByCheckoutOrderId(order.getId()).isPresent()) throw new ApplicationException(ErrorCode.REFUND_RELEASE_CONFLICT);
        if (order.getStatus() != BofaCheckoutOrderStatus.CAPTURED || !StringUtils.hasText(order.getBofaCaptureId())
                || order.getCapturedAt() == null) throw new ApplicationException(ErrorCode.INVALID_CHECKOUT_ORDER_STATUS);
        if (!"USD".equals(request.expectedAmount().currency())) throw new ApplicationException(ErrorCode.REFUND_CURRENCY_MISMATCH);
        if (amount.compareTo(order.getAmountUsd()) != 0) throw new ApplicationException(ErrorCode.REFUND_AMOUNT_MISMATCH);
        // Restore the actual capture ledger, never invent an opening balance or accept a caller's payer identity.
        BofaAccountBalance balance = balances.findWithLockByBankAccountNumber(order.getPayerBankAccountNumber())
                .orElseThrow(() -> new ApplicationException(ErrorCode.REFUND_PAYER_LEDGER_MISSING));
        BofaCheckoutRefund refund = new BofaCheckoutRefund();
        refund.setCheckoutOrderId(order.getId()); refund.setPayerUserId(order.getPayerUserId()); refund.setRefundKey(request.refundKey());
        refund.setPayloadHash(hash); refund.setAmount(amount); refund.setCurrency("USD"); refund.setStatus(BofaRefundStatus.PROCESSING);
        refund.setCreatedAt(now()); refund.setUpdatedAt(refund.getCreatedAt()); refunds.saveAndFlush(refund);
        balance.setBalance(balance.getBalance().add(amount)); balances.saveAndFlush(balance);
        refund.setRefundReference("sim-refund-" + refund.getId()); refund.setStatus(BofaRefundStatus.SUCCEEDED);
        refund.setUpdatedAt(now()); refunds.saveAndFlush(refund);
        return response(refund);
    }

    @Override @Transactional(readOnly = true) public BofaCheckoutRefundResponse getByRefundKey(String key) {
        if (!StringUtils.hasText(key) || key.length() > 100) throw new ApplicationException(ErrorCode.INVALID_DATA);
        return refunds.findByRefundKey(key).map(this::response).orElseThrow(() -> new ApplicationException(ErrorCode.REFUND_NOT_FOUND));
    }
    private BofaCheckoutRefundResponse response(BofaCheckoutRefund r) {
        return new BofaCheckoutRefundResponse(r.getId(), r.getRefundKey(), r.getCheckoutOrderId(), r.getPayerUserId(),
                r.getStatus().name(), r.getAmount(), r.getCurrency(), r.isSimulation(), r.getRefundReference(), false,
                r.getCreatedAt().toInstant(ZoneOffset.UTC), r.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS); }
    private String hash(String payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
