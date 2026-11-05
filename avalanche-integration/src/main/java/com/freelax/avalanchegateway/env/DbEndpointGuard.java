package com.freelax.avalanchegateway.env;

import com.freelax.avalanchegateway.applicationException.EnvironmentErrorDefinitions;
import com.freelax.avalanchegateway.applicationException.EnvironmentGuardException;

import java.net.URI;
import java.util.Locale;

/**
 * Fail-closed check of the runtime JDBC URL: only a local MySQL host and only the database named avalanche_gateway.
 * This prevents the gateway from being pointed at another FreelaX database or at an unidentified remote server.
 */
public final class DbEndpointGuard {

    public static final String DATABASE_NAME = "avalanche_gateway";
    private static final String PREFIX = "jdbc:mysql://";

    private DbEndpointGuard() {
    }

    /** Returns the lower-case host (the only part that may be logged). */
    public static String check(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.DB_URL_MISSING);
        }
        String url = jdbcUrl.trim();
        if (!url.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.DB_URL_INVALID);
        }
        URI uri;
        try {
            uri = URI.create(url.substring("jdbc:".length()));
        } catch (IllegalArgumentException e) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.DB_URL_INVALID, "the JDBC URL is not a valid URI");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || uri.getUserInfo() != null) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.DB_URL_INVALID, "the JDBC URL needs a host and must not embed credentials");
        }
        if (!EnvironmentGuard.isLocalClass(host)) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.DB_HOST_NOT_LOCAL, "host " + host);
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.equals("/" + DATABASE_NAME)) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.DB_NAME_NOT_ALLOWED, "the database must be named " + DATABASE_NAME);
        }
        return host;
    }
}
