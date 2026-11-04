package com.freelax.avalanchegateway.contract;

import com.freelax.avalanchegateway.chain.LogEntry;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether a receipt's logs prove exactly one ERC-20 {@code Transfer(from, to, amount)} of one token. Pure: no RPC.
 * Used for the Avalanche payout rail, where the freelancer moves Testnet/simulated USDC from their own wallet to a partner
 * deposit address (FreelaX never holds the funds). An on-chain match is never a fiat payout confirmation.
 */
public final class TokenTransferMatcher {

    public static final String TRANSFER_TOPIC = Hash.sha3String("Transfer(address,address,uint256)");

    public enum Outcome { MATCHED, TRANSFER_NOT_FOUND, AMOUNT_MISMATCH, AMBIGUOUS }

    private TokenTransferMatcher() {
    }

    public static Outcome match(List<LogEntry> logs, String token, String from, String to, BigInteger amount) {
        int exact = 0;
        boolean sameParties = false;
        for (LogEntry log : logs) {
            if (log.removed() || log.address() == null || !log.address().equalsIgnoreCase(token)) {
                continue;
            }
            List<String> t = log.topics();
            if (t == null || t.size() != 3 || !TRANSFER_TOPIC.equalsIgnoreCase(t.get(0))) {
                continue;
            }
            if (!topicAddress(t.get(1)).equals(lower(from)) || !topicAddress(t.get(2)).equals(lower(to))) {
                continue;
            }
            sameParties = true;
            if (log.data() != null && Numeric.toBigInt(log.data()).equals(amount)) {
                exact++;
            }
        }
        if (exact == 1) {
            return Outcome.MATCHED;
        }
        if (exact > 1) {
            return Outcome.AMBIGUOUS;
        }
        return sameParties ? Outcome.AMOUNT_MISMATCH : Outcome.TRANSFER_NOT_FOUND;
    }

    private static String topicAddress(String topic) {
        String clean = Numeric.cleanHexPrefix(topic);
        if (clean.length() < 40) {
            return "";
        }
        return "0x" + clean.substring(clean.length() - 40).toLowerCase(Locale.ROOT);
    }

    private static String lower(String address) {
        return address.toLowerCase(Locale.ROOT);
    }
}
