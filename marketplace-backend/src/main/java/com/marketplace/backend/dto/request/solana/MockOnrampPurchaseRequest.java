package com.marketplace.backend.dto.request.solana;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MockOnrampPurchaseRequest {
    String clientPublicKey;
    String purchaseId;
    String usdAmountE6;
    String idempotencyKey;
}