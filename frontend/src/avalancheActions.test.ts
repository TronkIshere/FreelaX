import { describe, expect, it } from 'vitest';
import { availableAvalancheActions, deliveryDateSeconds, type AvalancheActionContext } from './avalancheActions';

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
      expect(Object.keys(a.input).every(k => k === 'submissionId' || k === 'disputeId')).toBe(true);
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
