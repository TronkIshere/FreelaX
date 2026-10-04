package com.payment.backend.service;

import com.payment.backend.dto.request.bofa.CreateCheckoutOrderRequest;
import com.payment.backend.entity.BofaCheckoutOrder;
import com.payment.backend.entity.BofaCheckoutOrderStatus;
import com.payment.backend.exception.ApplicationException;
import com.payment.backend.repository.BofaCheckoutOrderRepository;
import com.payment.backend.service.impl.BofaCheckoutOrderServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BofaCheckoutOrderServiceImplTest {
    @Test
    void sameKeyReturnsSameOrderAndCaptureDebitsOnlyOnce() {
        BofaCheckoutOrderRepository orders = mock(BofaCheckoutOrderRepository.class);
        BofaAccountBalanceService balances = mock(BofaAccountBalanceService.class);
        BofaCheckoutOrderServiceImpl service = new BofaCheckoutOrderServiceImpl(orders, balances);
        CreateCheckoutOrderRequest request = request();
        when(orders.save(any())).thenAnswer(invocation -> {
            BofaCheckoutOrder order = invocation.getArgument(0);
            if (order.getId() == null) order.setId(UUID.randomUUID());
            return order;
        });

        var first = service.create(request);
        var persisted = new BofaCheckoutOrder();
        persisted.setId(first.getId());
        persisted.setPayerUserId(request.getPayerUserId());
        persisted.setJobId(request.getJobId());
        persisted.setAmountUsd(request.getAmountUsd());
        persisted.setPayerBankCode(request.getPayerBankCode());
        persisted.setPayerBankAccountNumber(request.getPayerBankAccountNumber());
        persisted.setPayerBankAccountHolderName(request.getPayerBankAccountHolderName());
        persisted.setStatus(BofaCheckoutOrderStatus.CREATED);
        when(orders.findByIdempotencyKey(request.getIdempotencyKey())).thenReturn(Optional.of(persisted));
        when(orders.findWithLockById(first.getId())).thenReturn(Optional.of(persisted));

        assertThat(service.create(request).getId()).isEqualTo(first.getId());
        assertThat(service.capture(first.getId()).getStatus()).isEqualTo("CAPTURED");
        assertThat(service.capture(first.getId()).getStatus()).isEqualTo("CAPTURED");
        verify(orders, times(2)).save(any());
        verify(balances, times(1)).debit(request.getPayerBankAccountNumber(), request.getAmountUsd());
    }

    @Test
    void sameKeyWithChangedAmountIsRejected() {
        BofaCheckoutOrderRepository orders = mock(BofaCheckoutOrderRepository.class);
        BofaCheckoutOrderServiceImpl service = new BofaCheckoutOrderServiceImpl(orders, mock(BofaAccountBalanceService.class));
        CreateCheckoutOrderRequest request = request();
        BofaCheckoutOrder existing = new BofaCheckoutOrder();
        existing.setPayerUserId(request.getPayerUserId());
        existing.setJobId(request.getJobId());
        existing.setAmountUsd(new BigDecimal("600.00"));
        when(orders.findByIdempotencyKey(request.getIdempotencyKey())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ApplicationException.class);
        verify(orders, never()).save(any());
    }

    private CreateCheckoutOrderRequest request() {
        CreateCheckoutOrderRequest request = new CreateCheckoutOrderRequest();
        request.setPayerUserId(UUID.randomUUID());
        request.setJobId(UUID.randomUUID());
        request.setAmountUsd(new BigDecimal("500.00"));
        request.setPayerBankCode("VIETCOMBANK");
        request.setPayerBankAccountNumber("123456789");
        request.setPayerBankAccountHolderName("Client");
        request.setIdempotencyKey("funding-test-1");
        return request;
    }
}
