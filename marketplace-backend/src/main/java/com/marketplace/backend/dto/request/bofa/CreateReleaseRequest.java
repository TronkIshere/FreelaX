package com.marketplace.backend.dto.request.bofa;

import java.util.UUID;

public record CreateReleaseRequest(UUID checkoutOrderId, UUID recipientUserId,
                                   ExpectedAmount expectedAmount, String releaseKey) {
    public record ExpectedAmount(String amount, String currency) {}
}
