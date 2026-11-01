package com.freelax.avalanchegateway.config;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.ChainClients;
import com.freelax.avalanchegateway.contract.DeploymentHolder;
import com.freelax.avalanchegateway.contract.DeploymentManifest;
import com.freelax.avalanchegateway.contract.ManifestVerifier;
import com.freelax.avalanchegateway.env.EnvironmentGuardException;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.startup.EnvironmentStartupValidator;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import com.freelax.avalanchegateway.tx.ErrorClassifier;
import com.freelax.avalanchegateway.tx.NonceAllocator;
import com.freelax.avalanchegateway.tx.SecondaryProvider;
import com.freelax.avalanchegateway.tx.SubmissionService;
import com.freelax.avalanchegateway.tx.TxReconcileScheduler;
import com.freelax.avalanchegateway.tx.TxRepository;
import com.freelax.avalanchegateway.tx.TxTracker;
import com.freelax.avalanchegateway.tx.TxTransitions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Transaction tracking beans. The tracker needs only the database and a verified environment. The submission service
 * additionally needs development signers, which exist only in LOCAL_SIMULATION, so there is no submission path in Fuji.
 */
@Configuration
@ConditionalOnProperty(name = "avalanche.tx.enabled", havingValue = "true")
public class TxConfig {

    @Bean
    Clock avalancheClock() {
        return Clock.systemUTC();
    }

    @Bean
    ValidatedEnvironment validatedEnvironment(EnvironmentStartupValidator validator) {
        return validator.validated();
    }

    @Bean
    TxRepository txRepository(JdbcTemplate avalancheJdbcTemplate) {
        return new TxRepository(avalancheJdbcTemplate);
    }

    @Bean
    NonceAllocator nonceAllocator(JdbcTemplate avalancheJdbcTemplate, TxRepository txRepository) {
        return new NonceAllocator(avalancheJdbcTemplate, txRepository);
    }

    @Bean
    ErrorClassifier errorClassifier() {
        return new ErrorClassifier();
    }

    @Bean
    TxTransitions txTransitions(TxRepository txRepository, Clock avalancheClock) {
        return new TxTransitions(txRepository, avalancheClock);
    }

    /** The manifest is verified against the live endpoint before use; without one nothing is configured to be called. */
    @Bean
    DeploymentHolder deploymentHolder(AvalancheProperties properties, ChainClient chainClient, ValidatedEnvironment validatedEnvironment) {
        String path = properties.deploymentManifest();
        if (path == null || path.isBlank()) {
            return new DeploymentHolder(null);
        }
        DeploymentManifest manifest = DeploymentManifest.read(java.nio.file.Path.of(path.trim()));
        return new DeploymentHolder(ManifestVerifier.verify(manifest, validatedEnvironment, chainClient));
    }

    @Bean
    @ConditionalOnProperty(name = "avalanche.signers.enabled", havingValue = "true")
    SubmissionService submissionService(ChainClient chainClient, ValidatedEnvironment validatedEnvironment,
                                        SignerRegistry signerRegistry, TxRepository txRepository, NonceAllocator nonceAllocator,
                                        TransactionTemplate avalancheTransactionTemplate, TxProperties txProperties,
                                        ErrorClassifier errorClassifier, TxTransitions txTransitions, Clock avalancheClock,
                                        DeploymentHolder deploymentHolder) {
        if (deploymentHolder.deployment() == null) {
            throw new EnvironmentGuardException("MANIFEST_REQUIRED",
                    "avalanche.deployment-manifest is required to submit transactions: only the manifest contracts may be called");
        }
        return new SubmissionService(chainClient, validatedEnvironment, signerRegistry, txRepository, nonceAllocator,
                avalancheTransactionTemplate, txProperties, errorClassifier, txTransitions, avalancheClock,
                deploymentHolder.deployment().allowedTargets());
    }

    @Bean(destroyMethod = "close")
    SecondaryProvider secondaryProvider(AvalancheProperties properties, ValidatedEnvironment validatedEnvironment) {
        String url = properties.secondaryUrl();
        return new SecondaryProvider(url == null ? null
                : ChainClients.create(validatedEnvironment.environment(), url, properties.timeoutMs(), properties.minGapMs()));
    }

    @Bean
    TxTracker txTracker(ChainClient chainClient, ValidatedEnvironment validatedEnvironment, TxRepository txRepository,
                        TxTransitions txTransitions, ObjectProvider<SubmissionService> submissionService,
                        ObjectProvider<SignerRegistry> signerRegistry, ErrorClassifier errorClassifier,
                        TxProperties txProperties, Clock avalancheClock, SecondaryProvider secondaryProvider) {
        return new TxTracker(chainClient, validatedEnvironment, txRepository, txTransitions,
                submissionService.getIfAvailable(), signerRegistry.getIfAvailable(), errorClassifier, txProperties, avalancheClock,
                secondaryProvider.client());
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    TxReconcileScheduler txReconcileScheduler(TxTracker txTracker, TxProperties txProperties) {
        return new TxReconcileScheduler(txTracker, txProperties.reconcileIntervalMs());
    }
}
