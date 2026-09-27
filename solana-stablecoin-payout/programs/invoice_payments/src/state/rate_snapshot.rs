use anchor_lang::prelude::*;

#[account]
#[derive(InitSpace)]
pub struct RateSnapshot {
    pub rate_id: u64,
    pub usdc_usd_e6: u64,
    pub usd_vnd_e6: u64,
    pub usdc_vnd_e6: u64,
    pub observed_at: i64,
    pub expires_at: i64,
    pub source_hash: [u8; 32],
    pub publisher: Pubkey,
    pub bump: u8,
}
