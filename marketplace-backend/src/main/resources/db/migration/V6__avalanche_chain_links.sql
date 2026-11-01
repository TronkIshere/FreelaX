-- Avalanche rail (additive, forward-only, backward compatible). A NULL payment_network_id means the existing behaviour
-- (Solana or fiat rails); no existing row, column or persisted value is changed or renamed.
ALTER TABLE work_contracts ADD COLUMN payment_network_id VARCHAR(40) NULL;

CREATE TABLE avalanche_chain_transactions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  contract_id BINARY(16) NOT NULL,
  milestone_id BINARY(16) NULL,
  network_id VARCHAR(40) NOT NULL,
  environment VARCHAR(32) NOT NULL,
  chain_id BIGINT NOT NULL,
  escrow_id VARCHAR(66) NOT NULL,
  action VARCHAR(30) NOT NULL,
  operation_key VARCHAR(190) NOT NULL,
  actor_user_id BINARY(16) NOT NULL,
  tx_hash VARCHAR(66) NULL,
  status VARCHAR(16) NOT NULL,
  error_class VARCHAR(64) NULL,
  business_applied BIT(1) NOT NULL DEFAULT b'0',
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_avalanche_chain_tx_operation (network_id, operation_key),
  KEY ix_avalanche_chain_tx_contract (contract_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
