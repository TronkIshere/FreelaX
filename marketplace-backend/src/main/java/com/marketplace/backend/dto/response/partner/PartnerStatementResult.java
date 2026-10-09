package com.marketplace.backend.dto.response.partner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PartnerStatementResult(BigDecimal balanceUsd, List<Entry> entries,
                                     Instant observedAt, boolean simulation) {
    public record Entry(UUID milestoneId, String eventKey, String kind,
                        BigDecimal deltaUsd, Instant occurredAt) {}
}
