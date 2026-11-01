package com.marketplace.backend.blockchain;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class BlockchainGatewayRegistry {
    private final Map<String, BlockchainGateway> byNetwork = new LinkedHashMap<>();

    public BlockchainGatewayRegistry(List<BlockchainGateway> gateways) {
        for (BlockchainGateway gateway : gateways) {
            BlockchainGateway previous = byNetwork.put(gateway.network().id(), gateway);
            if (previous != null) {
                throw new IllegalStateException("two gateways claim network " + gateway.network().id());
            }
        }
    }

    public Optional<BlockchainGateway> find(String networkId) {
        return Optional.ofNullable(byNetwork.get(networkId));
    }

    public BlockchainGateway require(String networkId) {
        return find(networkId).orElseThrow(() -> new IllegalArgumentException("no blockchain gateway is enabled for network " + networkId));
    }

    public List<String> networks() {
        return List.copyOf(byNetwork.keySet());
    }
}
