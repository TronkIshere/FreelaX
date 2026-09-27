CREATE TABLE solana_transaction_builds (
    id VARCHAR(36) PRIMARY KEY,
    instruction_name VARCHAR(64) NOT NULL,
    message_hash VARCHAR(64) NOT NULL UNIQUE,
    fee_payer VARCHAR(44) NOT NULL,
    required_signers VARCHAR(512) NOT NULL,
    recent_blockhash VARCHAR(44) NOT NULL,
    last_valid_block_height BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    submitted_signature VARCHAR(88) NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX idx_solana_build_expiry (expires_at),
    INDEX idx_solana_build_signature (submitted_signature)
);
