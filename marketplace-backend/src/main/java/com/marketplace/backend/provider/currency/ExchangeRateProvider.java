package com.marketplace.backend.provider.currency;

public interface ExchangeRateProvider {

    ExchangeRateResult getUsdToVndRate();

    ExchangeRateResult getUsdcToVndRate();
}