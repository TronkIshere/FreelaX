package com.payment.backend.service;
import com.payment.backend.dto.request.bofa.CreateCheckoutRefundRequest;
import com.payment.backend.dto.response.bofa.BofaCheckoutRefundResponse;
public interface BofaCheckoutRefundService {
    BofaCheckoutRefundResponse refund(CreateCheckoutRefundRequest request);
    BofaCheckoutRefundResponse getByRefundKey(String key);
}
