package com.payment.backend.service;

import com.payment.backend.dto.request.bofa.CreateCheckoutOrderRequest;
import com.payment.backend.dto.response.bofa.BofaCheckoutOrderResponse;

import java.util.UUID;

public interface BofaCheckoutOrderService {

    BofaCheckoutOrderResponse create(CreateCheckoutOrderRequest request);

    BofaCheckoutOrderResponse capture(UUID orderId);

    BofaCheckoutOrderResponse getById(UUID orderId);
}
