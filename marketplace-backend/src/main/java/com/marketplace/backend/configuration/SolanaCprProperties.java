package com.marketplace.backend.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "solana-cpr")
public class SolanaCprProperties {
    /**
     * R13 (owner decision 2026-11-12, option a): the Solana gateway modules are not in master (branch {@code solana}). When false,
     * every call to the Solana gateway fails closed with SOLANA_RAIL_DISABLED and the Solana escrow scheduler stays idle.
     * The runtime default is false ({@code solana-cpr.enabled} in application.yml, SOLANA_CPR_ENABLED); this field defaults to
     * true only so that unit tests constructing the properties by hand keep exercising the client as before.
     */
    private boolean enabled = true;
    private String baseUrl;
    private String internalApiKey;
    private String custodialClientPublicKey;
    private String onrampAuthorityPublicKey;
    private String network = "localnet";
    private String commitment = "confirmed";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 15000;
    private int confirmPollAttempts = 10;
    private long confirmPollIntervalMs = 1000;
    private long pendingExpirySeconds = 300;
    private long invoiceValiditySeconds = 900;
}
