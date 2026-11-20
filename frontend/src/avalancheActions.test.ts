import { describe, expect, it } from 'vitest';
import { availableAvalancheActions, deliveryDateSeconds, offersForChainState, type AvalancheActionContext } from './avalancheActions';

const base: AvalancheActionContext = { userId: 'u', isClient: false, isFreelancer: false, isArbiter: false, submissions: [], dispute: null };
const actions = (over: Partial<AvalancheActionContext>) => availableAvalancheActions({ ...base, ...over }).map(a => a.action);
const offer = (over: Partial<AvalancheActionContext>, action: string) =>
  availableAvalancheActions({ ...base, ...over }).find(a => a.action === action);

describe('availableAvalancheActions (mirrors marketplace AvalancheEscrowService.authorize)', () => {
  it('offers nothing to a non-participant who is not the arbiter', () => {
    expect(actions({})).toEqual([]);
  });

  it('offers the client fund, extension approval, release and mutual refund', () => {
    expect(actions({ isClient: true })).toEqual(['fund', 'extension-approve', 'release', 'mutual-refund']);
  });

  it('offers the freelancer extension request, release and mutual refund', () => {
    expect(actions({ isFreelancer: true })).toEqual(['extension-request', 'release', 'mutual-refund']);
    expect(offer({ isFreelancer: true }, 'extension-request')?.needsDate).toBe(true);
  });

  it('anchors only the latest submission, and only when it is in review', () => {
    const submissions = [{ id: 's1', version: 1, status: 'REVISION_REQUESTED' as const }, { id: 's2', version: 2, status: 'SUBMITTED' as const }];
    expect(offer({ isFreelancer: true, submissions }, 'submit')?.input).toEqual({ submissionId: 's2' });
    expect(offer({ isClient: true, submissions }, 'revise')).toBeUndefined();
    expect(offer({ isFreelancer: true, submissions: [{ id: 's3', version: 1, status: 'APPROVED' }] }, 'submit')).toBeUndefined();
  });

  it('offers the client a revision anchor for a recorded revision request', () => {
    const submissions = [{ id: 's1', version: 1, status: 'REVISION_REQUESTED' as const }];
    expect(offer({ isClient: true, submissions }, 'revise')?.input).toEqual({ submissionId: 's1' });
    expect(offer({ isFreelancer: true, submissions }, 'revise')).toBeUndefined();
  });

  it('lets only the party who opened the recorded dispute anchor it, while it is open', () => {
    const dispute = { disputeId: 'd', status: 'OPEN' as const, openedBy: 'u' };
    expect(offer({ isClient: true, dispute }, 'dispute')?.input).toEqual({ disputeId: 'd' });
    expect(offer({ isFreelancer: true, dispute: { ...dispute, openedBy: 'other' } }, 'dispute')).toBeUndefined();
    expect(offer({ isClient: true, dispute: { ...dispute, status: 'RESOLVED_RELEASE' } }, 'dispute')).toBeUndefined();
  });

  it('offers resolve only to a non-participant arbiter once a decision awaits settlement', () => {
    const decided = { disputeId: 'd', status: 'DECISION_PENDING_REFUND' as const, openedBy: 'x' };
    expect(actions({ isArbiter: true, dispute: decided })).toEqual(['resolve']);
    expect(offer({ isArbiter: true, dispute: { ...decided, status: 'UNDER_REVIEW' } }, 'resolve')).toBeUndefined();
    expect(offer({ isArbiter: true, isClient: true, dispute: decided }, 'resolve')).toBeUndefined();
    expect(offer({ isClient: true, dispute: decided }, 'resolve')).toBeUndefined();
  });

  it('offers mutual refund by wallet to both parties (F13 resolved) and never to a non-party', () => {
    expect(offer({ isClient: true }, 'mutual-refund')?.wallet).toBe(true);
    expect(offer({ isFreelancer: true }, 'mutual-refund')?.wallet).toBe(true);
    expect(offer({ isArbiter: true }, 'mutual-refund')).toBeUndefined();
  });

  it('never puts amounts, parties or signatures into the input', () => {
    const all = availableAvalancheActions({ ...base, isClient: true, isFreelancer: true, isArbiter: true,
      submissions: [{ id: 's', version: 1, status: 'SUBMITTED' }], dispute: { disputeId: 'd', status: 'OPEN', openedBy: 'u' } });
    for (const a of all) {
      // releaseToFreelancer is the proposed outcome of a mutual settlement (R12), never an amount, party or signature
      expect(Object.keys(a.input).every(k => k === 'submissionId' || k === 'disputeId' || k === 'releaseToFreelancer')).toBe(true);
    }
  });

  it('R12: offers both settlement outcomes to each party while a recorded dispute is unresolved, never to others', () => {
    const dispute = { disputeId: 'd', status: 'UNDER_REVIEW' as const, openedBy: 'x' };
    for (const role of [{ isClient: true }, { isFreelancer: true }]) {
      const settle = availableAvalancheActions({ ...base, ...role, dispute }).filter(a => a.action === 'mutual-settle');
      expect(settle.map(a => [a.variant, a.input])).toEqual([
        ['release', { disputeId: 'd', releaseToFreelancer: true }], ['refund', { disputeId: 'd', releaseToFreelancer: false }]]);
      expect(settle.every(a => a.wallet)).toBe(true);
    }
    expect(offer({ isArbiter: true, dispute: { ...dispute, status: 'DECISION_PENDING_RELEASE' } }, 'mutual-settle')).toBeUndefined();
    expect(offer({ isClient: true }, 'mutual-settle')).toBeUndefined();
    for (const status of ['RESOLVED_RELEASE', 'RESOLVED_REFUND', 'CANCELLED'] as const) {
      expect(offer({ isClient: true, dispute: { ...dispute, status } }, 'mutual-settle')).toBeUndefined();
    }
  });
});

describe('deliveryDateSeconds', () => {
  it('converts a datetime-local value and rejects empty or invalid input', () => {
    expect(deliveryDateSeconds('')).toBeNull();
    expect(deliveryDateSeconds('not a date')).toBeNull();
    expect(deliveryDateSeconds('2026-11-10T12:00')).toBe(Math.floor(new Date('2026-11-10T12:00').getTime() / 1000));
  });
});

describe('offersForChainState (B7 follow-up 2026-11-20: no buttons the escrow would revert)', () => {
  const client = availableAvalancheActions({ ...base, isClient: true, submissions: [{ id: 's', version: 1, status: 'SUBMITTED' }] });
  const freelancer = availableAvalancheActions({ ...base, isFreelancer: true, submissions: [{ id: 's', version: 1, status: 'SUBMITTED' }] });
  const names = (offers: ReturnType<typeof offersForChainState>) => offers.map(o => o.action).sort();
  const chain = (status: string, reviewDueAt = 1000) => ({ status, deliveryDueAt: 500, reviewDueAt });
  it('offers only funding before the escrow exists', () => {
    expect(names(offersForChainState(client, null, true, 0))).toEqual(['fund']);
    expect(names(offersForChainState(freelancer, null, false, 0))).toEqual([]);
  });
  it('offers nothing once the escrow is released or refunded', () => {
    for (const status of ['RELEASED', 'REFUNDED', 'Released']) {
      expect(offersForChainState(client, chain(status), true, 0)).toEqual([]);
      expect(offersForChainState(freelancer, chain(status), false, 0)).toEqual([]);
    }
  });
  it('while funded: extension, submit and mutual refund, no release', () => {
    expect(names(offersForChainState(client, chain('FUNDED'), true, 0))).toEqual(['extension-approve', 'mutual-refund']);
    expect(names(offersForChainState(freelancer, chain('FUNDED'), false, 0))).toEqual(['extension-request', 'mutual-refund', 'submit']);
  });
  it('while submitted: the client releases at any time, the freelancer only after the review window', () => {
    expect(names(offersForChainState(client, chain('SUBMITTED'), true, 0))).toEqual(['mutual-refund', 'release']);
    expect(names(offersForChainState(freelancer, chain('SUBMITTED', 1000), false, 999))).toEqual(['mutual-refund']);
    expect(names(offersForChainState(freelancer, chain('SUBMITTED', 1000), false, 1000))).toEqual(['mutual-refund', 'release']);
  });
});
