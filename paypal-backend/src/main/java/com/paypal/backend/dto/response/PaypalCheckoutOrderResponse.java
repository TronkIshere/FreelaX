package com.paypal.backend.dto.response.paypal;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class PaypalCheckoutOrderResponse {

    UUID id;
    UUID payeeId;
    String referenceId;
    BigDecimal amountUsd;
    String paypalOrderId;
    String paypalCaptureId;
    String status;
    String approvalUrl;
    LocalDateTime createdAt;
    LocalDateTime capturedAt;
}
