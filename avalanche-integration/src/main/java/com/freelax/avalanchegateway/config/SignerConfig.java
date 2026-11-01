package com.freelax.avalanchegateway.config;

import com.freelax.avalanchegateway.env.EnvironmentGuard;
import com.freelax.avalanchegateway.signer.DevSignerFactory;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.startup.EnvironmentStartupValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SignerConfig {

    private static final Logger log = LoggerFactory.getLogger(SignerConfig.class);

    /** Exists only when explicitly enabled. Enabling it outside LOCAL_SIMULATION aborts startup before any network call. */
    @Bean
    @ConditionalOnProperty(name = "avalanche.signers.enabled", havingValue = "true")
    SignerRegistry signerRegistry(AvalancheProperties properties, EnvironmentStartupValidator validator) {
        EnvironmentGuard.requireSignerEnvironment(properties.environment());
        SignerRegistry registry = DevSignerFactory.createRegistry(validator.validated());
        log.info("Ephemeral development signers created for LOCAL_SIMULATION (in memory only, never persisted): {}",
                registry.addresses());
        return registry;
    }
}
