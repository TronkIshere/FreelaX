export type TransactionState =
  | 'CREATED' | 'SIGNED' | 'SUBMITTED' | 'PENDING' | 'CONFIRMED'
  | 'FINALIZED' | 'REVERTED' | 'FAILED' | 'REPLACED' | 'UNKNOWN';

export type AvalancheEnvironmentInfo = {
  environment: 'LOCAL_SIMULATION' | 'AVALANCHE_FUJI';
  chainId: number;
  simulated: boolean;
  explorerBaseUrl: string | null;
  backendSignersAvailable: boolean;
};

export type AvalancheChainTransaction = {
  id: number;
  contractId: string;
  milestoneId: string | null;
  networkId: string;
  environment: string;
  chainId: number;
  escrowId: string;
  action: string;
  operationKey: string;
  txHash: string | null;
  status: TransactionState;
  errorClass: string | null;
  businessApplied: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AvalancheEscrowSnapshot = {
  status: string;
  client: string;
  freelancer: string;
  amountBaseUnits: string;
  maxRevisions: number;
  revisionsUsed: number;
  deliveryDueAt: number;
  reviewDueAt: number;
};

export type AvalancheContractView = {
  networkId: string | null;
  avalancheEnabled: boolean;
  environment: AvalancheEnvironmentInfo | null;
  escrowId: string | null;
  chain: AvalancheEscrowSnapshot | null;
  transactions: AvalancheChainTransaction[];
  notice: string | null;
  /** Pending mutual-refund authorization (F13): which party has signed the shared typed data, and until when. */
  mutualRefund?: AvalancheMutualRefundState | null;
  /** P3-C4: token issuer controls (blacklist, pause) blocking this escrow; stuck and reported, never bypassed (O-6). */
  issuerAlerts?: AvalancheIssuerAlert[] | null;
};

export type AvalancheIssuerAlert = { code: string; address: string | null; blocks: string[] };

export type AvalancheMutualRefundState = {
  authorizationDeadline: number;
  typedDataDigest: string;
  clientSigned: boolean;
  freelancerSigned: boolean;
};

export type EvmWalletChallenge = { challengeId: string; walletAddress: string; message: string; expiresAt: string };

export type AvalancheActionInput = {
  idempotencyKey?: string;
  submissionId?: string;
  disputeId?: string;
  newDeliveryDueAt?: number;
  by?: string;
  clientSignature?: string;
  freelancerSignature?: string;
  authorizationDeadline?: number;
};

/** Phase 3 non-custodial payout (Avalanche rail). On-chain transfer and fiat payout confirmations are separate fields. */
export type AvalanchePayoutStatus = 'AWAITING_USER_TRANSFER' | 'TRANSFER_SUBMITTED' | 'TRANSFER_FAILED' | 'TRANSFER_CONFIRMED'
  | 'FIAT_PAYOUT_PENDING' | 'FIAT_PAYOUT_CONFIRMED' | 'FIAT_PAYOUT_FAILED';

export type AvalanchePayoutView = {
  id: string; contractId: string; networkId: string; environment: string; chainId: number; tokenAddress: string;
  amountBaseUnits: string; fromAddress: string; depositAddress: string; partnerId: string; partnerSimulated: boolean;
  partnerReportedFee: string | null; partnerReportedRate: string | null; status: AvalanchePayoutStatus;
  transferTxHash: string | null; transferChainState: string | null; transferMatch: string | null;
  onChainTransferConfirmed: boolean; transferConfirmedAt: string | null;
  fiatPayoutConfirmed: boolean; fiatReference: string | null; fiatConfirmedAt: string | null;
  failureReason: string | null; taxTreatment: string; notice: string;
  /** Built by the backend; present only while a transfer is awaited. The UI sends it unchanged from the user's wallet. */
  preparedTransfer: { chainId: number; to: string; data: string; value: string } | null;
};

export type AvalanchePayoutRequestResult = {
  outcome: 'CREATED' | 'EXISTING' | 'UNSUPPORTED_RAIL'; reason: string | null; payout: AvalanchePayoutView | null;
};
