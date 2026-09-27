package com.marketplace.backend.provider.currency;

import com.marketplace.backend.entity.ExchangeRateSource;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeRateResult(
        BigDecimal rate,
        ExchangeRateSource source,
        Instant fetchedAt
) {}