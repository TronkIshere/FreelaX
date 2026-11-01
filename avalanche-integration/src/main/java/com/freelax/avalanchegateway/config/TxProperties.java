package com.freelax.avalanchegateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Transaction tracking and submission settings. Persistence and submission are opt-in (default off) so a service that
 * only verifies its environment (M1) or only holds signers (M2) needs no database.
 * Fee, bump and grace defaults are conservative placeholders marked "to calibrate": Avalanche-native values need a
 * funded Fuji wallet, which is not available (Phase 1 blocker).
 */
@ConfigurationProperties(prefix = "avalanche.tx")
public record TxProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("5000000") long maxGasLimit,
        @DefaultValue("200") long maxFeeCapGwei,
        @DefaultValue("1") long priorityFeeGwei,
        @DefaultValue("30") int feeBumpPercent,
        @DefaultValue("1") int confirmations,
        @DefaultValue("30000") long replaceGraceMs,
        @DefaultValue("2") int replaceObservations,
        @DefaultValue("0") long reconcileIntervalMs) {
}
