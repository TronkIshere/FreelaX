package com.marketplace.backend.dto.request.solana;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompleteOfframpRequest {
    String oracleAuthority;
    String mode;
    String commitment;
    Boolean skipPreflight;
}
