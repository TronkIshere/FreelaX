package com.paypal.backend.dto.response.paypal;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaypalFeeBreakdown {
    BigDecimal commercialFeeUsd;
    BigDecimal fxSpreadCostUsd;
    BigDecimal netUsdAfterFees;
    BigDecimal effectiveFeeRatePercent;
}
