export type UserType = 'CLIENT' | 'FREELANCER';

export interface User {
  id: string;
  email: string;
  displayName: string;
  userType: UserType;
  authorities?: readonly string[];
}

export interface Page<T> {
  currentPage: number;
  pageSize: number;
  totalPages: number;
  totalElements: number;
  data: T[];
}

export interface ProfileLanguage { code: string; proficiency: string }
export interface Profile {
  userId: string; userType: UserType; displayName: string; email?: string; version: number;
  avatarUrl?: string | null; headline?: string | null; bio?: string | null; countryCode?: string | null;
  languages: ProfileLanguage[]; skills: string[]; hourlyRateUsd?: DecimalValue | null; availability?: string | null;
  companyName?: string | null; companyWebsite?: string | null;
  verification: { email: string; identity: string; paymentMethod: string; source: string };
  reputation: { completedContracts: number; fundedContracts?: number | null; disputeCount: number; reviewCount: number;
    averageRating?: DecimalValue | null; onTimeRate?: DecimalValue | null; paymentReleaseRate?: DecimalValue | null;
    medianReviewHours?: DecimalValue | null; calculatedAt: string };
}
export interface ProfilePatch {
  version: number; displayName: string; avatarUrl: string | null; headline: string | null; bio: string | null;
  countryCode: string | null; languages: ProfileLanguage[]; hourlyRateUsd?: number | null; availability?: string | null;
  companyName?: string | null; companyWebsite?: string | null;
}
export interface PortfolioInput { title: string; description: string; projectUrl: string | null; thumbnailUrl: string | null;
  skills: string[]; completedAt: string | null; sortOrder: number }
export interface PortfolioItem extends PortfolioInput { id: string; userId: string; version: number }
export interface ReviewInput { overall: number; dimensions: { communication: number; requirementsOrQuality: number; timeliness: number }; comment?: string | null }
export interface Review {
  id: string; contractId: string; reviewerId: string; revieweeId: string; submitted: boolean;
  submittedAt?: string | null; publishedAt?: string | null; overall?: number | null; dimensions?: ReviewInput['dimensions'] | null;
  comment?: string | null; contentHidden: boolean; reported: boolean;
}
export type ModerationAction = 'HIDE_CONTENT' | 'INVALIDATE';
export interface AdminReviewDetail { review: Review; audit: { actorId: string; action: string; beforeState: string; afterState: string; reason: string; requestId: string; createdAt: string }[] }

export type DisputeStatus = 'OPEN' | 'UNDER_REVIEW' | 'DECISION_PENDING_RELEASE' | 'DECISION_PENDING_REFUND' | 'RESOLVED_RELEASE' | 'RESOLVED_REFUND' | 'CANCELLED';
export interface DisputeEvidenceInput { kind: 'TEXT' | 'LINK'; text?: string; url?: string; sha256?: string }
export interface DisputeEvidence {
  id: string; actorId: string; createdAt: string | null; kind: 'TEXT' | 'LINK';
  text?: string | null; url?: string | null; sha256?: string | null;
}
export interface OpenDisputeInput { reasonCode: string; description: string; evidence?: DisputeEvidenceInput[] }
export interface Dispute {
  disputeId: string; contractId: string; milestoneId: string; jobId: string; submissionId: string | null;
  openedBy: string; reasonCode: string; description: string; status: DisputeStatus; openedAt: string;
  claimedBy: string | null; claimedAt: string | null; resolvedBy: string | null; decisionAt: string | null;
  negotiationUntil?: string | null; moderationDueAt?: string | null;
  negotiationProposedBy?: string | null; negotiationOutcome?: 'RELEASE_TO_FREELANCER' | 'REFUND_TO_CLIENT' | null;
  negotiationReason?: string | null;
  resolvedAt: string | null; resolutionReason: string | null; refundStatus: SettlementMoneyStatus | null;
  refundReference: string | null; evidence: DisputeEvidence[];
}
export interface SpringPage<T> { content: T[]; number: number; size: number; totalPages: number; totalElements: number }
export interface AdminDisputeDetail {
  dispute: Dispute;
  contract: { contractId: string; clientId: string; freelancerId: string; title: string; description: string;
    amount: DecimalValue; currency: string; deliveryDueAt: string | null; maxRevisions: number; revisionsUsed: number };
  deliverables: { id: string; title: string; description: string; required: boolean }[];
  acceptanceCriteria: { id: string; description: string; required: boolean }[];
  submissions: { id: string; version: number; status: string; summary: string; reviewerFeedback: string | null;
    submittedAt: string; reviewedAt: string | null; evidence: { requirementId: string; kind: string; description: string | null; url: string | null }[] }[];
  fundingStatus: string | null;
  audit: { actorId: string; action: string; beforeStatus: string | null; afterStatus: string; reason: string | null; requestId: string | null; createdAt: string }[];
}
export interface DisputeDecision { outcome: 'RELEASE_TO_FREELANCER' | 'REFUND_TO_CLIENT'; reason: string }

export interface UnifiedTermsPreview {
  rail: 'UNIFIED_USDC_PAYOUT'; version: number; fingerprint: string;
  grossUsd: DecimalValue; escrowUsdc: DecimalValue; platformFeeUsdc: DecimalValue;
  usdcVndRate: DecimalValue; estimatedTaxableVnd: DecimalValue; estimatedTaxVnd: DecimalValue;
  estimatedPayoutVnd: DecimalValue; fullRefundUsd: DecimalValue;
  fundingHours: number; reviewWindowHours: number; maxRevisions: number;
  network: string | null; mint: string | null; simulation: boolean; legacyPayout?: boolean;
}

export interface Job {
  id: string;
  title: string;
  description: string;
  category?: JobCategory | null;
  skills?: string[] | null;
  budgetUsd: number;
  localPaymentTerms?: UnifiedTermsPreview | null;
  clientUserId: string;
  freelancerId: string | null;
  status: string;
  createdAt: string | null;
  updatedAt?: string | null;
  checkoutOrderId?: string | null;
  taxExportStatus?: string | null;
  deliveryDueAt?: string | null;
  reviewWindowHours?: number;
  maxRevisions?: number;
  deliverables?: Requirement[];
  acceptanceCriteria?: Requirement[];
  contract?: ContractSummary | null;
}

export interface Requirement {
  id: string;
  title?: string | null;
  description: string;
  required: boolean;
  order: number;
}

export interface ContractSummary {
  id: string;
  status: string;
  paymentRail?: 'SIMULATED' | 'SOLANA_ESCROW' | 'PARTNER_ESCROW_MOCK' | 'UNIFIED_USDC_PAYOUT';
  milestoneId: string | null;
  milestoneStatus: string | null;
  amount: DecimalValue;
  currency: string;
  deliveryDueAt: string | null;
  reviewWindowHours: number;
  maxRevisions: number;
  revisionsUsed: number;
  deliverables: Requirement[];
  acceptanceCriteria: Requirement[];
}

export interface EscrowFundingView {
  paymentRail: 'SOLANA_ESCROW'; status: string; settlementStatus: string;
  escrowAddress: string;
  clientWallet: string; freelancerWallet: string; mint: string | null;
  amountBaseUnits: string | null; fundingExpiresAt: string | null;
  deliveryDueAt: string | null;
  reviewDueAt: string | null; submissionHash: string | null;
  submissionCount: number | null; disputeHash: string | null;
  fundSignature: string | null; releaseSignature: string | null;
  refundSignature: string | null;
  resolutionSignature: string | null; vaultAddress: string | null;
  vaultBalanceBaseUnits: string | null; vaultBalanceStatus: string;
  requestedDeliveryDueAt: string | null; extensionUsed: boolean;
}

export interface EscrowFundingBuild {
  buildSessionId: string; transactionBase64: string; escrowAddress: string;
  clientWallet: string; freelancerWallet: string; amountBaseUnits: string; mint: string;
}
export interface EscrowActionBuild {
  intentId: string; action: string; buildSessionId: string;
  transactionBase64: string; escrowAddress: string; actorWallet: string;
  reviewDueAt: string | null; payloadHash: string | null;
}
export interface EscrowActionStatus {
  intentId: string; action: string; signature: string; chainStatus: string;
}
export interface EscrowMutualRefund {
  intentId: string; buildSessionId: string; originalTransaction: string;
  partialTransaction: string | null; clientWallet: string; freelancerWallet: string;
}

export interface ClientBankAccount {
  paymentMethodId: 'BANK_ACCOUNT_ON_FILE'; ready: boolean;
  bankCode: BankCode | null; maskedAccountNumber: string | null;
}
export interface ClientBankInput { bankCode: BankCode; bankAccountNumber: string; bankAccountHolderName: string }
export interface FundingResponse {
  fundingTransactionId: string; fundingStatus: 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'UNKNOWN';
  simulation: boolean; providerReference: string | null; nextAction: string; retryAfterSeconds: number | null;
}
export interface PaymentFlowTimeline {
  paymentFlowId: string; version: number; jobId: string; contractId: string; milestoneId: string;
  paymentRail: 'UNIFIED_USDC_PAYOUT'; termsStatus: 'DRAFT' | 'LOCKED';
  grossUsd: DecimalValue; escrowUsdc: DecimalValue; platformFeeUsd: DecimalValue;
  payerBankCode?: string | null; payerBankMaskedAccount?: string | null;
  network: string | null; mint: string | null; quoteSource: string | null;
  quoteExpiresAt: string | null; fundingExpiresAt: string | null; deliveryDueAt: string | null;
  reviewWindowHours: number; maxRevisions: number; jobStatus: string; contractStatus: string;
  steps: { kind: string; status: string; amount: DecimalValue | null; currency: string | null;
    provider: string | null; reference: string | null; evidenceSource: string | null;
    retryAfter: string | null; confirmedAt: string | null; transactionSignature?: string | null;
    vndRate?: DecimalValue | null; payoutVnd?: DecimalValue | null; feeUsdc?: DecimalValue | null;
    quoteExpiresAt?: string | null }[];
  evidence: { kind: string; status: string; reference: string | null; evidenceSource: string;
    amount: DecimalValue | null; currency: string | null; occurredAt: string }[];
  simulation: boolean;
}
export interface SubmissionPayload {
  summary: string;
  deliverables: { requirementId: string; url: string; description: string }[];
  acceptanceEvidence: { criterionId: string; note: string; url: string }[];
}
export interface ContractSubmission {
  id: string; contractId: string; milestoneId: string; freelancerId: string; version: number;
  status: 'SUBMITTED' | 'REVISION_REQUESTED' | 'APPROVED' | 'DISPUTED'; summary: string;
  submittedAt: string; submittedLate: boolean; reviewDueAt: string | null; reviewGraceDueAt: string | null;
  reviewedAutomatically: boolean; disputeId: string | null; reviewerFeedback: string | null;
  reviewCriterionIds: string[]; reviewDeliverableIds: string[];
  deliverables: { requirementId: string; description: string | null; url: string | null }[];
  acceptanceEvidence: { requirementId: string; description: string | null; url: string | null }[];
}
export type ReviewDecision = { decision: 'APPROVE'; note?: string }
  | { decision: 'REQUEST_REVISION'; feedback: string; criterionIds: string[]; deliverableIds: string[] }
  | { decision: 'OPEN_DISPUTE'; reasonCode: string; description: string };

export interface DiscoverJob {
  id: string;
  title: string;
  description: string;
  category?: JobCategory | null;
  skills?: string[] | null;
  budgetUsd: number;
  localPaymentTerms?: UnifiedTermsPreview | null;
  status: string;
  client: { id: string; displayName: string };
  hasApplied: boolean;
  applicationId: string | null;
  applicationStatus: string | null;
  createdAt: string | null;
  deliveryDueAt?: string | null;
  reviewWindowHours?: number;
  maxRevisions?: number;
  deliverables?: Requirement[];
  acceptanceCriteria?: Requirement[];
}

export type DiscoverySort = 'NEWEST' | 'BUDGET_ASC' | 'BUDGET_DESC';
export type ApplicationFilter = 'ALL' | 'APPLIED' | 'NOT_APPLIED';

export interface DiscoveryFilters {
  keyword: string;
  minBudgetUsd: string;
  maxBudgetUsd: string;
  sort: DiscoverySort;
  application: ApplicationFilter;
  category?: JobCategory | '';
  skills?: string[];
}

export type JobCategory = 'WEB_FRONTEND' | 'BACKEND_API' | 'SEO_CONTENT' | 'MOBILE_APP' | 'UI_UX_DESIGN'
  | 'ECOMMERCE' | 'DATA_ANALYTICS' | 'BRANDING_GRAPHIC' | 'OTHER';
export interface CreateJobInput {
  title: string; description: string; category: JobCategory; skills: string[]; budgetUsd: number;
  deliveryDueAt: string; reviewWindowHours: number; maxRevisions: number;
  deliverables: { title: string; description: string; required: boolean }[];
  acceptanceCriteria: { description: string; required: boolean }[];
}
export interface UpdateJobInput { title: string; description: string; category: JobCategory; skills: string[] }


export type JobStatus = 'OPEN' | 'AWAITING_PAYMENT' | 'IN_PROGRESS' | 'SUBMITTED_FOR_REVIEW' | 'REVISION_REQUESTED' | 'COMPLETED' | 'CANCELLED';
export type JobApplicationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';
export type JobSubmissionStatus = 'SUBMITTED' | 'REVISION_REQUESTED' | 'APPROVED';
export interface JobSubmission {
  id: string;
  jobId: string;
  freelancerId: string;
  version: number;
  summary: string;
  deliverableUrl: string | null;
  status: JobSubmissionStatus;
  reviewerFeedback: string | null;
  reviewedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}
export interface JobApplication {
  id: string; jobId: string; freelancerId: string; status: JobApplicationStatus; createdAt: string | null;
}
export interface MyApplication {
  id: string; status: JobApplicationStatus; createdAt: string | null; updatedAt: string | null;
  job: { id: string; title: string; description: string; budgetUsd: number; status: JobStatus;
    category?: JobCategory | null; skills?: string[] | null;
    clientDisplayName: string | null; createdAt: string | null };
}

export type DecimalValue = number | string;
export type SettlementMoneyStatus = 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED_RETRYABLE' | 'FAILED' | 'UNKNOWN';
export type SettlementStageStatus = 'NOT_STARTED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED_RETRYABLE' | 'FAILED' | 'UNKNOWN';
export interface ContractSettlement {
  contractId: string; milestoneId: string; jobId: string;
  // BigDecimal is numeric in this DTO; accept decimal strings without rounding as well.
  amount: DecimalValue; currency: string; simulation: boolean;
  moneyStatus: SettlementMoneyStatus; onChainStatus: SettlementStageStatus;
  offRampStatus: SettlementStageStatus; taxStatus: SettlementStageStatus;
  releaseReference: string | null; onChainReference: string | null;
  offRampReference: string | null; taxReference: string | null;
  platformFeeUsd?: DecimalValue | null; freelancerUsd?: DecimalValue | null;
  lockedUsdVndRate?: DecimalValue | null; partnerPayoutVnd?: DecimalValue | null;
  onChainError: string | null; offRampError: string | null; taxError: string | null;
  retryable: boolean; lastError: string | null; createdAt: string; updatedAt: string;
}
export type CancellationDecision = 'ACCEPT' | 'REJECT';
export interface CancellationRequest { reasonCode: string; description: string }
export interface ContractCancellationRecord {
  cancellationId: string; contractId: string; milestoneId: string;
  cancellationStatus: 'REQUESTED' | 'REJECTED' | 'REFUND_PENDING' | 'CANCELLED';
  refundStatus: SettlementMoneyStatus | null; refundReference: string | null; simulation: boolean;
  requestedBy: string; decidedBy: string | null; reasonCode: string; reason: string;
  amount: string; currency: string;
  requestedAt: string; decidedAt: string | null; updatedAt: string;
  retryable: boolean; lastError: string | null; allowedActions: CancellationDecision[];
}
export interface JobPaymentStatus {
  jobId: string;
  checkoutOrderId: string | null;
  checkoutOrderStatus: string | null;
  taxExportStatus: string | null;
  simulation: boolean | null;
  network: string | null;
  onRampStatus: string | null;
  offRampStatus: string | null;
  onRampClientPublicKey: string | null;
  onRampPurchaseId: string | null;
  onRampTransactionSignature: string | null;
  onRampReceiptPda: string | null;
  explorerUrl: string | null;
  amountUsdcReceived: DecimalValue | null;
  clientPaymentStatus: string | null;
  freelancerPublicKey: string | null;
  rateId: string | null;
  rateTransactionSignature: string | null;
  rateSnapshotPda: string | null;
  invoiceId: string | null;
  invoicePda: string | null;
  paymentMint: string | null;
  invoiceTransactionSignature: string | null;
  paymentTransactionSignature: string | null;
  paymentExplorerUrl: string | null;
  clientPaymentSubmittedAt: string | null;
  clientPaymentConfirmedAt: string | null;
  clientPaymentError: string | null;
  onChainOffRampStatus: string | null;
  withdrawalId: string | null;
  withdrawalPda: string | null;
  treasuryPublicKey: string | null;
  treasuryUsdcAta: string | null;
  withdrawalTokenAmount: string | null;
  withdrawalFiatAmountVnd: string | null;
  withdrawalTransactionSignature: string | null;
  withdrawalExplorerUrl: string | null;
  withdrawalSubmittedAt: string | null;
  withdrawalConfirmedAt: string | null;
  onChainOffRampError: string | null;
  payoutBankCode: string | null;
  payoutBankAccountNumber: string | null;
  payoutBankAccountHolderName: string | null;
  offRampReference: string | null;
  amountVndBeforeOffRampFee: DecimalValue | null;
  offRampFeeVnd: DecimalValue | null;
  simulatedPayoutAt: string | null;
  offRampCompletionSignature: string | null;
  offRampCompletionExplorerUrl: string | null;
  offRampCompletionSubmittedAt: string | null;
  offRampCompletedAt: string | null;
  offRampError: string | null;
  estimatedAmountVnd: DecimalValue | null;
  usdcToVndRateSource: string | null;
  usdcToVndRate: DecimalValue | null;
  usdcToVndRateObservedAt: string | null;
  taxableAmountVnd: DecimalValue | null;
  taxRateSource: string | null;
  taxUsdToVndRate: DecimalValue | null;
  taxRateObservedAt: string | null;
}

export interface TaxRecord {
  id: string;
  jobId: string;
  jobTitle: string | null;
  freelancerId: string;
  clientUserId: string;
  status: string;
  statusLabel: string;
  amountUsd: DecimalValue | null;
  usdToVndRate: DecimalValue | null;
  rateSource: string | null;
  rateObservedAt: string | null;
  taxableIncomeVnd: DecimalValue | null;
  taxWithheldVnd: DecimalValue | null;
  certificateNumber: string | null;
  certificateSymbol: string | null;
  lookupCode: string | null;
  misaCertificateId: string | null;
  misaPayoutTransactionId: string | null;
  transactionReference: string | null;
  submissionId: string | null;
  taxAuthorityReference: string | null;
  issuedAt: string | null;
  submittedAt: string | null;
  lastSyncedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export type BankCode = 'VIETCOMBANK' | 'VIETINBANK' | 'BIDV' | 'AGRIBANK' | 'TECHCOMBANK' |
  'MBBANK' | 'ACB' | 'VPBANK' | 'SACOMBANK' | 'TPBANK';

export type RegisterInput = {
  displayName: string;
  email: string;
  password: string;
} & ({ userType: 'CLIENT' } | {
  userType: 'FREELANCER';
  taxCode: string;
  identityNumber: string;
  nationality: string;
  taxAddress: string;
  bankCode: BankCode;
  bankAccountNumber: string;
});

export interface Notification {
  id: string;
  title: string;
  message: string;
  type: string;
  jobId: string | null;
  read: boolean;
  amount: number | string | null;
  createdAt: string | null;
}
