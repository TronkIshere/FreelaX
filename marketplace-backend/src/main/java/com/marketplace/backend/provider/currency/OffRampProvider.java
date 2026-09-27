package com.marketplace.backend.provider.currency;

import java.math.BigDecimal;

public interface OffRampProvider {

    OffRampResult convertUsdcToVnd(BigDecimal amountUsdc, BigDecimal usdcToVndRate);
}