package com.marketplace.backend.configuration;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "paypal-backend")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaypalBackendProperties {
    String baseUrl;
    String internalApiKey;
}
