package com.freelax.avalanchegateway.chain;

/** The few receipt facts the tracker needs; web3j types do not leave the chain package. */
public record TxReceipt(String txHash, boolean success, long blockNumber, String blockHash, String to) {
}
