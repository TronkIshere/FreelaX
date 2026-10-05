// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { api, ApiError, hasAuthority } from './api';
import { SessionProvider, useSession } from './session';
import type { User } from './types';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const client: User = { id: 'client-test', email: 'client@example.test', userType: 'CLIENT',
  displayName: 'Người dùng với tên dài để kiểm tra bố cục' };
const freelancer: User = { ...client, id: 'freelancer-test', userType: 'FREELANCER' };
const page = { currentPage: 0, pageSize: 10, totalPages: 0, totalElements: 0, data: [] };
let host: HTMLDivElement;
let root: Root;
beforeEach(() => {
  host = document.createElement('div'); document.body.append(host); root = createRoot(host);
  vi.spyOn(api, 'myJobs').mockResolvedValue(page);
  vi.spyOn(api, 'clientJobs').mockResolvedValue(page);
  vi.spyOn(api, 'discoverJobs').mockResolvedValue(page);
  vi.spyOn(api, 'myApplications').mockResolvedValue(page);
  vi.spyOn(api, 'notifications').mockResolvedValue(page);
  vi.spyOn(api, 'taxRecords').mockResolvedValue(page);
});
afterEach(() => {
  act(() => root.unmount()); host.remove(); vi.restoreAllMocks();
});
function Location() { return <output data-testid="path">{useLocation().pathname}</output>; }
function SessionAuthority() {
  const { session } = useSession();
  return <output data-testid="admin-authority">{String(session.status === 'ready' && hasAuthority(session.user, 'ROLE_ADMIN'))}</output>;
}
async function render(user: User, path = '/account') {
  vi.spyOn(api, 'restore').mockResolvedValue(user);
  await act(async () => {
    root.render(<MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <SessionProvider><App /><Location /><SessionAuthority /></SessionProvider>
    </MemoryRouter>);
  });
}
function currentPath() { return host.querySelector('[data-testid="path"]')?.textContent; }

describe('P06.2 role-aware shell', () => {
  it.each([client, freelancer, { ...client, authorities: ['ROLE_ADMIN'] }, { ...freelancer, authorities: ['ROLE_ADMIN'] }])('preserves the five destinations and trusted $userType identity', async user => {
    await render(user);
    const links = [...host.querySelectorAll('.primary-nav a')];
    expect(links.map(link => link.textContent)).toEqual([
      'Tổng quan', 'Công việc', user.userType === 'CLIENT' ? 'Thanh toán' : 'Thu nhập', 'Hoạt động', 'Tài khoản',
    ]);
    expect(links.map(link => link.getAttribute('href'))).toEqual(['/', '/work', '/finance', '/activity', '/account']);
    expect(host.querySelector('.identity strong')?.textContent).toBe(user.displayName);
    expect(host.querySelector('.identity span')?.textContent).toBe(user.userType === 'CLIENT' ? 'Client' : 'Freelancer');
    expect(host.querySelector('.masthead select, .masthead input')).toBeNull();
    expect(host.querySelector('.top-context')).toBeNull();
    expect(host.querySelector('.brand small')).toBeNull();
    expect(host.querySelector('.primary-nav [aria-current="page"]')?.textContent).toBe('Tài khoản');
    expect(host.querySelector('.subnav')).toBeNull();
    expect(host.querySelector('[data-testid="admin-authority"]')?.textContent).toBe(String(hasAuthority(user, 'ROLE_ADMIN')));
    expect(host.querySelector('.admin-entry')?.textContent ?? null).toBe(hasAuthority(user, 'ROLE_ADMIN') ? 'Quản trị' : null);
    if (hasAuthority(user, 'ROLE_ADMIN')) expect(host.querySelector('.admin-entry')?.getAttribute('href')).toBe('/admin/disputes');
  });

  it.each([
    ['/work', 'Khám phá'], ['/work/applications', 'Ứng tuyển'], ['/work/mine', 'Công việc của tôi'],
  ])('keeps the Freelancer subnav and active destination at %s', async (path, label) => {
    await render(freelancer, path);
    const links = [...host.querySelectorAll('.subnav a')];
    expect(links.map(link => link.textContent)).toEqual(['Khám phá', 'Ứng tuyển', 'Công việc của tôi']);
    expect(links.map(link => link.getAttribute('href'))).toEqual(['/work', '/work/applications', '/work/mine']);
    expect(host.querySelector('.subnav [aria-current="page"]')?.textContent).toBe(label);
    expect(host.querySelectorAll('.subnav [aria-current="page"]')).toHaveLength(1);
    expect(host.querySelector('.primary-nav [aria-current="page"]')?.textContent).toBe('Công việc');
  });

  it('keeps parent navigation active on nested financial routes', async () => {
    await render(freelancer, '/finance/tax-records');
    expect(host.querySelectorAll('.primary-nav [aria-current="page"]')).toHaveLength(1);
    expect(host.querySelector('.primary-nav [aria-current="page"]')?.textContent).toBe('Thu nhập');
    expect(host.querySelector('.subnav')).toBeNull();
  });

  it('navigates through real links and provides a focusable skip-link destination', async () => {
    await render(client);
    expect(host.querySelector('.skip-link')?.getAttribute('href')).toBe('#main');
    expect(host.querySelector('main#main')?.getAttribute('tabindex')).toBe('-1');
    const link = host.querySelector('.primary-nav a[href="/activity"]') as HTMLAnchorElement;
    await act(async () => link.click());
    expect(currentPath()).toBe('/activity');
    expect(host.querySelector('.primary-nav [aria-current="page"]')?.textContent).toBe('Hoạt động');
  });

  it('keeps the Client work route on its own list without Freelancer navigation', async () => {
    await render(client, '/work');
    expect(currentPath()).toBe('/work');
    expect(host.querySelector('.subnav')).toBeNull();
    expect(api.clientJobs).toHaveBeenCalledOnce();
    expect(api.discoverJobs).not.toHaveBeenCalled();
  });

  it('keeps logout pending, disabled and recoverable without losing the shell', async () => {
    let reject!: (reason: Error) => void;
    const logout = vi.spyOn(api, 'signOut').mockImplementation(() => new Promise((_, fail) => { reject = fail; }));
    await render(freelancer);
    const button = host.querySelector('.masthead button') as HTMLButtonElement;
    await act(async () => button.click());
    await act(async () => button.click());
    expect(logout).toHaveBeenCalledOnce();
    expect(button.disabled).toBe(true);
    expect(button.textContent).toBe('Đang đăng xuất…');
    await act(async () => reject(new ApiError('Chưa thể đăng xuất', 503)));
    expect(host.querySelector('.logout-error[role="alert"]')?.textContent).toBe('Chưa thể đăng xuất');
    expect(button.disabled).toBe(false);
    expect(host.querySelectorAll('.primary-nav a')).toHaveLength(5);
  });

  it('preserves successful logout and the login route', async () => {
    const logout = vi.spyOn(api, 'signOut').mockResolvedValue(undefined);
    await render(client);
    await act(async () => (host.querySelector('.masthead button') as HTMLButtonElement).click());
    expect(logout).toHaveBeenCalledOnce();
    expect(currentPath()).toBe('/login');
    expect(host.querySelector('.primary-nav')).toBeNull();
    expect(host.querySelector('input[type="email"]')).toBeTruthy();
  });
});
