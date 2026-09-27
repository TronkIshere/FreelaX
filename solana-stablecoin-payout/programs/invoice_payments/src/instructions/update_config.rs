use anchor_lang::prelude::*;
use anchor_spl::token::Mint;

use crate::{constants::*, error::ErrorCode, state::Config};

#[derive(Accounts)]
pub struct UpdateConfig<'info> {
    pub admin: Signer<'info>,

    #[account(
        mut,
        seeds = [CONFIG_SEED],
        bump = config.bump,
        has_one = admin @ ErrorCode::UnauthorizedAdmin
    )]
    pub config: Account<'info, Config>,

    #[account(
        constraint = accepted_mint.decimals == USDC_DECIMALS
            @ ErrorCode::InvalidMintDecimals
    )]
    pub accepted_mint: Account<'info, Mint>,
}

pub fn handle_update_config(
    ctx: Context<UpdateConfig>,
    treasury_authority: Pubkey,
    rate_authority: Pubkey,
    oracle_authority: Pubkey,
    max_rate_age_seconds: i64,
    paused: bool,
) -> Result<()> {
    require!(
        treasury_authority != Pubkey::default(),
        ErrorCode::InvalidTreasuryAuthority
    );

    require!(
        oracle_authority != Pubkey::default(),
        ErrorCode::InvalidOracleAuthority
    );

    require!(
        rate_authority != Pubkey::default(),
        ErrorCode::InvalidRateAuthority
    );

    require_keys_neq!(
        rate_authority,
        oracle_authority,
        ErrorCode::DuplicateRateAndOracleAuthority
    );

    require!(max_rate_age_seconds > 0, ErrorCode::InvalidMaxRateAge);

    let config = &mut ctx.accounts.config;
    config.accepted_mint = ctx.accounts.accepted_mint.key();
    config.treasury_authority = treasury_authority;
    config.rate_authority = rate_authority;
    config.oracle_authority = oracle_authority;
    config.max_rate_age_seconds = max_rate_age_seconds;
    config.paused = paused;

    msg!("Config updated");
    msg!("Accepted mint: {}", config.accepted_mint);
    msg!("Treasury authority: {}", config.treasury_authority);
    msg!("Rate authority: {}", config.rate_authority);
    msg!("Oracle authority: {}", config.oracle_authority);
    msg!("Maximum rate age: {} seconds", config.max_rate_age_seconds);
    msg!("Paused: {}", config.paused);

    Ok(())
}
