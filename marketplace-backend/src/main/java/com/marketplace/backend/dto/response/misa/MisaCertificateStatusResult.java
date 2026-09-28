package com.marketplace.backend.dto.response.misa;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MisaCertificateStatusResult {
    @JsonAlias({"certificateId"})
    String id;
    @JsonAlias({"certificateStatus", "currentStatus"})
    String status;
    @JsonAlias({"number"})
    String certificateNumber;
    @JsonAlias({"certificateSymbol"})
    String symbol;
    String lookupCode;
    String submissionId;
    String taxAuthorityReference;
    BigDecimal taxableIncome;
    @JsonAlias({"taxWithheldAmount"})
    BigDecimal taxWithheld;
}