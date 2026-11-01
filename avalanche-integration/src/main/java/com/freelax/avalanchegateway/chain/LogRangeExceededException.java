package com.freelax.avalanchegateway.chain;

/** The provider refused the block range or the result size of eth_getLogs: the caller must retry with a smaller range. */
public class LogRangeExceededException extends ChainClientException {

    public LogRangeExceededException(String message) {
        super(message);
    }
}
