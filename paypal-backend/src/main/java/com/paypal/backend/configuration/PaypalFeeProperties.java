package com.paypal.backend.configuration;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "paypal.fee")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaypalFeeProperties {

    double commercialFeeRate;
    double fixedFeeUsd;
    double fxSpreadRate;
}
