import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, MarketplaceApi, springPage } from './api';
import { validateDispute, validateDisputeEvidence } from './disputeContracts';
import type { DisputeEvidenceInput } from './types';

const user = { id: 'participant', email: 'participant@example.test', displayName: 'Participant', userType: 'CLIENT' };
const response = (data?: unknown) => new Response(JSON.stringify({ code: 200, ...(data === undefined ? {} : { data }) }));
afterEach(() => vi.unstubAllGlobals());
describe('Step 6 dispute contracts', () => {
  it('accepts 2,000 and rejects 2,001 characters without changing evidence limits', () => {
    expect(validateDispute({ reasonCode: 'QUALITY', description: 'x'.repeat(2000) })).toBeNull();
    expect(validateDispute({ reasonCode: 'QUALITY', description: 'x'.repeat(2001) })).toContain('2.000');
    expect(validateDispute({ reasonCode: '', description: 'Valid' })).not.toBeNull();
    expect(validateDispute({ reasonCode: 'x'.repeat(61), description: 'Valid' })).not.toBeNull();
    expect(validateDispute({ reasonCode: 'QUALITY', description: '   ' })).not.toBeNull();
  });
  it.each([
    { kind: 'LINK', url: 'https:example.test' }, { kind: 'LINK', url: 'https:///example.test' }, { kind: 'LINK', url: 'https://example.test\\proof' },
    { kind: 'TEXT', text: '' }, { kind: 'TEXT', text: 'x'.repeat(2001) }, { kind: 'TEXT', text: 'Valid', url: 'https://example.test' },
    { kind: 'LINK', url: 'http://example.test' }, { kind: 'LINK', url: 'javascript:alert(1)' },
    { kind: 'LINK', url: 'https://user:pass@example.test' }, { kind: 'LINK', url: 'https://' },
    { kind: 'LINK', url: 'https://example.test/' + 'x'.repeat(2048) }, { kind: 'LINK', url: 'https://example.test', text: 'x'.repeat(2001) },
    { kind: 'TEXT', text: 'Valid', sha256: 'a'.repeat(63) }, { kind: 'TEXT', text: 'Valid', sha256: 'z'.repeat(64) },
  ] as DisputeEvidenceInput[])('rejects invalid evidence %j', item => expect(validateDisputeEvidence([item])).not.toBeNull());
  it('accepts safe TEXT/LINK and valid optional hashes; limits batches to ten', () => {
    expect(validateDisputeEvidence([{ kind: 'TEXT', text: 'x'.repeat(2000), sha256: 'aF'.repeat(32) }, { kind: 'LINK', url: 'https://example.test/evidence', text: 'Context' }])).toBeNull();
    expect(validateDisputeEvidence(Array.from({ length: 11 }, () => ({ kind: 'TEXT', text: 'Evidence' })))).not.toBeNull();
  });
  it.each([undefined, null])('normalizes missing participant dispute data %j to null', async value => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(response({ accessToken: 'test' })).mockResolvedValueOnce(response(user)).mockResolvedValueOnce(response(value)));
    const api = new MarketplaceApi(); await api.restore(); expect(await api.dispute('c')).toBeNull();
  });
  it('uses exact participant/Admin routes, bare evidence arrays and supplied stable keys', async () => {
    const page = { content: [], number: 2, size: 20, totalPages: 3, totalElements: 41 };
    const mock = vi.fn().mockResolvedValueOnce(response({ accessToken: 'test' })).mockResolvedValueOnce(response(user)).mockImplementation(async url => response(url.includes('?status=') ? page : {}));
    vi.stubGlobal('fetch', mock); const api = new MarketplaceApi(); await api.restore();
    const evidence: DisputeEvidenceInput[] = [{ kind: 'TEXT', text: 'Proof' }];
    await api.openDispute('c/one', { reasonCode: 'QUALITY', description: 'Mismatch', evidence });
    await api.appendDisputeEvidence('c/one', 'd/one', 'append-key', evidence);
    expect(await api.adminDisputes('UNDER_REVIEW', 2)).toEqual(page);
    await api.adminDispute('d/one'); await api.claimDispute('d/one');
    await api.resolveDispute('d/one', 'decision-key', { outcome: 'REFUND_TO_CLIENT', reason: 'Reviewed' });
    expect(mock.mock.calls.slice(2).map(call => call[0])).toEqual([
      '/api/v1/contracts/c%2Fone/disputes', '/api/v1/contracts/c%2Fone/disputes/d%2Fone/evidence',
      '/api/v1/admin/disputes?status=UNDER_REVIEW&page=2&size=20', '/api/v1/admin/disputes/d%2Fone',
      '/api/v1/admin/disputes/d%2Fone/claim', '/api/v1/admin/disputes/d%2Fone/resolve',
    ]);
    expect(JSON.parse(mock.mock.calls[2][1].body)).toEqual({ reasonCode: 'QUALITY', description: 'Mismatch', evidence });
    expect(JSON.parse(mock.mock.calls[3][1].body)).toEqual(evidence);
    expect(mock.mock.calls[3][1].headers.get('Idempotency-Key')).toBe('append-key');
    expect(mock.mock.calls[7][1].headers.get('Idempotency-Key')).toBe('decision-key');
    expect(JSON.parse(mock.mock.calls[7][1].body)).toEqual({ outcome: 'REFUND_TO_CLIENT', reason: 'Reviewed' });
  });
  it('does not confuse Spring Page with the Marketplace wrapper', () => {
    expect(springPage({ content: [], number: 0, size: 20, totalPages: 0, totalElements: 0 }).content).toEqual([]);
    expect(() => springPage({ data: [], currentPage: 0, pageSize: 20, totalPages: 0, totalElements: 0 })).toThrow(ApiError);
  });
  it('preserves numeric business code and safe Step 7 support metadata', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(response({ accessToken: 'test' })).mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 4097, error: 'conflict', code: 'DISPUTE_CONFLICT', requestId: 'http-request-1', retryable: false }), { status: 409 })));
    const api = new MarketplaceApi(); await api.restore();
    await expect(api.dispute('c')).rejects.toMatchObject({ code: 4097, businessCode: 'DISPUTE_CONFLICT', requestId: 'http-request-1', retryable: false });
  });
  it('drops unsafe support metadata and does not retry a 503 mutation', async () => {
    const mock = vi.fn().mockResolvedValueOnce(response({ accessToken: 'test' })).mockResolvedValueOnce(response(user))
      .mockResolvedValue(new Response(JSON.stringify({ status: 5000, code: '<script>', requestId: 'stack\ntrace', retryable: true }), { status: 503 }));
    vi.stubGlobal('fetch', mock); const api = new MarketplaceApi(); await api.restore();
    await expect(api.appendDisputeEvidence('c', 'd', 'key', [{ kind: 'TEXT', text: 'Proof' }])).rejects.toMatchObject({ businessCode: null, requestId: null, retryable: true });
    expect(mock).toHaveBeenCalledTimes(3);
  });
});
