-- Avalanche rail, additive and forward-only (register items A8 and F13). No existing table, column or value is changed.
-- EVM wallets are kept apart from the Solana `wallets` table on purpose: Solana services pick a user's first `wallets` row,
-- so an EVM address there could change which wallet a Solana flow uses.

-- A8: one EVM address per user, proven by an EIP-191 personal_sign challenge verified in the Avalanche gateway.
CREATE TABLE evm_wallets (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BINARY(16) NOT NULL,
  address VARCHAR(42) NOT NULL,
  linked_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_evm_wallets_user (user_id),
  UNIQUE KEY uq_evm_wallets_address (address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- F13: a two-party authorization (mutual refund) in progress. Both parties sign the SAME typed data (same deadline and
-- digest); the first signature is held here until the second arrives. Signatures are public authorization data, not keys.
CREATE TABLE avalanche_pending_authorizations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  contract_id BINARY(16) NOT NULL,
  action VARCHAR(30) NOT NULL,
  authorization_deadline BIGINT NOT NULL,
  typed_data_digest VARCHAR(66) NOT NULL,
  client_signature VARCHAR(132) NULL,
  freelancer_signature VARCHAR(132) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_avalanche_pending_auth (contract_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
