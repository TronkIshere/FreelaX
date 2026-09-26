package com.marketplace.backend.dto.response.job;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JobPaymentStatusResponse {
    UUID jobId;
    UUID checkoutOrderId;
    String checkoutOrderStatus;
    String taxExportStatus;
}