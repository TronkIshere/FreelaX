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
}