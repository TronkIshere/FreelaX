-- Additive only: chain terms accepted with the unified Job fingerprint, and Admin audit on evidence.
ALTER TABLE `jobs`
  ADD COLUMN `payment_network` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `payment_mint` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL;

ALTER TABLE `payment_flow_evidence`
  ADD COLUMN `actor_id` binary(16) DEFAULT NULL,
  ADD COLUMN `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
