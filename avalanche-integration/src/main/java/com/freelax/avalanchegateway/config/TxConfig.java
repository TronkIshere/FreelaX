package com.freelax.avalanchegateway.config;

import com.freelax.avalanchegateway.applicationException.EnvironmentErrorDefinitions;
import com.freelax.avalanchegateway.applicationException.EnvironmentGuardException;
import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.ChainClients;
import com.freelax.avalanchegateway.contract.DeploymentHolder;
import com.freelax.avalanchegateway.contract.DeploymentManifest;
import com.freelax.avalanchegateway.contract.ManifestVerifier;
import com.freelax.avalanchegateway.repository.TxRepository;
import com.freelax.avalanchegateway.service.TransactionReconcileService;
import com.freelax.avalanchegateway.service.TransactionSubmissionService;
import com.freelax.avalanchegateway.service.impl.TransactionReconcileServiceImpl;
import com.freelax.avalanchegateway.service.impl.TransactionSubmissionServiceImpl;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.startup.EnvironmentStartupValidator;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import com.freelax.avalanchegateway.tx.ErrorClassifier;
import com.freelax.avalanchegateway.tx.NonceAllocator;
import com.freelax.avalanchegateway.tx.SecondaryProvider;
import com.freelax.avalanchegateway.tx.TxReconcileScheduler;
import com.freelax.avalanchegateway.tx.TxTransitions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
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
        DeploymentManifest manifest = DeploymentManifest.read(Path.of(path.trim()));
        return new DeploymentHolder(ManifestVerifier.verify(manifest, validatedEnvironment, chainClient));
    }

    @Bean
    @ConditionalOnProperty(name = "avalanche.signers.enabled", havingValue = "true")
    TransactionSubmissionService transactionSubmissionService(ChainClient chainClient, ValidatedEnvironment validatedEnvironment,
                                                              SignerRegistry signerRegistry, TxRepository txRepository,
                                                              NonceAllocator nonceAllocator, TransactionTemplate avalancheTransactionTemplate,
                                                              TxProperties txProperties, ErrorClassifier errorClassifier,
                                                              TxTransitions txTransitions, Clock avalancheClock,
                                                              DeploymentHolder deploymentHolder) {
        if (deploymentHolder.deployment() == null) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.MANIFEST_REQUIRED);
        }
        return new TransactionSubmissionServiceImpl(chainClient, validatedEnvironment, signerRegistry, txRepository, nonceAllocator,
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
    TransactionReconcileService transactionReconcileService(ChainClient chainClient, ValidatedEnvironment validatedEnvironment,
                                                            TxRepository txRepository, TxTransitions txTransitions,
                                                            ObjectProvider<TransactionSubmissionService> submissionService,
                                                            ObjectProvider<SignerRegistry> signerRegistry, ErrorClassifier errorClassifier,
                                                            TxProperties txProperties, Clock avalancheClock,
                                                            SecondaryProvider secondaryProvider) {
        return new TransactionReconcileServiceImpl(chainClient, validatedEnvironment, txRepository, txTransitions,
                submissionService.getIfAvailable(), signerRegistry.getIfAvailable(), errorClassifier, txProperties, avalancheClock,
                secondaryProvider.client());
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    TxReconcileScheduler txReconcileScheduler(TransactionReconcileService reconcileService, TxProperties txProperties) {
        return new TxReconcileScheduler(reconcileService, txProperties.reconcileIntervalMs());
    }
}
