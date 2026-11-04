package com.marketplace.backend.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "avalanche-gateway")
public class AvalancheGatewayProperties {
    private boolean enabled = false;
    private String baseUrl;
    private String internalApiKey;
    private String network = "avalanche-local";
    /** Phase 3 (D-B, notifications only): notify both parties when an Avalanche settlement is applied. Default off. */
    private boolean settlementNotifications = false;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 20000;
}
