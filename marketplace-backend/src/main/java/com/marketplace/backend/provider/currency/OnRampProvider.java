package com.marketplace.backend.provider.currency;

import java.math.BigDecimal;
import java.util.UUID;

public interface OnRampProvider {

    OnRampResult convertUsdToUsdc(UUID reference, BigDecimal amountUsd);
}