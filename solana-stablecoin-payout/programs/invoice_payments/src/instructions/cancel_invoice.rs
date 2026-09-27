use anchor_lang::prelude::*;

use crate::{
    constants::*,
    error::ErrorCode,
    events::InvoiceCancelled,
    state::{Config, Invoice, InvoiceStatus},
};

#[derive(Accounts)]
pub struct CancelInvoice<'info> {
    pub freelancer: Signer<'info>,

    #[account(
        seeds = [CONFIG_SEED],
        bump = config.bump,
        constraint = !config.paused @ ErrorCode::SystemPaused
    )]
    pub config: Account<'info, Config>,

    #[account(
        mut,
        seeds = [
            INVOICE_SEED,
            invoice.freelancer.as_ref(),
            invoice.invoice_id.to_le_bytes().as_ref()
        ],
        bump = invoice.bump,
        has_one = freelancer @ ErrorCode::UnauthorizedFreelancer,
        constraint = invoice.status == InvoiceStatus::Pending
            @ ErrorCode::InvoiceNotCancellable
    )]
    pub invoice: Account<'info, Invoice>,
}

pub fn handle_cancel_invoice(ctx: Context<CancelInvoice>) -> Result<()> {
    let cancelled_at = Clock::get()?.unix_timestamp;
    let invoice = &mut ctx.accounts.invoice;

    invoice.status = InvoiceStatus::Cancelled;

    emit!(InvoiceCancelled {
        invoice: invoice.key(),
        invoice_id: invoice.invoice_id,
        freelancer: invoice.freelancer,
        cancelled_at,
    });

    Ok(())
}
