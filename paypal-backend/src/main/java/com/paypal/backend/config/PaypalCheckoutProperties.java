package com.paypal.backend.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "paypal.checkout")
@Getter
@Setter
public class PaypalCheckoutProperties {

    String baseUrl;
    String clientId;
    String clientSecret;
    String returnUrl;
    String cancelUrl;
}
