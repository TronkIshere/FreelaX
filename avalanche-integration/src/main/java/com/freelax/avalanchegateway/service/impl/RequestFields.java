package com.freelax.avalanchegateway.service.impl;

import com.freelax.avalanchegateway.applicationException.ApiException;
import com.freelax.avalanchegateway.applicationException.AvalancheErrorDefinitions;

import java.util.Locale;
import java.util.regex.Pattern;

/** The one place that validates the shape of caller-supplied values; a violation is a 400 with a stable code. */
final class RequestFields {

    private static final Pattern BYTES32 = Pattern.compile("0x[0-9a-fA-F]{64}");
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");

    private RequestFields() {
    }

    static String address(String value) {
        if (value == null || !ADDRESS.matcher(value).matches()) {
            throw new ApiException(AvalancheErrorDefinitions.INVALID_ADDRESS);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    static String escrowId(String value) {
        if (value == null || !BYTES32.matcher(value).matches()) {
            throw new ApiException(AvalancheErrorDefinitions.INVALID_ESCROW_ID);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /** Validates only; the caller decides whether to lower-case the hash. */
    static String txHash(String value) {
        if (value == null || !BYTES32.matcher(value).matches()) {
            throw new ApiException(AvalancheErrorDefinitions.INVALID_TX_HASH);
        }
        return value;
    }

    static String bytes32(String value, String field) {
        if (value == null || !BYTES32.matcher(value).matches()) {
            throw new ApiException(AvalancheErrorDefinitions.INVALID_REQUEST, field + " must be a 32-byte hex value");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new ApiException(AvalancheErrorDefinitions.INVALID_REQUEST, field + " is required");
        }
        return value;
    }
}
