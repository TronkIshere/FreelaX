package com.freelax.avalanchegateway.config;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.startup.EnvironmentStartupValidator;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import com.freelax.avalanchegateway.tx.ErrorClassifier;
import com.freelax.avalanchegateway.tx.NonceAllocator;
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

    @Bean
    @ConditionalOnProperty(name = "avalanche.signers.enabled", havingValue = "true")
    SubmissionService submissionService(ChainClient chainClient, ValidatedEnvironment validatedEnvironment,
                                        SignerRegistry signerRegistry, TxRepository txRepository, NonceAllocator nonceAllocator,
                                        TransactionTemplate avalancheTransactionTemplate, TxProperties txProperties,
                                        ErrorClassifier errorClassifier, TxTransitions txTransitions, Clock avalancheClock) {
        return new SubmissionService(chainClient, validatedEnvironment, signerRegistry, txRepository, nonceAllocator,
                avalancheTransactionTemplate, txProperties, errorClassifier, txTransitions, avalancheClock);
    }

    @Bean
    TxTracker txTracker(ChainClient chainClient, ValidatedEnvironment validatedEnvironment, TxRepository txRepository,
                        TxTransitions txTransitions, ObjectProvider<SubmissionService> submissionService,
                        ObjectProvider<SignerRegistry> signerRegistry, ErrorClassifier errorClassifier,
                        TxProperties txProperties, Clock avalancheClock) {
        return new TxTracker(chainClient, validatedEnvironment, txRepository, txTransitions,
                submissionService.getIfAvailable(), signerRegistry.getIfAvailable(), errorClassifier, txProperties, avalancheClock);
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    TxReconcileScheduler txReconcileScheduler(TxTracker txTracker, TxProperties txProperties) {
        return new TxReconcileScheduler(txTracker, txProperties.reconcileIntervalMs());
    }
}
