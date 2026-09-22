package com.paypal.backend.service;

import com.paypal.backend.dto.response.paypal.PaypalPayeeStatusResponse;

import java.util.UUID;

public interface PaypalPayeeStatusService {

    PaypalPayeeStatusResponse getStatus(UUID userId);
}
