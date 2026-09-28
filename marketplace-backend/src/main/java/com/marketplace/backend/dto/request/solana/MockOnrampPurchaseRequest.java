package com.marketplace.backend.dto.request.solana;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MockOnrampPurchaseRequest {
    String onrampAuthority;
    String client;
    String purchaseId;
    String usdAmountE6;
    String mode;
    String commitment;
    boolean skipPreflight;
}