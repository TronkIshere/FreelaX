package com.freelax.avalanchegateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Log scanning is opt-in and needs persistence (avalanche.tx.enabled=true). maxLogRange 0 means "use the provider profile";
 * a positive value can only lower it. wsUrl is optional and used only for latency hints.
 */
@ConfigurationProperties(prefix = "avalanche.scan")
public record ScanProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String contractAddress,
        @DefaultValue("0") long startBlock,
        @DefaultValue("1") int confirmations,
        @DefaultValue("0") long maxLogRange,
        @DefaultValue("0") long intervalMs,
        @DefaultValue("") String wsUrl) {
}
