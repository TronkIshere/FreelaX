package com.paypal.backend.service.impl;

import com.paypal.backend.client.PaypalPayoutClient;
import com.paypal.backend.dto.request.paypal.ReleasePayoutRequest;
import com.paypal.backend.dto.response.paypal.PaypalPayoutReleaseResponse;
import com.paypal.backend.entity.PaypalCheckoutOrder;
import com.paypal.backend.entity.PaypalCheckoutOrderStatus;
import com.paypal.backend.entity.PaypalPayee;
import com.paypal.backend.entity.PaypalPayoutRelease;
import com.paypal.backend.entity.PaypalPayoutReleaseStatus;
import com.paypal.backend.exception.ApplicationException;
import com.paypal.backend.exception.ErrorCode;
import com.paypal.backend.repository.PaypalCheckoutOrderRepository;
import com.paypal.backend.repository.PaypalPayeeRepository;
import com.paypal.backend.repository.PaypalPayoutReleaseRepository;
import com.paypal.backend.service.PaypalPayoutReleaseService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaypalPayoutReleaseServiceImpl implements PaypalPayoutReleaseService {

    PaypalPayoutReleaseRepository paypalPayoutReleaseRepository;
    PaypalCheckoutOrderRepository paypalCheckoutOrderRepository;
    PaypalPayeeRepository paypalPayeeRepository;
    PaypalPayoutClient paypalPayoutClient;

    @Override
    @Transactional
    public PaypalPayoutReleaseResponse release(ReleasePayoutRequest request) {
        PaypalCheckoutOrder checkoutOrder = paypalCheckoutOrderRepository.findById(request.getCheckoutOrderId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.CHECKOUT_ORDER_NOT_FOUND, request.getCheckoutOrderId()));

        if (checkoutOrder.getStatus() != PaypalCheckoutOrderStatus.CAPTURED) {
            throw new ApplicationException(ErrorCode.INVALID_CHECKOUT_ORDER_STATUS, checkoutOrder.getStatus());
        }

        if (paypalPayoutReleaseRepository.existsByCheckoutOrderId(checkoutOrder.getId())) {
            throw new ApplicationException(ErrorCode.PAYOUT_ALREADY_RELEASED, checkoutOrder.getId());
        }

        PaypalPayee payee = paypalPayeeRepository.findById(checkoutOrder.getPayeeId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYEE_NOT_FOUND, checkoutOrder.getPayeeId()));

        if (!payee.isActive()) {
            throw new ApplicationException(ErrorCode.PAYEE_NOT_ACTIVE, payee.getId());
        }

        Map<String, Object> payout = paypalPayoutClient.sendPayout(
                payee.getPaypalEmail(),
                checkoutOrder.getAmountUsd(),
                checkoutOrder.getId().toString()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> batchHeader = (Map<String, Object>) payout.get("batch_header");
        String batchId = batchHeader != null ? (String) batchHeader.get("payout_batch_id") : null;
        String batchStatus = batchHeader != null ? (String) batchHeader.get("batch_status") : null;

        PaypalPayoutRelease entity = new PaypalPayoutRelease();
        entity.setCheckoutOrderId(checkoutOrder.getId());
        entity.setPayeeId(payee.getId());
        entity.setJobId(checkoutOrder.getJobId());
        entity.setAmountUsd(checkoutOrder.getAmountUsd());
        entity.setPaypalPayoutBatchId(batchId);
        entity.setStatus(mapStatus(batchStatus));
        entity.setCreatedAt(LocalDateTime.now());

        if (entity.getStatus() == PaypalPayoutReleaseStatus.SUCCESS) {
            entity.setReleasedAt(LocalDateTime.now());
        }

        if (entity.getStatus() == PaypalPayoutReleaseStatus.FAILED) {
            paypalPayoutReleaseRepository.save(entity);
            throw new ApplicationException(ErrorCode.PAYPAL_PAYOUT_FAILED, batchId);
        }

        paypalPayoutReleaseRepository.save(entity);

        return toResponse(entity);
    }

    @Override
    public PaypalPayoutReleaseResponse getById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    private PaypalPayoutRelease getOrThrow(UUID id) {
        return paypalPayoutReleaseRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYOUT_RELEASE_NOT_FOUND, id));
    }

    private PaypalPayoutReleaseStatus mapStatus(String batchStatus) {
        if (batchStatus == null) {
            return PaypalPayoutReleaseStatus.PENDING;
        }
        return switch (batchStatus) {
            case "SUCCESS" -> PaypalPayoutReleaseStatus.SUCCESS;
            case "DENIED" -> PaypalPayoutReleaseStatus.FAILED;
            default -> PaypalPayoutReleaseStatus.PENDING;
        };
    }

    private PaypalPayoutReleaseResponse toResponse(PaypalPayoutRelease entity) {
        return PaypalPayoutReleaseResponse.builder()
                .id(entity.getId())
                .checkoutOrderId(entity.getCheckoutOrderId())
                .payeeId(entity.getPayeeId())
                .jobId(entity.getJobId())
                .amountUsd(entity.getAmountUsd())
                .paypalPayoutBatchId(entity.getPaypalPayoutBatchId())
                .paypalPayoutItemId(entity.getPaypalPayoutItemId())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .releasedAt(entity.getReleasedAt())
                .build();
    }
}
