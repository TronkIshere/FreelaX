-- Avalanche gateway persistence (M3). Additive, forward-only. Every row carries its environment and chain id.

CREATE TABLE avalanche_nonce (
    environment    VARCHAR(32) NOT NULL,
    chain_id       BIGINT      NOT NULL,
    signer_address VARCHAR(42) NOT NULL,
    next_nonce     BIGINT      NOT NULL,
    updated_at_ms  BIGINT      NOT NULL,
    PRIMARY KEY (environment, chain_id, signer_address)
) ENGINE = InnoDB;

CREATE TABLE avalanche_tx (
    id                       BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operation_key            VARCHAR(190) NOT NULL,
    environment              VARCHAR(32)  NOT NULL,
    chain_id                 BIGINT       NOT NULL,
    signer_role              VARCHAR(32)  NOT NULL,
    signer_address           VARCHAR(42)  NOT NULL,
    to_address               VARCHAR(42)  NOT NULL,
    data                     MEDIUMTEXT   NOT NULL,
    gas_limit                BIGINT       NOT NULL,
    max_fee_per_gas          DECIMAL(38, 0) NOT NULL,
    max_priority_fee_per_gas DECIMAL(38, 0) NOT NULL,
    nonce                    BIGINT       NOT NULL,
    raw_tx                   MEDIUMTEXT   NULL,
    tx_hash                  VARCHAR(66)  NULL,
    previous_tx_hashes       TEXT         NULL,
    state                    VARCHAR(16)  NOT NULL,
    block_number             BIGINT       NULL,
    block_hash               VARCHAR(66)  NULL,
    receipt_status           TINYINT      NULL,
    last_error_class         VARCHAR(48)  NULL,
    replaced_by_tx_hash      VARCHAR(66)  NULL,
    missing_observations     INT          NOT NULL DEFAULT 0,
    nonce_gap                BOOLEAN      NOT NULL DEFAULT FALSE,
    attempts                 INT          NOT NULL DEFAULT 0,
    version                  BIGINT       NOT NULL DEFAULT 0,
    created_at_ms            BIGINT       NOT NULL,
    updated_at_ms            BIGINT       NOT NULL,
    submitted_at_ms          BIGINT       NULL,
    last_checked_at_ms       BIGINT       NULL,
    CONSTRAINT uq_avalanche_tx_operation UNIQUE (environment, chain_id, operation_key),
    CONSTRAINT uq_avalanche_tx_nonce UNIQUE (environment, chain_id, signer_address, nonce),
    KEY ix_avalanche_tx_state (environment, chain_id, state)
) ENGINE = InnoDB;
