use anchor_lang::prelude::*;

use crate::{
    constants::*,
    error::ErrorCode,
    events::OfframpFailedPendingReview,
    state::{Config, WithdrawalRecord, WithdrawalStatus},
};

#[derive(Accounts)]
pub struct MarkOfframpFailed<'info> {
    #[account(address = config.oracle_authority @ ErrorCode::UnauthorizedOracle)]
    pub oracle_authority: Signer<'info>,

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
        constraint = withdrawal_record.status == WithdrawalStatus::Pending
            @ ErrorCode::WithdrawalNotPending
    )]
    pub withdrawal_record: Account<'info, WithdrawalRecord>,
}

pub fn handle_mark_offramp_failed(
    ctx: Context<MarkOfframpFailed>,
    failure_hash: [u8; 32],
) -> Result<()> {
    require!(failure_hash != [0; 32], ErrorCode::InvalidFailureHash);

    let failed_at = Clock::get()?.unix_timestamp;
    let withdrawal_record = &mut ctx.accounts.withdrawal_record;
    withdrawal_record.status = WithdrawalStatus::FailedPendingReview;
    withdrawal_record.failure_hash = Some(failure_hash);
    withdrawal_record.failed_at = Some(failed_at);

    emit!(OfframpFailedPendingReview {
        withdrawal_record: withdrawal_record.key(),
        withdrawal_id: withdrawal_record.withdrawal_id,
        freelancer: withdrawal_record.freelancer,
        failure_hash,
        failed_at,
    });

    Ok(())
}
