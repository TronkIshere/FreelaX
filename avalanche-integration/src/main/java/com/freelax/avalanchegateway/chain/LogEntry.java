package com.freelax.avalanchegateway.chain;

import java.util.List;

/** One log as returned by a node. Identity is (transaction hash, log index). */
public record LogEntry(String txHash, int logIndex, long blockNumber, String blockHash, String address,
                       List<String> topics, String data, boolean removed) {
}
