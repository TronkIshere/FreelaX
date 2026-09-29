package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.request.solana.CompleteOfframpRequest;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaOperationResult;
import com.marketplace.backend.dto.response.solana.SolanaWithdrawalResult;
import com.marketplace.backend.entity.BankCode;
import com.marketplace.backend.entity.ExchangeRateSource;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.OffRampStatus;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.provider.currency.OffRampProvider;
import com.marketplace.backend.provider.currency.OffRampResult;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VndPayoutServiceImplTest {

    private OffRampProvider offRampProvider;
    private UserRepository userRepository;
    private SolanaCprClient client;
    private FreelancerPayoutRecordRepository repository;
    private VndPayoutServiceImpl service;

    @BeforeEach
    void setUp() {
        offRampProvider = mock(OffRampProvider.class);
        userRepository = mock(UserRepository.class);
        client = mock(SolanaCprClient.class);
        repository = mock(FreelancerPayoutRecordRepository.class);
        SolanaCprProperties properties = new SolanaCprProperties();
        properties.setCommitment("confirmed");
        properties.setPendingExpirySeconds(300);
        service = new VndPayoutServiceImpl(offRampProvider, userRepository, client, properties, repository);
        when(repository.save(any(FreelancerPayoutRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordsSimulatedVndPayoutWithoutChangingTaxData() {
        FreelancerPayoutRecord record = record();
        BigDecimal originalTaxableAmount = record.getTaxableAmountVnd();
        User freelancer = new User();
        freelancer.setBankCode(BankCode.MBBANK);
        freelancer.setBankAccountNumber("0123456789");
        freelancer.setBankAccountHolderName("NGUYEN VAN A");
        when(userRepository.findById(record.getFreelancerId())).thenReturn(Optional.of(freelancer));
        when(offRampProvider.convertUsdcToVnd(record.getJobId(), record.getAmountUsdcReceived()))
                .thenReturn(new OffRampResult(new BigDecimal("99.5"), new BigDecimal("25000"),
                        ExchangeRateSource.FALLBACK_PLACEHOLDER, java.time.Instant.parse("2026-01-01T00:00:00Z"),
                        new BigDecimal("2487500"),
                        new BigDecimal("7463"), new BigDecimal("2480037"), "offramp-job-test"));

        service.advance(record);

        assertThat(record.getOffRampStatus()).isEqualTo(OffRampStatus.SIMULATED);
        assertThat(record.getOffRampReference()).isEqualTo("offramp-job-test");
        assertThat(record.getPayoutBankCode()).isEqualTo(BankCode.MBBANK);
        assertThat(record.getPayoutBankAccountNumber()).isEqualTo("0123456789");
        assertThat(record.getSimulatedPayoutAt()).isNotNull();
        assertThat(record.getUsdcToVndRateObservedAt())
                .isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(record.getTaxableAmountVnd()).isEqualByComparingTo(originalTaxableAmount);
    }

    @Test
    void submitsOracleCompletionAfterSimulatedPayout() {
        FreelancerPayoutRecord record = record();
        record.setOffRampStatus(OffRampStatus.SIMULATED);
        when(client.findWithdrawal(record.getFreelancerPublicKey(), record.getWithdrawalId()))
                .thenReturn(Optional.of(withdrawal(record, "Pending")));
        SolanaConfigResult config = new SolanaConfigResult();
        config.setOracleAuthority("Oracle111111111111111111111111111111111111");
        when(client.getConfig()).thenReturn(config);
        SolanaOperationResult operation = new SolanaOperationResult();
        operation.setSignature("completion-signature");
        when(client.completeOfframp(any(), any(), any())).thenReturn(operation);

        service.advance(record);

        assertThat(record.getOffRampStatus()).isEqualTo(OffRampStatus.COMPLETION_SUBMITTED);
        assertThat(record.getOffRampCompletionSignature()).isEqualTo("completion-signature");
        ArgumentCaptor<CompleteOfframpRequest> request = ArgumentCaptor.forClass(CompleteOfframpRequest.class);
        verify(client).completeOfframp(any(), any(), request.capture());
        assertThat(request.getValue().getOracleAuthority()).isEqualTo(config.getOracleAuthority());
        assertThat(request.getValue().getMode()).isEqualTo("send");
    }

    @Test
    void completesOnlyFromMatchingCompletedWithdrawal() {
        FreelancerPayoutRecord record = record();
        record.setOffRampStatus(OffRampStatus.COMPLETION_SUBMITTED);
        when(client.findWithdrawal(record.getFreelancerPublicKey(), record.getWithdrawalId()))
                .thenReturn(Optional.of(withdrawal(record, "Completed")));

        service.advance(record);

        assertThat(record.getOffRampStatus()).isEqualTo(OffRampStatus.COMPLETED);
        assertThat(record.getOffRampCompletedAt()).isNotNull();
        assertThat(record.getOffRampError()).isNull();
    }

    private FreelancerPayoutRecord record() {
        FreelancerPayoutRecord record = new FreelancerPayoutRecord();
        record.setId(UUID.randomUUID());
        record.setJobId(UUID.randomUUID());
        record.setFreelancerId(UUID.randomUUID());
        record.setFreelancerPublicKey("Freelancer111111111111111111111111111111111");
        record.setAmountUsdcReceived(new BigDecimal("99.500000"));
        record.setWithdrawalId("44");
        record.setWithdrawalPda("WithdrawalPda11111111111111111111111111111");
        record.setWithdrawalTokenAmount("99500000");
        record.setTaxableAmountVnd(new BigDecimal("2500000"));
        record.setOffRampStatus(OffRampStatus.NOT_STARTED);
        return record;
    }

    private SolanaWithdrawalResult withdrawal(FreelancerPayoutRecord record, String status) {
        SolanaWithdrawalResult withdrawal = new SolanaWithdrawalResult();
        withdrawal.setAddress(record.getWithdrawalPda());
        withdrawal.setWithdrawalId(record.getWithdrawalId());
        withdrawal.setFreelancer(record.getFreelancerPublicKey());
        withdrawal.setTokenAmount(record.getWithdrawalTokenAmount());
        withdrawal.setStatus(status);
        return withdrawal;
    }
}
