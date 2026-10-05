import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, hasAuthority, MarketplaceApi, serverPage, trustedUser } from './api';
import type { DiscoveryFilters } from './types';

const user = { id: 'user-client', email: 'client@example.test', displayName: 'Client One', userType: 'CLIENT' };
const page = { currentPage: 1, pageSize: 10, totalPages: 3, totalElements: 21, data: [
  { id: 'job-one', title: 'Real server row', description: 'A job', budgetUsd: 250, status: 'OPEN',
    clientUserId: 'user-client', freelancerId: null, createdAt: '2026-09-29T10:00:00' },
] };

function response(data: unknown, status = 200) {
  return new Response(JSON.stringify({ code: 200, data }), { status, headers: { 'Content-Type': 'application/json' } });
}

afterEach(() => vi.unstubAllGlobals());

describe('P0 settlement and cancellation Marketplace contracts', () => {
  it('reads nullable settlement/cancellation envelopes and preserves distinct amount serializations', async () => {
    const mock = vi.fn().mockResolvedValueOnce(response({ accessToken: 'test' })).mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(response(null)).mockResolvedValueOnce(response(null))
      .mockResolvedValueOnce(response({ amount: 500.01, moneyStatus: 'SUCCEEDED', simulation: true }))
      .mockResolvedValueOnce(response({ amount: '500.01', cancellationStatus: 'REQUESTED', refundStatus: null }));
    vi.stubGlobal('fetch', mock); const api = new MarketplaceApi(); await api.restore();
    expect(await api.settlement('contract/one')).toBeNull(); expect(await api.cancellation('contract/one')).toBeNull();
    expect((await api.settlement('contract/one'))?.amount).toBe(500.01);
    expect((await api.cancellation('contract/one'))?.amount).toBe('500.01');
    expect(mock.mock.calls.slice(2).map(call => call[0])).toEqual([
      '/api/v1/contracts/contract%2Fone/settlement', '/api/v1/contracts/contract%2Fone/cancellations',
      '/api/v1/contracts/contract%2Fone/settlement', '/api/v1/contracts/contract%2Fone/cancellations',
    ]);
  });
  it('sends only cancellation intent and decision to Marketplace, with no invented refund key/amount', async () => {
    const mock = vi.fn().mockResolvedValueOnce(response({ accessToken: 'test' })).mockResolvedValueOnce(response(user))
      .mockImplementation(async () => response({ cancellationId: 'cancel' }));
    vi.stubGlobal('fetch', mock); const api = new MarketplaceApi(); await api.restore();
    await api.requestCancellation('c', { reasonCode: 'MUTUAL_CANCELLATION', description: 'Scope changed' });
    await api.decideCancellation('c', 'cancel/one', 'ACCEPT'); await api.decideCancellation('c', 'cancel/one', 'REJECT');
    expect(mock.mock.calls.slice(2).map(call => call[0])).toEqual([
      '/api/v1/contracts/c/cancellations', '/api/v1/contracts/c/cancellations/cancel%2Fone/decisions', '/api/v1/contracts/c/cancellations/cancel%2Fone/decisions',
    ]);
    expect(mock.mock.calls.slice(2).map(call => JSON.parse(call[1].body))).toEqual([
      { reasonCode: 'MUTUAL_CANCELLATION', description: 'Scope changed' }, { decision: 'ACCEPT' }, { decision: 'REJECT' },
    ]);
    for (const [, init] of mock.mock.calls.slice(2)) {
      expect(init.method).toBe('POST'); expect(init.headers.get('Idempotency-Key')).toBeNull();
      expect(init.credentials).toBe('include'); expect(init.headers.get('Authorization')).toBe('Bearer test');
    }
  });
  it('preserves exact cancellation intent through cookie refresh and exposes backend conflicts', async () => {
    const mock = vi.fn().mockResolvedValueOnce(response({ accessToken: 'before' })).mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 2005, error: 'expired' }), { status: 401 }))
      .mockResolvedValueOnce(response({ accessToken: 'after' })).mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(response({ cancellationId: 'saved' }));
    vi.stubGlobal('fetch', mock); const api = new MarketplaceApi(); await api.restore();
    await api.requestCancellation('c', { reasonCode: 'MUTUAL_CANCELLATION', description: 'Scope changed' });
    expect(mock.mock.calls[2][1].body).toBe(mock.mock.calls[5][1].body);
    mock.mockResolvedValueOnce(new Response(JSON.stringify({ status: 4037, error: 'conflict' }), { status: 409 }));
    await expect(api.decideCancellation('c', 'cancel', 'ACCEPT')).rejects.toMatchObject({ status: 409, code: 4037 });
  });
});

describe('Marketplace API contract', () => {
  it('preserves exact server authorities without inventing an Admin userType', () => {
    const parsed = trustedUser({ ...user, authorities: ['ROLE_USER', 'ROLE_ADMIN', 'ROLE_USER'] });
    expect(parsed.authorities).toEqual(['ROLE_USER', 'ROLE_ADMIN']);
    expect(parsed.userType).toBe('CLIENT');
    expect(hasAuthority(parsed, 'ROLE_ADMIN')).toBe(true);
    expect(hasAuthority(parsed, 'ADMIN')).toBe(false);
    expect(() => trustedUser({ ...user, userType: 'ADMIN' })).toThrow(ApiError);
    expect(hasAuthority(null, 'ROLE_ADMIN')).toBe(false);
    expect(hasAuthority(undefined, 'ROLE_ADMIN')).toBe(false);
  });

  it.each([undefined, null, 'ROLE_ADMIN', {}, ['ROLE_ADMIN', null], [' ROLE_ADMIN '], ['']])(
    'treats missing or malformed authorities %j as no capability without failing authentication', authorities => {
      const parsed = trustedUser({ ...user, authorities });
      expect(parsed.userType).toBe('CLIENT');
      expect(parsed.authorities).toEqual([]);
      expect(hasAuthority(parsed, 'ROLE_ADMIN')).toBe(false);
    },
  );

  it('accepts only a trusted backend role', () => {
    expect(trustedUser(user).userType).toBe('CLIENT');
    expect(() => trustedUser({ ...user, userType: undefined })).toThrow(ApiError);
  });

  it('requires the server pagination wrapper', () => {
    expect(serverPage(page).totalElements).toBe(21);
    expect(() => serverPage(page.data)).toThrow(ApiError);
  });

  it('signs in, then resolves role from /auth/me', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token-A', userId: user.id, authorities: ['ROLE_ADMIN'] }))
      .mockResolvedValueOnce(response({ ...user, authorities: ['ROLE_USER'] }));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    const signedIn = await api.signIn('client@example.test', 'pass');
    expect(signedIn.userType).toBe('CLIENT');
    expect(signedIn.authorities).toEqual(['ROLE_USER']);
    expect(hasAuthority(signedIn, 'ROLE_ADMIN')).toBe(false);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/auth/sign-in');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/auth/me');
    expect(((fetchMock.mock.calls[1][1] as RequestInit).headers as Headers).get('Authorization')).toBe('Bearer token-A');
  });

  it('restores the session through the refresh cookie before /auth/me', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ accessToken: 'token-B' }))
      .mockResolvedValueOnce(response({ ...user, userType: 'FREELANCER', authorities: ['ROLE_ADMIN', 'ROLE_USER'] }));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    const restored = await api.restore();
    expect(restored.userType).toBe('FREELANCER');
    expect(restored.authorities).toEqual(['ROLE_ADMIN', 'ROLE_USER']);
    expect(hasAuthority(restored, 'ROLE_ADMIN')).toBe(true);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/auth/refresh-token');
    expect((fetchMock.mock.calls[0][1] as RequestInit).credentials).toBe('include');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/auth/me');
  });

  it('parses the participant page and discovery filters from server responses', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token-C' }))
      .mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(response(page))
      .mockResolvedValueOnce(response({ ...page, data: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('client@example.test', 'pass');
    expect((await api.clientJobs(1)).data[0].id).toBe('job-one');
    const filters: DiscoveryFilters = {
      keyword: 'editorial', minBudgetUsd: '100', maxBudgetUsd: '500',
      sort: 'BUDGET_DESC', application: 'NOT_APPLIED',
    };
    expect((await api.discoverJobs(2, filters)).currentPage).toBe(1);
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/marketplace/jobs?page=1&size=10');
    const url = new URL(fetchMock.mock.calls[3][0] as string, 'http://localhost');
    expect(url.pathname).toBe('/api/v1/marketplace/jobs/discover');
    expect(url.searchParams.get('page')).toBe('2');
    expect(url.searchParams.get('keyword')).toBe('editorial');
    expect(url.searchParams.get('minBudgetUsd')).toBe('100');
    expect(url.searchParams.get('maxBudgetUsd')).toBe('500');
    expect(url.searchParams.get('sort')).toBe('BUDGET_DESC');
    expect(url.searchParams.get('application')).toBe('NOT_APPLIED');
  });

  it('reports backend auth errors and sends the access token on logout', async () => {
    const failed = new Response(JSON.stringify({ status: 2003, error: 'Sai email hoặc mật khẩu' }), { status: 401 });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(failed));
    await expect(new MarketplaceApi().signIn('wrong@example.test', 'pass')).rejects.toThrow('Sai email hoặc mật khẩu');
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token-D' }))
      .mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(response(null));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('client@example.test', 'pass');
    await api.signOut();
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/auth/sign-out');
    expect(JSON.parse((fetchMock.mock.calls[2][1] as RequestInit).body as string)).toEqual({ accessToken: 'token-D' });
  });
});

it('expires the session only after a protected request and refresh both fail', async () => {
  const unauthorized = new Response(JSON.stringify({ status: 2005, error: 'Phiên đăng nhập đã hết hạn' }), { status: 401 });
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'stale-token' }))
    .mockResolvedValueOnce(response(user))
    .mockResolvedValueOnce(unauthorized)
    .mockResolvedValueOnce(unauthorized);
  vi.stubGlobal('fetch', fetchMock);
  const api = new MarketplaceApi();
  const expired = vi.fn();
  api.onSessionExpired = expired;
  await api.signIn('client@example.test', 'pass');
  await expect(api.clientJobs(0)).rejects.toThrow('Phiên đăng nhập đã hết hạn');
  expect(fetchMock.mock.calls[3][0]).toBe('/api/v1/auth/refresh-token');
  expect(expired).toHaveBeenCalledOnce();
});


describe('P05.2 mutation contracts', () => {
  it('POSTs empty JSON to Apply and reads the application response', async () => {
    const application = { id: 'application-1', jobId: 'job-one', freelancerId: 'freelancer-1',
      status: 'PENDING', createdAt: '2026-09-30T00:00:00' };
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token' }))
      .mockResolvedValueOnce(response({ ...user, userType: 'FREELANCER' }))
      .mockResolvedValueOnce(response(application));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('freelancer@example.test', 'pass');
    expect((await api.apply('job-one')).status).toBe('PENDING');
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/marketplace/jobs/job-one/apply');
    expect((fetchMock.mock.calls[2][1] as RequestInit).method).toBe('POST');
    expect((fetchMock.mock.calls[2][1] as RequestInit).body).toBe('{}');
  });

  it('parses my applications page with exact status filter and pagination', async () => {
    const applications = { ...page, data: [{ id: 'app-1', status: 'ACCEPTED',
      createdAt: '2026-09-29T11:00:00', updatedAt: '2026-09-29T12:00:00',
      job: { id: 'job-one', title: 'Real server row', budgetUsd: 250, status: 'IN_PROGRESS',
        clientDisplayName: 'Client One' } }] };
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token' }))
      .mockResolvedValueOnce(response({ ...user, userType: 'FREELANCER' }))
      .mockResolvedValueOnce(response(applications));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('freelancer@example.test', 'pass');
    const result = await api.myApplications(1, 'ACCEPTED', 5);
    expect(result.data[0].job.clientDisplayName).toBe('Client One');
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/marketplace/jobs/applications/me?page=1&size=5&status=ACCEPTED');
  });

  it('reads applicants and creates an assignment for the selected real freelancerId', async () => {
    const application = { id: 'app-1', jobId: 'job-one', freelancerId: 'freelancer-1',
      status: 'PENDING', createdAt: '2026-09-29T11:00:00' };
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token' }))
      .mockResolvedValueOnce(response(user)).mockResolvedValueOnce(response([application]))
      .mockResolvedValueOnce(response({ ...page.data[0], status: 'AWAITING_PAYMENT', freelancerId: 'freelancer-1' }));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('client@example.test', 'pass');
    const applicants = await api.applicants('job-one');
    expect(applicants[0].freelancerId).toBe('freelancer-1');
    expect((await api.assign('job-one', applicants[0].freelancerId)).status).toBe('AWAITING_PAYMENT');
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/marketplace/jobs/job-one/applications');
    expect(fetchMock.mock.calls[3][0]).toBe('/api/v1/marketplace/jobs/job-one/assignments');
    expect((fetchMock.mock.calls[3][1] as RequestInit).method).toBe('POST');
    expect(JSON.parse((fetchMock.mock.calls[3][1] as RequestInit).body as string)).toEqual({ freelancerId: 'freelancer-1' });
  });

  it('preserves backend numeric error codes for already applied reconciliation', async () => {
    const failed = new Response(JSON.stringify({ status: 4008, error: 'Bạn đã ứng tuyển' }), { status: 409 });
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token' }))
      .mockResolvedValueOnce(response({ ...user, userType: 'FREELANCER' })).mockResolvedValueOnce(failed);
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('freelancer@example.test', 'pass');
    await expect(api.apply('job-one')).rejects.toMatchObject({ status: 409, code: 4008 });
  });
});

describe('P06.4 real funding/contract API', () => {
  it('uses bank GET/PUT, latest/exact funding reads and exact decimal POST', async () => {
    const mock = vi.fn().mockImplementation(async () => response(null));
    mock.mockResolvedValueOnce(response({ accessToken: 'test' })).mockResolvedValueOnce(response(user));
    vi.stubGlobal('fetch', mock); const api = new MarketplaceApi(); await api.restore();
    await api.clientBank(); await api.saveClientBank({ bankCode: 'BIDV', bankAccountNumber: '123456', bankAccountHolderName: 'Client' });
    await api.funding('contract', 'milestone'); await api.funding('contract', 'milestone', 'tx'); await api.fund('contract', 'milestone', 'stable-key', '500.01', 'USD');
    expect(mock.mock.calls.slice(2).map(c => c[0])).toEqual(['/api/v1/payment-methods/bank-account', '/api/v1/payment-methods/bank-account', '/api/v1/contracts/contract/milestones/milestone/fund', '/api/v1/contracts/contract/milestones/milestone/fund/tx', '/api/v1/contracts/contract/milestones/milestone/fund']);
    expect(mock.mock.calls[3][1].method).toBe('PUT');
    expect(JSON.parse(mock.mock.calls[6][1].body)).toEqual({ paymentMethodId: 'BANK_ACCOUNT_ON_FILE', expectedAmount: { amount: '500.01', currency: 'USD' } });
    expect(mock.mock.calls[6][1].headers.get('Idempotency-Key')).toBe('stable-key');
  });
  it.each(['fund', 'submit'] as const)('keeps %s key and exact payload through 401 refresh', async kind => {
    const mock = vi.fn().mockResolvedValueOnce(response({ accessToken: 'before' })).mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 2005, error: 'expired' }), { status: 401 }))
      .mockResolvedValueOnce(response({ accessToken: 'after' })).mockResolvedValueOnce(response(user)).mockResolvedValueOnce(response({ id: 'saved' }));
    vi.stubGlobal('fetch', mock); const api = new MarketplaceApi(); await api.restore();
    if (kind === 'fund') await api.fund('c', 'm', 'same', '500.00', 'USD');
    else await api.submitContract('c', 'same', { summary: 'Work', deliverables: [], acceptanceEvidence: [{ criterionId: 'a', note: 'Verified', url: '' }] });
    expect(mock.mock.calls[2][1].body).toBe(mock.mock.calls[5][1].body); expect(mock.mock.calls[2][1].headers.get('Idempotency-Key')).toBe('same'); expect(mock.mock.calls[5][1].headers.get('Idempotency-Key')).toBe('same');
  });
  it('uses only contract submissions and decisions with real response/error envelope', async () => {
    const mock = vi.fn().mockImplementation(async () => response([])); mock.mockResolvedValueOnce(response({ accessToken: 'test' })).mockResolvedValueOnce(response(user));
    vi.stubGlobal('fetch', mock); const api = new MarketplaceApi(); await api.restore(); await api.contractSubmissions('c');
    await api.decideSubmission('c', 's', { decision: 'REQUEST_REVISION', feedback: 'Fix', criterionIds: ['criterion'], deliverableIds: [] });
    expect(mock.mock.calls[2][0]).toBe('/api/v1/contracts/c/submissions'); expect(mock.mock.calls[3][0]).toBe('/api/v1/contracts/c/submissions/s/decisions');
    expect(JSON.parse(mock.mock.calls[3][1].body).criterionIds).toEqual(['criterion']);
    mock.mockResolvedValueOnce(new Response(JSON.stringify({ status: 4027, error: 'stale' }), { status: 409 }));
    await expect(api.decideSubmission('c', 's', { decision: 'APPROVE' })).rejects.toMatchObject({ status: 409, code: 4027 });
  });
});

describe('P05.3 work lifecycle API', () => {
  it('uses the participant jobs and ordered submissions contracts', async () => {
    const submission = { id: 'submission-1', jobId: 'job-one', freelancerId: 'freelancer-1',
      version: 1, summary: 'Delivered V1', deliverableUrl: null, status: 'SUBMITTED',
      reviewerFeedback: null, reviewedAt: null, createdAt: '2026-09-30T01:00:00', updatedAt: null };
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token' }))
      .mockResolvedValueOnce(response(user)).mockResolvedValueOnce(response(page))
      .mockResolvedValueOnce(response([submission]));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('client@example.test', 'pass');
    expect((await api.myJobs(0)).data[0].id).toBe('job-one');
    expect((await api.submissions('job-one'))[0].version).toBe(1);
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/marketplace/jobs?page=0&size=10');
    expect(fetchMock.mock.calls[3][0]).toBe('/api/v1/marketplace/jobs/job-one/submissions');
  });

  it('sends exactly summary and deliverableUrl for submit work', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token' }))
      .mockResolvedValueOnce(response({ ...user, userType: 'FREELANCER' }))
      .mockResolvedValueOnce(response({ id: 'submission-1', version: 1, status: 'SUBMITTED' }));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('freelancer@example.test', 'pass');
    expect((await api.submitWork('job-one', 'Finished V1', 'https://example.test/v1')).version).toBe(1);
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/marketplace/jobs/job-one/submit-work');
    expect((fetchMock.mock.calls[2][1] as RequestInit).method).toBe('POST');
    expect(JSON.parse((fetchMock.mock.calls[2][1] as RequestInit).body as string))
      .toEqual({ summary: 'Finished V1', deliverableUrl: 'https://example.test/v1' });
  });

  it('sends only feedback for revision and empty JSON for approval', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token' }))
      .mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(response({ id: 'submission-1', status: 'REVISION_REQUESTED' }))
      .mockResolvedValueOnce(response({ ...page.data[0], status: 'COMPLETED' }));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('client@example.test', 'pass');
    expect((await api.requestRevision('job-one', 'Please revise')).status).toBe('REVISION_REQUESTED');
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/marketplace/jobs/job-one/request-revision');
    expect(JSON.parse((fetchMock.mock.calls[2][1] as RequestInit).body as string))
      .toEqual({ feedback: 'Please revise' });
    expect((await api.approveWork('job-one')).status).toBe('COMPLETED');
    expect(fetchMock.mock.calls[3][0]).toBe('/api/v1/marketplace/jobs/job-one/approve');
    expect((fetchMock.mock.calls[3][1] as RequestInit).method).toBe('POST');
    expect((fetchMock.mock.calls[3][1] as RequestInit).body).toBe('{}');
  });
});

describe('P05.4 financial and tax API', () => {
  it('uses participant payment and tax-record endpoints with the server envelope', async () => {
    const taxPage = { currentPage: 0, pageSize: 10, totalPages: 1, totalElements: 1,
      data: [{ id: 'tax-one', jobId: 'job-one', status: 'ACCEPTED', statusLabel: 'Đã chấp nhận' }] };
    const tax = taxPage.data[0];
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token' }))
      .mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(response({ jobId: 'job-one', checkoutOrderStatus: 'CAPTURED' }))
      .mockResolvedValueOnce(response(taxPage))
      .mockResolvedValueOnce(response(tax))
      .mockResolvedValueOnce(response(tax))
      .mockResolvedValueOnce(response(tax))
      .mockResolvedValueOnce(response(tax));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('client@example.test', 'pass');
    expect((await api.paymentStatus('job-one')).checkoutOrderStatus).toBe('CAPTURED');
    expect((await api.taxRecords(0)).data[0].status).toBe('ACCEPTED');
    expect((await api.taxRecord('tax-one')).id).toBe('tax-one');
    expect((await api.taxRecordForJob('job-one')).id).toBe('tax-one');
    expect((await api.syncTaxRecord('tax-one')).status).toBe('ACCEPTED');
    expect((await api.retryTaxExport('tax-one')).status).toBe('ACCEPTED');
    expect(fetchMock.mock.calls.slice(2).map(call => call[0])).toEqual([
      '/api/v1/marketplace/jobs/job-one/payment-status',
      '/api/v1/marketplace/tax-records?page=0&size=10',
      '/api/v1/marketplace/tax-records/tax-one',
      '/api/v1/marketplace/tax-records/jobs/job-one',
      '/api/v1/marketplace/tax-records/tax-one/sync',
      '/api/v1/marketplace/tax-records/tax-one/retry-export',
    ]);
    expect((fetchMock.mock.calls[6][1] as RequestInit).method).toBe('POST');
    expect((fetchMock.mock.calls[7][1] as RequestInit).method).toBe('POST');
  });

  it('downloads authenticated PDF bytes without parsing the successful response as JSON', async () => {
    const file = new Response(new Blob(['%PDF-test'], { type: 'application/pdf' }),
      { headers: { 'Content-Type': 'application/pdf' } });
    const json = vi.spyOn(file, 'json');
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token' }))
      .mockResolvedValueOnce(response(user)).mockResolvedValueOnce(file);
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('client@example.test', 'pass');
    const blob = await api.downloadTaxFile('tax-one', 'pdf');
    expect(await blob.text()).toBe('%PDF-test');
    expect(json).not.toHaveBeenCalled();
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/marketplace/tax-records/tax-one/pdf');
    expect(((fetchMock.mock.calls[2][1] as RequestInit).headers as Headers).get('Authorization')).toBe('Bearer token');
    expect((fetchMock.mock.calls[2][1] as RequestInit).credentials).toBe('include');
  });

  it('refreshes the cookie session once for an expired authenticated file request', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'old-token' }))
      .mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 2005, error: 'expired' }), { status: 401 }))
      .mockResolvedValueOnce(response({ accessToken: 'new-token' }))
      .mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(new Response('<xml/>', { headers: { 'Content-Type': 'application/xml' } }));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('client@example.test', 'pass');
    expect(await (await api.downloadTaxFile('tax-one', 'xml')).text()).toBe('<xml/>');
    expect(fetchMock.mock.calls[3][0]).toBe('/api/v1/auth/refresh-token');
    expect(((fetchMock.mock.calls[5][1] as RequestInit).headers as Headers).get('Authorization')).toBe('Bearer new-token');
  });
});

describe('P05.5 auth and notification contracts', () => {
  it('registers Client with only supported core fields', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response(user));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    const input = { displayName: 'Client One', email: 'client@example.test',
      password: 'secret123', userType: 'CLIENT' as const };
    expect((await api.register(input)).userType).toBe('CLIENT');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/auth/register');
    expect(JSON.parse((fetchMock.mock.calls[0][1] as RequestInit).body as string)).toEqual(input);
  });

  it('registers Freelancer with required tax and bank fields and the real role enum', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response({ ...user, userType: 'FREELANCER' }));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    const input = { displayName: 'Freelancer One', email: 'freelancer@example.test',
      password: 'secret123', userType: 'FREELANCER' as const, taxCode: 'TAX-1',
      identityNumber: 'ID-1', nationality: 'Việt Nam', taxAddress: 'Hà Nội',
      bankCode: 'BIDV' as const, bankAccountNumber: '1234567890' };
    expect((await api.register(input)).userType).toBe('FREELANCER');
    expect(JSON.parse((fetchMock.mock.calls[0][1] as RequestInit).body as string)).toEqual(input);
  });

  it('loads paged notifications and PATCHes a real unread notification', async () => {
    const notification = { id: 'notification-1', type: 'WORK_SUBMITTED', title: 'Bàn giao',
      message: 'Freelancer đã gửi bản bàn giao', jobId: 'job-one', read: false,
      amount: null, createdAt: '2026-09-30T09:00:00' };
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token' }))
      .mockResolvedValueOnce(response(user))
      .mockResolvedValueOnce(response({ currentPage: 0, pageSize: 10,
        totalPages: 1, totalElements: 1, data: [notification] }))
      .mockResolvedValueOnce(response({ ...notification, read: true }));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('client@example.test', 'secret123');
    expect((await api.notifications(0)).data[0].read).toBe(false);
    expect((await api.markNotificationRead(notification.id)).read).toBe(true);
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/notifications?page=0&size=10');
    expect(fetchMock.mock.calls[3][0]).toBe('/api/v1/notifications/notification-1/read');
    expect((fetchMock.mock.calls[3][1] as RequestInit).method).toBe('PATCH');
  });
});
