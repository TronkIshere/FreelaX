package com.marketplace.backend.dto.response.avalanche;

import java.util.List;

public record GatewayIssuerAlertResponse(String code, String address, List<String> blocks) {
}
