use anchor_lang::prelude::*;

use crate::{
    constants::*,
    error::ErrorCode,
    events::OfframpResolved,
    state::{Config, WithdrawalRecord, WithdrawalStatus},
};

#[derive(Accounts)]
pub struct ResolveOfframp<'info> {
    #[account(address = config.admin @ ErrorCode::UnauthorizedAdmin)]
    pub admin: Signer<'info>,

    #[account(
        seeds = [CONFIG_SEED],
        bump = config.bump,
        constraint = !config.paused @ ErrorCode::SystemPaused
    )]
    pub config: Account<'info, Config>,

    #[account(
        mut,
        seeds = [
            WITHDRAWAL_SEED,
            withdrawal_record.freelancer.as_ref(),
            withdrawal_record.withdrawal_id.to_le_bytes().as_ref()
        ],
        bump = withdrawal_record.bump,
        constraint = withdrawal_record.status == WithdrawalStatus::FailedPendingReview
            @ ErrorCode::WithdrawalNotPendingReview
    )]
    pub withdrawal_record: Account<'info, WithdrawalRecord>,
}

pub fn handle_resolve_offramp(
    ctx: Context<ResolveOfframp>,
    resolution_hash: [u8; 32],
) -> Result<()> {
    require!(resolution_hash != [0; 32], ErrorCode::InvalidResolutionHash);

    let resolved_at = Clock::get()?.unix_timestamp;
    let resolved_by = ctx.accounts.admin.key();
    let withdrawal_record = &mut ctx.accounts.withdrawal_record;
    withdrawal_record.status = WithdrawalStatus::Completed;
    withdrawal_record.completed_at = Some(resolved_at);
    withdrawal_record.resolution_hash = Some(resolution_hash);
    withdrawal_record.resolved_at = Some(resolved_at);
    withdrawal_record.resolved_by = Some(resolved_by);

    emit!(OfframpResolved {
        withdrawal_record: withdrawal_record.key(),
        withdrawal_id: withdrawal_record.withdrawal_id,
        freelancer: withdrawal_record.freelancer,
        resolution_hash,
        resolved_by,
        resolved_at,
    });

    Ok(())
}
