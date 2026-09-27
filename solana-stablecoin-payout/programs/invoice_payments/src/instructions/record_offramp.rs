use anchor_lang::prelude::*;

use crate::{
    constants::*,
    error::ErrorCode,
    events::OfframpCompleted,
    state::{Config, WithdrawalRecord, WithdrawalStatus},
};

#[derive(Accounts)]
pub struct RecordOfframp<'info> {
    #[account(
        address = config.oracle_authority @ ErrorCode::UnauthorizedOracle
    )]
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

pub fn handle_record_offramp(ctx: Context<RecordOfframp>) -> Result<()> {
    let completed_at = Clock::get()?.unix_timestamp;
    let withdrawal_record = &mut ctx.accounts.withdrawal_record;

    withdrawal_record.status = WithdrawalStatus::Completed;
    withdrawal_record.completed_at = Some(completed_at);

    emit!(OfframpCompleted {
        withdrawal_record: withdrawal_record.key(),
        withdrawal_id: withdrawal_record.withdrawal_id,
        freelancer: withdrawal_record.freelancer,
        fiat_amount_vnd: withdrawal_record.fiat_amount_vnd,
        completed_at,
    });

    Ok(())
}
