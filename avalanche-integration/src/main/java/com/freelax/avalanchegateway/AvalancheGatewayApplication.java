package com.freelax.avalanchegateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot's DataSource/Flyway auto-configuration is excluded on purpose: persistence is wired explicitly in
 * PersistenceConfig and only when avalanche.tx.enabled=true, so the service starts without any database otherwise.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        FlywayAutoConfiguration.class})
@ConfigurationPropertiesScan
public class AvalancheGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvalancheGatewayApplication.class, args);
    }
}
