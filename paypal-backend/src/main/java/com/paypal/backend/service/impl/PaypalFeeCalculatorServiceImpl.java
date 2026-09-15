package com.paypal.backend.service.impl;

import com.paypal.backend.configuration.PaypalFeeProperties;
import com.paypal.backend.service.PaypalFeeCalculatorService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaypalFeeCalculatorServiceImpl implements PaypalFeeCalculatorService {

    private static final int USD_SCALE = 2;
    private static final int VND_SCALE = 0;
    private static final int PERCENT_SCALE = 2;

    PaypalFeeProperties paypalFeeProperties;

    @Override
    public FeeCalculationResult calculate(BigDecimal grossAmountUsd, BigDecimal midMarketRate) {
        BigDecimal commercialFeeRate = BigDecimal.valueOf(paypalFeeProperties.getCommercialFeeRate());
        BigDecimal fixedFeeUsd = BigDecimal.valueOf(paypalFeeProperties.getFixedFeeUsd());
        BigDecimal fxSpreadRate = BigDecimal.valueOf(paypalFeeProperties.getFxSpreadRate());

        BigDecimal commercialFeeUsd = grossAmountUsd.multiply(commercialFeeRate)
                .add(fixedFeeUsd)
                .setScale(USD_SCALE, RoundingMode.HALF_UP);

        BigDecimal netUsdAfterTransactionFee = grossAmountUsd.subtract(commercialFeeUsd);

        BigDecimal fxSpreadCostUsd = netUsdAfterTransactionFee.multiply(fxSpreadRate)
                .setScale(USD_SCALE, RoundingMode.HALF_UP);

        BigDecimal netUsdAfterFees = netUsdAfterTransactionFee.subtract(fxSpreadCostUsd);

        BigDecimal netVnd = netUsdAfterFees.multiply(midMarketRate)
                .setScale(VND_SCALE, RoundingMode.HALF_UP);

        BigDecimal grossVnd = grossAmountUsd.multiply(midMarketRate);

        BigDecimal effectiveFeeRatePercent = BigDecimal.ONE
                .subtract(netVnd.divide(grossVnd, 10, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);

        return new FeeCalculationResult(commercialFeeUsd, fxSpreadCostUsd, netUsdAfterFees, netVnd, effectiveFeeRatePercent);
    }
}
