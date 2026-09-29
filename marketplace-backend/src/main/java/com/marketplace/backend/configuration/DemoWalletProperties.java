package com.marketplace.backend.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "demo.wallet")
public class DemoWalletProperties {
    private String clientPublicKey;
    private String freelancerPublicKey;
}
