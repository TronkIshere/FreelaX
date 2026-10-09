package com.marketplace.backend.service;

import com.marketplace.backend.entity.PaymentFlow;
import com.marketplace.backend.entity.PaymentFlowStep;
import com.marketplace.backend.repository.PaymentFlowRepository;
import com.marketplace.backend.repository.PaymentFlowStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Where unified money sits, per currency, from confirmed flow steps. Currencies are never
 * added together; amounts awaiting an independent confirmation are listed separately.
 */
@Service
@RequiredArgsConstructor
public class UnifiedLedgerSummaryService {
    private final PaymentFlowRepository flows;
    private final PaymentFlowStepRepository steps;

    public record Bucket(String code, String label, BigDecimal amount, int flows) { }
    public record CurrencySummary(String currency, List<Bucket> buckets, int unknownSteps) { }
    public record Summary(Instant observedAt, int flows, List<CurrencySummary> currencies, boolean simulation) { }

    @Transactional(readOnly = true)
    public Summary summary() {
        List<PaymentFlow> all = flows.findAll();
        Map<UUID, Map<String, PaymentFlowStep>> byFlow = steps.findAll().stream()
                .collect(Collectors.groupingBy(PaymentFlowStep::getPaymentFlowId,
                        Collectors.toMap(PaymentFlowStep::getKind, Function.identity(), (a, b) -> a)));
        Acc usd = new Acc(), usdc = new Acc(), vnd = new Acc();
        for (PaymentFlow flow : all) {
            Map<String, PaymentFlowStep> s = byFlow.getOrDefault(flow.getId(), Map.of());
            boolean usdReceived = ok(s, "USD_RECEIVED"), clientUsdc = ok(s, "CLIENT_USDC"),
                    escrow = ok(s, "ESCROW"), released = ok(s, "USDC_RELEASE"), refunded = ok(s, "USDC_REFUND"),
                    withdrawn = ok(s, "WITHDRAWAL"), paid = ok(s, "VND_PAYOUT"), fee = ok(s, "PLATFORM_FEE"),
                    usdRefunded = ok(s, "USD_REFUND");
            if (usdReceived) usd.add("RECEIVED", "Đối tác đã xác nhận nhận", flow.getGrossUsd());
            if (usdReceived && !clientUsdc) usd.add("AWAITING_ONRAMP", "Đối tác giữ, chờ on-ramp", flow.getGrossUsd());
            if (withdrawn && refunded && !usdRefunded) usd.add("REFUND_DUE", "Phải hoàn cho Client", flow.getGrossUsd());
            if (usdRefunded) usd.add("REFUNDED", "Đã hoàn cho Client", flow.getGrossUsd());
            if (clientUsdc && !escrow && !refunded) usdc.add("CLIENT_WALLET", "Ở ví Client, chưa vào vault", flow.getEscrowUsdc());
            if (escrow && !released && !refunded) usdc.add("VAULT", "Khóa trong vault", flow.getEscrowUsdc());
            if ((released || refunded) && !withdrawn) usdc.add("RECIPIENT_WALLET", "Ở ví người nhận, chưa withdrawal", flow.getEscrowUsdc());
            if (withdrawn && !(released ? paid && fee : usdRefunded)) usdc.add("TREASURY_PENDING", "Ở treasury, chờ chi/hoàn fiat", flow.getEscrowUsdc());
            if (fee) usdc.add("FEE_COLLECTED", "Phí FreelaX đã đối soát", amount(s.get("PLATFORM_FEE")));
            if (released && withdrawn && !paid) vnd.add("PAYOUT_DUE", "Phải chi cho Freelancer", amount(s.get("WITHDRAWAL"), PaymentFlowStep::getPayoutVnd));
            if (paid) vnd.add("PAID", "Đối tác đã xác nhận chi", amount(s.get("VND_PAYOUT")));
            for (PaymentFlowStep step : s.values()) {
                if (!"UNKNOWN".equals(step.getStatus())) continue;
                switch (step.getKind()) {
                    case "USD_ORDER", "USD_RECEIVED", "USD_REFUND" -> usd.unknown++;
                    case "VND_PAYOUT" -> vnd.unknown++;
                    default -> usdc.unknown++;
                }
            }
        }
        return new Summary(Instant.now(), all.size(), List.of(usd.view("USD"), usdc.view("USDC"), vnd.view("VND")), true);
    }

    private static boolean ok(Map<String, PaymentFlowStep> steps, String kind) {
        PaymentFlowStep step = steps.get(kind);
        return step != null && "CONFIRMED".equals(step.getStatus());
    }

    private static BigDecimal amount(PaymentFlowStep step) { return amount(step, PaymentFlowStep::getAmount); }

    private static BigDecimal amount(PaymentFlowStep step, Function<PaymentFlowStep, BigDecimal> field) {
        return step == null || field.apply(step) == null ? BigDecimal.ZERO : field.apply(step);
    }

    private static final class Acc {
        private final Map<String, Bucket> buckets = new LinkedHashMap<>();
        private int unknown;

        void add(String code, String label, BigDecimal value) {
            Bucket current = buckets.get(code);
            BigDecimal safe = value == null ? BigDecimal.ZERO : value;
            buckets.put(code, current == null ? new Bucket(code, label, safe, 1)
                    : new Bucket(code, label, current.amount().add(safe), current.flows() + 1));
        }

        CurrencySummary view(String currency) { return new CurrencySummary(currency, List.copyOf(buckets.values()), unknown); }
    }
}
