package com.freelax.avalanchegateway.chain;

/** Result of eth_sendRawTransaction: either the node returned a hash (acceptance, not success) or an RPC error message. */
public record SendOutcome(String hash, String errorMessage) {

    public static SendOutcome accepted(String hash) {
        return new SendOutcome(hash, null);
    }

    public static SendOutcome rejected(String errorMessage) {
        return new SendOutcome(null, errorMessage == null ? "" : errorMessage);
    }

    public boolean accepted() {
        return hash != null;
    }
}
