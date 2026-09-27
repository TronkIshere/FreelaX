use anchor_lang::prelude::*;

#[error_code]
pub enum ErrorCode {
    #[msg("The accepted mint must use 6 decimals")]
    InvalidMintDecimals,

    #[msg("Treasury authority cannot be the default public key")]
    InvalidTreasuryAuthority,

    #[msg("Oracle authority cannot be the default public key")]
    InvalidOracleAuthority,

    #[msg("Only the configured admin can perform this action")]
    UnauthorizedAdmin,

    #[msg("The payment system is currently paused")]
    SystemPaused,

    #[msg("Invoice amount must be greater than zero")]
    InvalidInvoiceAmount,

    #[msg("Client cannot be the default public key")]
    InvalidClient,

    #[msg("Only the client assigned to this invoice can pay it")]
    UnauthorizedClient,

    #[msg("Only a pending invoice can be paid")]
    InvoiceNotPending,

    #[msg("Invoice mint does not match the configured accepted mint")]
    InvalidInvoiceMint,

    #[msg("Freelancer account does not match the invoice")]
    InvalidFreelancer,

    // Keep rate-module errors after the pre-existing invoice errors so adding
    // this module does not change the numeric codes exposed by the old API.
    #[msg("Rate authority cannot be the default public key")]
    InvalidRateAuthority,

    #[msg("Rate authority and oracle authority must be different")]
    DuplicateRateAndOracleAuthority,

    #[msg("Only the configured rate authority can publish rates")]
    UnauthorizedRateAuthority,

    #[msg("Maximum rate age must be greater than zero")]
    InvalidMaxRateAge,

    #[msg("Both component rates must be greater than zero")]
    InvalidRateValue,

    #[msg("Rate observation time is too far in the future")]
    RateObservedInFuture,

    #[msg("Rate expiration must be after its observation time")]
    InvalidRateExpiration,

    #[msg("Rate snapshot lifetime exceeds the configured maximum")]
    RateLifetimeTooLong,

    #[msg("Rate calculation overflowed")]
    RateCalculationOverflow,

    #[msg("Only the freelancer assigned to this invoice can perform this action")]
    UnauthorizedFreelancer,

    #[msg("Only a pending invoice can be cancelled")]
    InvoiceNotCancellable,

    #[msg("A pending invoice cannot be closed")]
    InvoiceNotClosable,

    #[msg("Withdrawal token amount must be greater than zero")]
    InvalidWithdrawalAmount,

    #[msg("The rate snapshot has expired")]
    RateSnapshotExpired,

    #[msg("The rate snapshot is older than the configured maximum age")]
    RateSnapshotTooOld,

    #[msg("The rate snapshot was not published by the configured rate authority")]
    InvalidRatePublisher,

    #[msg("The fiat amount calculation overflowed")]
    FiatCalculationOverflow,

    #[msg("The token amount is too small to produce one VND")]
    FiatAmountTooSmall,

    #[msg("Only the configured settlement oracle can complete an off-ramp")]
    UnauthorizedOracle,

    #[msg("Only a pending withdrawal can be completed")]
    WithdrawalNotPending,

    #[msg("Only the program upgrade authority can initialize Config")]
    UnauthorizedInitializer,

    #[msg("The invoice expiration must be in the future and no later than the rate expiration")]
    InvalidInvoiceExpiration,

    #[msg("The invoice has expired")]
    InvoiceExpired,

    #[msg("Failure audit hash cannot be all zeroes")]
    InvalidFailureHash,

    #[msg("Only a withdrawal pending manual review can be resolved")]
    WithdrawalNotPendingReview,

    #[msg("Resolution audit hash cannot be all zeroes")]
    InvalidResolutionHash,
}
