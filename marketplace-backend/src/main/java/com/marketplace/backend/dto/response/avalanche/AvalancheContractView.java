package com.marketplace.backend.dto.response.avalanche;

import com.marketplace.backend.blockchain.EnvironmentInfo;
import com.marketplace.backend.blockchain.EscrowSnapshot;
import com.marketplace.backend.blockchain.IssuerAlert;
import com.marketplace.backend.entity.AvalancheChainTransaction;

import java.util.List;

public record AvalancheContractView(String networkId, boolean avalancheEnabled, EnvironmentInfo environment, String escrowId, EscrowSnapshot chain,
                           List<AvalancheChainTransaction> transactions, String notice, MutualRefundState mutualRefund,
                           List<IssuerAlert> issuerAlerts, List<MutualSettlementState> mutualSettlements) {
    public AvalancheContractView(String networkId, boolean avalancheEnabled, EnvironmentInfo environment, String escrowId, EscrowSnapshot chain,
                        List<AvalancheChainTransaction> transactions, String notice, MutualRefundState mutualRefund,
                        List<IssuerAlert> issuerAlerts) {
        this(networkId, avalancheEnabled, environment, escrowId, chain, transactions, notice, mutualRefund, issuerAlerts, List.of());
    }

    public AvalancheContractView(String networkId, boolean avalancheEnabled, EnvironmentInfo environment, String escrowId, EscrowSnapshot chain,
                        List<AvalancheChainTransaction> transactions, String notice) {
        this(networkId, avalancheEnabled, environment, escrowId, chain, transactions, notice, null, null, List.of());
    }

    public AvalancheContractView(String networkId, boolean avalancheEnabled, EnvironmentInfo environment, String escrowId, EscrowSnapshot chain,
                        List<AvalancheChainTransaction> transactions, String notice, MutualRefundState mutualRefund) {
        this(networkId, avalancheEnabled, environment, escrowId, chain, transactions, notice, mutualRefund, null, List.of());
    }
}
