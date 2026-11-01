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
            throw new IllegalStateException("unexpected getEscrow answer length " + hex.length());
        }
        return new EscrowState(address(hex, 0), address(hex, 1), address(hex, 2), number(hex, 3), number(hex, 4).intValueExact(),
                number(hex, 5).intValueExact(), number(hex, 6).intValueExact(), number(hex, 7).signum() != 0,
                number(hex, 8).longValueExact(), number(hex, 9).longValueExact(), number(hex, 10).longValueExact(),
                number(hex, 11).longValueExact(), number(hex, 12).longValueExact(), number(hex, 13).longValueExact(),
                number(hex, 14).longValueExact(), "0x" + word(hex, 15), "0x" + word(hex, 16), address(hex, 17),
                number(hex, 18).longValueExact());
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
