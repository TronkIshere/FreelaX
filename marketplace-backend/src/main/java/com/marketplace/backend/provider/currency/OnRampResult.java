package com.marketplace.backend.provider.currency;

import java.math.BigDecimal;

public record OnRampResult(
        BigDecimal amountUsdSource,
        BigDecimal amountUsdcGross,
        BigDecimal feeUsdc,
        BigDecimal amountUsdcNet
) {}