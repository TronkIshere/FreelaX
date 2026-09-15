package com.paypal.backend.service;

import com.paypal.backend.dto.request.paypal.CreatePaypalPayeeRequest;
import com.paypal.backend.dto.response.paypal.PaypalPayeeResponse;

import java.util.UUID;

public interface PaypalPayeeService {

    PaypalPayeeResponse register(UUID userId, CreatePaypalPayeeRequest request);

    PaypalPayeeResponse getByUserId(UUID userId);

    PaypalPayeeResponse getById(UUID payeeId);
}
