package com.marketplace.backend.provider.currency;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class MockOnRampProvider implements OnRampProvider {

    private static final int USDC_SCALE = 6;
    private static final BigDecimal USD_TO_USDC_PEG_RATE = BigDecimal.ONE;

    @Value("${onramp.fee-rate:0.005}")
    private BigDecimal feeRate;

    @Override
    public OnRampResult convertUsdToUsdc(BigDecimal amountUsd) {
        BigDecimal amountUsdcGross = amountUsd
                .multiply(USD_TO_USDC_PEG_RATE)
                .setScale(USDC_SCALE, RoundingMode.HALF_UP);

        BigDecimal feeUsdc = amountUsdcGross
                .multiply(feeRate)
                .setScale(USDC_SCALE, RoundingMode.HALF_UP);

        BigDecimal amountUsdcNet = amountUsdcGross.subtract(feeUsdc);

        return new OnRampResult(amountUsd, amountUsdcGross, feeUsdc, amountUsdcNet);
    }
}