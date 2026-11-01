package com.freelax.avalanchegateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime database settings. No defaults: the operator provisions a local MySQL database named avalanche_gateway. */
@ConfigurationProperties(prefix = "avalanche.db")
public record DbProperties(String url, String username, String password) {

    @Override
    public String toString() {
        return "DbProperties[url=<redacted>, username=<redacted>, password=<redacted>]";
    }
}
