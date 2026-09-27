package com.marketplace.backend.provider.currency;

import com.marketplace.backend.entity.ExchangeRateSource;

import java.math.BigDecimal;

public record OffRampResult(
        BigDecimal amountUsdcInput,
        BigDecimal usdcToVndRate,
        ExchangeRateSource rateSource,
        BigDecimal amountVndGross,
        BigDecimal feeVnd,
        BigDecimal amountVndNet,
        String payoutReference
) {}