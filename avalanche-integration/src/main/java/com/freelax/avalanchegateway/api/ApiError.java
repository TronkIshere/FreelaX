package com.freelax.avalanchegateway.api;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String code, String path) {
}
