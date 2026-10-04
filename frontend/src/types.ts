export type UserType = 'CLIENT' | 'FREELANCER';

export interface User {
  id: string;
  email: string;
  displayName: string;
  userType: UserType;
}

export interface Page<T> {
  currentPage: number;
  pageSize: number;
  totalPages: number;
  totalElements: number;
  data: T[];
}

export interface Job {
  id: string;
  title: string;
  description: string;
  budgetUsd: number;
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
  milestoneId: string | null;
  milestoneStatus: string | null;
  amount: number;
  currency: string;
  deliveryDueAt: string | null;
  reviewWindowHours: number;
  maxRevisions: number;
  revisionsUsed: number;
  deliverables: Requirement[];
  acceptanceCriteria: Requirement[];
}

export interface DiscoverJob {
  id: string;
  title: string;
  description: string;
  budgetUsd: number;
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
}


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
    clientDisplayName: string | null; createdAt: string | null };
}

export type DecimalValue = number | string;
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
