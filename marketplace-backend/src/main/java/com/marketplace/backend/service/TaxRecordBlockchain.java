package com.marketplace.backend.service;

import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.repository.WorkContractRepository;

import java.util.UUID;

/**
 * Blockchain label sent with a payout transaction to the (simulated) MISA tax service (register item A7).
 * A contract on the Avalanche rail carries a {@code payment_network_id} starting with {@code avalanche} (V6, for example the
 * default {@code avalanche-local}); every other contract, including every legacy rail with a null network id, keeps the
 * historical label {@code solana}, so existing Solana-rail tax records are unchanged.
 */
public final class TaxRecordBlockchain {

    public static final String SOLANA = "solana";
    public static final String AVALANCHE = "avalanche";

    private TaxRecordBlockchain() {
    }

    public static String label(String paymentNetworkId) {
        return paymentNetworkId != null && paymentNetworkId.trim().toLowerCase(java.util.Locale.ROOT).startsWith(AVALANCHE)
                ? AVALANCHE : SOLANA;
    }

    public static String forJob(WorkContractRepository contracts, UUID jobId) {
        if (contracts == null || jobId == null) {
            return SOLANA;
        }
        return label(contracts.findByJobId(jobId).map(WorkContract::getPaymentNetworkId).orElse(null));
    }
}
