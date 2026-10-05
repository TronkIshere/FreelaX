package com.payment.backend.dto.request.bofa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePayoutReleaseRequest(
        @NotNull UUID checkoutOrderId,
        @NotNull UUID recipientUserId,
        @NotNull @Valid ExpectedAmount expectedAmount,
        @NotBlank @Size(max = 100) String releaseKey) {
    public record ExpectedAmount(
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency) { }
}
