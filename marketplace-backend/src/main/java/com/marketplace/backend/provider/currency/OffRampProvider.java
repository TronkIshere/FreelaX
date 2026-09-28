package com.marketplace.backend.provider.currency;

import java.math.BigDecimal;
import java.util.UUID;

public interface OffRampProvider {

    OffRampResult convertUsdcToVnd(UUID reference, BigDecimal amountUsdc);
}