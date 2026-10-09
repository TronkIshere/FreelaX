package com.marketplace.backend.entity;

public enum ExchangeRateSource {
    LIVE_OPEN_ER_API,
    LIVE_COINGECKO,
    FALLBACK_PLACEHOLDER,
    /** Unified rail: the USDC/VND quote locked on the withdrawal and confirmed by the payout statement. */
    LOCKED_PAYOUT_QUOTE
}