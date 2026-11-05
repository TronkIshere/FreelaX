package com.freelax.avalanchegateway.startup;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.ChainClients;
import com.freelax.avalanchegateway.config.AvalancheProperties;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.env.EnvironmentGuard;
import com.freelax.avalanchegateway.exception.ChainClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Runs after all singletons exist and before the web server accepts requests, so any failure aborts startup.
 * Only eth_chainId and web3_clientVersion are sent; nothing is signed or submitted.
 */
@Component
public class EnvironmentStartupValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentStartupValidator.class);

    private final AvalancheProperties properties;
    private final ChainClient primary;
    private ValidatedEnvironment validated;

    public EnvironmentStartupValidator(AvalancheProperties properties, ChainClient primary) {
        this.properties = properties;
        this.primary = primary;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validated();
    }

    /**
     * Idempotent and lazy: the first caller (startup, or a component that needs the verified environment such as the
     * signer registry) runs the checks; later callers get the same result. A failure is never cached as success.
     */
    public synchronized ValidatedEnvironment validated() {
        if (validated == null) {
            validated = validate();
        }
        return validated;
    }

    private ValidatedEnvironment validate() {
        AvalancheEnvironment environment = EnvironmentGuard.requireEnvironment(properties.environment());
        String host = EnvironmentGuard.checkEndpoint(environment, properties.primaryUrl());
        long chainId;
        String family;
        try {
            chainId = primary.chainId();
            EnvironmentGuard.checkChainId(environment, chainId);
            family = EnvironmentGuard.checkClientVersion(environment, primary.clientVersion());
        } catch (ChainClientException e) {
            throw new IllegalStateException("The configured RPC endpoint could not be verified; refusing to start (" + e.getMessage() + ")", e);
        }
        verifySecondary(environment, chainId, family);
        log.info("Avalanche environment validated: environment={} chainId={} clientFamily={} endpointHost={}",
                environment, chainId, family, host);
        return new ValidatedEnvironment(environment, chainId, family, host);
    }

    private void verifySecondary(AvalancheEnvironment environment, long chainId, String family) {
        String url = properties.secondaryUrl();
        if (url == null) {
            return;
        }
        ChainClient secondary = ChainClients.create(environment, url, properties.timeoutMs(), properties.minGapMs());
        try {
            long secondaryChain = secondary.chainId();
            EnvironmentGuard.checkChainId(environment, secondaryChain);
            String secondaryFamily = EnvironmentGuard.checkClientVersion(environment, secondary.clientVersion());
            EnvironmentGuard.checkSecondaryMatchesPrimary(chainId, family, secondaryChain, secondaryFamily);
        } catch (ChainClientException e) {
            throw new IllegalStateException("The secondary RPC endpoint could not be verified; refusing to start (" + e.getMessage() + ")", e);
        } finally {
            secondary.shutdown();
        }
    }

}
