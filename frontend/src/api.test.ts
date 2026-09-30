import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, MarketplaceApi, serverPage, trustedUser } from './api';
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

describe('Marketplace API contract', () => {
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
      .mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token-A', userId: user.id }))
      .mockResolvedValueOnce(response(user));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    expect((await api.signIn('client@example.test', 'pass')).userType).toBe('CLIENT');
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/auth/sign-in');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/auth/me');
    expect(((fetchMock.mock.calls[1][1] as RequestInit).headers as Headers).get('Authorization')).toBe('Bearer token-A');
  });

  it('restores the session through the refresh cookie before /auth/me', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ accessToken: 'token-B' }))
      .mockResolvedValueOnce(response({ ...user, userType: 'FREELANCER' }));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    expect((await api.restore()).userType).toBe('FREELANCER');
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

  it('reads applicants and PATCHes only the selected real freelancerId', async () => {
    const application = { id: 'app-1', jobId: 'job-one', freelancerId: 'freelancer-1',
      status: 'PENDING', createdAt: '2026-09-29T11:00:00' };
    const fetchMock = vi.fn().mockResolvedValueOnce(response({ status: 'SUCCESS', accessToken: 'token' }))
      .mockResolvedValueOnce(response(user)).mockResolvedValueOnce(response([application]))
      .mockResolvedValueOnce(response({ ...page.data[0], status: 'IN_PROGRESS', freelancerId: 'freelancer-1' }));
    vi.stubGlobal('fetch', fetchMock);
    const api = new MarketplaceApi();
    await api.signIn('client@example.test', 'pass');
    const applicants = await api.applicants('job-one');
    expect(applicants[0].freelancerId).toBe('freelancer-1');
    expect((await api.assign('job-one', applicants[0].freelancerId)).status).toBe('IN_PROGRESS');
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/marketplace/jobs/job-one/applications');
    expect(fetchMock.mock.calls[3][0]).toBe('/api/v1/marketplace/jobs/job-one/assign-freelancer');
    expect((fetchMock.mock.calls[3][1] as RequestInit).method).toBe('PATCH');
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
