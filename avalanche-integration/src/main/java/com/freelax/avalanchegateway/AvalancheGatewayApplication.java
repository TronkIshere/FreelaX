package com.freelax.avalanchegateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AvalancheGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvalancheGatewayApplication.class, args);
    }
}
