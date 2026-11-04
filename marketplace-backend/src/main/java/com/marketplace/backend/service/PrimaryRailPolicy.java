package com.marketplace.backend.service;

import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Phase 3 P3-C2 (O1): the default rail for NEW contracts in development and demonstration environments (D-H). Existing
 * contracts are never changed. {@code SOLANA} (default) is the current behaviour. {@code AVALANCHE} records the configured
 * Avalanche network on the new contract, exactly as the client opt-in ({@code AvalancheEscrowService.enable}) does today.
 * Fail closed: an unknown value stops startup, and {@code AVALANCHE} with the gateway disabled refuses contract creation
 * instead of silently falling back to Solana (SIMULATION_SCOPE section 8 rule 2). Rollback = set the property back.
 */
@Component
public class PrimaryRailPolicy {
    public enum Rail { SOLANA, AVALANCHE }

    private final Rail rail;
    private final AvalancheGatewayProperties avalanche;

    public PrimaryRailPolicy(@Value("${marketplace.primary-rail:SOLANA}") String rail, AvalancheGatewayProperties avalanche) {
        try {
            this.rail = Rail.valueOf(rail == null ? "" : rail.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("marketplace.primary-rail must be SOLANA or AVALANCHE, was: " + rail);
        }
        this.avalanche = avalanche;
    }

    public Rail rail() {
        return rail;
    }

    /** Applies the policy to a contract being created; never called for an existing contract. */
    public void applyToNewContract(WorkContract contract) {
        if (rail != Rail.AVALANCHE) {
            return;
        }
        if (avalanche == null || !avalanche.isEnabled()) {
            throw new ApplicationException(ErrorCode.INVALID_DATA,
                    "the primary rail is AVALANCHE but the Avalanche gateway is disabled; no fallback to another rail");
        }
        contract.setPaymentNetworkId(avalanche.getNetwork());
    }
}
