package com.paypal.backend.service;

import com.paypal.backend.dto.request.paypal.CreateCheckoutOrderRequest;
import com.paypal.backend.dto.response.paypal.PaypalCheckoutOrderResponse;

import java.util.UUID;

public interface PaypalCheckoutOrderService {

    PaypalCheckoutOrderResponse create(UUID createdByUserId, CreateCheckoutOrderRequest request);

    PaypalCheckoutOrderResponse capture(UUID createdByUserId, UUID orderId);

    PaypalCheckoutOrderResponse getByIdForOwner(UUID createdByUserId, UUID orderId);
}
