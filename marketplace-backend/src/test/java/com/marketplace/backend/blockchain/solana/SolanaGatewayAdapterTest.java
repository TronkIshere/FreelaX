package com.marketplace.backend.blockchain.solana;

import com.marketplace.backend.blockchain.BlockchainAddress;
import com.marketplace.backend.blockchain.ChainConfig;
import com.marketplace.backend.blockchain.EscrowState;
import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.response.solana.MockOnrampReceiptResult;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import com.marketplace.backend.entity.PaymentFlow;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.PaymentFlowEvidenceRepository;
import com.marketplace.backend.repository.PaymentFlowRepository;
import com.marketplace.backend.repository.PaymentFlowStepRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.repository.WalletRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.provider.onchain.SolanaOnRampProvider;
import com.marketplace.backend.service.PaymentFlowService;
import com.marketplace.backend.service.UnifiedUsdFundingService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SolanaGatewayAdapterTest {
    private final SolanaCprClient solana = mock(SolanaCprClient.class);
    private final SolanaCprProperties properties = new SolanaCprProperties();
    private final SolanaGatewayAdapter adapter = new SolanaGatewayAdapter(solana, properties);

    @Test
    void mapsNetworkAndConfigWithoutExposingSolanaTypes() {
        properties.setNetwork("localnet");
        SolanaConfigResult config = new SolanaConfigResult();
        config.setAcceptedMint("mint-1");
        config.setPaused(Boolean.TRUE);
        when(solana.getConfig()).thenReturn(config);

        assertEquals("localnet", adapter.network().id());
        ChainConfig mapped = adapter.config();
        assertEquals("mint-1", mapped.acceptedAsset().id());
        assertTrue(mapped.paused());
    }

    @Test
    void blankMintAndMissingPausedFlagMapToNoAssetAndNotPaused() {
        SolanaConfigResult config = new SolanaConfigResult();
        config.setAcceptedMint(" ");
        when(solana.getConfig()).thenReturn(config);

        ChainConfig mapped = adapter.config();
        assertNull(mapped.acceptedAsset());
        assertFalse(mapped.paused());
    }

    @Test
    void onrampReceiptExistenceIsDelegatedAndErrorsPropagate() {
        when(solana.findMockOnrampReceipt("wallet", "p1")).thenReturn(Optional.of(new MockOnrampReceiptResult()));
        when(solana.findMockOnrampReceipt("wallet", "p2")).thenReturn(Optional.empty());
        when(solana.findMockOnrampReceipt("wallet", "p3")).thenThrow(new IllegalStateException("rpc down"));

        assertTrue(adapter.onrampReceiptExists(new BlockchainAddress("wallet"), "p1"));
        assertFalse(adapter.onrampReceiptExists(new BlockchainAddress("wallet"), "p2"));
        assertThrows(IllegalStateException.class,
                () -> adapter.onrampReceiptExists(new BlockchainAddress("wallet"), "p3"));
    }

    @Test
    void escrowStateKeepsRawBaseUnitStringsAndAddress() {
        SolanaEscrowResult chain = escrow("m1", "mint-1", "5000000", "5000000");
        EscrowState state = SolanaGatewayAdapter.escrowState(chain);
        assertEquals("escrow-address", state.reference().id());
        assertEquals("mint-1", state.assetId());
        assertEquals("5000000", state.amountBaseUnits());
        assertEquals("5000000", state.vaultBalanceBaseUnits());
        assertNull(SolanaGatewayAdapter.escrowState(null));
    }

    @Test
    void confirmEscrowRejectsMismatchesAndUnderfundedVault() {
        UUID milestoneId = UUID.randomUUID();
        PaymentFlow flow = new PaymentFlow();
        flow.setId(UUID.randomUUID());
        flow.setMilestoneId(milestoneId);
        flow.setMint("mint-1");
        flow.setEscrowUsdc(new BigDecimal("5.000000"));
        UnifiedUsdFundingService service = new UnifiedUsdFundingService(mock(PaymentFlowRepository.class),
                mock(PaymentFlowStepRepository.class), mock(PaymentFlowEvidenceRepository.class),
                mock(WorkContractRepository.class), mock(MilestoneRepository.class), mock(WalletRepository.class),
                mock(UserRepository.class), mock(PaymentBackendClient.class), adapter,
                mock(SolanaOnRampProvider.class), mock(PaymentFlowService.class), mock(TransactionTemplate.class));
        String id = milestoneId.toString();

        assertThrows(ApplicationException.class, () -> service.confirmEscrow(flow, null));
        assertThrows(ApplicationException.class, () -> service.confirmEscrow(flow,
                SolanaGatewayAdapter.escrowState(escrow("other", "mint-1", "5000000", "5000000"))));
        assertThrows(ApplicationException.class, () -> service.confirmEscrow(flow,
                SolanaGatewayAdapter.escrowState(escrow(id, "other-mint", "5000000", "5000000"))));
        assertThrows(ApplicationException.class, () -> service.confirmEscrow(flow,
                SolanaGatewayAdapter.escrowState(escrow(id, "mint-1", "4000000", "4000000"))));
        assertThrows(ApplicationException.class, () -> service.confirmEscrow(flow,
                SolanaGatewayAdapter.escrowState(escrow(id, "mint-1", "5000000", "4999999"))));
        assertThrows(ApplicationException.class, () -> service.confirmEscrow(flow,
                SolanaGatewayAdapter.escrowState(escrow(id, "mint-1", "5000000", null))));
    }

    private static SolanaEscrowResult escrow(String milestoneId, String mint, String amount, String vault) {
        return new SolanaEscrowResult("escrow-address", milestoneId, null, null, null, mint, amount,
                null, null, null, null, false, null, null, null, 0, 0, 0, null, null, null, null, null,
                null, null, null, 0, "vault", vault);
    }
}
