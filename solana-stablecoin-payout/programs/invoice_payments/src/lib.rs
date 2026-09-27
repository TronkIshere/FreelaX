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

declare_id!("CwuaAPrxYLK6avPUbMRBerBYt1apdNU829TDZmoAnhEf");

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
}
