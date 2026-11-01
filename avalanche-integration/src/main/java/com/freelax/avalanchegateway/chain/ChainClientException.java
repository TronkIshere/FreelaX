package com.freelax.avalanchegateway.chain;

/** A failed lookup is never evidence about chain state; callers must treat it as non-definitive. */
public class ChainClientException extends RuntimeException {

    public ChainClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChainClientException(String message) {
        super(message);
    }
}
