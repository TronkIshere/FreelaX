import type { AvalancheActionInput } from './avalancheTypes';
import type { ContractSubmission, Dispute } from './types';

/**
 * Which Avalanche escrow actions the current user may start, derived only from records the marketplace already holds.
 * Mirrors the checks in marketplace `AvalancheEscrowService.authorize`; the backend re-checks every rule and builds all
 * calldata and typed data, so this only decides what to offer. Nothing here is amount- or party-configurable.
 */
export type AvalancheActionContext = {
  userId: string;
  isClient: boolean;
  isFreelancer: boolean;
  /** Administrator acting as the arbiter role; must not be a participant of the contract. */
  isArbiter: boolean;
  submissions: readonly Pick<ContractSubmission, 'id' | 'version' | 'status'>[];
  dispute: Pick<Dispute, 'disputeId' | 'status' | 'openedBy'> | null;
};

export type AvalancheActionOffer = {
  action: string;
  /** Recorded ids the backend needs; never amounts, parties or signatures. */
  input: AvalancheActionInput;
  /** The user's own EVM wallet can perform it. */
  wallet: boolean;
  /** The action needs a delivery date chosen by the freelancer (extension request). */
  needsDate?: boolean;
  /** Shown instead of a wallet button when the wallet path is not available. */
  walletUnavailableReason?: string;
  /** Distinguishes two offers of the same action (the two outcomes of a mutual settlement). */
  variant?: string;
};

const OPEN_DISPUTE: ReadonlySet<string> = new Set(['OPEN', 'UNDER_REVIEW', 'DECISION_PENDING_RELEASE', 'DECISION_PENDING_REFUND']);
const DECIDED_DISPUTE: ReadonlySet<string> = new Set(['DECISION_PENDING_RELEASE', 'DECISION_PENDING_REFUND']);


function latestSubmission(submissions: AvalancheActionContext['submissions']) {
  return submissions.reduce<AvalancheActionContext['submissions'][number] | null>(
    (best, s) => (best === null || s.version > best.version ? s : best), null);
}

export function availableAvalancheActions(ctx: AvalancheActionContext): AvalancheActionOffer[] {
  const party = ctx.isClient || ctx.isFreelancer;
  const latest = latestSubmission(ctx.submissions);
  const d = ctx.dispute;
  const offers: AvalancheActionOffer[] = [];
  if (ctx.isClient) offers.push({ action: 'fund', input: {}, wallet: true });
  if (ctx.isFreelancer && latest?.status === 'SUBMITTED') offers.push({ action: 'submit', input: { submissionId: latest.id }, wallet: true });
  if (ctx.isClient && latest?.status === 'REVISION_REQUESTED') offers.push({ action: 'revise', input: { submissionId: latest.id }, wallet: true });
  if (ctx.isFreelancer) offers.push({ action: 'extension-request', input: {}, wallet: true, needsDate: true });
  if (ctx.isClient) offers.push({ action: 'extension-approve', input: {}, wallet: true });
  if (party && d && OPEN_DISPUTE.has(d.status) && d.openedBy === ctx.userId) {
    offers.push({ action: 'dispute', input: { disputeId: d.disputeId }, wallet: true });
  }
  if (party) offers.push({ action: 'release', input: {}, wallet: true });
  if (ctx.isArbiter && !party && d && DECIDED_DISPUTE.has(d.status)) {
    offers.push({ action: 'resolve', input: { disputeId: d.disputeId }, wallet: true });
  }
  // F13 resolved 2026-11-04: each party signs the same typed data with its own wallet; the marketplace holds the first signature.
  if (party) offers.push({ action: 'mutual-refund', input: {}, wallet: true });
  // R12 (owner decision 2026-11-05): while a recorded dispute is unresolved, both parties may settle it together, one of the
  // two outcomes the arbiter could choose (no partial split). Each party signs with its own wallet; the contract checks both.
  if (party && d && OPEN_DISPUTE.has(d.status)) {
    offers.push({ action: 'mutual-settle', input: { disputeId: d.disputeId, releaseToFreelancer: true }, wallet: true, variant: 'release' });
    offers.push({ action: 'mutual-settle', input: { disputeId: d.disputeId, releaseToFreelancer: false }, wallet: true, variant: 'refund' });
  }
  return offers;
}

/**
 * Keeps only the offers the escrow can accept in its current on-chain state (mirrors the state checks of FreelaXEscrow), so a
 * finished or not yet funded escrow does not show buttons that could only revert. `chain` is null before the escrow exists.
 * Time windows the contract also checks (delivery and review deadlines) are applied where the snapshot has them; the backend
 * and the contract still decide.
 */
export function offersForChainState(offers: AvalancheActionOffer[],
  chain: { status: string; deliveryDueAt?: number; reviewDueAt?: number } | null, isClient: boolean, nowSeconds: number): AvalancheActionOffer[] {
  const status = (chain?.status ?? 'NONE').toUpperCase();
  const reviewOpen = !!chain?.reviewDueAt && nowSeconds < chain.reviewDueAt;
  const allowed: Record<string, (s: string) => boolean> = {
    fund: s => s === 'NONE',
    submit: s => s === 'FUNDED' || s === 'REVISION',
    revise: s => s === 'SUBMITTED',
    'extension-request': s => s === 'FUNDED',
    'extension-approve': s => s === 'FUNDED',
    dispute: s => s === 'FUNDED' || s === 'SUBMITTED' || s === 'REVISION',
    // the client at any time while Submitted; anyone else only once the review window has passed
    release: s => s === 'SUBMITTED' && (isClient || !reviewOpen),
    resolve: s => s === 'DISPUTED',
    'mutual-refund': s => s === 'FUNDED' || s === 'SUBMITTED' || s === 'REVISION',
    'mutual-settle': s => s === 'DISPUTED',
  };
  return offers.filter(o => (allowed[o.action] ?? (() => true))(status));
}

/** Converts a `datetime-local` value to epoch seconds; null when empty or invalid. The contract enforces the bounds. */
export function deliveryDateSeconds(value: string): number | null {
  if (!value) return null;
  const ms = new Date(value).getTime();
  return Number.isFinite(ms) ? Math.floor(ms / 1000) : null;
}
