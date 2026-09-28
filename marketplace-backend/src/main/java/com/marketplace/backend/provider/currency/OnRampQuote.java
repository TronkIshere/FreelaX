package com.marketplace.backend.provider.currency;

import java.math.BigDecimal;

public record OnRampQuote(
        BigDecimal amountUsdSource,
        BigDecimal feeUsd,
        BigDecimal amountUsdNet,
        String usdAmountE6,
        String purchaseId
) {}