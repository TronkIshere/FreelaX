package com.marketplace.backend.provider.currency;

public interface ExchangeRateProvider {

    ExchangeRateResult getUsdcToVndRate();
}