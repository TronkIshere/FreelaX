-- Log scanning (M4). Additive, forward-only. Every row carries its environment and chain id.

CREATE TABLE avalanche_scan_cursor (
    environment        VARCHAR(32) NOT NULL,
    chain_id           BIGINT      NOT NULL,
    contract_address   VARCHAR(42) NOT NULL,
    last_scanned_block BIGINT      NOT NULL,
    updated_at_ms      BIGINT      NOT NULL,
    PRIMARY KEY (environment, chain_id, contract_address)
) ENGINE = InnoDB;

CREATE TABLE avalanche_event (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    environment      VARCHAR(32) NOT NULL,
    chain_id         BIGINT      NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    tx_hash          VARCHAR(66) NOT NULL,
    log_index        INT         NOT NULL,
    block_number     BIGINT      NOT NULL,
    block_hash       VARCHAR(66) NOT NULL,
    topic0           VARCHAR(66) NOT NULL,
    topics           TEXT        NOT NULL,
    data             MEDIUMTEXT  NOT NULL,
    event_name       VARCHAR(64) NULL,
    created_at_ms    BIGINT      NOT NULL,
    CONSTRAINT uq_avalanche_event_identity UNIQUE (environment, chain_id, tx_hash, log_index),
    KEY ix_avalanche_event_block (environment, chain_id, contract_address, block_number)
) ENGINE = InnoDB;
