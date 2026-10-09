package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.response.partner.PartnerStatementResult;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PartnerReconciliationServiceTest {
    private final PaymentBackendClient payment = mock(PaymentBackendClient.class);
    private final PartnerReconciliationService service = new PartnerReconciliationService(payment,
            mock(FundingTransactionRepository.class), mock(ContractSettlementRepository.class),
            mock(ContractCancellationRepository.class), mock(ContractDisputeRepository.class));
    private final UUID milestone = UUID.randomUUID();
    private final BigDecimal amount = new BigDecimal("100.00");

    @Test void fundingNeedsExactlyOneMatchingStatementEntry() {
        statement(entry("FUND", "fund:", "100.00"));
        assertThat(service.confirms(milestone, "FUND", amount)).isTrue();
        statement(entry("FUND", "fund:", "99.99"));
        assertThat(service.confirms(milestone, "FUND", amount)).isFalse();
        statement(entry("FUND", "fund:", "100.00"), entry("FUND", "fund:", "100.00"));
        assertThat(service.confirms(milestone, "FUND", amount)).isFalse();
    }

    @Test void finalMoneyRequiresTheMatchingDebitAndNoOppositeOutcome() {
        statement(entry("FUND", "fund:", "100.00"), entry("RELEASE", "release:", "-100.00"));
        assertThat(service.confirms(milestone, "RELEASE", amount)).isTrue();
        assertThat(service.confirms(milestone, "REFUND", amount)).isFalse();
        statement(entry("FUND", "fund:", "100.00"));
        assertThat(service.confirms(milestone, "RELEASE", amount)).isFalse();
        statement(entry("FUND", "fund:", "100.00"), entry("REFUND", "refund:", "-100.00"));
        assertThat(service.confirms(milestone, "REFUND", amount)).isTrue();
        assertThat(service.confirms(milestone, "RELEASE", amount)).isFalse();
    }

    private PartnerStatementResult.Entry entry(String kind, String prefix, String delta) {
        return new PartnerStatementResult.Entry(milestone, prefix + milestone, kind,
                new BigDecimal(delta), Instant.now());
    }

    private void statement(PartnerStatementResult.Entry... entries) {
        BigDecimal total = List.of(entries).stream().map(PartnerStatementResult.Entry::deltaUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        when(payment.getPartnerStatement()).thenReturn(new PartnerStatementResult(total,
                List.of(entries), Instant.now(), true));
    }
}
