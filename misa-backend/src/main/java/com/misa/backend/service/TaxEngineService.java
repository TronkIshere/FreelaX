package com.misa.backend.service;

import java.math.BigDecimal;

public interface TaxEngineService {

    BigDecimal calculateTaxWithheld(BigDecimal taxableIncome);

    /** PIT withheld under a payout's scheme; {@code null} scheme is the configured legacy rate. */
    default BigDecimal calculateTaxWithheld(BigDecimal taxableIncome, String scheme) {
        if (scheme == null) return calculateTaxWithheld(taxableIncome);
        return taxableIncome.multiply(TaxScheme.DECREE_117_PIT_RATE).setScale(0, java.math.RoundingMode.HALF_UP);
    }

    /** VAT withheld on behalf of the provider; only under {@link TaxScheme#DECREE_117_SERVICES}, otherwise {@code null}. */
    default BigDecimal calculateVatWithheld(BigDecimal taxableIncome, String scheme) {
        if (scheme == null) return null;
        return taxableIncome.multiply(TaxScheme.DECREE_117_VAT_RATE).setScale(0, java.math.RoundingMode.HALF_UP);
    }
}
