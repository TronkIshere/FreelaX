package com.marketplace.backend.blockchain.solana;

import com.marketplace.backend.blockchain.AssetReference;
import com.marketplace.backend.blockchain.BlockchainAddress;
import com.marketplace.backend.blockchain.BlockchainGateway;
import com.marketplace.backend.blockchain.BlockchainNetwork;
import com.marketplace.backend.blockchain.ChainConfig;
import com.marketplace.backend.blockchain.EscrowReference;
import com.marketplace.backend.blockchain.EscrowState;
import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SolanaGatewayAdapter implements BlockchainGateway {
    private final SolanaCprClient solana;
    private final SolanaCprProperties properties;

    @Override
    public BlockchainNetwork network() {
        return new BlockchainNetwork(properties.getNetwork());
    }

    @Override
    public ChainConfig config() {
        SolanaConfigResult config = solana.getConfig();
        return new ChainConfig(StringUtils.hasText(config.getAcceptedMint())
                ? new AssetReference(config.getAcceptedMint()) : null,
                Boolean.TRUE.equals(config.getPaused()));
    }

    @Override
    public boolean onrampReceiptExists(BlockchainAddress client, String purchaseId) {
        return solana.findMockOnrampReceipt(client.value(), purchaseId).isPresent();
    }

    public static EscrowState escrowState(SolanaEscrowResult chain) {
        if (chain == null) return null;
        return new EscrowState(new EscrowReference(chain.address()), chain.milestoneId(),
                chain.mint() == null ? null : new AssetReference(chain.mint()),
                chain.amount(), chain.vaultBalanceBaseUnits());
    }
}
