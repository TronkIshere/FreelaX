use anchor_lang::prelude::*;
use anchor_spl::{
    associated_token::AssociatedToken,
    token::{self, Mint, Token, TokenAccount, TransferChecked},
};

use crate::{
    constants::*,
    error::ErrorCode,
    events::MockOnrampCompleted,
    state::{Config, MockOnrampReceipt},
};

#[derive(Accounts)]
#[instruction(purchase_id: u64)]
pub struct MockOnramp<'info> {
    #[account(
        mut,
        address = config.mock_onramp_authority
            @ ErrorCode::UnauthorizedMockOnrampAuthority
    )]
    pub onramp_authority: Signer<'info>,

    #[account(
        seeds = [CONFIG_SEED],
        bump = config.bump,
        constraint = !config.paused @ ErrorCode::SystemPaused,
        constraint = config.mock_onramp_enabled @ ErrorCode::MockOnrampDisabled
    )]
    pub config: Account<'info, Config>,

    #[account(address = config.accepted_mint @ ErrorCode::InvalidInvoiceMint)]
    pub accepted_mint: Account<'info, Mint>,

    /// CHECK: This is a deterministic program PDA and no account data is read.
    #[account(
        seeds = [MOCK_ONRAMP_TREASURY_AUTHORITY_SEED],
        bump
    )]
    pub mock_onramp_treasury_authority: UncheckedAccount<'info>,

    #[account(
        mut,
        associated_token::mint = accepted_mint,
        associated_token::authority = mock_onramp_treasury_authority,
        associated_token::token_program = token_program
    )]
    pub mock_onramp_treasury_ata: Account<'info, TokenAccount>,

    /// CHECK: The public key is stored in the receipt and used as ATA authority.
    #[account(constraint = client.key() != Pubkey::default() @ ErrorCode::InvalidClient)]
    pub client: UncheckedAccount<'info>,

    #[account(
        init_if_needed,
        payer = onramp_authority,
        associated_token::mint = accepted_mint,
        associated_token::authority = client,
        associated_token::token_program = token_program
    )]
    pub client_ata: Account<'info, TokenAccount>,

    #[account(
        init,
        payer = onramp_authority,
        space = 8 + MockOnrampReceipt::INIT_SPACE,
        seeds = [
            MOCK_ONRAMP_RECEIPT_SEED,
            client.key().as_ref(),
            purchase_id.to_le_bytes().as_ref()
        ],
        bump
    )]
    pub mock_onramp_receipt: Account<'info, MockOnrampReceipt>,

    pub associated_token_program: Program<'info, AssociatedToken>,
    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

pub fn handle_mock_onramp(
    ctx: Context<MockOnramp>,
    purchase_id: u64,
    usd_amount_e6: u64,
) -> Result<()> {
    require!(usd_amount_e6 > 0, ErrorCode::InvalidMockOnrampAmount);
    require!(
        usd_amount_e6 <= ctx.accounts.config.max_mock_onramp_amount,
        ErrorCode::MockOnrampAmountTooLarge
    );

    // Demo peg: USD and Mock USDC both use 10^6 scale, so the base-unit
    // amount is identical. No floating-point arithmetic is used on-chain.
    let token_amount = usd_amount_e6;
    let treasury_bump = ctx.bumps.mock_onramp_treasury_authority;
    let signer_seeds: &[&[u8]] = &[MOCK_ONRAMP_TREASURY_AUTHORITY_SEED, &[treasury_bump]];
    let signer = &[signer_seeds];

    token::transfer_checked(
        CpiContext::new_with_signer(
            ctx.accounts.token_program.key(),
            TransferChecked {
                from: ctx.accounts.mock_onramp_treasury_ata.to_account_info(),
                mint: ctx.accounts.accepted_mint.to_account_info(),
                to: ctx.accounts.client_ata.to_account_info(),
                authority: ctx
                    .accounts
                    .mock_onramp_treasury_authority
                    .to_account_info(),
            },
            signer,
        ),
        token_amount,
        ctx.accounts.accepted_mint.decimals,
    )?;

    let completed_at = Clock::get()?.unix_timestamp;
    let receipt = &mut ctx.accounts.mock_onramp_receipt;
    receipt.purchase_id = purchase_id;
    receipt.client = ctx.accounts.client.key();
    receipt.client_ata = ctx.accounts.client_ata.key();
    receipt.mint = ctx.accounts.accepted_mint.key();
    receipt.treasury = ctx.accounts.mock_onramp_treasury_ata.key();
    receipt.usd_amount_e6 = usd_amount_e6;
    receipt.token_amount = token_amount;
    receipt.authority = ctx.accounts.onramp_authority.key();
    receipt.completed_at = completed_at;
    receipt.bump = ctx.bumps.mock_onramp_receipt;

    emit!(MockOnrampCompleted {
        receipt: receipt.key(),
        purchase_id,
        client: receipt.client,
        client_ata: receipt.client_ata,
        mint: receipt.mint,
        treasury: receipt.treasury,
        usd_amount_e6,
        token_amount,
        completed_at,
    });

    Ok(())
}
