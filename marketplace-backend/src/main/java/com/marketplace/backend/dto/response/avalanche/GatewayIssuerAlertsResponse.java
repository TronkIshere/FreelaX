package com.marketplace.backend.dto.response.avalanche;

import java.util.List;

public record GatewayIssuerAlertsResponse(String environment, boolean isSimulated, long chainId, String escrowId, String escrowStatus,
                           List<GatewayIssuerAlertResponse> alerts, String note) {
}
