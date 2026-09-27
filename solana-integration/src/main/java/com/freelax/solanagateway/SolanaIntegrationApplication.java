package com.freelax.solanagateway;

import com.freelax.solanagateway.config.SolanaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SolanaProperties.class)
public class SolanaIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SolanaIntegrationApplication.class, args);
    }
}
