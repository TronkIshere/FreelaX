package com.marketplace.backend.dto.response.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SolanaTransactionStatusResult {
    String signature;
    boolean found;
    String slot;
    Long confirmations;
    String confirmationStatus;
    JsonNode error;

    public boolean hasError() {
        return error != null && !error.isNull() && !error.isMissingNode();
    }
}