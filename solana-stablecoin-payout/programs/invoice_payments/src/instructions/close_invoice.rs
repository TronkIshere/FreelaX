use anchor_lang::prelude::*;

use crate::{
    constants::INVOICE_SEED,
    error::ErrorCode,
    state::{Invoice, InvoiceStatus},
};

#[derive(Accounts)]
pub struct CloseInvoice<'info> {
    #[account(mut)]
    pub freelancer: Signer<'info>,

    #[account(
        mut,
        close = freelancer,
        seeds = [
            INVOICE_SEED,
            invoice.freelancer.as_ref(),
            invoice.invoice_id.to_le_bytes().as_ref()
        ],
        bump = invoice.bump,
        has_one = freelancer @ ErrorCode::UnauthorizedFreelancer,
        constraint = (
            invoice.status == InvoiceStatus::Paid
                || invoice.status == InvoiceStatus::Cancelled
        )
            @ ErrorCode::InvoiceNotClosable
    )]
    pub invoice: Account<'info, Invoice>,
}

pub fn handle_close_invoice(_ctx: Context<CloseInvoice>) -> Result<()> {
    Ok(())
}
