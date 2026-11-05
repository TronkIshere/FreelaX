package com.freelax.avalanchegateway.config;

import com.freelax.avalanchegateway.applicationException.EnvironmentErrorDefinitions;
import com.freelax.avalanchegateway.applicationException.EnvironmentGuardException;
import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.contract.DeploymentHolder;
import com.freelax.avalanchegateway.contract.EscrowEvents;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.env.EnvironmentGuard;
import com.freelax.avalanchegateway.repository.ScanRepository;
import com.freelax.avalanchegateway.scan.EventDecoder;
import com.freelax.avalanchegateway.scan.LogScanner;
import com.freelax.avalanchegateway.scan.ProviderProfile;
import com.freelax.avalanchegateway.scan.ScanCoordinator;
import com.freelax.avalanchegateway.scan.WsHintListener;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.regex.Pattern;

@Configuration
@ConditionalOnProperty(name = {"avalanche.tx.enabled", "avalanche.scan.enabled"}, havingValue = "true")
public class ScanConfig {

    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");

    @Bean
    EventDecoder eventDecoder() {
        return EscrowEvents.registerAll(new EventDecoder());
    }

    @Bean
    ScanRepository scanRepository(JdbcTemplate avalancheJdbcTemplate, TransactionTemplate avalancheTransactionTemplate, Clock avalancheClock) {
        return new ScanRepository(avalancheJdbcTemplate, avalancheTransactionTemplate, avalancheClock);
    }

    @Bean
    LogScanner logScanner(ChainClient chainClient, ValidatedEnvironment validatedEnvironment, ScanRepository scanRepository,
                          EventDecoder eventDecoder, ScanProperties properties, DeploymentHolder deployment) {
        String contract = properties.contractAddress();
        long startBlock = properties.startBlock();
        if (deployment.deployment() != null) {
            String escrow = deployment.deployment().manifest().escrow();
            if (contract != null && !contract.isBlank() && !contract.equalsIgnoreCase(escrow)) {
                throw new EnvironmentGuardException(EnvironmentErrorDefinitions.SCAN_CONTRACT_MISMATCH);
            }
            contract = escrow;
            startBlock = Math.max(startBlock, deployment.deployment().manifest().startBlock());
        }
        if (contract == null || !ADDRESS.matcher(contract).matches()) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.SCAN_CONTRACT_INVALID);
        }
        boolean local = validatedEnvironment.environment() == AvalancheEnvironment.LOCAL_SIMULATION;
        ProviderProfile profile = ProviderProfile.forHost(validatedEnvironment.endpointHost(), local);
        return new LogScanner(chainClient, validatedEnvironment, scanRepository, eventDecoder, contract,
                startBlock, properties.confirmations(), profile, properties.maxLogRange());
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    ScanCoordinator scanCoordinator(LogScanner logScanner, ScanProperties properties) {
        return new ScanCoordinator(logScanner, properties.intervalMs());
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnProperty(name = "avalanche.scan.ws-url")
    WsHintListener wsHintListener(ScanCoordinator scanCoordinator, ScanProperties properties, ValidatedEnvironment validatedEnvironment) {
        String http = properties.wsUrl().replaceFirst("^wss://", "https://").replaceFirst("^ws://", "http://");
        EnvironmentGuard.checkEndpoint(validatedEnvironment.environment(), http);
        return new WsHintListener(properties.wsUrl(), scanCoordinator);
    }
}
