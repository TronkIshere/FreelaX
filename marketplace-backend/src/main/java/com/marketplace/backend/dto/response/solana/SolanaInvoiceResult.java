package com.marketplace.backend.dto.response.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SolanaInvoiceResult {
    String address;
    String invoiceId;
    String freelancer;
    String client;
    String amount;
    String mint;
    String rateSnapshot;
    String expiresAt;
    String status;
    String createdAt;
    String paidAt;
}
