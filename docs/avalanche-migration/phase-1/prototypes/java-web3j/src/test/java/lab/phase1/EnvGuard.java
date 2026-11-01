package lab.phase1;

import java.net.URI;
import java.util.Set;

/**
 * EXPERIMENT-LEVEL environment guard (research prototype of the specification in simulation-environment-guards.md).
 * NOT production code. Fails closed: anything not explicitly allowed is rejected. Mainnet is never accepted.
 */
public final class EnvGuard {
    public enum Env { LOCAL_SIMULATION, AVALANCHE_FUJI }

    static final Set<Long> REJECTED_ALWAYS = Set.of(43114L, 1L);          // Avalanche C-Chain Mainnet, Ethereum Mainnet
    static final Set<String> MAINNET_HOSTS = Set.of("api.avax.network");   // defence in depth

    static boolean isLoopback(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]");
    }

    /** Network-free pre-check: MUST run before any request is sent, so a Mainnet endpoint is never even contacted. */
    public static void checkEndpoint(Env env, String rpcUrl) {
        String host;
        try { host = URI.create(rpcUrl).getHost(); } catch (IllegalArgumentException e) { throw new IllegalStateException("BAD_ENDPOINT"); }
        if (host == null) throw new IllegalStateException("BAD_ENDPOINT");
        if (MAINNET_HOSTS.contains(host.toLowerCase())) throw new IllegalStateException("REJECTED_MAINNET_ENDPOINT:" + host);
        if (env == Env.AVALANCHE_FUJI && isLoopback(host)) throw new IllegalStateException("FUJI_FORBIDS_LOOPBACK_ENDPOINT");
        if (env == Env.LOCAL_SIMULATION && !isLoopback(host)) throw new IllegalStateException("LOCAL_REQUIRES_LOOPBACK_ENDPOINT:" + host);
    }

    /** After eth_chainId has been read from the (already endpoint-checked) node. */
    public static void check(Env env, String rpcUrl, long chainId) {
        if (REJECTED_ALWAYS.contains(chainId)) throw new IllegalStateException("REJECTED_MAINNET_CHAIN_ID:" + chainId);
        checkEndpoint(env, rpcUrl);
        String host = URI.create(rpcUrl).getHost();
        switch (env) {
            case LOCAL_SIMULATION -> {
                if (!isLoopback(host)) throw new IllegalStateException("LOCAL_REQUIRES_LOOPBACK_ENDPOINT:" + host);
                // 31337 = plain anvil; 43113 only because a LOCAL fork of Fuji reports it (Phase 1 J14), allowed solely on loopback
                if (chainId != 31337L && chainId != 43113L) throw new IllegalStateException("LOCAL_CHAIN_ID_NOT_ALLOWED:" + chainId);
            }
            case AVALANCHE_FUJI -> {
                if (isLoopback(host)) throw new IllegalStateException("FUJI_FORBIDS_LOOPBACK_ENDPOINT");
                if (chainId != 43113L) throw new IllegalStateException("FUJI_CHAIN_ID_MISMATCH:" + chainId);
            }
        }
    }
}
