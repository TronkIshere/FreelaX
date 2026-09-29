package com.marketplace.backend.provider.offchain;

import com.marketplace.backend.provider.currency.ExchangeRateProvider;
import com.marketplace.backend.provider.currency.ExchangeRateResult;
import com.marketplace.backend.provider.currency.OffRampProvider;
import com.marketplace.backend.provider.currency.OffRampResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Component
public class OffChainOffRampProvider implements OffRampProvider {

    private final ExchangeRateProvider exchangeRateProvider;

    @Value("${offramp.fee-rate:0.003}")
    private BigDecimal feeRate;

    public OffChainOffRampProvider(ExchangeRateProvider exchangeRateProvider) {
        this.exchangeRateProvider = exchangeRateProvider;
    }

    @Override
    public OffRampResult convertUsdcToVnd(UUID reference, BigDecimal amountUsdc) {
        ExchangeRateResult rate = exchangeRateProvider.getUsdcToVndRate();

        BigDecimal amountVndGross = amountUsdc
                .multiply(rate.rate())
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal feeVnd = amountVndGross
                .multiply(feeRate)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal amountVndNet = amountVndGross.subtract(feeVnd);

        return new OffRampResult(
                amountUsdc,
                rate.rate(),
                rate.source(),
                rate.fetchedAt(),
                amountVndGross,
                feeVnd,
                amountVndNet,
                "offramp-job-" + reference
        );
    }
}
