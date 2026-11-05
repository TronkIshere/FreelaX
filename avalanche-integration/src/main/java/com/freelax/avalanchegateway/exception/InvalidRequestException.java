package com.freelax.avalanchegateway.exception;

import com.freelax.avalanchegateway.applicationException.ApiException;
import com.freelax.avalanchegateway.applicationException.AvalancheErrorDefinitions;

public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String detail) {
        super(AvalancheErrorDefinitions.INVALID_REQUEST, detail);
    }
}
