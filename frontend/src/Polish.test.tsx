// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Account } from './Account';
import { Activity } from './Activity';
import { AuthEntry } from './Auth';
import { api, ApiError } from './api';
import { Overview } from './Overview';
import { SessionProvider } from './session';
import type { Job, MyApplication, Notification as MarketplaceNotification, User } from './types';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const client: User = { id: 'client-1', email: 'client@example.test', displayName: 'Client One', userType: 'CLIENT' };
const freelancer: User = { id: 'freelancer-1', email: 'freelancer@example.test',
  displayName: 'Freelancer One', userType: 'FREELANCER' };
const job: Job = { id: 'job-1', title: 'Editorial work', description: 'A real job', budgetUsd: 300,
  clientUserId: client.id, freelancerId: freelancer.id,
  status: 'SUBMITTED_FOR_REVIEW', createdAt: '2026-09-30T09:00:00' };
let host: HTMLDivElement;
let root: Root;
beforeEach(() => {
  host = document.createElement('div');
  document.body.append(host);
  root = createRoot(host);
});
afterEach(() => {
  act(() => root.unmount());
  host.remove();
  vi.restoreAllMocks();
});
async function render(element: React.ReactNode, path = '/') {
  await act(async () => { root.render(<MemoryRouter initialEntries={[path]}>{element}</MemoryRouter>); });
}
function click(label: string) {
  const button = [...host.querySelectorAll('button')].find(item => item.textContent?.trim() === label);
  expect(button).toBeTruthy();
  return button!;
}

describe('P05.5 final UI', () => {
  it('shows a real login form with password visibility and no dead OAuth actions', async () => {
    vi.spyOn(api, 'restore').mockRejectedValue(new ApiError('Guest', 401));
    await render(<SessionProvider><AuthEntry brand={<div>FreelaX</div>} /></SessionProvider>, '/login');
    expect(host.querySelector('input[type="email"]')).toBeTruthy();
    expect(host.querySelector('input[type="password"]')).toBeTruthy();
    expect(host.textContent).toContain('WORK.');
    expect(host.querySelector('a[href="/register"]')).toBeTruthy();
    expect(host.textContent).not.toContain('Google');
    expect(host.textContent).not.toContain('GitHub');
    await act(async () => { click('Hiện').click(); });
    expect(host.querySelector('input[type="text"]')).toBeTruthy();
  });

  it('shows required Freelancer signup fields and hides them for Client', async () => {
    await render(<AuthEntry brand={<div>FreelaX</div>} />, '/register');
    expect(host.querySelector('#register-tax')).toBeTruthy();
    expect(host.querySelector('#register-bank')).toBeTruthy();
    expect(host.querySelector('input[value="FREELANCER"]')).toBeTruthy();
    await act(async () => { (host.querySelector('input[value="CLIENT"]') as HTMLInputElement).click(); });
    expect(host.querySelector('#register-tax')).toBeNull();
    expect(host.querySelector('#register-bank')).toBeNull();
    expect(host.querySelector('a[href="/login"]')).toBeTruthy();
  });

  it('prioritizes real Client work awaiting review', async () => {
    vi.spyOn(api, 'myJobs').mockResolvedValue({ currentPage: 0, pageSize: 8,
      totalPages: 1, totalElements: 1, data: [job] });
    await render(<Overview user={client} />);
    expect(host.textContent).toContain('1 công việc trong tài khoản');
    expect(host.textContent).toContain('Duyệt bản bàn giao');
    expect(host.querySelector('a[href="/work/job-1"]')).toBeTruthy();
  });

  it('shows Freelancer revision and actual application status', async () => {
    vi.spyOn(api, 'myJobs').mockResolvedValue({ currentPage: 0, pageSize: 8,
      totalPages: 1, totalElements: 1, data: [{ ...job, status: 'REVISION_REQUESTED' }] });
    vi.spyOn(api, 'myApplications').mockResolvedValue({ currentPage: 0, pageSize: 4,
      totalPages: 1, totalElements: 1, data: [{ id: 'application-1', status: 'PENDING',
        createdAt: null, updatedAt: null, job: { id: job.id, title: job.title,
          description: job.description, budgetUsd: job.budgetUsd, status: 'OPEN',
          clientDisplayName: client.displayName, createdAt: null } } as MyApplication] });
    await render(<Overview user={freelancer} />);
    expect(host.textContent).toContain('Gửi bản sửa');
    expect(host.textContent).toContain('Đang chờ');
    expect(api.myApplications).toHaveBeenCalledWith(0, 'ALL', 4);
  });

  it('lists real notifications and marks only an unread item as read', async () => {
    const note: MarketplaceNotification = { id: 'note-1', title: 'Bàn giao mới', message: 'Freelancer đã gửi V1.',
      type: 'WORK_SUBMITTED', jobId: job.id, read: false, amount: null, createdAt: '2026-09-30T09:00:00' };
    vi.spyOn(api, 'notifications').mockResolvedValue({ currentPage: 0, pageSize: 10,
      totalPages: 1, totalElements: 1, data: [note] });
    const mark = vi.spyOn(api, 'markNotificationRead').mockResolvedValue({ ...note, read: true });
    await render(<Activity />, '/activity');
    expect(host.querySelector('.activity-row.unread')).toBeTruthy();
    expect(host.textContent).toContain('Có bản bàn giao');
    await act(async () => { click('Đánh dấu đã đọc').click(); });
    expect(mark).toHaveBeenCalledExactlyOnceWith('note-1');
    expect(host.querySelector('.activity-row.unread')).toBeNull();
    expect(host.textContent).toContain('Đã đọc');
  });

  it('shows only trusted account values and a working logout action', async () => {
    const logout = vi.fn();
    await render(<Account user={freelancer} onLogout={logout} loggingOut={false} />, '/account');
    expect(host.textContent).toContain('freelancer@example.test');
    expect(host.textContent).toContain('Freelancer / Người thực hiện');
    expect(host.textContent).not.toContain('Đổi mật khẩu');
    await act(async () => { click('Đăng xuất').click(); });
    expect(logout).toHaveBeenCalledOnce();
  });
});
