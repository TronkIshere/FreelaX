package com.marketplace.backend.provider.currency;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class MockOffRampProvider implements OffRampProvider {

    @Value("${offramp.fee-rate:0.003}")
    private BigDecimal feeRate;

    @Override
    public OffRampResult convertUsdcToVnd(BigDecimal amountUsdc, BigDecimal usdcToVndRate) {
        BigDecimal amountVndGross = amountUsdc
                .multiply(usdcToVndRate)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal feeVnd = amountVndGross
                .multiply(feeRate)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal amountVndNet = amountVndGross.subtract(feeVnd);

        return new OffRampResult(amountUsdc, usdcToVndRate, amountVndGross, feeVnd, amountVndNet);
    }
}