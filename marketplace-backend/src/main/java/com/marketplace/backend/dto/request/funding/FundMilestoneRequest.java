package com.marketplace.backend.dto.request.funding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class FundMilestoneRequest {
    @NotBlank
    private String paymentMethodId;
    @Valid
    @NotNull
    private ExpectedAmount expectedAmount;

    @Getter
    @Setter
    public static class ExpectedAmount {
        @NotNull
        private BigDecimal amount;
        @NotBlank
        private String currency;
    }
}
