// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Account } from './Account';
import { Activity } from './Activity';
import { activityPresentation } from './activityPresentation';
import { App } from './App';
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

describe('P1 Step 4/5 Marketplace activity', () => {
  it.each([
    [client, 'RELEASE_CONFIRMED', 'Thanh toán'], [freelancer, 'REFUND_PENDING', 'Thu nhập'],
    [client, 'CANCELLATION_REQUESTED', 'Công việc'], [freelancer, 'CANCELLATION_REJECTED', 'Công việc'],
  ].map(([user, type, activeNav]) => ({ user: user as User, type: type as string, activeNav: activeNav as string })))
  ('opens the real App destination for $user.userType / $type', async ({ user, type, activeNav }) => {
    vi.spyOn(api, 'restore').mockResolvedValue(user);
    vi.spyOn(api, 'notifications').mockResolvedValue({ currentPage: 0, pageSize: 10, totalPages: 1, totalElements: 1,
      data: [{ id: 'event', type, title: 'Server title', message: 'Server message', jobId: job.id,
        read: true, createdAt: null, amount: null }] });
    const jobRead = vi.spyOn(api, 'job').mockImplementation(() => new Promise(() => {}));
    await render(<SessionProvider><App /></SessionProvider>, '/activity');
    await act(async () => (host.querySelector('.activity-row-content a') as HTMLAnchorElement).click());
    expect(host.querySelector('.primary-nav [aria-current="page"]')?.textContent).toBe(activeNav);
    expect(jobRead).toHaveBeenCalledExactlyOnceWith(job.id);
    expect(host.querySelector('.activity-list')).toBeNull();
  });
  it.each([
    ['RELEASE_CONFIRMED', 'Bản ghi release đã xác nhận', '/finance?jobId=job-1'],
    ['CANCELLATION_REQUESTED', 'Đề nghị hủy — công việc tiếp tục', '/work/job-1'],
    ['CANCELLATION_REJECTED', 'Đề nghị hủy bị từ chối — công việc tiếp tục', '/work/job-1'],
    ['REFUND_PENDING', 'Hoàn tiền đang đối soát', '/finance?jobId=job-1'],
    ['REFUND_CONFIRMED', 'Bản ghi hoàn tiền đã xác nhận', '/finance?jobId=job-1'],
  ])('renders %s and links to its existing role-shared destination', async (type, label, destination) => {
    vi.spyOn(api, 'notifications').mockResolvedValue({ currentPage: 0, pageSize: 10, totalPages: 1, totalElements: 1,
      data: [{ id: 'event', type, title: 'Server title', message: 'Server message', jobId: job.id,
        read: true, createdAt: null, amount: null }] });
    await render(<Activity />, '/activity');
    expect(host.querySelector('.activity-row-meta')?.textContent).toContain(label);
    expect(host.querySelector('.activity-row-content a')?.getAttribute('href')).toBe(destination);
    expect(host.textContent).not.toContain('ngân hàng đã hoàn tiền');
  });
  it('keeps unknown events safe and does not invent a job destination', async () => {
    vi.spyOn(api, 'notifications').mockResolvedValue({ currentPage: 0, pageSize: 10, totalPages: 1, totalElements: 1,
      data: [{ id: 'event', type: 'FUTURE_EVENT', title: '<script>unsafe</script>', message: 'Server message',
        jobId: null, read: true, createdAt: null, amount: null }] });
    await render(<Activity />, '/activity');
    expect(host.textContent).toContain('Cập nhật từ Marketplace');
    expect(host.querySelector('script')).toBeNull();
    expect(host.querySelector('.activity-row-content a')).toBeNull();
  });
});

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

describe('P06.5D editorial Marketplace notification ledger', () => {
  const note: MarketplaceNotification = { id: 'dispatch-1', type: 'WORK_SUBMITTED', title: 'Server title',
    message: 'Server message', jobId: job.id, read: false, amount: null, createdAt: '2026-10-07T09:15:00' };
  function notifications(data: MarketplaceNotification[], totalElements = data.length, totalPages = 1) {
    return vi.spyOn(api, 'notifications').mockResolvedValue({ currentPage: 0, pageSize: 10,
      totalPages, totalElements, data });
  }
  it.each([
    ['JOB_ASSIGNED', 'Được giao việc', 'work', 'success'],
    ['JOB_CANCELLED', 'Công việc đã hủy', 'work', 'closed'],
    ['WORK_SUBMITTED', 'Có bản bàn giao', 'work', 'active'],
    ['REVISION_REQUESTED', 'Yêu cầu chỉnh sửa', 'work', 'attention'],
    ['WORK_APPROVED', 'Bàn giao được duyệt', 'work', 'success'],
    ['FUNDING_CONFIRMED', 'Funding đã xác nhận', 'finance', 'success'],
    ['RELEASE_CONFIRMED', 'Bản ghi release đã xác nhận', 'finance', 'success'],
    ['CANCELLATION_REQUESTED', 'Đề nghị hủy — công việc tiếp tục', 'refund', 'attention'],
    ['CANCELLATION_REJECTED', 'Đề nghị hủy bị từ chối — công việc tiếp tục', 'refund', 'neutral'],
    ['REFUND_PENDING', 'Hoàn tiền đang đối soát', 'refund', 'active'],
    ['REFUND_CONFIRMED', 'Bản ghi hoàn tiền đã xác nhận', 'refund', 'success'],
    ['REVIEW_GRACE_STARTED', 'Gia hạn review', 'review', 'attention'],
    ['REVIEW_AUTO_APPROVED', 'Máy chủ tự duyệt', 'review', 'success'],
    ['DISPUTE_OPENED', 'Đã mở tranh chấp', 'dispute', 'attention'],
    ['DISPUTE_DECIDED', 'Admin đã quyết định tranh chấp', 'dispute', 'neutral'],
    ['REVIEW_INVITED', 'Mời đánh giá hợp đồng', 'review', 'attention'],
    ['REVIEW_PUBLISHED', 'Đánh giá đã công bố', 'review', 'success'],
    ['PAYMENT_SENT', 'Thanh toán Client', 'finance', 'neutral'],
    ['PAYMENT_RECEIVED', 'Chi trả mô phỏng', 'finance', 'success'],
    ['TAX_EXPORT_FAILED', 'Chứng từ thuế', 'tax', 'error'],
    ['PAYOUT_FAILED', 'Chi trả cần xử lý', 'finance', 'error'],
  ])('resolves %s without changing its human claim', (type, label, family, tone) => {
    expect(activityPresentation(type)).toEqual({ label, family, tone });
  });
  it.each(['FUTURE_EVENT', '__proto__', 'constructor', 'toString'])('keeps %s neutral and safe', async type => {
    expect(activityPresentation(type)).toEqual({ label: 'Cập nhật từ Marketplace', family: 'neutral', tone: 'neutral' });
    notifications([{ ...note, type, jobId: null, title: '<script>unsafe</script>', message: '<img src=x onerror=unsafe()>' }]);
    await render(<Activity />);
    expect(host.querySelector('.activity-tone-neutral.unread')).toBeTruthy();
    expect(host.querySelector('.activity-row-content')?.textContent).toContain('<img src=x onerror=unsafe()>');
    expect(host.querySelector('script, img, .activity-row-content a')).toBeNull();
  });
  it.each(['PAYOUT_FAILED', 'TAX_EXPORT_FAILED'])('keeps %s an error after it is read', async type => {
    notifications([{ ...note, type }]);
    vi.spyOn(api, 'markNotificationRead').mockResolvedValue({ ...note, type, read: true });
    await render(<Activity />);
    expect(host.querySelector('.activity-tone-error.unread')).toBeTruthy();
    await act(async () => click('Đánh dấu đã đọc').click());
    expect(host.querySelector('.activity-tone-error')).toBeTruthy();
    expect(host.querySelector('.unread')).toBeNull();
    expect(host.textContent).not.toContain('Đã xử lý');
  });
  it('separates unread confirmation from read cancellation and unknown outcomes', async () => {
    notifications([{ ...note, type: 'RELEASE_CONFIRMED' },
      { ...note, id: 'closed', type: 'JOB_CANCELLED', read: true },
      { ...note, id: 'decision', type: 'DISPUTE_DECIDED', read: true }]);
    await render(<Activity />);
    expect(host.querySelector('.activity-tone-success.unread')).toBeTruthy();
    expect(host.querySelector('.activity-tone-closed .is-read')).toBeTruthy();
    expect(host.querySelector('.activity-tone-neutral .is-read')).toBeTruthy();
    expect(host.querySelectorAll('.activity-tone-success')).toHaveLength(1);
  });
  it('uses account total metadata and counts unread on this page only, without guessing money', async () => {
    notifications([note, { ...note, id: 'second', amount: 123456.78 }, { ...note, id: 'third', read: true }], 42, 5);
    await render(<Activity />);
    expect(host.textContent).toContain('42 thông báo trong tài khoản');
    expect(host.textContent).toContain('2 chưa đọc trên trang này');
    expect(host.textContent).not.toContain('123456');
    expect(host.textContent).not.toMatch(/USD|VND|USDC/);
    expect(host.textContent).toContain('không phải nhật ký mọi sự kiện trên hệ thống');
  });
  it('renders the returned server object rather than optimistic read success', async () => {
    notifications([note]);
    vi.spyOn(api, 'markNotificationRead').mockResolvedValue({ ...note, read: false, title: 'Reconciled title' });
    await render(<Activity />);
    await act(async () => click('Đánh dấu đã đọc').click());
    expect(host.querySelector('.unread')).toBeTruthy();
    expect(host.textContent).toContain('Reconciled title');
    expect(host.textContent).not.toContain('Đã đọc');
  });
  it('has no mutation control for an already-read notification', async () => {
    notifications([{ ...note, read: true }]);
    const mark = vi.spyOn(api, 'markNotificationRead');
    await render(<Activity />);
    expect(host.querySelector('.activity-mark-read')).toBeNull();
    expect(mark).not.toHaveBeenCalled();
  });
  it('locks duplicate actions while pending and shows truthful mutation failure', async () => {
    notifications([note]);
    let reject!: (reason: Error) => void;
    const mark = vi.spyOn(api, 'markNotificationRead').mockImplementation(() => new Promise((_, fail) => { reject = fail; }));
    await render(<Activity />);
    const button = click('Đánh dấu đã đọc');
    await act(async () => { button.click(); button.click(); });
    expect(mark).toHaveBeenCalledExactlyOnceWith(note.id);
    expect(click('Đang lưu…').disabled).toBe(true);
    await act(async () => reject(new Error('Server could not save')));
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Server could not save');
    expect(host.querySelector('.unread')).toBeTruthy();
    expect(click('Đánh dấu đã đọc').disabled).toBe(false);
  });
  it.each([
    ['REVIEW_INVITED', '/work/job-1#contract-reviews'], ['REVIEW_PUBLISHED', '/work/job-1#contract-reviews'],
    ['PAYMENT_SENT', '/work/job-1'], ['TAX_EXPORT_FAILED', '/work/job-1'], ['FUTURE_EVENT', '/work/job-1'],
  ])('preserves the existing %s destination', async (type, href) => {
    notifications([{ ...note, type }]);
    await render(<Activity />);
    expect(host.querySelector('.activity-row-content a')?.getAttribute('href')).toBe(href);
  });
  it.each(['REVIEW_INVITED', 'WORK_SUBMITTED'])('preserves %s cross-surface dispatch after a successful response', async type => {
    notifications([{ ...note, type }]);
    vi.spyOn(api, 'markNotificationRead').mockResolvedValue({ ...note, type, read: true });
    const listener = vi.fn();
    const event = type === 'REVIEW_INVITED' ? 'freelax:rating-update' : 'freelax:review-update';
    window.addEventListener(event, listener);
    try {
      await render(<Activity />);
      await act(async () => click('Đánh dấu đã đọc').click());
      expect(listener).toHaveBeenCalledOnce();
      if (type === 'WORK_SUBMITTED') expect((listener.mock.calls[0][0] as CustomEvent).detail).toEqual({ jobId: job.id });
    } finally { window.removeEventListener(event, listener); }
  });
  it('loads only the requested server page, preserving order and page navigation', async () => {
    const list = notifications([note], 11, 2);
    list.mockResolvedValueOnce({ currentPage: 0, pageSize: 10, totalPages: 2, totalElements: 11, data: [note] });
    list.mockResolvedValueOnce({ currentPage: 1, pageSize: 10, totalPages: 2, totalElements: 11,
      data: [{ ...note, id: 'older', title: 'Older server record' }] });
    await render(<Activity />);
    expect(list).toHaveBeenCalledExactlyOnceWith(0);
    expect(click('Trang trước').disabled).toBe(true);
    await act(async () => click('Trang sau').click());
    expect(list).toHaveBeenLastCalledWith(1);
    expect(list).toHaveBeenCalledTimes(2);
    expect(host.textContent).toContain('Trang 2/2');
    expect(host.textContent).toContain('Older server record');
    expect(click('Trang sau').disabled).toBe(true);
  });
  it('keeps loading, read errors and retry separate from empty notification history', async () => {
    const list = vi.spyOn(api, 'notifications').mockImplementationOnce(() => new Promise(() => {}));
    await render(<Activity />);
    expect(host.textContent).toContain('Đang tải thông báo');
    await act(async () => root.unmount());
    root = createRoot(host);
    list.mockRejectedValueOnce(new Error('Read unavailable'));
    list.mockResolvedValueOnce({ currentPage: 0, pageSize: 10, totalPages: 0, totalElements: 0, data: [] });
    await render(<Activity />);
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Read unavailable');
    expect(host.querySelector('.activity-list')).toBeNull();
    await act(async () => click('Thử lại').click());
    expect(host.textContent).toContain('Chưa có thông báo');
    expect(host.textContent).not.toContain('không có giao dịch');
  });
});
