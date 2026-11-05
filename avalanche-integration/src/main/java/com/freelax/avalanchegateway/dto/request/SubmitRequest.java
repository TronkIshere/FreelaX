package com.freelax.avalanchegateway.dto.request;

import com.freelax.avalanchegateway.signer.SignerRole;

public record SubmitRequest(String operationKey, SignerRole role, String to, String data, long gasLimit) {
}
