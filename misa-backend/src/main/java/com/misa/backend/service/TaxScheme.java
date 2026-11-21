package com.misa.backend.service;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Withholding scheme of a payout transaction (owner decision 2026-11-21). A payout without a scheme keeps the configured
 * personal-income-tax withholding rate (legacy behaviour, unchanged). {@link #DECREE_117_SERVICES} models the platform
 * withholding on behalf of an individual service provider (Decree 117/2025/ND-CP): PIT 2% of revenue plus VAT 5% of revenue.
 * Simulation only: no figure here is a real tax filing.
 */
public final class TaxScheme {

    public static final String DECREE_117_SERVICES = "VN_DECREE_117_SERVICES";
    public static final BigDecimal DECREE_117_PIT_RATE = new BigDecimal("0.02");
    public static final BigDecimal DECREE_117_VAT_RATE = new BigDecimal("0.05");

    private TaxScheme() {
    }

    /** Null or blank means "no scheme" (legacy); any other value must be a known scheme. */
    public static String normalize(String scheme) {
        if (scheme == null || scheme.isBlank()) return null;
        String value = scheme.trim().toUpperCase(Locale.ROOT);
        if (!DECREE_117_SERVICES.equals(value)) throw new IllegalArgumentException("Unknown tax scheme: " + scheme);
        return value;
    }
}
