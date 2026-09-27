use anchor_lang::prelude::*;

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, PartialEq, Eq, InitSpace)]
pub enum InvoiceStatus {
    /// Hóa đơn đã tạo nhưng client chưa thanh toán.
    Pending,

    /// Client đã thanh toán thành công.
    Paid,

    /// Freelancer đã hủy hóa đơn.
    Cancelled,
}

#[account]
#[derive(InitSpace)]
pub struct Invoice {
    /// ID hóa đơn, duy nhất trong phạm vi một freelancer.
    pub invoice_id: u64,

    /// Người tạo hóa đơn và nhận token.
    pub freelancer: Pubkey,

    /// Client được yêu cầu thanh toán.
    pub client: Pubkey,

    /// Số token tính theo base units.
    /// Ví dụ: 100 USDC = 100_000_000 khi decimals = 6.
    pub amount: u64,

    /// Mint được chấp nhận tại thời điểm tạo hóa đơn.
    pub mint: Pubkey,

    /// Snapshot tỷ giá được khóa để ghi nhận thu nhập khi hóa đơn được trả.
    pub rate_snapshot: Pubkey,

    /// Hạn cuối thanh toán. Không được vượt quá hạn của RateSnapshot.
    pub expires_at: i64,

    /// Pending, Paid hoặc Cancelled.
    pub status: InvoiceStatus,

    pub created_at: i64,

    /// Thời điểm thanh toán; ban đầu là None.
    pub paid_at: Option<i64>,

    pub bump: u8,
}
