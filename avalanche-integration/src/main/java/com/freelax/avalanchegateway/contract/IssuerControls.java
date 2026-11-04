package com.freelax.avalanchegateway.contract;

import com.freelax.avalanchegateway.chain.ChainClient;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Phase 3 P3-C4: the "reported" half of the accepted O-6 behaviour. Reads the token issuer's controls (blacklist and pause)
 * for the parties of an OPEN escrow and for the escrow contract itself, and says which settlement paths they block. Read-only
 * by construction: it makes eth_calls only and has no way to move, withdraw, bypass or reroute funds (O-6). Circle's FiatToken
 * exposes {@code isBlacklisted(address)}; the LOCAL MockUSDC exposes the public mapping {@code blacklisted(address)}; both
 * expose {@code paused()}.
 */
public final class IssuerControls {

    /** One observed control and the actions it blocks; the escrow stays as it is until the issuer lifts the control. */
    public record Alert(String code, String address, List<String> blocks) {
    }

    public static final String NOTE = "Stuck and reported (O-6): no privileged withdrawal, bypass or rerouting exists. "
            + "The escrow stays in place until the token issuer lifts the control; then the normal action succeeds.";

    private final ChainClient client;
    private final String token;
    private final boolean mock;

    public IssuerControls(ChainClient client, String token, String tokenKind) {
        this.client = client;
        this.token = token;
        this.mock = "MOCK".equals(tokenKind);
    }

    public boolean blacklisted(String account) {
        String data = FunctionEncoder.encode(new Function(mock ? "blacklisted" : "isBlacklisted", List.of(new Address(account)), List.of()));
        return truthy(client.call(token, data), "blacklist");
    }

    public boolean paused() {
        String selector = Numeric.toHexString(org.web3j.crypto.Hash.sha3("paused()".getBytes(StandardCharsets.UTF_8)), 0, 4, true);
        return truthy(client.call(token, selector), "paused");
    }

    private static boolean truthy(ChainClient.CallResult result, String what) {
        if (result.reverted()) {
            throw new IllegalStateException("the token's " + what + " query reverted");
        }
        String hex = Numeric.cleanHexPrefix(result.data());
        return !hex.isEmpty() && new BigInteger(hex, 16).signum() != 0;
    }

    /** Escrow statuses that still hold funds: Funded, Submitted, Revision, Disputed. */
    public static boolean open(int status) {
        return status >= 1 && status <= 4;
    }

    /** Pure assessment, independent of how the controls are read (unit-testable). */
    public static List<Alert> assess(EscrowState escrow, String escrowContract, Predicate<String> blacklisted, BooleanSupplier paused) {
        List<Alert> alerts = new ArrayList<>();
        if (!open(escrow.status())) {
            return alerts;
        }
        if (paused.getAsBoolean()) {
            alerts.add(new Alert("TOKEN_PAUSED", null, List.of("release", "resolve", "mutual-refund")));
        }
        if (blacklisted.test(escrowContract)) {
            alerts.add(new Alert("ESCROW_BLACKLISTED", escrowContract.toLowerCase(), List.of("release", "resolve", "mutual-refund")));
        }
        if (blacklisted.test(escrow.freelancer())) {
            alerts.add(new Alert("FREELANCER_BLACKLISTED", escrow.freelancer().toLowerCase(), List.of("release", "resolve:release")));
        }
        if (blacklisted.test(escrow.client())) {
            alerts.add(new Alert("CLIENT_BLACKLISTED", escrow.client().toLowerCase(), List.of("mutual-refund", "resolve:refund")));
        }
        return alerts;
    }

    public List<Alert> assess(EscrowState escrow, String escrowContract) {
        return assess(escrow, escrowContract, this::blacklisted, this::paused);
    }
}
