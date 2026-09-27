package com.marketplace.backend.provider.currency;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeRateResult(
        BigDecimal rate,
        RateSource source,
        Instant fetchedAt
) {
    public enum RateSource {
        LIVE_COINGECKO,
        FALLBACK_PLACEHOLDER
    }
}