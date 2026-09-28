package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.request.solana.RequestOfframpRequest;
import com.marketplace.backend.dto.response.solana.DerivedAccountsResult;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaOperationResult;
import com.marketplace.backend.dto.response.solana.SolanaWithdrawalResult;
import com.marketplace.backend.entity.ClientPaymentStatus;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.OnChainOffRampStatus;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnChainOffRampServiceImplTest {

    private SolanaCprClient client;
    private FreelancerPayoutRecordRepository repository;
    private OnChainOffRampServiceImpl service;

    @BeforeEach
    void setUp() {
        client = mock(SolanaCprClient.class);
        repository = mock(FreelancerPayoutRecordRepository.class);
        SolanaCprProperties properties = new SolanaCprProperties();
        properties.setCommitment("confirmed");
        properties.setPendingExpirySeconds(300);
        service = new OnChainOffRampServiceImpl(client, properties, repository);
        when(repository.save(any(FreelancerPayoutRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void submitsWithdrawalFromFreelancerToTreasury() {
        FreelancerPayoutRecord record = record();
        when(client.findWithdrawal(any(), any())).thenReturn(Optional.empty());

        SolanaConfigResult config = new SolanaConfigResult();
        config.setAcceptedMint(record.getPaymentMint());
        config.setTreasuryAuthority("TreasuryAuthority111111111111111111111111111");
        when(client.getConfig()).thenReturn(config);

        DerivedAccountsResult accounts = new DerivedAccountsResult();
        accounts.setWithdrawalRecord("WithdrawalPda11111111111111111111111111111");
        accounts.setTreasuryAta("TreasuryAta1111111111111111111111111111111");
        SolanaOperationResult submitted = new SolanaOperationResult();
        submitted.setSignature("withdrawal-signature");
        submitted.setDerivedAccounts(accounts);
        when(client.requestOfframp(any())).thenReturn(submitted);

        service.advance(record);

        assertThat(record.getOnChainOffRampStatus()).isEqualTo(OnChainOffRampStatus.REQUEST_SUBMITTED);
        assertThat(record.getWithdrawalTransactionSignature()).isEqualTo("withdrawal-signature");
        assertThat(record.getWithdrawalPda()).isEqualTo(accounts.getWithdrawalRecord());
        assertThat(record.getTreasuryPublicKey()).isEqualTo(config.getTreasuryAuthority());
        assertThat(record.getTreasuryUsdcAta()).isEqualTo(accounts.getTreasuryAta());
        assertThat(record.getWithdrawalSubmittedAt()).isNotNull();

        ArgumentCaptor<RequestOfframpRequest> request = ArgumentCaptor.forClass(RequestOfframpRequest.class);
        verify(client).requestOfframp(request.capture());
        assertThat(request.getValue().getFreelancer()).isEqualTo(record.getFreelancerPublicKey());
        assertThat(request.getValue().getWithdrawalId()).isEqualTo(record.getWithdrawalId());
        assertThat(request.getValue().getRateId()).isEqualTo(record.getRateId());
        assertThat(request.getValue().getTokenAmount()).isEqualTo("99500000");
        assertThat(request.getValue().getMode()).isEqualTo("send");
    }

    @Test
    void confirmsOnlyMatchingOnChainWithdrawal() {
        FreelancerPayoutRecord record = record();
        record.setOnChainOffRampStatus(OnChainOffRampStatus.REQUEST_SUBMITTED);
        record.setWithdrawalId("44");
        record.setWithdrawalTokenAmount("99500000");

        SolanaWithdrawalResult withdrawal = new SolanaWithdrawalResult();
        withdrawal.setAddress("WithdrawalPda11111111111111111111111111111");
        withdrawal.setWithdrawalId("44");
        withdrawal.setFreelancer(record.getFreelancerPublicKey());
        withdrawal.setTokenAmount("99500000");
        withdrawal.setMint(record.getPaymentMint());
        withdrawal.setTreasury("TreasuryAta1111111111111111111111111111111");
        withdrawal.setRateSnapshot(record.getRateSnapshotPda());
        withdrawal.setFiatAmountVnd("2487500000");
        withdrawal.setStatus("Pending");
        when(client.findWithdrawal(record.getFreelancerPublicKey(), "44"))
                .thenReturn(Optional.of(withdrawal));

        SolanaConfigResult config = new SolanaConfigResult();
        config.setAcceptedMint(record.getPaymentMint());
        config.setTreasuryAuthority("TreasuryAuthority111111111111111111111111111");
        when(client.getConfig()).thenReturn(config);

        service.advance(record);

        assertThat(record.getOnChainOffRampStatus()).isEqualTo(OnChainOffRampStatus.CONFIRMED);
        assertThat(record.getWithdrawalPda()).isEqualTo(withdrawal.getAddress());
        assertThat(record.getTreasuryPublicKey()).isEqualTo(config.getTreasuryAuthority());
        assertThat(record.getTreasuryUsdcAta()).isEqualTo(withdrawal.getTreasury());
        assertThat(record.getWithdrawalFiatAmountVnd()).isEqualTo("2487500000");
        assertThat(record.getWithdrawalConfirmedAt()).isNotNull();
        assertThat(record.getOnChainOffRampError()).isNull();
    }

    private FreelancerPayoutRecord record() {
        FreelancerPayoutRecord record = new FreelancerPayoutRecord();
        record.setId(UUID.randomUUID());
        record.setJobId(UUID.randomUUID());
        record.setFreelancerId(UUID.randomUUID());
        record.setClientUserId(UUID.randomUUID());
        record.setCreatedAt(LocalDateTime.now());
        record.setFreelancerPublicKey("Freelancer111111111111111111111111111111111");
        record.setAmountUsdcReceived(new BigDecimal("99.500000"));
        record.setClientPaymentStatus(ClientPaymentStatus.CONFIRMED);
        record.setOnChainOffRampStatus(OnChainOffRampStatus.NOT_STARTED);
        record.setRateId("33");
        record.setRateSnapshotPda("RatePda111111111111111111111111111111111111");
        record.setPaymentMint("Mint111111111111111111111111111111111111");
        record.setInvoiceExpiresAtEpoch(Instant.now().plusSeconds(600).getEpochSecond());
        return record;
    }
}
