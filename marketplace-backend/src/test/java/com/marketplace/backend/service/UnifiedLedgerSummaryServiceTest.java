package com.marketplace.backend.service;

import com.marketplace.backend.entity.PaymentFlow;
import com.marketplace.backend.entity.PaymentFlowStep;
import com.marketplace.backend.repository.PaymentFlowRepository;
import com.marketplace.backend.repository.PaymentFlowStepRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UnifiedLedgerSummaryServiceTest {
    private final PaymentFlowRepository flows = mock(PaymentFlowRepository.class);
    private final PaymentFlowStepRepository steps = mock(PaymentFlowStepRepository.class);
    private final UnifiedLedgerSummaryService service = new UnifiedLedgerSummaryService(flows, steps);
    private final List<PaymentFlow> flowRows = new ArrayList<>();
    private final List<PaymentFlowStep> stepRows = new ArrayList<>();

    @Test
    void placesEachFlowInOneBucketPerCurrencyWithoutMixingCurrencies() {
        flow("10.00", Map.of("USD_RECEIVED", "CONFIRMED", "CLIENT_USDC", "UNKNOWN"));       // USD held, on-ramp unknown
        flow("20.00", Map.of("USD_RECEIVED", "CONFIRMED", "CLIENT_USDC", "CONFIRMED", "ESCROW", "CONFIRMED")); // in vault
        PaymentFlow paid = flow("100.00", Map.of("USD_RECEIVED", "CONFIRMED", "CLIENT_USDC", "CONFIRMED",
                "ESCROW", "CONFIRMED", "USDC_RELEASE", "CONFIRMED", "WITHDRAWAL", "CONFIRMED",
                "VND_PAYOUT", "CONFIRMED", "PLATFORM_FEE", "CONFIRMED"));
        amount(paid, "VND_PAYOUT", "2425000"); amount(paid, "PLATFORM_FEE", "3.000000");
        flow("7.00", Map.of("USD_RECEIVED", "CONFIRMED", "CLIENT_USDC", "CONFIRMED", "ESCROW", "CONFIRMED",
                "USDC_REFUND", "CONFIRMED", "WITHDRAWAL", "CONFIRMED"));                       // refund due
        when(flows.findAll()).thenReturn(flowRows);
        when(steps.findAll()).thenReturn(stepRows);

        var summary = service.summary();

        Map<String, Map<String, BigDecimal>> view = summary.currencies().stream().collect(Collectors.toMap(
                UnifiedLedgerSummaryService.CurrencySummary::currency,
                c -> c.buckets().stream().collect(Collectors.toMap(UnifiedLedgerSummaryService.Bucket::code,
                        UnifiedLedgerSummaryService.Bucket::amount))));
        assertThat(view.get("USD")).containsEntry("RECEIVED", new BigDecimal("137.00"))
                .containsEntry("AWAITING_ONRAMP", new BigDecimal("10.00"))
                .containsEntry("REFUND_DUE", new BigDecimal("7.00"));
        assertThat(view.get("USDC")).containsEntry("VAULT", new BigDecimal("20.000000"))
                .containsEntry("TREASURY_PENDING", new BigDecimal("7.000000"))
                .containsEntry("FEE_COLLECTED", new BigDecimal("3.000000"))
                .doesNotContainKey("RECIPIENT_WALLET");
        assertThat(view.get("VND")).containsEntry("PAID", new BigDecimal("2425000")).doesNotContainKey("PAYOUT_DUE");
        assertThat(summary.currencies().stream().filter(c -> c.currency().equals("USDC")).findFirst().orElseThrow()
                .unknownSteps()).isEqualTo(1);
        assertThat(summary.flows()).isEqualTo(4);
    }

    private PaymentFlow flow(String usd, Map<String, String> statuses) {
        PaymentFlow flow = new PaymentFlow();
        flow.setId(UUID.randomUUID());
        flow.setGrossUsd(new BigDecimal(usd));
        flow.setEscrowUsdc(new BigDecimal(usd).setScale(6));
        flowRows.add(flow);
        statuses.forEach((kind, status) -> {
            PaymentFlowStep step = new PaymentFlowStep();
            step.setPaymentFlowId(flow.getId()); step.setKind(kind); step.setStatus(status);
            stepRows.add(step);
        });
        return flow;
    }

    private void amount(PaymentFlow flow, String kind, String value) {
        stepRows.stream().filter(s -> s.getPaymentFlowId().equals(flow.getId()) && s.getKind().equals(kind))
                .findFirst().orElseThrow().setAmount(new BigDecimal(value));
    }
}
