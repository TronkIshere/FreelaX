package com.freelax.avalanchegateway.contract;

import com.freelax.avalanchegateway.chain.ChainClient;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

/** Reads an escrow's on-chain record with eth_call. The struct is fully static, so the answer is 19 consecutive words. */
public class EscrowReader {

    private static final int WORDS = 19;

    private final ChainClient client;
    private final String contract;

    public EscrowReader(ChainClient client, String contractAddress) {
        this.client = client;
        this.contract = contractAddress;
    }

    public EscrowState read(String escrowId) {
        ChainClient.CallResult result = client.call(contract, EscrowCalls.getEscrow(escrowId));
        if (result.reverted()) {
            throw new IllegalStateException("getEscrow reverted: " + EscrowErrors.decode(result.data()).orElse("unknown error"));
        }
        String hex = Numeric.cleanHexPrefix(result.data());
        if (hex.length() != WORDS * 64) {
            throw new UnreadableChainStateException("unexpected getEscrow answer length " + hex.length());
        }
        return new EscrowState(address(hex, 0), address(hex, 1), address(hex, 2), number(hex, 3), number(hex, 4).intValueExact(),
                number(hex, 5).intValueExact(), number(hex, 6).intValueExact(), number(hex, 7).signum() != 0,
                number(hex, 8).longValueExact(), number(hex, 9).longValueExact(), number(hex, 10).longValueExact(),
                number(hex, 11).longValueExact(), number(hex, 12).longValueExact(), number(hex, 13).longValueExact(),
                number(hex, 14).longValueExact(), "0x" + word(hex, 15), "0x" + word(hex, 16), address(hex, 17),
                number(hex, 18).longValueExact());
    }

    /** The arbiter that new escrows will snapshot (changing it never affects an existing escrow). */
    public String defaultArbiter() {
        return addressAnswer(call("defaultArbiter()"));
    }

    public boolean paused() {
        return hexNumber(call("paused()"), "paused()").signum() != 0;
    }

    /** ERC-20 balance in base units (6 decimals) of an owner for a token. */
    public BigInteger tokenBalance(String token, String owner) {
        String data = org.web3j.abi.FunctionEncoder.encode(new org.web3j.abi.datatypes.Function("balanceOf",
                java.util.List.of(new org.web3j.abi.datatypes.Address(owner)), java.util.List.of()));
        ChainClient.CallResult result = client.call(token, data);
        if (result.reverted()) {
            throw new IllegalStateException("balanceOf reverted");
        }
        return hexNumber(result.data(), "balanceOf");
    }

    private String call(String signature) {
        String selector = Numeric.toHexString(org.web3j.crypto.Hash.sha3(signature.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 0, 4, true);
        ChainClient.CallResult result = client.call(contract, selector);
        if (result.reverted()) {
            throw new IllegalStateException(signature + " reverted");
        }
        return result.data();
    }

    private static String addressAnswer(String word) {
        String hex = Numeric.cleanHexPrefix(word);
        if (hex.length() < 40) {
            throw new UnreadableChainStateException("defaultArbiter() returned no address (no contract at that address?)");
        }
        return "0x" + hex.substring(hex.length() - 40).toLowerCase();
    }

    /** A numeric eth_call answer; an empty one (no code at the address) is reported, never parsed. */
    private static BigInteger hexNumber(String data, String what) {
        String hex = Numeric.cleanHexPrefix(data == null ? "" : data);
        if (hex.isEmpty()) {
            throw new UnreadableChainStateException(what + " returned no data (no contract at that address?)");
        }
        return new BigInteger(hex, 16);
    }

    private static String word(String hex, int index) {
        return hex.substring(index * 64, index * 64 + 64);
    }

    private static BigInteger number(String hex, int index) {
        return new BigInteger(word(hex, index), 16);
    }

    private static String address(String hex, int index) {
        return "0x" + word(hex, index).substring(24).toLowerCase();
    }
}
