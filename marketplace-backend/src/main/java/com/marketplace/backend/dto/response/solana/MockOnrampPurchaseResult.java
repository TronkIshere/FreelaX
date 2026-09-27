package com.marketplace.backend.dto.response.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MockOnrampPurchaseResult {
    String status;
    String signature;
    String purchaseId;
    String receiptPda;
    String clientUsdcAta;
    String usdAmountE6;
    String tokenAmountBaseUnits;
    String slot;
}