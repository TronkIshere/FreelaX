use anchor_lang::prelude::*;

use crate::{
    constants::*,
    error::ErrorCode,
    events::RatePublished,
    state::{Config, RateSnapshot},
};

#[derive(Accounts)]
#[instruction(rate_id: u64)]
pub struct PublishRate<'info> {
    #[account(
        mut,
        address = config.rate_authority @ ErrorCode::UnauthorizedRateAuthority
    )]
    pub rate_authority: Signer<'info>,

    #[account(
        seeds = [CONFIG_SEED],
        bump = config.bump,
        constraint = !config.paused @ ErrorCode::SystemPaused
    )]
    pub config: Account<'info, Config>,

    #[account(
        init,
        payer = rate_authority,
        space = 8 + RateSnapshot::INIT_SPACE,
        seeds = [RATE_SEED, rate_id.to_le_bytes().as_ref()],
        bump
    )]
    pub rate_snapshot: Account<'info, RateSnapshot>,

    pub system_program: Program<'info, System>,
}

#[allow(clippy::too_many_arguments)]
pub fn handle_publish_rate(
    ctx: Context<PublishRate>,
    rate_id: u64,
    usdc_usd_e6: u64,
    usd_vnd_e6: u64,
    observed_at: i64,
    expires_at: i64,
    source_hash: [u8; 32],
) -> Result<()> {
    require!(
        usdc_usd_e6 > 0 && usd_vnd_e6 > 0,
        ErrorCode::InvalidRateValue
    );

    let current_time = Clock::get()?.unix_timestamp;
    let latest_valid_observation = current_time
        .checked_add(MAX_RATE_FUTURE_DRIFT_SECONDS)
        .ok_or(ErrorCode::RateCalculationOverflow)?;

    require!(
        observed_at <= latest_valid_observation,
        ErrorCode::RateObservedInFuture
    );
    require!(expires_at > observed_at, ErrorCode::InvalidRateExpiration);

    let lifetime = expires_at
        .checked_sub(observed_at)
        .ok_or(ErrorCode::InvalidRateExpiration)?;
    require!(
        lifetime <= ctx.accounts.config.max_rate_age_seconds,
        ErrorCode::RateLifetimeTooLong
    );

    let scaled_rate = (usdc_usd_e6 as u128)
        .checked_mul(usd_vnd_e6 as u128)
        .and_then(|product| product.checked_div(RATE_SCALE as u128))
        .ok_or(ErrorCode::RateCalculationOverflow)?;
    let usdc_vnd_e6 = u64::try_from(scaled_rate).map_err(|_| ErrorCode::RateCalculationOverflow)?;

    let rate_snapshot = &mut ctx.accounts.rate_snapshot;
    rate_snapshot.rate_id = rate_id;
    rate_snapshot.usdc_usd_e6 = usdc_usd_e6;
    rate_snapshot.usd_vnd_e6 = usd_vnd_e6;
    rate_snapshot.usdc_vnd_e6 = usdc_vnd_e6;
    rate_snapshot.observed_at = observed_at;
    rate_snapshot.expires_at = expires_at;
    rate_snapshot.source_hash = source_hash;
    rate_snapshot.publisher = ctx.accounts.rate_authority.key();
    rate_snapshot.bump = ctx.bumps.rate_snapshot;

    emit!(RatePublished {
        rate_snapshot: rate_snapshot.key(),
        rate_id,
        usdc_usd_e6,
        usd_vnd_e6,
        usdc_vnd_e6,
        observed_at,
        expires_at,
        source_hash,
    });

    Ok(())
}
