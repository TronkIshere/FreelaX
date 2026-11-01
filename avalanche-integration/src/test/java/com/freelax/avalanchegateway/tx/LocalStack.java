package com.freelax.avalanchegateway.tx;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * LOCAL_SIMULATION test stack started once per JVM through Testcontainers (O-18): a throwaway MySQL with a database named
 * avalanche_gateway and a plain local Anvil (chain 31337). Nothing here touches any existing database or any public network.
 */
final class LocalStack {

    private static MySQLContainer<?> mysql;
    private static GenericContainer<?> anvil;
    private static HikariDataSource dataSource;

    private LocalStack() {
    }


    static synchronized void start() {
        if (mysql != null) {
            return;
        }
        mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4")).withDatabaseName("avalanche_gateway")
                .withUsername("avalanche_test").withPassword("avalanche_test");
        mysql.start();
        anvil = new GenericContainer<>(DockerImageName.parse("ghcr.io/foundry-rs/foundry:v1.8.7"))
                .withCommand(new String[]{"anvil --host 0.0.0.0 --chain-id 31337 --hardfork cancun"}) // one element: the image entrypoint is sh -c
                .withExposedPorts(8545)
                .waitingFor(Wait.forLogMessage(".*Listening on.*", 1));
        anvil.start();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mysql.getJdbcUrl());
        config.setUsername(mysql.getUsername());
        config.setPassword(mysql.getPassword());
        config.setMaximumPoolSize(24);
        dataSource = new HikariDataSource(config);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    static String anvilUrl() {
        // 127.0.0.1 explicitly: "localhost" may resolve to IPv6, which Docker Desktop's port forwarding can drop
        String host = "localhost".equals(anvil.getHost()) ? "127.0.0.1" : anvil.getHost();
        return "http://" + host + ":" + anvil.getMappedPort(8545);
    }

    static String jdbcUrl() {
        return mysql.getJdbcUrl();
    }

    static String dbUser() {
        return mysql.getUsername();
    }

    static String dbPassword() {
        return mysql.getPassword();
    }

    static HikariDataSource dataSource() {
        return dataSource;
    }
}
