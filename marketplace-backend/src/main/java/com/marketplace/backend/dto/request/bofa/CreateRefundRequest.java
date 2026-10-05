package com.marketplace.backend.dto.request.bofa;
import java.util.UUID;
public record CreateRefundRequest(UUID checkoutOrderId, ExpectedAmount expectedAmount, String refundKey) {
    public record ExpectedAmount(String amount, String currency) { }
}
