package com.freelax.avalanchegateway.contract;

/** The verified deployment, or null when no manifest is configured (tracking-only processes). */
public record DeploymentHolder(VerifiedDeployment deployment) {
}
