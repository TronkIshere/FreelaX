package com.payment.backend.service.impl;

import com.payment.backend.client.BofaCheckoutClient;
import com.payment.backend.dto.request.bofa.CreateCheckoutOrderRequest;
import com.payment.backend.dto.response.bofa.BofaCheckoutOrderResponse;
import com.payment.backend.entity.BofaAccountRole;
import com.payment.backend.entity.BofaCheckoutOrder;
import com.payment.backend.entity.BofaCheckoutOrderStatus;
import com.payment.backend.exception.ApplicationException;
import com.payment.backend.exception.ErrorCode;
import com.payment.backend.repository.BofaCheckoutOrderRepository;
import com.payment.backend.service.BofaAccountBalanceService;
import com.payment.backend.service.BofaCheckoutOrderService;
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
public class BofaCheckoutOrderServiceImpl implements BofaCheckoutOrderService {

    BofaCheckoutOrderRepository bofaCheckoutOrderRepository;
    BofaCheckoutClient bofaCheckoutClient;
    BofaAccountBalanceService bofaAccountBalanceService;

    @Override
    @Transactional
    public BofaCheckoutOrderResponse create(CreateCheckoutOrderRequest request) {
        Map<String, Object> order = bofaCheckoutClient.createOrder(request.getAmountUsd());

        String bofaOrderId = (String) order.get("id");
        String approvalUrl = extractApprovalUrl(order);

        BofaCheckoutOrder entity = new BofaCheckoutOrder();
        entity.setPayerUserId(request.getPayerUserId());
        entity.setJobId(request.getJobId());
        entity.setAmountUsd(request.getAmountUsd());
        entity.setBofaOrderId(bofaOrderId);
        entity.setStatus(BofaCheckoutOrderStatus.CREATED);
        entity.setCreatedAt(LocalDateTime.now());

        bofaCheckoutOrderRepository.save(entity);

        return toResponse(entity, approvalUrl);
    }

    @Override
    @Transactional
    public BofaCheckoutOrderResponse capture(UUID orderId) {
        BofaCheckoutOrder entity = getOrThrow(orderId);

        if (entity.getStatus() != BofaCheckoutOrderStatus.CREATED) {
            throw new ApplicationException(ErrorCode.INVALID_CHECKOUT_ORDER_STATUS, entity.getStatus());
        }

        Map<String, Object> capture = bofaCheckoutClient.captureOrder(entity.getBofaOrderId());
        String status = (String) capture.get("status");

        if (!"COMPLETED".equals(status)) {
            entity.setStatus(BofaCheckoutOrderStatus.FAILED);
            bofaCheckoutOrderRepository.save(entity);
            throw new ApplicationException(ErrorCode.BOFA_ORDER_FAILED, entity.getBofaOrderId());
        }

        entity.setBofaCaptureId((String) capture.get("id"));
        entity.setStatus(BofaCheckoutOrderStatus.CAPTURED);
        entity.setCapturedAt(LocalDateTime.now());
        bofaCheckoutOrderRepository.save(entity);

        bofaAccountBalanceService.credit(entity.getPayerUserId(), BofaAccountRole.PAYER, entity.getAmountUsd());

        return toResponse(entity, null);
    }

    @Override
    public BofaCheckoutOrderResponse getById(UUID orderId) {
        return toResponse(getOrThrow(orderId), null);
    }

    private BofaCheckoutOrder getOrThrow(UUID orderId) {
        return bofaCheckoutOrderRepository.findById(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CHECKOUT_ORDER_NOT_FOUND, orderId));
    }

    @SuppressWarnings("unchecked")
    private String extractApprovalUrl(Map<String, Object> order) {
        List<Map<String, Object>> links = (List<Map<String, Object>>) (List<?>) order.get("links");
        if (links == null) {
            return null;
        }
        for (Map<String, Object> link : links) {
            if ("approve".equals(link.get("rel"))) {
                return (String) link.get("href");
            }
        }
        return null;
    }

    private BofaCheckoutOrderResponse toResponse(BofaCheckoutOrder entity, String approvalUrl) {
        return BofaCheckoutOrderResponse.builder()
                .id(entity.getId())
                .payerUserId(entity.getPayerUserId())
                .jobId(entity.getJobId())
                .amountUsd(entity.getAmountUsd())
                .bofaOrderId(entity.getBofaOrderId())
                .bofaCaptureId(entity.getBofaCaptureId())
                .status(entity.getStatus().name())
                .approvalUrl(approvalUrl)
                .createdAt(entity.getCreatedAt())
                .capturedAt(entity.getCapturedAt())
                .build();
    }
}