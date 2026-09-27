package com.payment.backend.service.impl;

import com.payment.backend.dto.request.bofa.CreateCheckoutOrderRequest;
import com.payment.backend.dto.response.bofa.BofaCheckoutOrderResponse;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BofaCheckoutOrderServiceImpl implements BofaCheckoutOrderService {

    BofaCheckoutOrderRepository bofaCheckoutOrderRepository;
    BofaAccountBalanceService bofaAccountBalanceService;

    @Override
    @Transactional
    public BofaCheckoutOrderResponse create(CreateCheckoutOrderRequest request) {
        BofaCheckoutOrder entity = new BofaCheckoutOrder();
        entity.setPayerUserId(request.getPayerUserId());
        entity.setJobId(request.getJobId());
        entity.setAmountUsd(request.getAmountUsd());
        entity.setBofaOrderId(UUID.randomUUID().toString());
        entity.setPayerBankCode(request.getPayerBankCode());
        entity.setPayerBankAccountNumber(request.getPayerBankAccountNumber());
        entity.setPayerBankAccountHolderName(request.getPayerBankAccountHolderName());
        entity.setStatus(BofaCheckoutOrderStatus.CREATED);
        entity.setCreatedAt(LocalDateTime.now());

        bofaCheckoutOrderRepository.save(entity);

        return toResponse(entity);
    }

    @Override
    @Transactional
    public BofaCheckoutOrderResponse capture(UUID orderId) {
        BofaCheckoutOrder entity = getOrThrow(orderId);

        if (entity.getStatus() != BofaCheckoutOrderStatus.CREATED) {
            throw new ApplicationException(ErrorCode.INVALID_CHECKOUT_ORDER_STATUS, entity.getStatus());
        }

        bofaAccountBalanceService.debit(entity.getPayerBankAccountNumber(), entity.getAmountUsd());

        entity.setBofaCaptureId(UUID.randomUUID().toString());
        entity.setStatus(BofaCheckoutOrderStatus.CAPTURED);
        entity.setCapturedAt(LocalDateTime.now());
        bofaCheckoutOrderRepository.save(entity);

        return toResponse(entity);
    }

    @Override
    public BofaCheckoutOrderResponse getById(UUID orderId) {
        return toResponse(getOrThrow(orderId));
    }

    private BofaCheckoutOrder getOrThrow(UUID orderId) {
        return bofaCheckoutOrderRepository.findById(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CHECKOUT_ORDER_NOT_FOUND, orderId));
    }

    private BofaCheckoutOrderResponse toResponse(BofaCheckoutOrder entity) {
        return BofaCheckoutOrderResponse.builder()
                .id(entity.getId())
                .payerUserId(entity.getPayerUserId())
                .jobId(entity.getJobId())
                .amountUsd(entity.getAmountUsd())
                .bofaOrderId(entity.getBofaOrderId())
                .bofaCaptureId(entity.getBofaCaptureId())
                .payerBankCode(entity.getPayerBankCode())
                .payerBankAccountNumber(entity.getPayerBankAccountNumber())
                .payerBankAccountHolderName(entity.getPayerBankAccountHolderName())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .capturedAt(entity.getCapturedAt())
                .build();
    }
}