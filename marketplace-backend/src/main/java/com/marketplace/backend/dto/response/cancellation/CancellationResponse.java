package com.marketplace.backend.dto.response.cancellation;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.marketplace.backend.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record CancellationResponse(UUID cancellationId, UUID contractId, UUID milestoneId,
        String cancellationStatus, String refundStatus, String refundReference, boolean simulation,
        UUID requestedBy, UUID decidedBy, String reasonCode, String reason,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount, String currency,
        Instant requestedAt, Instant decidedAt, Instant updatedAt, boolean retryable, String lastError,
        List<String> allowedActions) {
    public static CancellationResponse from(ContractCancellation c, UUID actor) {
        return new CancellationResponse(c.getId(), c.getContractId(), c.getMilestoneId(), c.getStatus().name(),
                c.getRefundStatus() == null ? null : c.getRefundStatus().name(), c.getRefundReference(), c.isSimulation(),
                c.getRequestedBy(), c.getDecidedBy(), c.getReasonCode(), c.getReason(), c.getAmount(), c.getCurrency(),
                c.getCreatedAt().toInstant(ZoneOffset.UTC), c.getDecidedAt(), c.getUpdatedAt().toInstant(ZoneOffset.UTC),
                c.isRetryable(), c.getLastError(), c.getStatus() == CancellationStatus.REQUESTED && !actor.equals(c.getRequestedBy())
                    ? List.of("ACCEPT", "REJECT") : List.of());
    }
}
