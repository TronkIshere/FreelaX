package com.freelax.avalanchegateway.service;

import com.freelax.avalanchegateway.dto.response.ScanStatusResponse;
import com.freelax.avalanchegateway.dto.response.StatusResponse;

public interface GatewayStatusService {

    /** Performs live read-only RPC calls through the Spring-managed web3j client on every request. */
    StatusResponse status();

    /**
     * Register items A6 / F6: scanner metrics and alerting for monitoring (read-only). The state is DISABLED when no scanner is
     * configured, STARTING before the first pass, OK, or DEGRADED after repeated failed passes. Lag is the number of blocks
     * between the persisted cursor and the head the scanner may read; a lag read failure is reported as null, never as zero.
     */
    ScanStatusResponse scanStatus();
}
