-- Baseline: payment (mock provider) schema as produced by Hibernate ddl-auto=update on 2026-10-09.
-- Existing databases are baselined at version 1 and skip this file (spring.flyway.baseline-on-migrate).
-- Tables are listed alphabetically, so foreign keys are checked after all are created.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `bofa_account_balance` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `balance` decimal(20,6) NOT NULL,
  `bank_account_number` varchar(34) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsu71433egvd55bkkklcmcpb32` (`bank_account_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `bofa_checkout_orders` (
  `id` binary(16) NOT NULL,
  `amount_usd` decimal(19,2) NOT NULL,
  `bofa_capture_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bofa_order_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `captured_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `job_id` binary(16) NOT NULL,
  `payer_bank_account_holder_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payer_bank_account_number` varchar(34) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payer_bank_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payer_user_id` binary(16) NOT NULL,
  `status` enum('CAPTURED','CREATED','FAILED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKdgg9hplmuh8vaov5b7sp8pe2b` (`bofa_order_id`),
  UNIQUE KEY `UKg026f0o95nsd3xjub686qg7cr` (`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `bofa_checkout_refunds` (
  `id` binary(16) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `checkout_order_id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `currency` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_error` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payer_user_id` binary(16) NOT NULL,
  `payload_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `refund_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `refund_reference` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `simulation` bit(1) NOT NULL,
  `status` enum('FAILED','PENDING','PROCESSING','SUCCEEDED','UNKNOWN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_checkout` (`checkout_order_id`),
  UNIQUE KEY `uk_refund_key` (`refund_key`),
  UNIQUE KEY `UKpu7yj30yxgi2w16pmh6t8c7hp` (`refund_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `bofa_payout_releases` (
  `id` binary(16) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `checkout_order_id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `currency` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_error` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipient_user_id` binary(16) NOT NULL,
  `release_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `release_reference` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `simulation` bit(1) NOT NULL,
  `status` enum('FAILED','PENDING','PROCESSING','SUCCEEDED','UNKNOWN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_release_checkout` (`checkout_order_id`),
  UNIQUE KEY `uk_release_key` (`release_key`),
  UNIQUE KEY `UKi1c7keehfc13figoumvpteu2o` (`release_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `bofa_recipient_credits` (
  `release_id` binary(16) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `checkout_order_id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `currency` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipient_user_id` binary(16) NOT NULL,
  PRIMARY KEY (`release_id`),
  UNIQUE KEY `uk_credit_checkout` (`checkout_order_id`),
  KEY `idx_credit_recipient_currency` (`recipient_user_id`,`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `partner_escrow_mock` (
  `milestone_id` binary(16) NOT NULL,
  `client_id` binary(16) NOT NULL,
  `contract_id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `fee_usd` decimal(19,2) DEFAULT NULL,
  `freelancer_id` binary(16) NOT NULL,
  `freelancer_usd` decimal(19,2) DEFAULT NULL,
  `fund_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gross_usd` decimal(19,2) NOT NULL,
  `job_id` binary(16) NOT NULL,
  `payout_vnd` decimal(19,0) DEFAULT NULL,
  `rate_locked_at` datetime(6) DEFAULT NULL,
  `recipient_bank_code` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recipient_bank_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recipient_bank_last4` varchar(4) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `refund_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `release_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `usd_vnd_rate` decimal(19,6) DEFAULT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`milestone_id`),
  KEY `idx_partner_escrow_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `partner_statement_mock` (
  `id` binary(16) NOT NULL,
  `delta_usd` decimal(19,2) NOT NULL,
  `event_key` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `milestone_id` binary(16) NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_partner_statement_event` (`event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `role` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8sewwnpamngi6b1dwaa88askk` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `unified_fiat_exits` (
  `payment_flow_id` binary(16) NOT NULL,
  `beneficiary` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contract_id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `fee_usdc` decimal(19,6) NOT NULL,
  `gross_usd` decimal(19,2) NOT NULL,
  `gross_usdc` decimal(19,6) NOT NULL,
  `idempotency_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `job_id` binary(16) NOT NULL,
  `kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `milestone_id` binary(16) NOT NULL,
  `payout_vnd` decimal(19,0) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `vnd_rate` decimal(19,2) NOT NULL,
  `withdrawal_reference` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`payment_flow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `unified_mock_statements` (
  `event_key` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,6) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `kind` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `payment_flow_id` binary(16) NOT NULL,
  `reference` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `unified_usd_orders_mock` (
  `payment_flow_id` binary(16) NOT NULL,
  `client_id` binary(16) NOT NULL,
  `contract_id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `escrow_usdc` decimal(19,6) NOT NULL,
  `fund_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gross_usd` decimal(19,2) NOT NULL,
  `job_id` binary(16) NOT NULL,
  `milestone_id` binary(16) NOT NULL,
  `payer_bank_account_holder_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payer_bank_account_number` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payer_bank_code` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quote_expires_at` datetime(6) NOT NULL,
  `quote_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`payment_flow_id`),
  UNIQUE KEY `UKoi41ybuh647dvstbvnw114l6g` (`milestone_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `user` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `auth_provider` enum('LOCAL') COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` bit(1) NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `refresh_token` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKob8kqyqqgmefl0aco34akdtpe` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `user_role` (
  `user_id` binary(16) NOT NULL,
  `role_id` binary(16) NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `FKa68196081fvovjhkek5m97n3y` (`role_id`),
  CONSTRAINT `FK859n2jvi8ivhui0rl0esws6o` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `FKa68196081fvovjhkek5m97n3y` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


SET FOREIGN_KEY_CHECKS = 1;
