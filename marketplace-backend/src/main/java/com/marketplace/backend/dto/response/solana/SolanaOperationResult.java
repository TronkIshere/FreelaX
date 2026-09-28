package com.marketplace.backend.dto.response.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SolanaOperationResult {
    String status;
    String instruction;
    String signature;
    DerivedAccountsResult derivedAccounts;
}
