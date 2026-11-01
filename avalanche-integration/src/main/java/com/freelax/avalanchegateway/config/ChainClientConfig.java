package com.freelax.avalanchegateway.config;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.ChainClients;
import com.freelax.avalanchegateway.env.EnvironmentGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChainClientConfig {

    @Bean(destroyMethod = "shutdown")
    ChainClient chainClient(AvalancheProperties properties) {
        EnvironmentGuard.requireEnvironment(properties.environment());
        return ChainClients.create(properties.environment(), properties.primaryUrl(),
                properties.timeoutMs(), properties.minGapMs());
    }
}
