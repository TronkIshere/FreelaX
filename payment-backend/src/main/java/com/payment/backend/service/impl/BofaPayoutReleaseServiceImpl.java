package com.payment.backend.service.impl;

import com.payment.backend.dto.request.bofa.CreatePayoutReleaseRequest;
import com.payment.backend.dto.response.bofa.BofaPayoutReleaseResponse;
import com.payment.backend.entity.*;
import com.payment.backend.exception.ApplicationException;
import com.payment.backend.exception.ErrorCode;
import com.payment.backend.repository.*;
import com.payment.backend.service.BofaPayoutReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BofaPayoutReleaseServiceImpl implements BofaPayoutReleaseService {
    private final BofaCheckoutOrderRepository orders;
    private final BofaPayoutReleaseRepository releases;
    private final BofaRecipientCreditRepository credits;
    private final TransactionTemplate transactions;

    @Override
    public BofaPayoutReleaseResponse release(CreatePayoutReleaseRequest request) {
        validate(request);
        BigDecimal amount = request.expectedAmount().amount().setScale(2);
        String fingerprint = hash(request.checkoutOrderId() + ":" + request.recipientUserId()
                + ":" + amount.toPlainString() + ":" + request.expectedAmount().currency());
        try {
            return transactions.execute(tx -> execute(request, amount, fingerprint));
        } catch (DataIntegrityViolationException ex) {
            // Different checkout locks can race on the global release key. Read only
            // after rollback; never credit again in response to a constraint failure.
            if (releases.findByReleaseKey(request.releaseKey()).isPresent()) {
                throw new ApplicationException(ErrorCode.RELEASE_KEY_CONFLICT);
            }
            throw ex;
        }
    }

    private BofaPayoutReleaseResponse execute(CreatePayoutReleaseRequest request,
                                              BigDecimal amount, String fingerprint) {
        BofaCheckoutOrder order = orders.findWithLockById(request.checkoutOrderId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.CHECKOUT_ORDER_NOT_FOUND, request.checkoutOrderId()));
        BofaPayoutRelease prior = releases.findByReleaseKey(request.releaseKey()).orElse(null);
        if (prior != null) {
            if (!prior.getPayloadHash().equals(fingerprint)) {
                throw new ApplicationException(ErrorCode.RELEASE_KEY_CONFLICT);
            }
            return response(prior);
        }
        if (releases.findByCheckoutOrderId(order.getId()).isPresent()) {
            throw new ApplicationException(ErrorCode.PAYOUT_ALREADY_RELEASED, order.getId());
        }
        if (order.getStatus() != BofaCheckoutOrderStatus.CAPTURED) {
            throw new ApplicationException(ErrorCode.INVALID_CHECKOUT_ORDER_STATUS);
        }
        // Existing checkout amountUsd is denominated in USD, not a configurable FX amount.
        if (!"USD".equals(request.expectedAmount().currency())) {
            throw new ApplicationException(ErrorCode.RELEASE_CURRENCY_MISMATCH);
        }
        if (order.getAmountUsd() == null || amount.compareTo(order.getAmountUsd()) != 0) {
            throw new ApplicationException(ErrorCode.RELEASE_AMOUNT_MISMATCH);
        }
        if (request.recipientUserId().equals(order.getPayerUserId())) {
            throw new ApplicationException(ErrorCode.RELEASE_RECIPIENT_INVALID);
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        BofaPayoutRelease release = new BofaPayoutRelease();
        release.setCheckoutOrderId(order.getId());
        release.setRecipientUserId(request.recipientUserId());
        release.setAmount(amount);
        release.setCurrency("USD");
        release.setReleaseKey(request.releaseKey());
        release.setPayloadHash(fingerprint);
        release.setSimulation(true);
        release.setCreatedAt(now);
        release.setUpdatedAt(now);
        release.setStatus(BofaPayoutReleaseStatus.PROCESSING);
        releases.saveAndFlush(release);

        // An immutable user-scoped entitlement avoids inventing bank data or using
        // the legacy bank balance's artificial opening balance. No external call.
        BofaRecipientCredit credit = new BofaRecipientCredit();
        credit.setReleaseId(release.getId());
        credit.setCheckoutOrderId(order.getId());
        credit.setRecipientUserId(request.recipientUserId());
        credit.setAmount(amount);
        credit.setCurrency("USD");
        credit.setCreatedAt(now);
        credits.saveAndFlush(credit);

        release.setReleaseReference("sim-release-" + release.getId());
        release.setStatus(BofaPayoutReleaseStatus.SUCCEEDED);
        release.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        releases.saveAndFlush(release);
        return response(release);
    }

    @Override
    @Transactional(readOnly = true)
    public BofaPayoutReleaseResponse getByReleaseKey(String releaseKey) {
        if (!StringUtils.hasText(releaseKey) || releaseKey.length() > 100) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
        return releases.findByReleaseKey(releaseKey).map(this::response)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYOUT_RELEASE_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public BofaPayoutReleaseResponse getByCheckoutOrderId(UUID checkoutOrderId) {
        return releases.findByCheckoutOrderId(checkoutOrderId).map(this::response)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYOUT_RELEASE_NOT_FOUND));
    }

    private BofaPayoutReleaseResponse response(BofaPayoutRelease release) {
        return new BofaPayoutReleaseResponse(release.getId(), release.getReleaseKey(),
                release.getCheckoutOrderId(), release.getRecipientUserId(), release.getStatus().name(),
                release.getAmount(), release.getCurrency(), release.isSimulation(),
                release.getReleaseReference(), false, release.getCreatedAt().toInstant(ZoneOffset.UTC),
                release.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    private void validate(CreatePayoutReleaseRequest request) {
        if (request == null || request.checkoutOrderId() == null || request.recipientUserId() == null
                || !StringUtils.hasText(request.releaseKey()) || request.releaseKey().length() > 100
                || !request.releaseKey().equals(request.releaseKey().trim())
                || request.expectedAmount() == null || request.expectedAmount().amount() == null
                || request.expectedAmount().amount().signum() <= 0
                || request.expectedAmount().amount().scale() > 2
                || request.expectedAmount().amount().precision() - request.expectedAmount().amount().scale() > 17
                || !StringUtils.hasText(request.expectedAmount().currency())
                || request.expectedAmount().currency().length() != 3) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
