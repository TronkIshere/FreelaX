package com.freelax.avalanchegateway.exception;

import com.freelax.avalanchegateway.applicationException.ApiException;
import com.freelax.avalanchegateway.applicationException.AvalancheErrorDefinitions;

public class IdempotencyConflictException extends ApiException {

    public IdempotencyConflictException(String operationKey) {
        super(AvalancheErrorDefinitions.IDEMPOTENCY_CONFLICT,
                "operation " + operationKey + " already exists with different parameters");
    }
}
