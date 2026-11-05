package com.freelax.avalanchegateway.applicationException;

/**
 * Fail-closed startup and configuration refusals. Several codes cover more than one situation; the caller then passes the
 * specific detail. An empty default message means the code is always raised with a detail (for example the offending host).
 */
public enum EnvironmentErrorDefinitions implements ApplicationErrorDefinition {

    ENVIRONMENT_MISSING("avalanche.environment must be LOCAL_SIMULATION or AVALANCHE_FUJI; there is no default"),
    SIGNER_FORBIDDEN_ENVIRONMENT(""),
    BAD_ENDPOINT("RPC URL is not a valid URI"),
    ENDPOINT_CREDENTIALS_FORBIDDEN(""),
    REJECTED_MAINNET_ENDPOINT(""),
    LOCAL_REQUIRES_LOCAL_ENDPOINT(""),
    FUJI_FORBIDS_LOCAL_ENDPOINT(""),
    FUJI_REQUIRES_HTTPS(""),
    FUJI_HOST_NOT_ALLOW_LISTED(""),
    REJECTED_MAINNET_CHAIN_ID(""),
    CHAIN_ID_NOT_ALLOWED(""),
    CLIENT_FAMILY_MISMATCH(""),
    SECONDARY_PROVIDER_MISMATCH("the secondary provider must report the same chain id and client family as the primary"),

    DB_URL_MISSING("avalanche.db.url is required when avalanche.tx.enabled=true"),
    DB_URL_INVALID("only jdbc:mysql:// URLs are supported"),
    DB_HOST_NOT_LOCAL(""),
    DB_NAME_NOT_ALLOWED(""),

    MANIFEST_REQUIRED("avalanche.deployment-manifest is required to submit transactions: only the manifest contracts may be called"),
    MANIFEST_UNREADABLE("the deployment manifest could not be read"),
    MANIFEST_INVALID("the deployment manifest is not valid JSON"),
    MANIFEST_CODE_MISSING(""),
    MANIFEST_TOKEN_MISMATCH("the escrow pins a different token than the manifest"),
    MANIFEST_ENVIRONMENT_MISMATCH(""),
    MANIFEST_CHAIN_MISMATCH(""),
    MANIFEST_CALL_FAILED(""),
    ASSET_DECIMALS("the token must have 6 decimals"),
    ASSET_SYMBOL("the token symbol must be USDC (the bridged USDC.e is refused)"),
    ASSET_DENY_LISTED("a deny-listed address (Mainnet USDC or USDC.e)"),
    ASSET_NOT_ALLOWED(""),
    MOCK_TOKEN_FORBIDDEN("the simulated token exists only in LOCAL_SIMULATION"),

    SCAN_CONTRACT_MISMATCH("the configured contract differs from the deployment manifest"),
    SCAN_CONTRACT_INVALID("avalanche.scan.contract-address must be a 20-byte hex address");

    private final String message;

    EnvironmentErrorDefinitions(String message) {
        this.message = message;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String message() {
        return message;
    }
}
