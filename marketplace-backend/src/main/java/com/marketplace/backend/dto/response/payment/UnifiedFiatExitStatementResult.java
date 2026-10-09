package com.marketplace.backend.dto.response.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UnifiedFiatExitStatementResult(UUID paymentFlowId, List<Entry> entries,
        Instant observedAt, boolean simulation) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String eventKey, UUID paymentFlowId, String kind,
            BigDecimal amount, String currency, String reference, Instant occurredAt) { }
}
