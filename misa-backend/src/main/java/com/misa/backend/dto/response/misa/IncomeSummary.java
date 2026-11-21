package com.misa.backend.dto.response.misa;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IncomeSummary {
    BigDecimal taxableIncome;
    BigDecimal taxWithheld;
    /** Decree 117 scheme only (2026-11-21, simulated); null otherwise. */
    BigDecimal vatWithheld;
    String taxScheme;
    String currency;
}
