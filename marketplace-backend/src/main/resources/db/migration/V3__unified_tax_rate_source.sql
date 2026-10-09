-- Unified payouts certify income at the quote locked on the withdrawal.
ALTER TABLE `tax_certificate_records`
  MODIFY COLUMN `rate_source` enum('FALLBACK_PLACEHOLDER','LIVE_COINGECKO','LIVE_OPEN_ER_API','LOCKED_PAYOUT_QUOTE')
    COLLATE utf8mb4_unicode_ci NOT NULL;
