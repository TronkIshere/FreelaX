package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.request.solana.PublishRateRequest;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaInvoiceResult;
import com.marketplace.backend.dto.response.solana.SolanaOperationResult;
import com.marketplace.backend.entity.ClientPaymentStatus;
import com.marketplace.backend.entity.ExchangeRateSource;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientPaymentServiceImplTest {

    private SolanaCprClient client;
    private FreelancerPayoutRecordRepository repository;
    private ClientPaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        client = mock(SolanaCprClient.class);
        repository = mock(FreelancerPayoutRecordRepository.class);
        SolanaCprProperties properties = new SolanaCprProperties();
        properties.setCommitment("confirmed");
        properties.setInvoiceValiditySeconds(900);
        properties.setPendingExpirySeconds(300);
        service = new ClientPaymentServiceImpl(client, properties, repository);
        when(repository.save(any(FreelancerPayoutRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publishesRateAsFirstClientPaymentStep() {
        FreelancerPayoutRecord record = record();
        when(client.findRate(any())).thenReturn(Optional.empty());

        SolanaConfigResult config = new SolanaConfigResult();
        config.setRateAuthority("RateAuthority1111111111111111111111111111");
        when(client.getConfig()).thenReturn(config);

        SolanaOperationResult submitted = new SolanaOperationResult();
        submitted.setSignature("rate-signature");
        when(client.publishRate(any())).thenReturn(submitted);

        service.advance(record);

        assertThat(record.getClientPaymentStatus()).isEqualTo(ClientPaymentStatus.RATE_SUBMITTED);
        assertThat(record.getRateTransactionSignature()).isEqualTo("rate-signature");
        assertThat(record.getRateId()).isNotBlank();
        assertThat(record.getInvoiceId()).isNotBlank();
        assertThat(record.getInvoiceExpiresAtEpoch()).isNotNull();

        ArgumentCaptor<PublishRateRequest> request = ArgumentCaptor.forClass(PublishRateRequest.class);
        verify(client).publishRate(request.capture());
        assertThat(request.getValue().getMode()).isEqualTo("send");
        assertThat(request.getValue().getUsdcUsdE6()).isEqualTo("1000000");
        assertThat(request.getValue().getSourceHash()).hasSize(64);
    }

    @Test
    void confirmsPaymentOnlyFromMatchingPaidInvoice() {
        FreelancerPayoutRecord record = record();
        record.setClientPaymentStatus(ClientPaymentStatus.PAYMENT_SUBMITTED);
        record.setRateId("11");
        record.setInvoiceId("22");

        SolanaInvoiceResult invoice = new SolanaInvoiceResult();
        invoice.setAddress("InvoicePda1111111111111111111111111111111");
        invoice.setInvoiceId("22");
        invoice.setClient(record.getOnRampClientPublicKey());
        invoice.setFreelancer(record.getFreelancerPublicKey());
        invoice.setAmount("99500000");
        invoice.setMint("Mint111111111111111111111111111111111111");
        invoice.setStatus("Paid");
        when(client.findInvoice(record.getFreelancerPublicKey(), "22"))
                .thenReturn(Optional.of(invoice));

        service.advance(record);

        assertThat(record.getClientPaymentStatus()).isEqualTo(ClientPaymentStatus.CONFIRMED);
        assertThat(record.getInvoicePda()).isEqualTo(invoice.getAddress());
        assertThat(record.getPaymentMint()).isEqualTo(invoice.getMint());
        assertThat(record.getClientPaymentConfirmedAt()).isNotNull();
    }

    private FreelancerPayoutRecord record() {
        FreelancerPayoutRecord record = new FreelancerPayoutRecord();
        record.setId(UUID.randomUUID());
        record.setJobId(UUID.randomUUID());
        record.setFreelancerId(UUID.randomUUID());
        record.setClientUserId(UUID.randomUUID());
        record.setCreatedAt(LocalDateTime.now());
        record.setOnRampClientPublicKey("Client1111111111111111111111111111111111111");
        record.setFreelancerPublicKey("Freelancer111111111111111111111111111111111");
        record.setAmountUsdcReceived(new BigDecimal("99.500000"));
        record.setTaxUsdToVndRate(new BigDecimal("25000.00"));
        record.setTaxRateSource(ExchangeRateSource.FALLBACK_PLACEHOLDER);
        record.setClientPaymentStatus(ClientPaymentStatus.NOT_STARTED);
        return record;
    }
}
