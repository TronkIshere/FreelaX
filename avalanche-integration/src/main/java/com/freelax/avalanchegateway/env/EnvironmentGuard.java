package com.freelax.avalanchegateway.env;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fail-closed environment guard. All checks are pure (no I/O) so an endpoint can be rejected before any request is sent.
 * The allow-lists are compiled in on purpose: widening them is a code change, never a configuration change.
 */
public final class EnvironmentGuard {

    static final Set<Long> REJECTED_CHAIN_IDS = Set.of(0L, 1L, 43114L);
    static final Set<Long> LOCAL_CHAIN_IDS = Set.of(31337L, 43113L);
    static final Set<Long> FUJI_CHAIN_IDS = Set.of(43113L);
    static final Set<String> FUJI_HOSTS = Set.of("api.avax-test.network", "avalanche-fuji-c-chain-rpc.publicnode.com");
    private static final Pattern LOOPBACK_V4 = Pattern.compile("^127(\\.\\d{1,3}){3}$");

    private EnvironmentGuard() {
    }

    public static AvalancheEnvironment requireEnvironment(AvalancheEnvironment environment) {
        if (environment == null) {
            throw new EnvironmentGuardException("ENVIRONMENT_MISSING",
                    "avalanche.environment must be LOCAL_SIMULATION or AVALANCHE_FUJI; there is no default");
        }
        return environment;
    }

    /** Development signers exist only in LOCAL_SIMULATION. Called before any key is generated and before every signature. */
    public static void requireSignerEnvironment(AvalancheEnvironment environment) {
        requireEnvironment(environment);
        if (environment != AvalancheEnvironment.LOCAL_SIMULATION) {
            throw new EnvironmentGuardException("SIGNER_FORBIDDEN_ENVIRONMENT",
                    "development signers exist only in LOCAL_SIMULATION; " + environment + " is refused");
        }
    }

    /** Network-free endpoint check. Returns the lower-case host, the only part of the URL that may be logged. */
    public static String checkEndpoint(AvalancheEnvironment environment, String rpcUrl) {
        requireEnvironment(environment);
        String raw = rpcUrl == null ? "" : rpcUrl.trim();
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException e) {
            throw new EnvironmentGuardException("BAD_ENDPOINT", "RPC URL is not a valid URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if ((!scheme.equals("http") && !scheme.equals("https")) || host.isEmpty()) {
            throw new EnvironmentGuardException("BAD_ENDPOINT", "RPC URL needs an http(s) scheme and a host");
        }
        if (uri.getUserInfo() != null) {
            throw new EnvironmentGuardException("ENDPOINT_CREDENTIALS_FORBIDDEN", "host " + host);
        }
        String lowered = raw.toLowerCase(Locale.ROOT);
        if (host.equals("api.avax.network") || host.endsWith(".avax.network")
                || lowered.contains("mainnet") || lowered.contains("43114")) {
            throw new EnvironmentGuardException("REJECTED_MAINNET_ENDPOINT", "host " + host);
        }
        boolean local = isLocalClass(host);
        switch (environment) {
            case LOCAL_SIMULATION -> {
                if (!local) {
                    throw new EnvironmentGuardException("LOCAL_REQUIRES_LOCAL_ENDPOINT", "host " + host);
                }
            }
            case AVALANCHE_FUJI -> {
                if (local) {
                    throw new EnvironmentGuardException("FUJI_FORBIDS_LOCAL_ENDPOINT", "host " + host);
                }
                if (!scheme.equals("https")) {
                    throw new EnvironmentGuardException("FUJI_REQUIRES_HTTPS", "host " + host);
                }
                if (!FUJI_HOSTS.contains(host)) {
                    throw new EnvironmentGuardException("FUJI_HOST_NOT_ALLOW_LISTED", "host " + host);
                }
            }
        }
        return host;
    }

    public static void checkChainId(AvalancheEnvironment environment, long chainId) {
        requireEnvironment(environment);
        if (REJECTED_CHAIN_IDS.contains(chainId)) {
            throw new EnvironmentGuardException("REJECTED_MAINNET_CHAIN_ID", "chain id " + chainId);
        }
        Set<Long> allowed = environment == AvalancheEnvironment.LOCAL_SIMULATION ? LOCAL_CHAIN_IDS : FUJI_CHAIN_IDS;
        if (!allowed.contains(chainId)) {
            throw new EnvironmentGuardException("CHAIN_ID_NOT_ALLOWED", environment + " does not allow chain id " + chainId);
        }
    }

    /** Supporting evidence only: a local fork of Fuji reports chain id 43113, so the client family tells it apart. */
    public static String checkClientVersion(AvalancheEnvironment environment, String clientVersion) {
        requireEnvironment(environment);
        String version = clientVersion == null ? "" : clientVersion.toLowerCase(Locale.ROOT);
        String family = environment == AvalancheEnvironment.LOCAL_SIMULATION ? "anvil" : "avalanchego";
        if (!version.startsWith(family + "/")) {
            throw new EnvironmentGuardException("CLIENT_FAMILY_MISMATCH",
                    environment + " expects a client starting with '" + family + "/'");
        }
        return family;
    }

    public static void checkSecondaryMatchesPrimary(long primaryChainId, String primaryFamily,
                                                    long secondaryChainId, String secondaryFamily) {
        if (primaryChainId != secondaryChainId || !primaryFamily.equals(secondaryFamily)) {
            throw new EnvironmentGuardException("SECONDARY_PROVIDER_MISMATCH",
                    "the secondary provider must report the same chain id and client family as the primary");
        }
    }

    public static boolean isLocalClass(String host) {
        return host.equals("localhost") || host.equals("::1") || host.equals("[::1]")
                || LOOPBACK_V4.matcher(host).matches()
                || host.equals("host.docker.internal")
                || (!host.contains(".") && !host.contains(":"));
    }
}
