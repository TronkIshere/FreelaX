package com.freelax.avalanchegateway.contract;

import com.freelax.avalanchegateway.applicationException.ApiException;
import com.freelax.avalanchegateway.applicationException.AvalancheErrorDefinitions;

/** The verified deployment, or null when no manifest is configured (tracking-only processes). */
public record DeploymentHolder(VerifiedDeployment deployment) {

    public VerifiedDeployment require() {
        if (deployment == null) {
            throw new ApiException(AvalancheErrorDefinitions.DEPLOYMENT_NOT_CONFIGURED);
        }
        return deployment;
    }
}
