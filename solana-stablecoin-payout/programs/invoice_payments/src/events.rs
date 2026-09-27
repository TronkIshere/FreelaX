use anchor_lang::prelude::*;

#[event]
pub struct InvoiceCreated {
    pub invoice: Pubkey,
    pub invoice_id: u64,
    pub freelancer: Pubkey,
    pub amount: u64,
    pub client: Pubkey,
    pub mint: Pubkey,
    pub rate_snapshot: Pubkey,
    pub expires_at: i64,
    pub created_at: i64,
}

#[event]
pub struct InvoicePaid {
    pub invoice: Pubkey,
    pub client: Pubkey,
    pub freelancer: Pubkey,
    pub amount: u64,
    pub mint: Pubkey,
    pub rate_snapshot: Pubkey,
    pub paid_at: i64,
}

#[event]
pub struct InvoiceCancelled {
    pub invoice: Pubkey,
    pub invoice_id: u64,
    pub freelancer: Pubkey,
    pub cancelled_at: i64,
}

#[event]
pub struct RatePublished {
    pub rate_snapshot: Pubkey,
    pub rate_id: u64,
    pub usdc_usd_e6: u64,
    pub usd_vnd_e6: u64,
    pub usdc_vnd_e6: u64,
    pub observed_at: i64,
    pub expires_at: i64,
    pub source_hash: [u8; 32],
}

#[event]
pub struct OfframpRequested {
    pub withdrawal_record: Pubkey,
    pub withdrawal_id: u64,
    pub freelancer: Pubkey,
    pub token_amount: u64,
    pub mint: Pubkey,
    pub treasury: Pubkey,
    pub rate_snapshot: Pubkey,
    pub fiat_amount_vnd: u64,
    pub requested_at: i64,
}

#[event]
pub struct OfframpCompleted {
    pub withdrawal_record: Pubkey,
    pub withdrawal_id: u64,
    pub freelancer: Pubkey,
    pub fiat_amount_vnd: u64,
    pub completed_at: i64,
}

#[event]
pub struct OfframpFailedPendingReview {
    pub withdrawal_record: Pubkey,
    pub withdrawal_id: u64,
    pub freelancer: Pubkey,
    pub failure_hash: [u8; 32],
    pub failed_at: i64,
}

#[event]
pub struct OfframpResolved {
    pub withdrawal_record: Pubkey,
    pub withdrawal_id: u64,
    pub freelancer: Pubkey,
    pub resolution_hash: [u8; 32],
    pub resolved_by: Pubkey,
    pub resolved_at: i64,
}

#[event]
pub struct MockOnrampConfigured {
    pub authority: Pubkey,
    pub max_amount: u64,
    pub enabled: bool,
    pub updated_by: Pubkey,
}

#[event]
pub struct MockOnrampCompleted {
    pub receipt: Pubkey,
    pub purchase_id: u64,
    pub client: Pubkey,
    pub client_ata: Pubkey,
    pub mint: Pubkey,
    pub treasury: Pubkey,
    pub usd_amount_e6: u64,
    pub token_amount: u64,
    pub completed_at: i64,
}
