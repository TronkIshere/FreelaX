package com.freelax.avalanchegateway.config;

import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** No default for the environment and no property that can add a chain id or enable another environment. */
@ConfigurationProperties(prefix = "avalanche")
public record AvalancheProperties(AvalancheEnvironment environment, Rpc rpc, Signers signers, String deploymentManifest,
                                  String internalApiKey) {

    /** Development signers are opt-in and exist only in LOCAL_SIMULATION; enabling them elsewhere fails startup. */
    public record Signers(boolean enabled) {
    }

    public boolean signersEnabled() {
        return signers != null && signers.enabled();
    }

    public record Rpc(Provider primary, Provider secondary, int timeoutMs, int minGapMs) {
    }

    public record Provider(String url) {
    }

    public String primaryUrl() {
        return rpc == null || rpc.primary() == null ? null : rpc.primary().url();
    }

    public String secondaryUrl() {
        String url = rpc == null || rpc.secondary() == null ? null : rpc.secondary().url();
        return url == null || url.isBlank() ? null : url;
    }

    public int timeoutMs() {
        return rpc == null || rpc.timeoutMs() <= 0 ? 10_000 : rpc.timeoutMs();
    }

    public int minGapMs() {
        return rpc == null || rpc.minGapMs() < 0 ? 1_000 : rpc.minGapMs();
    }
}
