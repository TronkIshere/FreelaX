package com.freelax.avalanchegateway.scan;

import java.util.Locale;

/**
 * Per-provider limits for eth_getLogs, measured in Phase 1 (never assume 2,000 blocks): the official Fuji endpoint caps a
 * range at 100 blocks, the publicnode endpoint allows 50,000 but requires an address filter. Unknown hosts get the
 * conservative default and the scanner halves the range on any refusal.
 */
public record ProviderProfile(long maxLogRange, boolean requireAddress, long minGapMs) {

    public static final ProviderProfile CONSERVATIVE = new ProviderProfile(100, true, 1000);
    public static final ProviderProfile LOCAL = new ProviderProfile(2000, false, 0);

    public static ProviderProfile forHost(String host, boolean local) {
        if (local) {
            return LOCAL;
        }
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (h.equals("avalanche-fuji-c-chain-rpc.publicnode.com")) {
            return new ProviderProfile(50_000, true, 1000);
        }
        return CONSERVATIVE;
    }
}
