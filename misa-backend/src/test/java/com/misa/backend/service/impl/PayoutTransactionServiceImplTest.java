package com.misa.backend.service.impl;

import com.misa.backend.dto.request.misa.CreatePayoutTransactionRequest;
import com.misa.backend.entity.PayoutTransaction;
import com.misa.backend.entity.Taxpayer;
import com.misa.backend.exception.ApplicationException;
import com.misa.backend.repository.PayoutTransactionRepository;
import com.misa.backend.repository.TaxpayerRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class PayoutTransactionServiceImplTest {
    private final PayoutTransactionRepository payouts = mock(PayoutTransactionRepository.class);
    private final TaxpayerRepository taxpayers = mock(TaxpayerRepository.class);
    private final PayoutTransactionServiceImpl service = new PayoutTransactionServiceImpl(payouts, taxpayers);

    @Test
    void repeatPostReturnsExistingPayoutAfterLostResponse() {
        UUID taxpayerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        Taxpayer taxpayer = new Taxpayer();
        taxpayer.setId(taxpayerId);
        PayoutTransaction existing = new PayoutTransaction();
        existing.setId(payoutId);
        existing.setTaxpayer(taxpayer);
        existing.setPlatformPayoutId("job-1");
        existing.setTransactionHash("signature");
        existing.setBlockchain("solana");
        existing.setAmountUsdc(new BigDecimal("500.000000"));
        existing.setExchangeRate(new BigDecimal("25000.000000"));
        existing.setAmountVndGross(new BigDecimal("12500000"));
        existing.setPaymentDate(LocalDate.of(2026, 1, 1));
        when(payouts.findByPlatformPayoutId("job-1")).thenReturn(Optional.of(existing));

        assertThat(service.record(taxpayerId, request("500", "25000")).getId()).isEqualTo(payoutId);
        verify(payouts, never()).save(any());
    }

    @Test
    void repeatPostWithChangedAmountIsRejected() {
        UUID taxpayerId = UUID.randomUUID();
        Taxpayer taxpayer = new Taxpayer();
        taxpayer.setId(taxpayerId);
        PayoutTransaction existing = new PayoutTransaction();
        existing.setTaxpayer(taxpayer);
        existing.setPlatformPayoutId("job-1");
        existing.setTransactionHash("signature");
        existing.setBlockchain("solana");
        existing.setAmountUsdc(new BigDecimal("500"));
        existing.setExchangeRate(new BigDecimal("25000"));
        when(payouts.findByPlatformPayoutId("job-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.record(taxpayerId, request("497.5", "25000")))
                .isInstanceOf(ApplicationException.class);
        verify(payouts, never()).save(any());
    }

    private CreatePayoutTransactionRequest request(String amount, String rate) {
        CreatePayoutTransactionRequest request = new CreatePayoutTransactionRequest();
        request.setPlatformPayoutId("job-1");
        request.setTransactionHash("signature");
        request.setBlockchain("solana");
        request.setAmountUsdc(new BigDecimal(amount));
        request.setExchangeRate(new BigDecimal(rate));
        request.setPaymentDate(LocalDate.of(2026, 1, 2));
        return request;
    }
}
