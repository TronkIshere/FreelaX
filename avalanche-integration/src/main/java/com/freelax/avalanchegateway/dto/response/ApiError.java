package com.freelax.avalanchegateway.dto.response;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String code, String path) {
}
