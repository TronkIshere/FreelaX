package com.marketplace.backend.provider.currency;

import java.math.BigDecimal;

public record OffRampResult(
        BigDecimal amountUsdcInput,
        BigDecimal usdcToVndRate,
        BigDecimal amountVndGross,
        BigDecimal feeVnd,
        BigDecimal amountVndNet
) {}