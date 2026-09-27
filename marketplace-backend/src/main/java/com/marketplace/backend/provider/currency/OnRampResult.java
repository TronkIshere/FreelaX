package com.marketplace.backend.provider.currency;

import java.math.BigDecimal;

public record OnRampResult(
        BigDecimal amountUsdSource,
        BigDecimal feeUsd,
        BigDecimal amountUsdNet,
        BigDecimal amountUsdcReceived,
        String purchaseId,
        String transactionSignature,
        String clientUsdcAta,
        String receiptPda
) {}