-- Avalanche payout rail, fee and tax figures (owner decision 2026-11-05: mock partner only; fee and tax reuse the existing
-- Solana-rail rules, labelled simulated). Additive and forward-only: new NULLABLE columns only; no existing table, column or
-- value is changed. Rows created before V9 keep NULL here and are reported with the treatment NOT_DEFINED.
-- These are simulated accounting figures recorded next to the payout request; they never change the on-chain amount, which
-- the freelancer's own wallet transfers in full (non-custodial; FreelaX moves no funds).
ALTER TABLE avalanche_payout_requests
  ADD COLUMN platform_fee_usd DECIMAL(19,2) NULL,
  ADD COLUMN tax_usd_to_vnd_rate DECIMAL(20,2) NULL,
  ADD COLUMN tax_rate_source VARCHAR(32) NULL,
  ADD COLUMN tax_rate_observed_at DATETIME(6) NULL,
  ADD COLUMN taxable_amount_vnd DECIMAL(20,0) NULL,
  ADD COLUMN tax_treatment VARCHAR(40) NULL;
