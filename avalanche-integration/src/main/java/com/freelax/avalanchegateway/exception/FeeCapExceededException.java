package com.freelax.avalanchegateway.exception;

import com.freelax.avalanchegateway.applicationException.ApiException;
import com.freelax.avalanchegateway.applicationException.AvalancheErrorDefinitions;

public class FeeCapExceededException extends ApiException {

    public FeeCapExceededException() {
        super(AvalancheErrorDefinitions.FEE_CAP_EXCEEDED);
    }
}
