package com.freelax.avalanchegateway.contract;

import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Maps revert data (the 4-byte custom-error selector) to the contract's error name, for transaction result mapping. */
public final class EscrowErrors {

    public static final List<String> SIGNATURES = List.of(
            "InvalidState(uint8)", "NotClient()", "NotFreelancer()", "NotParticipant()", "NotArbiter()", "RevisionsExhausted()",
            "AlreadyExists()", "UnknownEscrow()", "WrongToken()", "BadTerms()", "BadArbiter()", "FundingExpired()",
            "AuthorizationExpired()", "InvalidAuthorization()", "StaleNonce()", "TooEarly()", "TooLate()", "ZeroHash()",
            "EnforcedPause()", "ExpectedPause()", "AccessControlUnauthorizedAccount(address,bytes32)",
            "AccessControlBadConfirmation()", "ReentrancyGuardReentrantCall()", "SafeERC20FailedOperation(address)",
            "Blacklisted(address)", "TokenPaused()");

    private static final Map<String, String> BY_SELECTOR = new HashMap<>();

    static {
        for (String signature : SIGNATURES) {
            BY_SELECTOR.put(selector(signature), signature.substring(0, signature.indexOf('(')));
        }
    }

    private EscrowErrors() {
    }

    public static String selector(String signature) {
        return Numeric.toHexString(Hash.sha3(signature.getBytes(StandardCharsets.UTF_8)), 0, 4, true);
    }

    /** @return the error name for revert data such as {@code 0xb2b3b0e5...}, or empty when the selector is unknown */
    public static Optional<String> decode(String revertData) {
        if (revertData == null) {
            return Optional.empty();
        }
        String hex = Numeric.prependHexPrefix(Numeric.cleanHexPrefix(revertData));
        if (hex.length() < 10) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_SELECTOR.get(hex.substring(0, 10).toLowerCase()));
    }
}
