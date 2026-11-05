package com.marketplace.backend.blockchain.avalanche;

import com.marketplace.backend.blockchain.BlockchainAddress;
import com.marketplace.backend.blockchain.BlockchainGateway;
import com.marketplace.backend.blockchain.BlockchainGatewayRegistry;
import com.marketplace.backend.blockchain.BlockchainNetwork;
import com.marketplace.backend.blockchain.ChainConfig;
import com.marketplace.backend.blockchain.EnvironmentInfo;
import com.marketplace.backend.blockchain.EscrowActionRequest;
import com.marketplace.backend.blockchain.EscrowReference;
import com.marketplace.backend.blockchain.EscrowSnapshot;
import com.marketplace.backend.blockchain.NeutralEscrowStatus;
import com.marketplace.backend.blockchain.PreparedAction;
import com.marketplace.backend.blockchain.TransactionReference;
import com.marketplace.backend.blockchain.TransactionResult;
import com.marketplace.backend.blockchain.TransactionStatus;
import com.marketplace.backend.blockchain.solana.SolanaGatewayAdapter;
import com.marketplace.backend.client.AvalancheClient;
import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.response.avalanche.GatewayBalanceResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayConfigResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayEscrowResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayHashStatusResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayPreparedActionResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayPreparedTxResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayTransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvalancheGatewayAdapterTest {
    private static final String ID = "0x" + "ab".repeat(16) + "0".repeat(32);

    private final AvalancheClient avalanche = mock(AvalancheClient.class);
    private final AvalancheGatewayProperties properties = new AvalancheGatewayProperties();
    private final AvalancheGatewayAdapter adapter = new AvalancheGatewayAdapter(avalanche, properties);

    @Test
    void mapsNetworkConfigAndEnvironmentWithoutExposingAvalancheTypes() {
        properties.setNetwork("avalanche-local");
        when(avalanche.getConfig()).thenReturn(new GatewayConfigResponse("LOCAL_SIMULATION", true, 31337, "0xescrow", "0xtoken", "MOCK", "1",
                true, "0xarb", true, null));

        assertEquals("avalanche-local", adapter.network().id());
        ChainConfig config = adapter.config();
        assertEquals("0xtoken", config.acceptedAsset().id());
        assertTrue(config.paused());
        EnvironmentInfo environment = adapter.environmentInfo();
        assertEquals("LOCAL_SIMULATION", environment.environment());
        assertEquals(31337, environment.chainId());
        assertTrue(environment.simulated());
        assertTrue(environment.backendSignersAvailable());
    }

    @Test
    void aMissingTokenMapsToNoAsset() {
        when(avalanche.getConfig()).thenReturn(new GatewayConfigResponse("AVALANCHE_FUJI", true, 43113, "0xe", " ", "CIRCLE_FUJI_USDC", "1",
                false, "0xa", false, "https://explorer-test.avax.network/c-chain"));
        assertNull(adapter.config().acceptedAsset());
        assertFalse(adapter.environmentInfo().backendSignersAvailable());
    }

    @Test
    void addressBalanceAndPreparedActionsAreDelegatedAsNeutralTypes() {
        when(avalanche.validateAddress("0xabc")).thenReturn(true);
        when(avalanche.getBalance("0xabc")).thenReturn(new GatewayBalanceResponse("LOCAL_SIMULATION", true, "0xabc", "0xtoken", "5000000", 6));
        when(avalanche.prepare(eq(ID), eq("fund"), any())).thenReturn(new GatewayPreparedActionResponse("LOCAL_SIMULATION", true, 31337, "fund", ID,
                List.of(new GatewayPreparedTxResponse("approve", "0xtoken", "0x095ea7b3", "0"),
                        new GatewayPreparedTxResponse("fund", "0xescrow", "0xdeadbeef", "0")), 1_700_000_600L, null, null));

        assertTrue(adapter.validateAddress(new BlockchainAddress("0xabc")));
        assertEquals("5000000", adapter.assetBalance(new BlockchainAddress("0xabc")));
        PreparedAction prepared = adapter.prepareAction(new EscrowActionRequest(ID, "fund", "op-1", Map.of("amount", "5000000")));
        assertEquals(2, prepared.transactions().size());
        assertEquals("EVM_CALLDATA", prepared.transactions().get(0).chainFormat());
        assertEquals(31337, prepared.transactions().get(1).chainId());
        assertEquals("LOCAL_SIMULATION", prepared.environment().environment());
        assertEquals(1_700_000_600L, prepared.authorizationDeadline());
    }

    @Test
    void escrowSnapshotNormalisesTheStatusAndAbsenceIsNull() {
        when(avalanche.findEscrow(ID)).thenReturn(Optional.of(new GatewayEscrowResponse("LOCAL_SIMULATION", true, 31337, ID, "DISPUTED", "0xc",
                "0xf", "0xa", "100", 2, 1, 2, 10L, 0L, "0xs", "0xr")));
        when(avalanche.findEscrow("0x" + "00".repeat(32))).thenReturn(Optional.empty());

        EscrowSnapshot snapshot = adapter.escrowSnapshot(new EscrowReference(ID));
        assertEquals(NeutralEscrowStatus.DISPUTED, snapshot.status());
        assertEquals("100", snapshot.amountBaseUnits());
        assertEquals(1, snapshot.revisionsUsed());
        assertNull(adapter.escrowSnapshot(new EscrowReference("0x" + "00".repeat(32))));
    }

    @Test
    void anUnknownGatewayStatusIsRejectedNotGuessed() {
        when(avalanche.findEscrow(ID)).thenReturn(Optional.of(new GatewayEscrowResponse("LOCAL_SIMULATION", true, 31337, ID, "EXOTIC", "0xc",
                "0xf", "0xa", "100", 2, 0, 0, 0L, 0L, "0x", "0x")));
        assertThrows(IllegalArgumentException.class, () -> adapter.escrowSnapshot(new EscrowReference(ID)));
    }

    @Test
    void transactionStatusUsesAllTenStatesAndNeverTurnsUnknownIntoFailure() {
        for (TransactionStatus status : TransactionStatus.values()) {
            when(avalanche.findTransaction("op-" + status)).thenReturn(Optional.of(new GatewayTransactionResponse("LOCAL_SIMULATION", true, 31337,
                    "op-" + status, status.name(), "0xhash", 1L, null, null, "RELAYER")));
            TransactionResult result = adapter.transactionStatus(new TransactionReference(adapter.network(), "op-" + status));
            assertEquals(status, result.status());
        }
        assertTrue(TransactionStatus.FINALIZED.isSuccessfulSettlement());
        for (TransactionStatus status : TransactionStatus.values()) {
            if (status != TransactionStatus.FINALIZED) {
                assertFalse(status.isSuccessfulSettlement(), status + " is never a successful settlement");
            }
        }
        assertFalse(TransactionStatus.UNKNOWN.isDefinitiveFailure());
        assertFalse(TransactionStatus.SUBMITTED.isTerminal());
        assertTrue(TransactionStatus.REPLACED.isDefinitiveFailure());

        when(avalanche.findTransaction("missing")).thenReturn(Optional.empty());
        TransactionResult missing = adapter.transactionStatus(new TransactionReference(adapter.network(), "missing"));
        assertEquals(TransactionStatus.UNKNOWN, missing.status());
        assertEquals("NOT_FOUND", missing.errorClass());
    }

    @Test
    void aTransactionHashIsLookedUpByHashAndOnRampIsNotSupported() {
        String hash = "0x" + "ab".repeat(32);
        when(avalanche.getStatusByHash(hash)).thenReturn(new GatewayHashStatusResponse("LOCAL_SIMULATION", true, 31337, hash, "CONFIRMED", 12L));
        TransactionResult result = adapter.transactionStatus(new TransactionReference(adapter.network(), hash));
        assertEquals(TransactionStatus.CONFIRMED, result.status());
        assertEquals(12L, result.blockNumber());
        assertThrows(UnsupportedOperationException.class, () -> adapter.onrampReceiptExists(new BlockchainAddress("0x1"), "p"));
    }

    @Test
    void theOperationKeyTravelsInTheBodyAndTheCallerCannotAddAnythingElse() {
        when(avalanche.execute(eq(ID), eq("release"), any())).thenAnswer(invocation -> {
            Map<?, ?> body = invocation.getArgument(2);
            assertEquals("op-9", body.get("operationKey"));
            assertEquals("client", body.get("by"));
            return new GatewayTransactionResponse("LOCAL_SIMULATION", true, 31337, "op-9", "SUBMITTED", "0xh", 3L, null, null, "TEST_BUYER");
        });
        TransactionResult result = adapter.executeAction(new EscrowActionRequest(ID, "release", "op-9", Map.of("by", "client")));
        assertEquals(TransactionStatus.SUBMITTED, result.status());
    }

    @Test
    void escrowIdsRoundTripTheMilestoneUuid() {
        UUID milestone = UUID.randomUUID();
        String id = AvalancheEscrowIds.fromMilestone(milestone);
        assertEquals(66, id.length());
        assertTrue(id.endsWith("0".repeat(32)));
        assertEquals(milestone, AvalancheEscrowIds.toMilestone(id));
        assertThrows(IllegalArgumentException.class, () -> AvalancheEscrowIds.toMilestone("0x" + "11".repeat(32)));
    }

    @Test
    void theRegistrySelectsByNetworkAndRefusesTwoGatewaysForOneNetwork() {
        SolanaCprProperties solanaProperties = new SolanaCprProperties();
        solanaProperties.setNetwork("localnet");
        SolanaGatewayAdapter solana = new SolanaGatewayAdapter(mock(SolanaCprClient.class), solanaProperties);
        properties.setNetwork("avalanche-local");
        BlockchainGatewayRegistry registry = new BlockchainGatewayRegistry(List.of(solana, adapter));
        assertEquals(List.of("localnet", "avalanche-local"), registry.networks());
        assertTrue(registry.require("localnet") instanceof SolanaGatewayAdapter);
        assertTrue(registry.require("avalanche-local") instanceof AvalancheGatewayAdapter);
        assertThrows(IllegalArgumentException.class, () -> registry.require("avalanche-fuji"));
        assertThrows(IllegalStateException.class, () -> new BlockchainGatewayRegistry(List.of(adapter, adapter)));
    }

    @Test
    void theSolanaAdapterKeepsWorkingAndRejectsAvalancheOnlyCalls() {
        SolanaCprProperties solanaProperties = new SolanaCprProperties();
        solanaProperties.setNetwork("localnet");
        BlockchainGateway solana = new SolanaGatewayAdapter(mock(SolanaCprClient.class), solanaProperties);
        assertEquals("localnet", solana.network().id());
        assertThrows(UnsupportedOperationException.class, () -> solana.prepareAction(new EscrowActionRequest(ID, "fund", "k", Map.of())));
        assertThrows(UnsupportedOperationException.class, () -> solana.transactionStatus(new TransactionReference(solana.network(), "x")));
    }

    @Configuration
    static class Beans {
        @Bean
        AvalancheClient client() {
            return mock(AvalancheClient.class);
        }

        @Bean
        AvalancheGatewayProperties props() {
            return new AvalancheGatewayProperties();
        }
    }

    @Test
    void theAdapterBeanExistsOnlyWhenTheGatewayIsEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Beans.class, AvalancheGatewayAdapter.class);
        runner.run(context -> assertTrue(context.getBeansOfType(AvalancheGatewayAdapter.class).isEmpty()));
        runner.withPropertyValues("avalanche-gateway.enabled=true")
                .run(context -> assertEquals(1, context.getBeansOfType(AvalancheGatewayAdapter.class).size()));
        BlockchainNetwork unused = new BlockchainNetwork("x");
        assertEquals("x", unused.id());
    }
}
