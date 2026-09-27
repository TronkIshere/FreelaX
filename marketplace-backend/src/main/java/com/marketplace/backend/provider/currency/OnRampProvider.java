package com.marketplace.backend.provider.currency;

import java.math.BigDecimal;

public interface OnRampProvider {

    OnRampResult convertUsdToUsdc(BigDecimal amountUsd);
}