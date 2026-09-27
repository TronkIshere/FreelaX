use anchor_lang::prelude::*;
use anchor_spl::token::Mint;

use crate::{constants::*, error::ErrorCode, state::Config};

#[derive(Accounts)]
pub struct InitializeConfig<'info> {
    #[account(mut)]
    pub admin: Signer<'info>,

    #[account(
        init,
        payer = admin,
        space = 8 + Config::INIT_SPACE,
        seeds = [CONFIG_SEED],
        bump
    )]
    pub config: Account<'info, Config>,

    /// Anchor xác minh account này thuộc Legacy Token Program
    /// và có cấu trúc dữ liệu hợp lệ của một Mint.
    #[account(
        constraint = accepted_mint.decimals == USDC_DECIMALS
            @ ErrorCode::InvalidMintDecimals
    )]
    pub accepted_mint: Account<'info, Mint>,

    #[account(address = crate::ID)]
    pub program: Program<'info, crate::program::InvoicePayments>,

    #[account(
        constraint = program.programdata_address()? == Some(program_data.key())
            @ ErrorCode::UnauthorizedInitializer,
        constraint = program_data.upgrade_authority_address == Some(admin.key())
            @ ErrorCode::UnauthorizedInitializer
    )]
    pub program_data: Account<'info, ProgramData>,

    pub system_program: Program<'info, System>,
}

pub fn handle_initialize_config(
    ctx: Context<InitializeConfig>,
    treasury_authority: Pubkey,
    rate_authority: Pubkey,
    oracle_authority: Pubkey,
    max_rate_age_seconds: i64,
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

    config.admin = ctx.accounts.admin.key();
    config.accepted_mint = ctx.accounts.accepted_mint.key();
    config.treasury_authority = treasury_authority;
    config.rate_authority = rate_authority;
    config.oracle_authority = oracle_authority;
    config.max_rate_age_seconds = max_rate_age_seconds;
    config.mock_onramp_authority = Pubkey::default();
    config.max_mock_onramp_amount = 0;
    config.mock_onramp_enabled = false;
    config.paused = false;
    config.bump = ctx.bumps.config;

    msg!("Config initialized");
    msg!("Admin: {}", config.admin);
    msg!("Accepted mint: {}", config.accepted_mint);
    msg!("Treasury authority: {}", config.treasury_authority);
    msg!("Rate authority: {}", config.rate_authority);
    msg!("Oracle authority: {}", config.oracle_authority);
    msg!("Maximum rate age: {} seconds", config.max_rate_age_seconds);

    Ok(())
}
