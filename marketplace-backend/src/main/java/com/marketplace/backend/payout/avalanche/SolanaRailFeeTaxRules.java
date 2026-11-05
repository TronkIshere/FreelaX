package com.marketplace.backend.payout.avalanche;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Owner decision 2026-11-05 (payout): fee and tax of an Avalanche payout reuse the EXISTING Solana-rail rules, unchanged and
 * labelled simulated. No new formula is defined here; each method restates one existing rule and names its source:
 * <ul>
 *   <li>platform fee: {@code PaymentFlowService} ({@code budgetUsd * 0.03}, 2 decimals, HALF_UP);</li>
 *   <li>tax rate: {@code PayoutServiceImpl.createRecord} (USD/VND rate from the exchange-rate provider, 2 decimals, HALF_UP);</li>
 *   <li>taxable income: {@code PayoutServiceImpl.createRecord} ({@code budgetUsd * rate}, 0 decimals, HALF_UP).</li>
 * </ul>
 * The withheld tax itself is not computed by FreelaX on either rail (MISA reports it); it is not computed here either.
 */
public final class SolanaRailFeeTaxRules {

    /** Label stored on every payout computed with these rules. */
    public static final String TREATMENT = "SOLANA_RAIL_RULES_SIMULATED";
    static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.03");

    private SolanaRailFeeTaxRules() {
    }

    public static BigDecimal platformFeeUsd(BigDecimal budgetUsd) {
        return budgetUsd.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal storedTaxRate(BigDecimal usdToVndRate) {
        return usdToVndRate.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal taxableAmountVnd(BigDecimal budgetUsd, BigDecimal storedTaxRate) {
        return budgetUsd.multiply(storedTaxRate).setScale(0, RoundingMode.HALF_UP);
    }
}
