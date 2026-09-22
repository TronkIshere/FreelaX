package com.paypal.backend.service;

import java.math.BigDecimal;

public interface PaypalFeeCalculatorService {

    FeeCalculationResult calculate(BigDecimal grossAmountUsd, BigDecimal midMarketRate);

    record FeeCalculationResult(
            BigDecimal commercialFeeUsd,
            BigDecimal fxSpreadCostUsd,
            BigDecimal netUsdAfterFees,
            BigDecimal netVnd,
            BigDecimal effectiveFeeRatePercent
    ) {
    }
}
