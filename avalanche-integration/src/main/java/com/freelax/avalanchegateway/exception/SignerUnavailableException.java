package com.freelax.avalanchegateway.exception;

import com.freelax.avalanchegateway.applicationException.ApiException;
import com.freelax.avalanchegateway.applicationException.AvalancheErrorDefinitions;

public class SignerUnavailableException extends ApiException {

    public SignerUnavailableException(String detail) {
        super(AvalancheErrorDefinitions.SIGNER_UNAVAILABLE, detail);
    }
}
