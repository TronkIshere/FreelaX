package com.payment.backend.service;

import com.payment.backend.dto.request.bofa.CreatePayoutReleaseRequest;
import com.payment.backend.dto.response.bofa.BofaPayoutReleaseResponse;
import java.util.UUID;

public interface BofaPayoutReleaseService {
    BofaPayoutReleaseResponse release(CreatePayoutReleaseRequest request);
    BofaPayoutReleaseResponse getByReleaseKey(String releaseKey);
    BofaPayoutReleaseResponse getByCheckoutOrderId(UUID checkoutOrderId);
}
