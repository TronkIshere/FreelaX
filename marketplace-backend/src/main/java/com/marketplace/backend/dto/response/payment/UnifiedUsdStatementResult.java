package com.marketplace.backend.dto.response.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UnifiedUsdStatementResult(UUID paymentFlowId, List<Row> entries,
        Instant observedAt, boolean simulation) {
    public record Row(String eventKey, UUID paymentFlowId, String kind,
            BigDecimal amount, String currency, String reference, Instant occurredAt) { }
}
