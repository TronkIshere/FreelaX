package com.marketplace.backend.blockchain;

public interface BlockchainGateway {
    BlockchainNetwork network();

    ChainConfig config();

    boolean onrampReceiptExists(BlockchainAddress client, String purchaseId);
}
