use anchor_lang::prelude::*;

use crate::{constants::*, error::ErrorCode, events::MockOnrampConfigured, state::Config};

#[derive(Accounts)]
pub struct ConfigureMockOnramp<'info> {
    pub admin: Signer<'info>,

    #[account(
        mut,
        seeds = [CONFIG_SEED],
        bump = config.bump,
        has_one = admin @ ErrorCode::UnauthorizedAdmin
    )]
    pub config: Account<'info, Config>,
}

pub fn handle_configure_mock_onramp(
    ctx: Context<ConfigureMockOnramp>,
    authority: Pubkey,
    max_amount: u64,
    enabled: bool,
) -> Result<()> {
    require!(
        authority != Pubkey::default(),
        ErrorCode::InvalidMockOnrampAuthority
    );
    require!(max_amount > 0, ErrorCode::InvalidMaxMockOnrampAmount);

    let config = &mut ctx.accounts.config;
    config.mock_onramp_authority = authority;
    config.max_mock_onramp_amount = max_amount;
    config.mock_onramp_enabled = enabled;

    emit!(MockOnrampConfigured {
        authority,
        max_amount,
        enabled,
        updated_by: ctx.accounts.admin.key(),
    });

    Ok(())
}
