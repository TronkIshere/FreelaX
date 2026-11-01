package com.freelax.avalanchegateway.startup;

import com.freelax.avalanchegateway.env.AvalancheEnvironment;

public record ValidatedEnvironment(AvalancheEnvironment environment, long chainId, String clientFamily, String endpointHost) {
}
