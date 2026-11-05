package com.freelax.avalanchegateway.dto.response;

import com.freelax.avalanchegateway.contract.IssuerControls.Alert;

import java.util.List;

/** P3-C4: stuck-and-reported escrow (O-6). {@code alerts} is empty when nothing blocks the escrow. */
public record IssuerAlertsResponse(String environment, boolean isSimulated, long chainId, String escrowId, String escrowStatus,
                                   List<Alert> alerts, String note) {

}
