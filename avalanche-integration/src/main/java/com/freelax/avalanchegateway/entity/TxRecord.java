package com.freelax.avalanchegateway.entity;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** One business operation and its current on-chain attempt. The raw transaction is public data, not a secret. */
public record TxRecord(
        long id,
        String operationKey,
        String environment,
        long chainId,
        String signerRole,
        String signerAddress,
        String toAddress,
        String data,
        long gasLimit,
        BigInteger maxFeePerGas,
        BigInteger maxPriorityFeePerGas,
        long nonce,
        String rawTx,
        String txHash,
        String previousTxHashes,
        TxState state,
        Long blockNumber,
        String blockHash,
        Integer receiptStatus,
        String lastErrorClass,
        String replacedByTxHash,
        int missingObservations,
        boolean nonceGap,
        int attempts,
        long version,
        long createdAtMs,
        long updatedAtMs,
        Long submittedAtMs,
        Long lastCheckedAtMs) {

    /** An unsent copy of this record carrying higher fees for the same nonce; used to sign a speed-up before anything is persisted. */
    public TxRecord withReplacementFees(BigInteger newMaxFeePerGas, BigInteger newMaxPriorityFeePerGas) {
        return new TxRecord(id, operationKey, environment, chainId, signerRole, signerAddress, toAddress, data, gasLimit,
                newMaxFeePerGas, newMaxPriorityFeePerGas, nonce, null, null, null, state, null, null, null, null, null,
                0, false, attempts, version, createdAtMs, updatedAtMs, null, null);
    }

    /** Current hash first, then earlier hashes of the same nonce (speed-ups): any of them may be the one that mines. */
    public List<String> allHashes() {
        List<String> hashes = new ArrayList<>();
        if (txHash != null) {
            hashes.add(txHash);
        }
        if (previousTxHashes != null && !previousTxHashes.isBlank()) {
            hashes.addAll(Arrays.asList(previousTxHashes.split(",")));
        }
        return hashes;
    }
}
