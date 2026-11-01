package com.marketplace.backend.blockchain;

public record EscrowState(EscrowReference reference, String milestoneId, AssetReference asset,
                          String amountBaseUnits, String vaultBalanceBaseUnits) {
    public String assetId() {
        return asset == null ? null : asset.id();
    }
}
