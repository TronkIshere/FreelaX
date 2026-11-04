package com.marketplace.backend.blockchain;

import java.util.List;

/**
 * Phase 3 P3-C4: a token issuer control (blacklist, pause) that blocks an open escrow, and the actions it blocks. Reported
 * only: the accepted O-6 behaviour has no privileged withdrawal, bypass or rerouting.
 */
public record IssuerAlert(String code, String address, List<String> blocks) {
}
