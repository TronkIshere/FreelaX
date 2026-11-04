package com.marketplace.backend.blockchain;

public interface BlockchainGateway {
    BlockchainNetwork network();

    ChainConfig config();

    boolean onrampReceiptExists(BlockchainAddress client, String purchaseId);

    default EnvironmentInfo environmentInfo() {
        throw unsupported();
    }

    default boolean validateAddress(BlockchainAddress address) {
        throw unsupported();
    }

    default String assetBalance(BlockchainAddress owner) {
        throw unsupported();
    }

    default PreparedAction prepareAction(EscrowActionRequest request) {
        throw unsupported();
    }

    default TransactionResult executeAction(EscrowActionRequest request) {
        throw unsupported();
    }

    default EscrowSnapshot escrowSnapshot(EscrowReference escrow) {
        throw unsupported();
    }

    default TransactionResult transactionStatus(TransactionReference transaction) {
        throw unsupported();
    }

    /** P3-C4: issuer controls blocking an escrow. Networks without such a report return an empty list. */
    default java.util.List<IssuerAlert> issuerAlerts(EscrowReference escrow) {
        return java.util.List.of();
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not supported on network " + network().id());
    }
}
