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
};

const OPEN_DISPUTE: ReadonlySet<string> = new Set(['OPEN', 'UNDER_REVIEW', 'DECISION_PENDING_RELEASE', 'DECISION_PENDING_REFUND']);
const DECIDED_DISPUTE: ReadonlySet<string> = new Set(['DECISION_PENDING_RELEASE', 'DECISION_PENDING_REFUND']);

/** Mutual refund needs both parties to sign the same typed data; the marketplace has no exchange for the first signature yet (register F13). */
export const MUTUAL_REFUND_WALLET_UNAVAILABLE =
  'Hoàn tiền thỏa thuận bằng ví chưa hỗ trợ: cần cả hai bên ký cùng một dữ liệu, hệ thống chưa có nơi lưu chữ ký thứ nhất.';

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
  if (party) offers.push({ action: 'mutual-refund', input: {}, wallet: false, walletUnavailableReason: MUTUAL_REFUND_WALLET_UNAVAILABLE });
  return offers;
}

/** Converts a `datetime-local` value to epoch seconds; null when empty or invalid. The contract enforces the bounds. */
export function deliveryDateSeconds(value: string): number | null {
  if (!value) return null;
  const ms = new Date(value).getTime();
  return Number.isFinite(ms) ? Math.floor(ms / 1000) : null;
}
