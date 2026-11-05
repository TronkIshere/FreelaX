package com.freelax.avalanchegateway.dto.response;

public record ScanStatusResponse(String state, long passes, int consecutiveFailures, String lastErrorClass, Long lastSuccessAtMillis,
                           Long cursor, Long safeHead, Long lagBlocks) {

}
