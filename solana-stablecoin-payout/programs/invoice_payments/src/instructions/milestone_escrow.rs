use anchor_lang::prelude::*;
use anchor_spl::{
    associated_token::AssociatedToken,
    token::{self, Mint, Token, TokenAccount, TransferChecked},
};

use crate::{
    constants::{CONFIG_SEED, MILESTONE_ESCROW_SEED, USDC_DECIMALS},
    error::ErrorCode,
    state::{Config, MilestoneEscrow, MilestoneEscrowStatus},
};

const MAX_EXTENSION_SECONDS: i64 = 7 * 24 * 60 * 60;
const MIN_REVIEW_HOURS: u16 = 24;
const MAX_REVIEW_HOURS: u16 = 168;
const HIGH_VALUE_THRESHOLD: u64 = 500_000_000;

#[derive(Accounts)]
#[instruction(milestone_id: [u8; 16])]
pub struct FundMilestoneEscrow<'info> {
    #[account(mut)]
    pub client: Signer<'info>,
    #[account(address = config.admin @ ErrorCode::UnauthorizedAdmin)]
    pub marketplace_authority: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump,
        constraint = !config.paused @ ErrorCode::SystemPaused)]
    pub config: Account<'info, Config>,
    #[account(address = config.accepted_mint @ ErrorCode::InvalidInvoiceMint,
        constraint = mint.decimals == USDC_DECIMALS @ ErrorCode::InvalidMintDecimals)]
    pub mint: Account<'info, Mint>,
    #[account(init, payer = client, space = 8 + MilestoneEscrow::INIT_SPACE,
        seeds = [MILESTONE_ESCROW_SEED, milestone_id.as_ref()], bump)]
    pub escrow: Account<'info, MilestoneEscrow>,
    #[account(init, payer = client, associated_token::mint = mint,
        associated_token::authority = escrow)]
    pub vault: Account<'info, TokenAccount>,
    #[account(mut, associated_token::mint = mint, associated_token::authority = client)]
    pub client_ata: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

pub fn fund(ctx: Context<FundMilestoneEscrow>, milestone_id: [u8; 16], freelancer: Pubkey,
            amount: u64, funding_expires_at: i64, delivery_due_at: i64, review_window_hours: u16,
            max_revisions: u8) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    require!(amount > 0 && funding_expires_at >= now && delivery_due_at > now
        && review_window_hours >= MIN_REVIEW_HOURS
        && review_window_hours <= MAX_REVIEW_HOURS && max_revisions <= 2
        && freelancer != Pubkey::default() && freelancer != ctx.accounts.client.key()
        && freelancer != ctx.accounts.config.admin
        && ctx.accounts.client.key() != ctx.accounts.config.admin,
        ErrorCode::InvalidEscrowTerms);
    let extra_hours: u16 = if amount > HIGH_VALUE_THRESHOLD { 24 } else { 0 };
    let review_window_seconds = i64::from(review_window_hours + extra_hours) * 3600;
    token::transfer_checked(
        CpiContext::new(ctx.accounts.token_program.key(), TransferChecked {
            from: ctx.accounts.client_ata.to_account_info(),
            mint: ctx.accounts.mint.to_account_info(),
            to: ctx.accounts.vault.to_account_info(),
            authority: ctx.accounts.client.to_account_info(),
        }), amount, ctx.accounts.mint.decimals)?;
    let escrow = &mut ctx.accounts.escrow;
    escrow.milestone_id = milestone_id;
    escrow.client = ctx.accounts.client.key();
    escrow.freelancer = freelancer;
    escrow.arbiter = ctx.accounts.config.admin;
    escrow.mint = ctx.accounts.mint.key();
    escrow.amount = amount;
    escrow.funding_expires_at = funding_expires_at;
    escrow.original_delivery_due_at = delivery_due_at;
    escrow.delivery_due_at = delivery_due_at;
    escrow.requested_delivery_due_at = None;
    escrow.extension_used = false;
    escrow.review_window_seconds = review_window_seconds;
    escrow.review_due_at = None;
    escrow.submission_hash = None;
    escrow.submission_count = 0;
    escrow.revisions_used = 0;
    escrow.max_revisions = max_revisions;
    escrow.status = MilestoneEscrowStatus::Funded;
    escrow.funded_at = now;
    escrow.settled_at = None;
    escrow.dispute_hash = None;
    escrow.disputed_by = None;
    escrow.disputed_at = None;
    escrow.resolution_hash = None;
    escrow.revision_hash = None;
    escrow.bump = ctx.bumps.escrow;
    Ok(())
}

#[derive(Accounts)]
pub struct FreelancerEscrowAction<'info> {
    pub freelancer: Signer<'info>,
    #[account(mut, seeds = [MILESTONE_ESCROW_SEED, escrow.milestone_id.as_ref()],
        bump = escrow.bump, has_one = freelancer @ ErrorCode::UnauthorizedEscrowParticipant)]
    pub escrow: Account<'info, MilestoneEscrow>,
}

#[derive(Accounts)]
pub struct SubmitEscrowWork<'info> {
    pub freelancer: Signer<'info>,
    #[account(address = escrow.arbiter @ ErrorCode::UnauthorizedEscrowArbiter)]
    pub marketplace_authority: Signer<'info>,
    #[account(mut, seeds = [MILESTONE_ESCROW_SEED, escrow.milestone_id.as_ref()],
        bump = escrow.bump, has_one = freelancer @ ErrorCode::UnauthorizedEscrowParticipant)]
    pub escrow: Account<'info, MilestoneEscrow>,
}

#[derive(Accounts)]
pub struct ClientEscrowAction<'info> {
    pub client: Signer<'info>,
    #[account(mut, seeds = [MILESTONE_ESCROW_SEED, escrow.milestone_id.as_ref()],
        bump = escrow.bump, has_one = client @ ErrorCode::UnauthorizedEscrowParticipant)]
    pub escrow: Account<'info, MilestoneEscrow>,
}

#[derive(Accounts)]
pub struct ClientEscrowRevisionAction<'info> {
    pub client: Signer<'info>,
    #[account(address = escrow.arbiter @ ErrorCode::UnauthorizedEscrowArbiter)]
    pub marketplace_authority: Signer<'info>,
    #[account(mut, seeds = [MILESTONE_ESCROW_SEED, escrow.milestone_id.as_ref()],
        bump = escrow.bump, has_one = client @ ErrorCode::UnauthorizedEscrowParticipant)]
    pub escrow: Account<'info, MilestoneEscrow>,
}

#[derive(Accounts)]
pub struct ParticipantEscrowAction<'info> {
    pub actor: Signer<'info>,
    #[account(mut, seeds = [MILESTONE_ESCROW_SEED, escrow.milestone_id.as_ref()],
        bump = escrow.bump)]
    pub escrow: Account<'info, MilestoneEscrow>,
}

pub fn request_extension(ctx: Context<FreelancerEscrowAction>, new_due_at: i64) -> Result<()> {
    let escrow = &mut ctx.accounts.escrow;
    let now = Clock::get()?.unix_timestamp;
    require!(escrow.status == MilestoneEscrowStatus::Funded, ErrorCode::InvalidEscrowState);
    require!(!escrow.extension_used && escrow.requested_delivery_due_at.is_none(),
        ErrorCode::ExtensionAlreadyUsed);
    require!(now < escrow.original_delivery_due_at
        && new_due_at > escrow.original_delivery_due_at
        && new_due_at <= escrow.original_delivery_due_at
            .checked_add(MAX_EXTENSION_SECONDS).ok_or(ErrorCode::EscrowOverflow)?,
        ErrorCode::InvalidExtension);
    escrow.requested_delivery_due_at = Some(new_due_at);
    Ok(())
}

pub fn approve_extension(ctx: Context<ClientEscrowAction>) -> Result<()> {
    let escrow = &mut ctx.accounts.escrow;
    require!(escrow.status == MilestoneEscrowStatus::Funded, ErrorCode::InvalidEscrowState);
    require!(!escrow.extension_used && Clock::get()?.unix_timestamp < escrow.original_delivery_due_at,
        ErrorCode::InvalidExtension);
    escrow.delivery_due_at = escrow.requested_delivery_due_at
        .take().ok_or(ErrorCode::InvalidExtension)?;
    escrow.extension_used = true;
    Ok(())
}

pub fn submit(ctx: Context<SubmitEscrowWork>, evidence_hash: [u8; 32]) -> Result<()> {
    let escrow = &mut ctx.accounts.escrow;
    let now = Clock::get()?.unix_timestamp;
    require!(evidence_hash != [0; 32], ErrorCode::InvalidSubmissionHash);
    require!(escrow.status == MilestoneEscrowStatus::Funded
        || escrow.status == MilestoneEscrowStatus::Revision, ErrorCode::InvalidEscrowState);
    if escrow.status == MilestoneEscrowStatus::Funded {
        require!(now <= escrow.delivery_due_at, ErrorCode::DeliveryDeadlinePassed);
    }
    escrow.submission_hash = Some(evidence_hash);
    escrow.submission_count = escrow.submission_count
        .checked_add(1).ok_or(ErrorCode::EscrowOverflow)?;
    escrow.review_due_at = Some(now.checked_add(escrow.review_window_seconds)
        .ok_or(ErrorCode::EscrowOverflow)?);
    escrow.status = MilestoneEscrowStatus::Submitted;
    Ok(())
}

pub fn request_revision(ctx: Context<ClientEscrowRevisionAction>, feedback_hash: [u8; 32]) -> Result<()> {
    let escrow = &mut ctx.accounts.escrow;
    require!(feedback_hash != [0; 32], ErrorCode::InvalidSubmissionHash);
    require!(escrow.status == MilestoneEscrowStatus::Submitted, ErrorCode::InvalidEscrowState);
    require!(Clock::get()?.unix_timestamp < escrow.review_due_at
        .ok_or(ErrorCode::InvalidEscrowState)?, ErrorCode::ReviewDeadlinePassed);
    require!(escrow.revisions_used < escrow.max_revisions, ErrorCode::RevisionLimitReached);
    escrow.revisions_used += 1;
    escrow.review_due_at = None;
    escrow.revision_hash = Some(feedback_hash);
    escrow.status = MilestoneEscrowStatus::Revision;
    Ok(())
}

pub fn open_dispute(ctx: Context<ParticipantEscrowAction>, reason_hash: [u8; 32]) -> Result<()> {
    let escrow = &mut ctx.accounts.escrow;
    let now = Clock::get()?.unix_timestamp;
    let actor = ctx.accounts.actor.key();
    require!(actor == escrow.client || actor == escrow.freelancer,
        ErrorCode::UnauthorizedEscrowParticipant);
    require!(reason_hash != [0; 32], ErrorCode::InvalidSubmissionHash);
    match escrow.status {
        MilestoneEscrowStatus::Submitted => {
            require!(now < escrow.review_due_at.ok_or(ErrorCode::InvalidEscrowState)?,
                ErrorCode::ReviewDeadlinePassed);
        }
        MilestoneEscrowStatus::Funded => {
            require!(now > escrow.delivery_due_at, ErrorCode::InvalidEscrowState);
        }
        MilestoneEscrowStatus::Revision => {}
        _ => return err!(ErrorCode::InvalidEscrowState),
    }
    escrow.status = MilestoneEscrowStatus::Disputed;
    escrow.dispute_hash = Some(reason_hash);
    escrow.disputed_by = Some(actor);
    escrow.disputed_at = Some(now);
    escrow.review_due_at = None;
    Ok(())
}

#[derive(Accounts)]
pub struct SettleMilestoneEscrow<'info> {
    #[account(mut)]
    pub actor: Signer<'info>,
    #[account(mut, seeds = [MILESTONE_ESCROW_SEED, escrow.milestone_id.as_ref()],
        bump = escrow.bump)]
    pub escrow: Account<'info, MilestoneEscrow>,
    #[account(address = escrow.mint @ ErrorCode::InvalidInvoiceMint)]
    pub mint: Account<'info, Mint>,
    #[account(mut, associated_token::mint = mint, associated_token::authority = escrow)]
    pub vault: Account<'info, TokenAccount>,
    /// CHECK: Address must be the recorded Client or Freelancer, depending on settlement.
    pub recipient: UncheckedAccount<'info>,
    #[account(init_if_needed, payer = actor, associated_token::mint = mint,
        associated_token::authority = recipient)]
    pub recipient_ata: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

pub fn settle(ctx: Context<SettleMilestoneEscrow>, release: bool,
              resolution_hash: [u8; 32]) -> Result<()> {
    let escrow = &ctx.accounts.escrow;
    let now = Clock::get()?.unix_timestamp;
    let actor = ctx.accounts.actor.key();
    let recipient = if release { escrow.freelancer } else { escrow.client };
    require!(ctx.accounts.recipient.key() == recipient, ErrorCode::InvalidEscrowRecipient);
    if escrow.status == MilestoneEscrowStatus::Disputed {
        require!(actor == escrow.arbiter, ErrorCode::UnauthorizedEscrowArbiter);
        require!(resolution_hash != [0; 32], ErrorCode::InvalidResolutionHash);
    } else if release {
        require!(resolution_hash == [0; 32], ErrorCode::InvalidResolutionHash);
        require!(escrow.status == MilestoneEscrowStatus::Submitted,
            ErrorCode::InvalidEscrowState);
        let due = escrow.review_due_at.ok_or(ErrorCode::InvalidEscrowState)?;
        require!(actor == escrow.client || now >= due,
            ErrorCode::ReviewDeadlineNotPassed);
    } else {
        return err!(ErrorCode::InvalidEscrowState);
    }
    let milestone_id = escrow.milestone_id;
    let bump = [escrow.bump];
    let signer_seeds: &[&[u8]] = &[MILESTONE_ESCROW_SEED, milestone_id.as_ref(), &bump];
    token::transfer_checked(CpiContext::new_with_signer(
        ctx.accounts.token_program.key(), TransferChecked {
            from: ctx.accounts.vault.to_account_info(),
            mint: ctx.accounts.mint.to_account_info(),
            to: ctx.accounts.recipient_ata.to_account_info(),
            authority: ctx.accounts.escrow.to_account_info(),
        }, &[signer_seeds]), escrow.amount, ctx.accounts.mint.decimals)?;
    let escrow = &mut ctx.accounts.escrow;
    escrow.status = if release { MilestoneEscrowStatus::Released } else {
        MilestoneEscrowStatus::Refunded
    };
    escrow.settled_at = Some(now);
    if resolution_hash != [0; 32] { escrow.resolution_hash = Some(resolution_hash); }
    Ok(())
}

#[derive(Accounts)]
pub struct RefundMutualEscrow<'info> {
    #[account(mut)]
    pub client: Signer<'info>,
    pub freelancer: Signer<'info>,
    #[account(mut, seeds = [MILESTONE_ESCROW_SEED, escrow.milestone_id.as_ref()],
        bump = escrow.bump, has_one = client @ ErrorCode::UnauthorizedEscrowParticipant,
        has_one = freelancer @ ErrorCode::UnauthorizedEscrowParticipant)]
    pub escrow: Account<'info, MilestoneEscrow>,
    #[account(address = escrow.mint @ ErrorCode::InvalidInvoiceMint)]
    pub mint: Account<'info, Mint>,
    #[account(mut, associated_token::mint = mint, associated_token::authority = escrow)]
    pub vault: Account<'info, TokenAccount>,
    #[account(mut, associated_token::mint = mint, associated_token::authority = client)]
    pub client_ata: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
}

pub fn refund_mutual(ctx: Context<RefundMutualEscrow>) -> Result<()> {
    let escrow = &ctx.accounts.escrow;
    require!(escrow.status == MilestoneEscrowStatus::Funded
        || escrow.status == MilestoneEscrowStatus::Submitted
        || escrow.status == MilestoneEscrowStatus::Revision, ErrorCode::InvalidEscrowState);
    let milestone_id = escrow.milestone_id;
    let bump = [escrow.bump];
    let signer_seeds: &[&[u8]] = &[MILESTONE_ESCROW_SEED, milestone_id.as_ref(), &bump];
    token::transfer_checked(CpiContext::new_with_signer(
        ctx.accounts.token_program.key(), TransferChecked {
            from: ctx.accounts.vault.to_account_info(),
            mint: ctx.accounts.mint.to_account_info(),
            to: ctx.accounts.client_ata.to_account_info(),
            authority: ctx.accounts.escrow.to_account_info(),
        }, &[signer_seeds]), escrow.amount, ctx.accounts.mint.decimals)?;
    ctx.accounts.escrow.status = MilestoneEscrowStatus::Refunded;
    ctx.accounts.escrow.settled_at = Some(Clock::get()?.unix_timestamp);
    Ok(())
}
