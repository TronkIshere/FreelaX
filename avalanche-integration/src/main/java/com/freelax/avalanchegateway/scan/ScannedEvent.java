package com.freelax.avalanchegateway.scan;

import java.util.List;

/** A persisted log. Identity is (environment, chain id, transaction hash, log index). */
public record ScannedEvent(String environment, long chainId, String contractAddress, String txHash, int logIndex,
                           long blockNumber, String blockHash, String topic0, List<String> topics, String data,
                           String eventName) {
}
