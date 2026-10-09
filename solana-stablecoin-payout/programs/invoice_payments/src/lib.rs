pub mod constants;
pub mod error;
pub mod events;
pub mod instructions;
pub mod state;

use anchor_lang::prelude::*;

pub use constants::*;
pub use events::*;
pub use instructions::*;
pub use state::*;

declare_id!("2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb");

#[program]
pub mod invoice_payments {
    use super::*;

    pub fn initialize_config(
        ctx: Context<InitializeConfig>,
        treasury_authority: Pubkey,
        rate_authority: Pubkey,
        oracle_authority: Pubkey,
        max_rate_age_seconds: i64,
    ) -> Result<()> {
        crate::instructions::initialize_config::handle_initialize_config(
            ctx,
            treasury_authority,
            rate_authority,
            oracle_authority,
            max_rate_age_seconds,
        )
    }

    pub fn update_config(
        ctx: Context<UpdateConfig>,
        treasury_authority: Pubkey,
        rate_authority: Pubkey,
        oracle_authority: Pubkey,
        max_rate_age_seconds: i64,
        paused: bool,
    ) -> Result<()> {
        crate::instructions::update_config::handle_update_config(
            ctx,
            treasury_authority,
            rate_authority,
            oracle_authority,
            max_rate_age_seconds,
            paused,
        )
    }

    pub fn create_invoice(
        ctx: Context<CreateInvoice>,
        invoice_id: u64,
        client: Pubkey,
        amount: u64,
        expires_at: i64,
    ) -> Result<()> {
        crate::instructions::handle_create_invoice(ctx, invoice_id, client, amount, expires_at)
    }

    pub fn pay_invoice(ctx: Context<PayInvoice>) -> Result<()> {
        crate::instructions::pay_invoice::handle_pay_invoice(ctx)
    }

    pub fn cancel_invoice(ctx: Context<CancelInvoice>) -> Result<()> {
        crate::instructions::cancel_invoice::handle_cancel_invoice(ctx)
    }

    pub fn close_invoice(ctx: Context<CloseInvoice>) -> Result<()> {
        crate::instructions::close_invoice::handle_close_invoice(ctx)
    }

    pub fn publish_rate(
        ctx: Context<PublishRate>,
        rate_id: u64,
        usdc_usd_e6: u64,
        usd_vnd_e6: u64,
        observed_at: i64,
        expires_at: i64,
        source_hash: [u8; 32],
    ) -> Result<()> {
        crate::instructions::publish_rate::handle_publish_rate(
            ctx,
            rate_id,
            usdc_usd_e6,
            usd_vnd_e6,
            observed_at,
            expires_at,
            source_hash,
        )
    }

    pub fn request_offramp(
        ctx: Context<RequestOfframp>,
        withdrawal_id: u64,
        token_amount: u64,
    ) -> Result<()> {
        crate::instructions::request_offramp::handle_request_offramp(
            ctx,
            withdrawal_id,
            token_amount,
        )
    }

    pub fn record_offramp(ctx: Context<RecordOfframp>) -> Result<()> {
        crate::instructions::record_offramp::handle_record_offramp(ctx)
    }

    pub fn mark_offramp_failed(
        ctx: Context<MarkOfframpFailed>,
        failure_hash: [u8; 32],
    ) -> Result<()> {
        crate::instructions::mark_offramp_failed::handle_mark_offramp_failed(ctx, failure_hash)
    }

    pub fn resolve_offramp(ctx: Context<ResolveOfframp>, resolution_hash: [u8; 32]) -> Result<()> {
        crate::instructions::resolve_offramp::handle_resolve_offramp(ctx, resolution_hash)
    }

    pub fn configure_mock_onramp(
        ctx: Context<ConfigureMockOnramp>,
        authority: Pubkey,
        max_amount: u64,
        enabled: bool,
    ) -> Result<()> {
        crate::instructions::configure_mock_onramp::handle_configure_mock_onramp(
            ctx, authority, max_amount, enabled,
        )
    }

    pub fn mock_onramp(
        ctx: Context<MockOnramp>,
        purchase_id: u64,
        usd_amount_e6: u64,
    ) -> Result<()> {
        crate::instructions::mock_onramp::handle_mock_onramp(ctx, purchase_id, usd_amount_e6)
    }

    pub fn fund_milestone_escrow(
        ctx: Context<FundMilestoneEscrow>, milestone_id: [u8; 16],
        freelancer: Pubkey, amount: u64, funding_expires_at: i64, delivery_due_at: i64,
        review_window_hours: u16, max_revisions: u8, high_value_review_grace: bool,
    ) -> Result<()> {
        crate::instructions::milestone_escrow::fund(ctx, milestone_id, freelancer,
            amount, funding_expires_at, delivery_due_at, review_window_hours, max_revisions,
            high_value_review_grace)
    }

    pub fn request_escrow_extension(ctx: Context<FreelancerEscrowAction>, new_due_at: i64) -> Result<()> {
        crate::instructions::milestone_escrow::request_extension(ctx, new_due_at)
    }

    pub fn approve_escrow_extension(ctx: Context<ClientEscrowAction>) -> Result<()> {
        crate::instructions::milestone_escrow::approve_extension(ctx)
    }

    pub fn submit_escrow_work(ctx: Context<SubmitEscrowWork>, evidence_hash: [u8; 32]) -> Result<()> {
        crate::instructions::milestone_escrow::submit(ctx, evidence_hash)
    }

    pub fn request_escrow_revision(ctx: Context<ClientEscrowRevisionAction>, feedback_hash: [u8; 32]) -> Result<()> {
        crate::instructions::milestone_escrow::request_revision(ctx, feedback_hash)
    }

    pub fn open_escrow_dispute(ctx: Context<ParticipantEscrowAction>, reason_hash: [u8; 32]) -> Result<()> {
        crate::instructions::milestone_escrow::open_dispute(ctx, reason_hash)
    }

    pub fn settle_milestone_escrow(ctx: Context<SettleMilestoneEscrow>, release: bool,
        resolution_hash: [u8; 32]) -> Result<()> {
        crate::instructions::milestone_escrow::settle(ctx, release, resolution_hash)
    }

    pub fn refund_mutual_escrow(ctx: Context<RefundMutualEscrow>) -> Result<()> {
        crate::instructions::milestone_escrow::refund_mutual(ctx)
    }
}
