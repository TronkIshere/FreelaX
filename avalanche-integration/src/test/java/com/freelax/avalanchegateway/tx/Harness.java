package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.ChainClients;
import com.freelax.avalanchegateway.config.TxProperties;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.signer.DevSignerFactory;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** One "process" worth of objects wired by hand over the shared test database: a second Harness is a restart. */
final class Harness {

    static final ValidatedEnvironment LOCAL = new ValidatedEnvironment(AvalancheEnvironment.LOCAL_SIMULATION, 31337, "anvil", "localhost");

    final JdbcTemplate jdbc;
    final TxRepository repository;
    final NonceAllocator allocator;
    final TransactionTemplate transactions;
    final TxTransitions transitions;
    final ChainClient client;
    final TxProperties properties;
    final SignerRegistry registry;
    final SubmissionService submission;
    final TxTracker tracker;

    Harness(HikariDataSource dataSource, String rpcUrl, int timeoutMs, TxProperties properties, SignerRegistry registry) {
        this(dataSource, rpcUrl, timeoutMs, properties, registry, null);
    }

    Harness(HikariDataSource dataSource, String rpcUrl, int timeoutMs, TxProperties properties, SignerRegistry registry, ChainClient secondary) {
        this(dataSource, rpcUrl, timeoutMs, properties, registry, secondary, null);
    }

    /** {@code allowedTargets} null means unrestricted (older tests); the contract tests pass the manifest's targets. */
    Harness(HikariDataSource dataSource, String rpcUrl, int timeoutMs, TxProperties properties, SignerRegistry registry, ChainClient secondary,
            java.util.Set<String> allowedTargets) {
        Clock clock = Clock.systemUTC();
        this.jdbc = new JdbcTemplate(dataSource);
        this.repository = new TxRepository(jdbc);
        this.allocator = new NonceAllocator(jdbc, repository);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.transitions = new TxTransitions(repository, clock);
        this.client = ChainClients.create(AvalancheEnvironment.LOCAL_SIMULATION, rpcUrl, timeoutMs, 0);
        this.properties = properties;
        this.registry = registry;
        ErrorClassifier classifier = new ErrorClassifier();
        this.submission = new SubmissionService(client, LOCAL, registry, repository, allocator, transactions, properties, classifier, transitions, clock, allowedTargets);
        this.tracker = new TxTracker(client, LOCAL, repository, transitions, submission, registry, classifier, properties, clock, secondary);
    }

    static SignerRegistry newRegistry() {
        return DevSignerFactory.createRegistry(LOCAL);
    }

    static TxProperties props(long graceMs, int observations) {
        return new TxProperties(true, 5_000_000, 200, 1, 30, 1, graceMs, observations, 0);
    }

    void close() {
        client.shutdown();
    }
}
