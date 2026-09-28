package com.marketplace.backend.dto.request.solana;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateInvoiceRequest {
    String freelancer;
    String invoiceId;
    String client;
    String amount;
    String rateId;
    String expiresAt;
    String mode;
    String commitment;
    Boolean skipPreflight;
}
