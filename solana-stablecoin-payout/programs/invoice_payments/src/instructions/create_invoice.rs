use anchor_lang::prelude::*;

use crate::{
    constants::*,
    error::ErrorCode,
    events::InvoiceCreated,
    state::{Config, Invoice, InvoiceStatus, RateSnapshot},
};

#[derive(Accounts)]
#[instruction(invoice_id: u64)]
pub struct CreateInvoice<'info> {
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
        init,
        payer = freelancer,
        space = 8 + Invoice::INIT_SPACE,
        seeds = [
            INVOICE_SEED,
            freelancer.key().as_ref(),
            invoice_id.to_le_bytes().as_ref()
        ],
        bump
    )]
    pub invoice: Account<'info, Invoice>,

    pub system_program: Program<'info, System>,
}

pub fn handle_create_invoice(
    ctx: Context<CreateInvoice>,
    invoice_id: u64,
    client: Pubkey,
    amount: u64,
    expires_at: i64,
) -> Result<()> {
    require!(amount > 0, ErrorCode::InvalidInvoiceAmount);

    require!(client != Pubkey::default(), ErrorCode::InvalidClient);

    let created_at = Clock::get()?.unix_timestamp;
    let rate_snapshot = &ctx.accounts.rate_snapshot;
    require!(
        created_at < rate_snapshot.expires_at,
        ErrorCode::RateSnapshotExpired
    );
    let oldest_allowed_observation = created_at
        .checked_sub(ctx.accounts.config.max_rate_age_seconds)
        .ok_or(ErrorCode::FiatCalculationOverflow)?;
    require!(
        rate_snapshot.observed_at >= oldest_allowed_observation,
        ErrorCode::RateSnapshotTooOld
    );
    require!(
        expires_at > created_at && expires_at <= rate_snapshot.expires_at,
        ErrorCode::InvalidInvoiceExpiration
    );
    let invoice = &mut ctx.accounts.invoice;

    invoice.invoice_id = invoice_id;
    invoice.freelancer = ctx.accounts.freelancer.key();
    invoice.client = client;
    invoice.amount = amount;

    invoice.mint = ctx.accounts.config.accepted_mint;
    invoice.rate_snapshot = rate_snapshot.key();
    invoice.expires_at = expires_at;
    invoice.status = InvoiceStatus::Pending;
    invoice.created_at = created_at;
    invoice.paid_at = None;
    invoice.bump = ctx.bumps.invoice;

    emit!(InvoiceCreated {
        invoice: invoice.key(),
        invoice_id,
        freelancer: invoice.freelancer,
        client,
        amount,
        mint: invoice.mint,
        rate_snapshot: invoice.rate_snapshot,
        expires_at,
        created_at,
    });

    msg!("Invoice created: {}", invoice.key());
    msg!("Invoice ID: {}", invoice_id);
    msg!("Freelancer: {}", invoice.freelancer);
    msg!("Client: {}", client);
    msg!("Amount: {}", amount);

    Ok(())
}
