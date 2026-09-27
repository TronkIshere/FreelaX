use anchor_lang::prelude::*;
use anchor_spl::token::{self, Mint, Token, TokenAccount, TransferChecked};

use crate::{
    constants::*,
    error::ErrorCode,
    events::InvoicePaid,
    state::{Config, Invoice, InvoiceStatus},
};

/// Những account cần thiết để thanh toán một Invoice.
#[derive(Accounts)]
pub struct PayInvoice<'info> {
    /// Client là người bị trừ token nên bắt buộc phải ký.
    pub client: Signer<'info>,

    /// Config được dùng để:
    /// - kiểm tra hệ thống có paused không;
    /// - lấy accepted_mint.
    #[account(
        seeds = [CONFIG_SEED],
        bump = config.bump,
        constraint = !config.paused @ ErrorCode::SystemPaused
    )]
    pub config: Account<'info, Config>,

    /// Invoice sẽ được đổi từ Pending sang Paid.
    #[account(
        mut,
        seeds = [
            INVOICE_SEED,
            invoice.freelancer.as_ref(),
            invoice.invoice_id.to_le_bytes().as_ref()
        ],
        bump = invoice.bump,
        has_one = client @ ErrorCode::UnauthorizedClient,
        constraint = invoice.status == InvoiceStatus::Pending
            @ ErrorCode::InvoiceNotPending
    )]
    pub invoice: Account<'info, Invoice>,

    /// Freelancer phải đúng với địa chỉ đã lưu trong Invoice.
    #[account(
        address = invoice.freelancer @ ErrorCode::InvalidFreelancer
    )]
    pub freelancer: SystemAccount<'info>,

    /// Mint phải đúng bằng mint đã khóa trong Invoice, kể cả sau Config rotation.
    #[account(
        address = invoice.mint
            @ ErrorCode::InvalidInvoiceMint
    )]
    pub accepted_mint: Account<'info, Mint>,

    /// ATA nguồn:
    /// - thuộc Client;
    /// - giữ accepted_mint;
    /// - sẽ bị trừ token.
    #[account(
        mut,
        associated_token::mint = accepted_mint,
        associated_token::authority = client,
        associated_token::token_program = token_program
    )]
    pub client_ata: Account<'info, TokenAccount>,

    /// ATA đích:
    /// - thuộc Freelancer;
    /// - giữ accepted_mint;
    /// - sẽ nhận token.
    #[account(
        mut,
        associated_token::mint = accepted_mint,
        associated_token::authority = freelancer,
        associated_token::token_program = token_program
    )]
    pub freelancer_ata: Account<'info, TokenAccount>,

    /// Legacy SPL Token Program thực hiện việc chuyển token.
    pub token_program: Program<'info, Token>,
}

pub fn handle_pay_invoice(ctx: Context<PayInvoice>) -> Result<()> {
    require!(
        Clock::get()?.unix_timestamp < ctx.accounts.invoice.expires_at,
        ErrorCode::InvoiceExpired
    );
    /*
     * Lấy amount từ Invoice đã lưu trên blockchain.
     *
     * Client không được tự truyền amount khi thanh toán,
     * nếu không họ có thể thử truyền số tiền nhỏ hơn.
     */
    let amount = ctx.accounts.invoice.amount;
    let decimals = ctx.accounts.accepted_mint.decimals;

    /*
     * Chuẩn bị bốn account mà Token Program cần:
     *
     * from      = Client ATA
     * mint      = Mock USDC Mint
     * to        = Freelancer ATA
     * authority = Client
     */
    let cpi_accounts = TransferChecked {
        from: ctx.accounts.client_ata.to_account_info(),
        mint: ctx.accounts.accepted_mint.to_account_info(),
        to: ctx.accounts.freelancer_ata.to_account_info(),
        authority: ctx.accounts.client.to_account_info(),
    };

    let cpi_context = CpiContext::new(ctx.accounts.token_program.key(), cpi_accounts);

    /*
     * Invoice Program gọi CPI sang Token Program.
     *
     * Dùng CpiContext::new
     * vì Client là ví thật và đã trực tiếp ký transaction.
     */
    token::transfer_checked(cpi_context, amount, decimals)?;

    /*
     * Chỉ sau khi Token Program chuyển thành công,
     * Invoice mới được đổi sang Paid.
     */
    let paid_at = Clock::get()?.unix_timestamp;
    let invoice = &mut ctx.accounts.invoice;

    invoice.status = InvoiceStatus::Paid;
    invoice.paid_at = Some(paid_at);

    emit!(InvoicePaid {
        invoice: invoice.key(),
        client: invoice.client,
        freelancer: invoice.freelancer,
        amount,
        mint: invoice.mint,
        rate_snapshot: invoice.rate_snapshot,
        paid_at,
    });

    msg!("Invoice paid: {}", invoice.key());
    msg!("Client: {}", invoice.client);
    msg!("Freelancer: {}", invoice.freelancer);
    msg!("Amount: {}", amount);

    Ok(())
}
