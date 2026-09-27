use anchor_lang::prelude::*;
use anchor_spl::token::{self, Mint, Token, TokenAccount, TransferChecked};

use crate::{
    constants::*,
    error::ErrorCode,
    events::OfframpRequested,
    state::{Config, RateSnapshot, WithdrawalRecord, WithdrawalStatus},
};

#[derive(Accounts)]
#[instruction(withdrawal_id: u64)]
pub struct RequestOfframp<'info> {
    #[account(mut)]
    pub freelancer: Signer<'info>,

    #[account(
        seeds = [CONFIG_SEED],
        bump = config.bump,
        constraint = !config.paused @ ErrorCode::SystemPaused
    )]
    pub config: Account<'info, Config>,

    #[account(
        seeds = [RATE_SEED, rate_snapshot.rate_id.to_le_bytes().as_ref()],
        bump = rate_snapshot.bump,
        constraint = rate_snapshot.publisher == config.rate_authority
            @ ErrorCode::InvalidRatePublisher
    )]
    pub rate_snapshot: Account<'info, RateSnapshot>,

    #[account(
        address = config.accepted_mint @ ErrorCode::InvalidInvoiceMint
    )]
    pub accepted_mint: Account<'info, Mint>,

    #[account(
        mut,
        associated_token::mint = accepted_mint,
        associated_token::authority = freelancer,
        associated_token::token_program = token_program
    )]
    pub freelancer_ata: Account<'info, TokenAccount>,

    /// CHECK: Only the address stored in Config is accepted. No account data is read.
    #[account(address = config.treasury_authority)]
    pub treasury_authority: UncheckedAccount<'info>,

    #[account(
        mut,
        associated_token::mint = accepted_mint,
        associated_token::authority = treasury_authority,
        associated_token::token_program = token_program
    )]
    pub treasury_ata: Account<'info, TokenAccount>,

    #[account(
        init,
        payer = freelancer,
        space = 8 + WithdrawalRecord::INIT_SPACE,
        seeds = [
            WITHDRAWAL_SEED,
            freelancer.key().as_ref(),
            withdrawal_id.to_le_bytes().as_ref()
        ],
        bump
    )]
    pub withdrawal_record: Account<'info, WithdrawalRecord>,

    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

pub fn handle_request_offramp(
    ctx: Context<RequestOfframp>,
    withdrawal_id: u64,
    token_amount: u64,
) -> Result<()> {
    require!(token_amount > 0, ErrorCode::InvalidWithdrawalAmount);

    let current_time = Clock::get()?.unix_timestamp;
    let rate_snapshot = &ctx.accounts.rate_snapshot;

    require!(
        current_time < rate_snapshot.expires_at,
        ErrorCode::RateSnapshotExpired
    );

    let oldest_allowed_observation = current_time
        .checked_sub(ctx.accounts.config.max_rate_age_seconds)
        .ok_or(ErrorCode::FiatCalculationOverflow)?;
    require!(
        rate_snapshot.observed_at >= oldest_allowed_observation,
        ErrorCode::RateSnapshotTooOld
    );

    let fiat_amount = (token_amount as u128)
        .checked_mul(rate_snapshot.usdc_vnd_e6 as u128)
        .and_then(|product| product.checked_div(FIAT_CALCULATION_SCALE))
        .ok_or(ErrorCode::FiatCalculationOverflow)?;
    let fiat_amount_vnd =
        u64::try_from(fiat_amount).map_err(|_| ErrorCode::FiatCalculationOverflow)?;
    require!(fiat_amount_vnd > 0, ErrorCode::FiatAmountTooSmall);

    let transfer_accounts = TransferChecked {
        from: ctx.accounts.freelancer_ata.to_account_info(),
        mint: ctx.accounts.accepted_mint.to_account_info(),
        to: ctx.accounts.treasury_ata.to_account_info(),
        authority: ctx.accounts.freelancer.to_account_info(),
    };
    token::transfer_checked(
        CpiContext::new(ctx.accounts.token_program.key(), transfer_accounts),
        token_amount,
        ctx.accounts.accepted_mint.decimals,
    )?;

    let withdrawal_record = &mut ctx.accounts.withdrawal_record;
    withdrawal_record.withdrawal_id = withdrawal_id;
    withdrawal_record.freelancer = ctx.accounts.freelancer.key();
    withdrawal_record.token_amount = token_amount;
    withdrawal_record.mint = ctx.accounts.accepted_mint.key();
    withdrawal_record.treasury = ctx.accounts.treasury_ata.key();
    withdrawal_record.rate_snapshot = rate_snapshot.key();
    withdrawal_record.fiat_amount_vnd = fiat_amount_vnd;
    withdrawal_record.status = WithdrawalStatus::Pending;
    withdrawal_record.requested_at = current_time;
    withdrawal_record.completed_at = None;
    withdrawal_record.failure_hash = None;
    withdrawal_record.failed_at = None;
    withdrawal_record.resolution_hash = None;
    withdrawal_record.resolved_at = None;
    withdrawal_record.resolved_by = None;
    withdrawal_record.bump = ctx.bumps.withdrawal_record;

    emit!(OfframpRequested {
        withdrawal_record: withdrawal_record.key(),
        withdrawal_id,
        freelancer: withdrawal_record.freelancer,
        token_amount,
        mint: withdrawal_record.mint,
        treasury: withdrawal_record.treasury,
        rate_snapshot: withdrawal_record.rate_snapshot,
        fiat_amount_vnd,
        requested_at: current_time,
    });

    Ok(())
}
