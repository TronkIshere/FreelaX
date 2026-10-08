use anchor_lang::prelude::*;

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, PartialEq, Eq, InitSpace)]
pub enum MilestoneEscrowStatus {
    Funded,
    Submitted,
    Revision,
    Disputed,
    Released,
    Refunded,
}

#[account]
#[derive(InitSpace)]
pub struct MilestoneEscrow {
    pub milestone_id: [u8; 16],
    pub client: Pubkey,
    pub freelancer: Pubkey,
    pub arbiter: Pubkey,
    pub mint: Pubkey,
    pub amount: u64,
    pub funding_expires_at: i64,
    pub original_delivery_due_at: i64,
    pub delivery_due_at: i64,
    pub requested_delivery_due_at: Option<i64>,
    pub extension_used: bool,
    pub review_window_seconds: i64,
    pub review_due_at: Option<i64>,
    pub submission_hash: Option<[u8; 32]>,
    pub submission_count: u8,
    pub revisions_used: u8,
    pub max_revisions: u8,
    pub status: MilestoneEscrowStatus,
    pub funded_at: i64,
    pub settled_at: Option<i64>,
    pub dispute_hash: Option<[u8; 32]>,
    pub disputed_by: Option<Pubkey>,
    pub disputed_at: Option<i64>,
    pub resolution_hash: Option<[u8; 32]>,
    pub revision_hash: Option<[u8; 32]>,
    pub bump: u8,
}
