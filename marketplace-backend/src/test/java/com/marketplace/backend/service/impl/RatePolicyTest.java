package com.marketplace.backend.service.impl;

import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.entity.ExchangeRateSource;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.provider.currency.ExchangeRateProvider;
import com.marketplace.backend.provider.currency.ExchangeRateResult;
import com.marketplace.backend.provider.currency.OnRampProvider;
import com.marketplace.backend.provider.currency.OnRampQuote;
import com.marketplace.backend.provider.currency.OnRampResult;
import com.marketplace.backend.provider.currency.OffRampResult;
import com.marketplace.backend.provider.offchain.OffChainOffRampProvider;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.WalletRepository;
import com.marketplace.backend.service.ClientPaymentService;
import com.marketplace.backend.service.NotificationService;
import com.marketplace.backend.service.OnChainOffRampService;
import com.marketplace.backend.service.TaxCertificateService;
import com.marketplace.backend.service.VndPayoutService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RatePolicyTest {
    record Pair(ExchangeRateSource taxSource, ExchangeRateSource payoutSource) {}

    static Stream<Pair> sources() {
        return Stream.of(
                new Pair(ExchangeRateSource.LIVE_OPEN_ER_API, ExchangeRateSource.LIVE_COINGECKO),
                new Pair(ExchangeRateSource.FALLBACK_PLACEHOLDER, ExchangeRateSource.FALLBACK_PLACEHOLDER),
                new Pair(ExchangeRateSource.LIVE_OPEN_ER_API, ExchangeRateSource.FALLBACK_PLACEHOLDER));
    }

    @ParameterizedTest
    @MethodSource("sources")
    void independentTaxAndPayoutRatesAreAccepted(Pair pair) {
        ExchangeRateProvider rates = mock(ExchangeRateProvider.class);
        Instant taxTime = Instant.parse("2026-01-01T00:00:00Z");
        Instant payoutTime = Instant.parse("2026-01-02T00:00:00Z");
        when(rates.getUsdToVndRate()).thenReturn(new ExchangeRateResult(
                new BigDecimal("25000"), pair.taxSource(), taxTime));
        when(rates.getUsdcToVndRate()).thenReturn(new ExchangeRateResult(
                new BigDecimal("26000"), pair.payoutSource(), payoutTime));
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setClientUserId(UUID.randomUUID());
        job.setFreelancerId(UUID.randomUUID());
        job.setBudgetUsd(new BigDecimal("500"));
        WalletRepository wallets = mock(WalletRepository.class);
        when(wallets.findFirstByUserIdOrderByIdAsc(any(UUID.class))).thenAnswer(invocation -> {
            Wallet wallet = new Wallet();
            wallet.setPublicKey("public-key");
            return Optional.of(wallet);
        });
        FreelancerPayoutRecordRepository records = mock(FreelancerPayoutRecordRepository.class);
        when(records.findByJobId(job.getId())).thenReturn(Optional.empty());
        when(records.save(any(FreelancerPayoutRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OnRampProvider onRamp = mock(OnRampProvider.class);
        when(onRamp.quote(job.getId(), job.getBudgetUsd())).thenReturn(new OnRampQuote(
                new BigDecimal("500"), new BigDecimal("2.5"), new BigDecimal("497.5"), "497500000", "1"));
        when(onRamp.network()).thenReturn("localnet");
        when(onRamp.execute(any(), any())).thenReturn(OnRampResult.notStarted("pending"));
        PayoutServiceImpl payout = new PayoutServiceImpl(mock(JobRepository.class), wallets, records,
                onRamp, rates, mock(TaxCertificateService.class), mock(ClientPaymentService.class),
                mock(OnChainOffRampService.class), mock(VndPayoutService.class),
                mock(NotificationService.class), new SolanaCprProperties());

        payout.settle(job);
        FreelancerPayoutRecord record = org.mockito.Mockito.mockingDetails(records).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (FreelancerPayoutRecord) invocation.getArgument(0))
                .findFirst().orElseThrow();
        OffChainOffRampProvider offRamp = new OffChainOffRampProvider(rates);
        ReflectionTestUtils.setField(offRamp, "feeRate", new BigDecimal("0.003"));
        OffRampResult result = offRamp.convertUsdcToVnd(job.getId(), new BigDecimal("497.5"));

        assertThat(record.getTaxUsdToVndRate()).isEqualByComparingTo("25000");
        assertThat(record.getTaxRateSource()).isEqualTo(pair.taxSource());
        assertThat(record.getTaxRateObservedAt()).isEqualTo(taxTime);
        assertThat(result.usdcToVndRate()).isEqualByComparingTo("26000");
        assertThat(result.rateSource()).isEqualTo(pair.payoutSource());
        assertThat(result.rateObservedAt()).isEqualTo(payoutTime);
        assertThat(result.amountVndGross()).isEqualByComparingTo("12935000");
    }
}
