package com.freelax.solanagateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class SolanaIntegrationApplicationTest {

    @Test
    void contextLoads() {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
                SolanaIntegrationApplication.class)
                .web(WebApplicationType.NONE)
                .run()) {
            // Starting and closing the real context is the assertion.
        }
    }
}
