package com.freelax.avalanchegateway.config;

import com.freelax.avalanchegateway.env.DbEndpointGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * Database wiring, active only when avalanche.tx.enabled=true. Fail closed: the JDBC URL must point to a local MySQL
 * host and to the database named avalanche_gateway. Schema changes come only from Flyway migrations.
 */
@Configuration
@ConditionalOnProperty(name = "avalanche.tx.enabled", havingValue = "true")
public class PersistenceConfig {

    private static final Logger log = LoggerFactory.getLogger(PersistenceConfig.class);

    @Bean(destroyMethod = "close")
    HikariDataSource avalancheDataSource(DbProperties db) {
        String host = DbEndpointGuard.check(db.url());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(db.url().trim());
        config.setUsername(db.username());
        config.setPassword(db.password());
        config.setMaximumPoolSize(10);
        config.setPoolName("avalanche-gateway");
        log.info("Avalanche gateway database: host={} database={}", host, DbEndpointGuard.DATABASE_NAME);
        return new HikariDataSource(config);
    }

    @Bean
    Flyway avalancheFlyway(DataSource avalancheDataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(avalancheDataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    JdbcTemplate avalancheJdbcTemplate(DataSource avalancheDataSource, Flyway avalancheFlyway) {
        return new JdbcTemplate(avalancheDataSource);
    }

    @Bean
    PlatformTransactionManager avalancheTransactionManager(DataSource avalancheDataSource) {
        return new DataSourceTransactionManager(avalancheDataSource);
    }

    @Bean
    TransactionTemplate avalancheTransactionTemplate(PlatformTransactionManager avalancheTransactionManager) {
        return new TransactionTemplate(avalancheTransactionManager);
    }
}
