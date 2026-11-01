package com.freelax.avalanchegateway.contract;

import java.util.Set;

/** A manifest that passed every check against the live endpoint. Only its escrow and token may be called by the gateway. */
public record VerifiedDeployment(DeploymentManifest manifest) {

    public Set<String> allowedTargets() {
        return Set.of(manifest.escrow(), manifest.token());
    }
}
