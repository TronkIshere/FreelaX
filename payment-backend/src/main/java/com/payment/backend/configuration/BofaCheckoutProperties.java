package com.payment.backend.configuration;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bofa.checkout")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BofaCheckoutProperties {
    String baseUrl;
    String clientId;
    String clientSecret;
    String currency = "USD";
    String returnUrl;
    String cancelUrl;
}
