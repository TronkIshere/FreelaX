package com.marketplace.backend.dto.response.solana;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class MockOnrampReceiptResult {
    @JsonAlias({"pda", "receipt", "receiptPda"})
    String address;
    String purchaseId;
    String client;
    @JsonAlias({"clientUsdcAta", "clientTokenAccount"})
    String clientAta;
    String mint;
    String treasury;
    String usdAmountE6;
    @JsonAlias({"tokenAmountBaseUnits", "tokenAmountE6"})
    String tokenAmount;
    String authority;
}