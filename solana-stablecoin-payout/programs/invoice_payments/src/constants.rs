use anchor_lang::prelude::*;

#[constant]
pub const CONFIG_SEED: &[u8] = b"config";

#[constant]
pub const USDC_DECIMALS: u8 = 6;

#[constant]
pub const INVOICE_SEED: &[u8] = b"invoice";

#[constant]
pub const RATE_SEED: &[u8] = b"rate";

#[constant]
pub const RATE_SCALE: u64 = 1_000_000;

#[constant]
pub const MAX_RATE_FUTURE_DRIFT_SECONDS: i64 = 60;

#[constant]
pub const WITHDRAWAL_SEED: &[u8] = b"withdrawal";

#[constant]
pub const MOCK_ONRAMP_TREASURY_AUTHORITY_SEED: &[u8] = b"mock_onramp_treasury";

#[constant]
pub const MOCK_ONRAMP_RECEIPT_SEED: &[u8] = b"mock_onramp";

#[constant]
pub const MILESTONE_ESCROW_SEED: &[u8] = b"milestone_escrow";

/// USDC base-unit scale (10^6) multiplied by the rate scale (10^6).
pub const FIAT_CALCULATION_SCALE: u128 = 1_000_000_000_000;
