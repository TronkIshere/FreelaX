package com.paypal.backend.service;

import com.paypal.backend.dto.request.paypal.ReleasePayoutRequest;
import com.paypal.backend.dto.response.paypal.PaypalPayoutReleaseResponse;

import java.util.UUID;

public interface PaypalPayoutReleaseService {

    PaypalPayoutReleaseResponse release(ReleasePayoutRequest request);

    PaypalPayoutReleaseResponse getById(UUID id);
}
