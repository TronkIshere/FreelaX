use anchor_lang::prelude::*;

#[account]
#[derive(InitSpace)]
pub struct MockOnrampReceipt {
    pub purchase_id: u64,
    pub client: Pubkey,
    pub client_ata: Pubkey,
    pub mint: Pubkey,
    pub treasury: Pubkey,
    pub usd_amount_e6: u64,
    pub token_amount: u64,
    pub authority: Pubkey,
    pub completed_at: i64,
    pub bump: u8,
}
