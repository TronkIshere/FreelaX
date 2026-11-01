package com.freelax.avalanchegateway.tx;

import java.util.Locale;

/**
 * Separates definitive from non-definitive failures. A timeout, an HTTP 429 or any transport error is TRANSPORT or
 * RATE_LIMITED: the transaction may have been accepted, so the caller must reconcile and must never re-sign.
 */
public class ErrorClassifier {

    public ErrorClass classifyRpcError(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (m.contains("already known") || m.contains("already imported") || m.contains("known transaction")
                || m.contains("already exists")) {
            return ErrorClass.ALREADY_KNOWN;
        }
        if (m.contains("nonce too low")) {
            return ErrorClass.NONCE_TOO_LOW;
        }
        if (m.contains("underpriced") || m.contains("fee cap less than block base fee")
                || m.contains("max fee per gas less than block base fee")) {
            return ErrorClass.UNDERPRICED;
        }
        if (m.contains("insufficient funds")) {
            return ErrorClass.INSUFFICIENT_FUNDS;
        }
        if (m.contains("intrinsic gas too low") || m.contains("intrinsic gas too high") || m.contains("exceeds block gas limit")
                || m.contains("gas_limit > env.block.gas_limit") || m.contains("invalid sender")
                || m.contains("invalid chain id") || m.contains("chain id mismatch") || m.contains("gas limit too high")) {
            return ErrorClass.PERMANENT_REJECTION;
        }
        return ErrorClass.UNCLASSIFIED_RPC_ERROR;
    }

    public ErrorClass classifyTransport(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String m = String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT);
            if (m.contains("429") || m.contains("too many requests") || m.contains("rate limit")) {
                return ErrorClass.RATE_LIMITED;
            }
        }
        return ErrorClass.TRANSPORT;
    }
}
