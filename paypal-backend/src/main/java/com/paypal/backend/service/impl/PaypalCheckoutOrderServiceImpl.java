package com.paypal.backend.service.impl;

import com.paypal.backend.client.PaypalCheckoutClient;
import com.paypal.backend.dto.request.paypal.CreateCheckoutOrderRequest;
import com.paypal.backend.dto.response.paypal.PaypalCheckoutOrderResponse;
import com.paypal.backend.entity.PaypalCheckoutOrder;
import com.paypal.backend.entity.PaypalCheckoutOrderStatus;
import com.paypal.backend.exception.ApplicationException;
import com.paypal.backend.exception.ErrorCode;
import com.paypal.backend.repository.PaypalCheckoutOrderRepository;
import com.paypal.backend.repository.PaypalPayeeRepository;
import com.paypal.backend.service.PaypalCheckoutOrderService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaypalCheckoutOrderServiceImpl implements PaypalCheckoutOrderService {

    PaypalCheckoutOrderRepository paypalCheckoutOrderRepository;
    PaypalPayeeRepository paypalPayeeRepository;
    PaypalCheckoutClient paypalCheckoutClient;

    @Override
    @Transactional
    public PaypalCheckoutOrderResponse create(UUID createdByUserId, CreateCheckoutOrderRequest request) {
        if (!paypalPayeeRepository.existsById(request.getPayeeId())) {
            throw new ApplicationException(ErrorCode.PAYEE_NOT_FOUND, request.getPayeeId());
        }

        Map<String, Object> order = paypalCheckoutClient.createOrder(request.getAmountUsd());

        String paypalOrderId = (String) order.get("id");
        String approvalUrl = null;
        List<Map<String, Object>> links = (List<Map<String, Object>>) (List) order.get("links");
        for (Map<String, Object> link : links) {
            if ("approve".equals(link.get("rel"))) {
                approvalUrl = (String) link.get("href");
            }
        }

        PaypalCheckoutOrder entity = new PaypalCheckoutOrder();
        entity.setCreatedByUserId(createdByUserId);
        entity.setPayeeId(request.getPayeeId());
        entity.setReferenceId(request.getReferenceId());
        entity.setAmountUsd(request.getAmountUsd());
        entity.setPaypalOrderId(paypalOrderId);
        entity.setStatus(PaypalCheckoutOrderStatus.CREATED);
        entity.setCreatedAt(LocalDateTime.now());

        paypalCheckoutOrderRepository.save(entity);

        return toResponse(entity, approvalUrl);
    }

    @Override
    @Transactional
    public PaypalCheckoutOrderResponse capture(UUID createdByUserId, UUID orderId) {
        PaypalCheckoutOrder entity = getOwnedOrThrow(createdByUserId, orderId);

        if (entity.getStatus() != PaypalCheckoutOrderStatus.CREATED) {
            throw new ApplicationException(ErrorCode.INVALID_CHECKOUT_ORDER_STATUS);
        }

        Map<String, Object> capture = paypalCheckoutClient.captureOrder(entity.getPaypalOrderId());
        String status = (String) capture.get("status");

        if (!"COMPLETED".equals(status)) {
            entity.setStatus(PaypalCheckoutOrderStatus.FAILED);
            paypalCheckoutOrderRepository.save(entity);
            throw new ApplicationException(ErrorCode.PAYPAL_ORDER_FAILED, entity.getPaypalOrderId());
        }

        entity.setPaypalCaptureId((String) capture.get("id"));
        entity.setStatus(PaypalCheckoutOrderStatus.CAPTURED);
        entity.setCapturedAt(LocalDateTime.now());
        paypalCheckoutOrderRepository.save(entity);

        return toResponse(entity, null);
    }

    @Override
    public PaypalCheckoutOrderResponse getByIdForOwner(UUID createdByUserId, UUID orderId) {
        return toResponse(getOwnedOrThrow(createdByUserId, orderId), null);
    }

    private PaypalCheckoutOrder getOwnedOrThrow(UUID createdByUserId, UUID orderId) {
        return paypalCheckoutOrderRepository.findById(orderId)
                .filter(o -> o.getCreatedByUserId().equals(createdByUserId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.CHECKOUT_ORDER_NOT_FOUND, orderId));
    }

    private PaypalCheckoutOrderResponse toResponse(PaypalCheckoutOrder entity, String approvalUrl) {
        return PaypalCheckoutOrderResponse.builder()
                .id(entity.getId())
                .payeeId(entity.getPayeeId())
                .referenceId(entity.getReferenceId())
                .amountUsd(entity.getAmountUsd())
                .paypalOrderId(entity.getPaypalOrderId())
                .paypalCaptureId(entity.getPaypalCaptureId())
                .status(entity.getStatus().name())
                .approvalUrl(approvalUrl)
                .createdAt(entity.getCreatedAt())
                .capturedAt(entity.getCapturedAt())
                .build();
    }
}
