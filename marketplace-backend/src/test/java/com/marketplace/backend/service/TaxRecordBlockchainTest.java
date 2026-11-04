package com.marketplace.backend.service;

import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.repository.WorkContractRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Register item A7: rail-aware blockchain label for MISA payout transactions; legacy rails keep "solana". */
class TaxRecordBlockchainTest {

    @Test
    void legacyRailsAndUnknownNetworksKeepTheHistoricalSolanaLabel() {
        assertThat(TaxRecordBlockchain.label(null)).isEqualTo("solana");
        assertThat(TaxRecordBlockchain.label("")).isEqualTo("solana");
        assertThat(TaxRecordBlockchain.label("solana-localnet")).isEqualTo("solana");
        assertThat(TaxRecordBlockchain.label("ethereum")).isEqualTo("solana");
    }

    @Test
    void avalancheNetworkIdsAreLabelledAvalanche() {
        assertThat(TaxRecordBlockchain.label("avalanche-local")).isEqualTo("avalanche");
        assertThat(TaxRecordBlockchain.label("Avalanche-Fuji")).isEqualTo("avalanche");
    }

    @Test
    void resolvesFromTheJobsContract() {
        WorkContractRepository contracts = mock(WorkContractRepository.class);
        UUID avalancheJob = UUID.randomUUID();
        UUID solanaJob = UUID.randomUUID();
        WorkContract onAvalanche = new WorkContract();
        onAvalanche.setPaymentNetworkId("avalanche-local");
        WorkContract onSolana = new WorkContract();
        when(contracts.findByJobId(avalancheJob)).thenReturn(Optional.of(onAvalanche));
        when(contracts.findByJobId(solanaJob)).thenReturn(Optional.of(onSolana));

        assertThat(TaxRecordBlockchain.forJob(contracts, avalancheJob)).isEqualTo("avalanche");
        assertThat(TaxRecordBlockchain.forJob(contracts, solanaJob)).isEqualTo("solana");
        assertThat(TaxRecordBlockchain.forJob(contracts, UUID.randomUUID())).isEqualTo("solana");
        assertThat(TaxRecordBlockchain.forJob(null, avalancheJob)).isEqualTo("solana");
        assertThat(TaxRecordBlockchain.forJob(contracts, null)).isEqualTo("solana");
    }
}
