package com.freelax.avalanchegateway.contract;

import com.freelax.avalanchegateway.chain.LogEntry;
import org.junit.jupiter.api.Test;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;

import static com.freelax.avalanchegateway.contract.TokenTransferMatcher.Outcome.AMBIGUOUS;
import static com.freelax.avalanchegateway.contract.TokenTransferMatcher.Outcome.AMOUNT_MISMATCH;
import static com.freelax.avalanchegateway.contract.TokenTransferMatcher.Outcome.MATCHED;
import static com.freelax.avalanchegateway.contract.TokenTransferMatcher.Outcome.TRANSFER_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenTransferMatcherTest {

    private static final String TOKEN = "0x" + "aa".repeat(20);
    private static final String FROM = "0x" + "f1".repeat(20);
    private static final String TO = "0x" + "e2".repeat(20);
    private static final BigInteger AMOUNT = BigInteger.valueOf(2_500_000);

    private static String topic(String address) {
        return "0x" + "0".repeat(24) + address.substring(2);
    }

    private static LogEntry log(String token, String topic0, String from, String to, BigInteger amount, boolean removed) {
        return new LogEntry("0x" + "01".repeat(32), 0, 10, "0x" + "02".repeat(32), token,
                List.of(topic0, topic(from), topic(to)), Numeric.toHexStringWithPrefixZeroPadded(amount, 64), removed);
    }

    private static LogEntry transfer(BigInteger amount) {
        return log(TOKEN, TokenTransferMatcher.TRANSFER_TOPIC, FROM, TO, amount, false);
    }

    @Test
    void exactlyOneMatchingTransferIsMatched() {
        assertEquals(MATCHED, TokenTransferMatcher.match(List.of(transfer(AMOUNT)), TOKEN, FROM, TO, AMOUNT));
        assertEquals(MATCHED, TokenTransferMatcher.match(List.of(transfer(AMOUNT)), TOKEN.toUpperCase().replace("0X", "0x"),
                FROM.toUpperCase().replace("0X", "0x"), TO, AMOUNT), "address case does not matter");
    }

    @Test
    void wrongTokenTopicPartiesOrRemovedLogsAreNotProof() {
        assertEquals(TRANSFER_NOT_FOUND, TokenTransferMatcher.match(List.of(), TOKEN, FROM, TO, AMOUNT));
        assertEquals(TRANSFER_NOT_FOUND, TokenTransferMatcher.match(List.of(
                log("0x" + "bb".repeat(20), TokenTransferMatcher.TRANSFER_TOPIC, FROM, TO, AMOUNT, false)), TOKEN, FROM, TO, AMOUNT),
                "the same event from another token (for example USDC.e) is not proof");
        assertEquals(TRANSFER_NOT_FOUND, TokenTransferMatcher.match(List.of(
                log(TOKEN, "0x" + "33".repeat(32), FROM, TO, AMOUNT, false)), TOKEN, FROM, TO, AMOUNT), "another event");
        assertEquals(TRANSFER_NOT_FOUND, TokenTransferMatcher.match(List.of(
                log(TOKEN, TokenTransferMatcher.TRANSFER_TOPIC, TO, FROM, AMOUNT, false)), TOKEN, FROM, TO, AMOUNT), "reversed direction");
        assertEquals(TRANSFER_NOT_FOUND, TokenTransferMatcher.match(List.of(
                log(TOKEN, TokenTransferMatcher.TRANSFER_TOPIC, FROM, TO, AMOUNT, true)), TOKEN, FROM, TO, AMOUNT), "a removed (reorged) log");
    }

    @Test
    void amountMustBeExactAndUnique() {
        assertEquals(AMOUNT_MISMATCH, TokenTransferMatcher.match(List.of(transfer(AMOUNT.subtract(BigInteger.ONE))), TOKEN, FROM, TO, AMOUNT));
        assertEquals(AMOUNT_MISMATCH, TokenTransferMatcher.match(List.of(transfer(BigInteger.ONE), transfer(AMOUNT.subtract(BigInteger.ONE))),
                TOKEN, FROM, TO, AMOUNT), "split transfers that only add up are not one transfer of the amount");
        assertEquals(AMBIGUOUS, TokenTransferMatcher.match(List.of(transfer(AMOUNT), transfer(AMOUNT)), TOKEN, FROM, TO, AMOUNT));
    }
}
