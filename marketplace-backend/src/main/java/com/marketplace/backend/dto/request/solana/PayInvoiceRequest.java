package com.marketplace.backend.dto.request.solana;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PayInvoiceRequest {
    String client;
    String mode;
    String commitment;
    Boolean skipPreflight;
}
