package com.freelax.avalanchegateway.applicationException;

/** Every error the gateway API reports: a stable code, the HTTP status and the default technical message (never sent to the caller). */
public enum AvalancheErrorDefinitions implements ApplicationErrorDefinition {

    INVALID_REQUEST(400, "the request is invalid"),
    INVALID_ADDRESS(400, "a 20-byte hex address is required"),
    INVALID_ESCROW_ID(400, "the escrow id must be a 32-byte hex value"),
    INVALID_TX_HASH(400, "a 32-byte transaction hash is required"),
    INVALID_AMOUNT(400, "amount must be a positive integer in base units"),
    UNKNOWN_ACTION(400, "unknown action"),
    DEADLINE_REQUIRED(400, "both signatures were given without the deadline they cover"),
    DEADLINE_OUT_OF_RANGE(400, "the authorization deadline is expired or beyond the allowed window"),
    INVALID_INTERNAL_API_KEY(401, "the internal API key is missing or wrong"),
    ESCROW_NOT_FOUND(404, "no escrow with this id on this chain"),
    TRANSACTION_NOT_FOUND(404, "no transaction with this operation key"),
    AUTHORIZATION_UNAVAILABLE(409, "this action needs a backend authorization signature"),
    ESCROW_NOT_DISPUTED(409, "a mutual settlement needs a Disputed escrow"),
    SIGNER_UNAVAILABLE(409, "no backend development wallet exists in this process"),
    FAUCET_UNAVAILABLE(409, "the simulated USDC faucet exists only in LOCAL_SIMULATION"),
    IDEMPOTENCY_CONFLICT(409, "the operation already exists with different parameters"),
    NONCE_GAP(409, "the signer has a rejected transaction that left an unfilled nonce; operator action is required"),
    FEE_CAP_EXCEEDED(409, "the replacement fee would exceed the configured cap"),
    INTERNAL_ERROR(500, "unexpected failure"),
    CHAIN_UNAVAILABLE(502, "the chain endpoint is unavailable"),
    CHAIN_STATE_UNREADABLE(502, "the chain answered with an empty result"),
    DEPLOYMENT_NOT_CONFIGURED(503, "no verified deployment manifest is configured");

    private final int status;
    private final String message;

    AvalancheErrorDefinitions(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int status() {
        return status;
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
