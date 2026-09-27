use anchor_lang::prelude::*;

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, PartialEq, Eq, InitSpace)]
pub enum WithdrawalStatus {
    Pending,
    FailedPendingReview,
    Completed,
}

#[account]
#[derive(InitSpace)]
pub struct WithdrawalRecord {
    pub withdrawal_id: u64,
    pub freelancer: Pubkey,
    pub token_amount: u64,
    pub mint: Pubkey,
    pub treasury: Pubkey,
    pub rate_snapshot: Pubkey,
    pub fiat_amount_vnd: u64,
    pub status: WithdrawalStatus,
    pub requested_at: i64,
    pub completed_at: Option<i64>,
    pub failure_hash: Option<[u8; 32]>,
    pub failed_at: Option<i64>,
    pub resolution_hash: Option<[u8; 32]>,
    pub resolved_at: Option<i64>,
    pub resolved_by: Option<Pubkey>,
    pub bump: u8,
}
