package com.freelax.solanagateway.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "solana")
public record SolanaProperties(
        @NotBlank String rpcUrl,
        @NotBlank String programId,
        @NotBlank String commitment,
        String systemFeePayer,
        String internalApiKey,
        boolean requireBuildSession,
        @Min(30) long buildSessionTtlSeconds,
        String localPrivateKeys
) {
}
