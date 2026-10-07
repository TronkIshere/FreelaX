// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Account } from './Account';
import { Activity } from './Activity';
import { ACTIVITY_MESSAGE_PREVIEW_LIMIT, activityPresentation, isLongActivityMessage } from './activityPresentation';
import { App } from './App';
import { AuthEntry } from './Auth';
import { api, ApiError } from './api';
import { Overview } from './Overview';
import { OwnProfile, ProfileRecord } from './Profile';
import { Portfolio } from './Portfolio';
import { PublicProfile } from './PublicProfile';
import { SessionProvider } from './session';
import type { Job, MyApplication, Notification as MarketplaceNotification, PortfolioItem, Profile, User } from './types';

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
  it.each([0, 240, 241])('uses a stable presentation-only threshold for a %i-character message', length => {
    expect(ACTIVITY_MESSAGE_PREVIEW_LIMIT).toBe(240);
    expect(isLongActivityMessage('x'.repeat(length))).toBe(length > 240);
  });
  it('expands and collapses the original server text without fetching, marking read or changing destinations/counts', async () => {
    const message = 'Server wording, unchanged. ' + 'transaction-reference-'.repeat(25);
    const list = notifications([{ ...note, message, type: 'PAYMENT_RECEIVED', amount: 123456.78 }], 42, 5);
    const mark = vi.spyOn(api, 'markNotificationRead');
    await render(<Activity />);
    const paragraph = host.querySelector('.activity-message-text')!;
    const toggle = click('Xem thêm nội dung');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-controls')).toBe(paragraph.id);
    expect(paragraph.classList.contains('is-collapsed')).toBe(true);
    expect(paragraph.textContent).toBe(message);
    await act(async () => toggle.click());
    expect(click('Thu gọn').getAttribute('aria-expanded')).toBe('true');
    expect(paragraph.classList.contains('is-expanded')).toBe(true);
    expect(paragraph.textContent).toBe(message);
    await act(async () => click('Thu gọn').click());
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(paragraph.classList.contains('is-collapsed')).toBe(true);
    expect(paragraph.textContent).toBe(message);
    expect(list).toHaveBeenCalledExactlyOnceWith(0);
    expect(mark).not.toHaveBeenCalled();
    expect(host.querySelector('.activity-row-content a')?.getAttribute('href')).toBe('/work/job-1');
    expect(host.textContent).toContain('42 thông báo trong tài khoản');
    expect(host.textContent).toContain('1 chưa đọc trên trang này');
    expect(host.textContent).not.toMatch(/123456|USD|VND|USDC/);
  });
  it('does not add disclosure to a short message or rewrite its text', async () => {
    notifications([note]);
    await render(<Activity />);
    expect(host.querySelector('.activity-message-toggle')).toBeNull();
    expect(host.querySelector('.activity-message-text')?.textContent).toBe(note.message);
    expect(host.querySelector('.is-collapsed, .is-expanded')).toBeNull();
  });
  it('keeps long HTML-like technical text inert and unknown severity neutral after expansion', async () => {
    const message = '<img src=x onerror=unsafe()> <script>unsafe()</script> ' + 'hash'.repeat(100);
    notifications([{ ...note, message, type: 'FUTURE_EVENT', jobId: null }]);
    await render(<Activity />);
    await act(async () => click('Xem thêm nội dung').click());
    expect(host.querySelector('.activity-message-text')?.textContent).toBe(message);
    expect(host.querySelector('script, img, .activity-row-content a')).toBeNull();
    expect(host.querySelector('.activity-tone-neutral.unread .activity-read-state')?.textContent).toBe('Chưa đọc');
  });
  it('keeps error severity and read actions independent of expanded content', async () => {
    const message = 'Exact server failure message. '.repeat(12);
    notifications([{ ...note, type: 'PAYOUT_FAILED', message }]);
    const mark = vi.spyOn(api, 'markNotificationRead').mockResolvedValue({ ...note, type: 'PAYOUT_FAILED', message, read: true });
    await render(<Activity />);
    await act(async () => click('Xem thêm nội dung').click());
    expect(host.querySelector('.activity-tone-error.unread .activity-read-state')?.textContent).toBe('Chưa đọc');
    expect(mark).not.toHaveBeenCalled();
    await act(async () => click('Đánh dấu đã đọc').click());
    expect(mark).toHaveBeenCalledExactlyOnceWith(note.id);
    expect(host.querySelector('.activity-tone-error .activity-event-type')).toBeTruthy();
    expect(host.querySelector('.activity-read-state.is-read')?.textContent).toBe('Đã đọc');
    expect(host.querySelector('.activity-mark-read')).toBeNull();
    expect(host.querySelector('.activity-message-text')?.textContent).toBe(message);
  });
  it('resets local expansion when reconciliation replaces the server message', async () => {
    const message = 'Original server message. '.repeat(15);
    const replacement = 'Reconciled server message. '.repeat(15);
    notifications([{ ...note, message }]);
    vi.spyOn(api, 'markNotificationRead').mockResolvedValue({ ...note, message: replacement, read: true });
    await render(<Activity />);
    await act(async () => click('Xem thêm nội dung').click());
    await act(async () => click('Đánh dấu đã đọc').click());
    expect(click('Xem thêm nội dung').getAttribute('aria-expanded')).toBe('false');
    expect(host.querySelector('.activity-message-text')?.textContent).toBe(replacement);
    expect(host.textContent).not.toContain(message);
  });
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

describe('P06.6 identity passport and Marketplace dossier', () => {
  const profile: Profile = { userId: freelancer.id, userType: 'FREELANCER', displayName: 'Marketplace Name', email: 'private@example.test', version: 7,
    headline: 'Server headline', bio: 'Server biography', avatarUrl: 'https://media.example.test/avatar', languages: [{ code: 'vi', proficiency: 'Native' }], skills: ['React', 'Java'],
    verification: { email: 'UNVERIFIED', identity: 'UNVERIFIED', paymentMethod: 'UNVERIFIED', source: 'NOT_CONFIGURED' },
    reputation: { completedContracts: 0, fundedContracts: 0, disputeCount: 0, reviewCount: 0, averageRating: null, onTimeRate: null, calculatedAt: '2026-10-07' } };
  const item: PortfolioItem = { id: 'folio', userId: freelancer.id, version: 3, title: 'Server project', description: 'Server project description', skills: ['React'], projectUrl: 'https://work.example.test/project', thumbnailUrl: 'https://media.example.test/project', completedAt: null, sortOrder: 0 };
  function reads(p = profile, rows: PortfolioItem[] = []) {
    vi.spyOn(api, 'ownProfile').mockResolvedValue(p); vi.spyOn(api, 'profile').mockResolvedValue(p);
    vi.spyOn(api, 'portfolio').mockResolvedValue(rows); vi.spyOn(api, 'publicReviews').mockResolvedValue([]);
  }
  const field = (label: string) => [...host.querySelectorAll('label')].find(l => l.textContent?.startsWith(label))!.querySelector('input,textarea') as HTMLInputElement;
  async function change(label: string, value: string) {
    const el = field(label); const ctor = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement : HTMLInputElement;
    await act(async () => { Object.getOwnPropertyDescriptor(ctor.prototype, 'value')!.set!.call(el, value); el.dispatchEvent(new Event('input', { bubbles: true })); });
  }
  async function save() { await act(async () => host.querySelector('.profile-editor')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); }
  it.each([client, freelancer])('keeps $userType session identity distinct from editable profile', async user => {
    reads({ ...profile, userId: user.id, userType: user.userType }); await render(<Account user={user} onLogout={vi.fn()} loggingOut={false} />);
    expect(host.querySelector('#account-identity-title')?.textContent).toBe(user.displayName);
    expect(host.querySelector('.account-record')?.textContent).toContain(user.email);
    expect(host.querySelector('.profile-record-heading h2')?.textContent).toBe('Marketplace Name');
    expect(host.querySelector('.account-record')?.textContent).not.toContain(profile.email);
    expect((host.querySelector('.account-technical') as HTMLDetailsElement).open).toBe(false);
    expect(host.querySelector('.account-technical code')?.textContent).toBe(user.id);
    expect(host.querySelector('.profile-document')?.textContent).not.toContain(user.email);
    expect(host.querySelectorAll('img')).toHaveLength(0);
  });
  it('keeps pending logout disabled without inventing account/security controls', async () => {
    reads(); const logout = vi.fn(); await render(<Account user={freelancer} onLogout={logout} loggingOut />);
    expect(click('Đang đăng xuất…').disabled).toBe(true); await act(async () => click('Đang đăng xuất…').click()); expect(logout).not.toHaveBeenCalled();
    expect(host.textContent).not.toMatch(/Đổi mật khẩu|Xóa tài khoản|2FA|Phantom/);
  });
  it.each([{ ...profile, userId: 'other' }, { ...profile, userType: 'CLIENT' as const }])('rejects an own-profile/session identity mismatch', async p => {
    reads(p); await render(<OwnProfile user={freelancer} />); expect(host.querySelector('.marketplace-profile-record')).toBeNull(); expect(host.textContent).toContain('Không thể tải'); expect(click('Thử tải hồ sơ')).toBeTruthy();
  });
  it('keeps real zero/null reputation, raw verification and explicit safe links', async () => {
    await render(<ProfileRecord profile={profile} />); expect(host.querySelector('.profile-no-rating')?.textContent).toBe('Chưa có đánh giá công bố');
    expect(host.textContent).not.toMatch(/0 \/ 5|Tỷ lệ đúng hạn|Top Rated|100%/);
    expect(host.querySelector('.profile-reputation')?.textContent).toContain('Hợp đồng đã cấp vốn0');
    const disclosure = host.querySelector('details') as HTMLDetailsElement; expect(disclosure.open).toBe(false); expect(disclosure.textContent).toContain('NOT_CONFIGURED');
    const link = host.querySelector('a')!; expect(link.getAttribute('target')).toBe('_blank'); expect(link.getAttribute('rel')).toBe('noopener noreferrer'); expect(host.querySelector('img')).toBeNull();
  });
  it('preserves long real content without private identity or truncation', async () => {
    const longName = 'Nguyễn '.repeat(10).trim(); const bio = 'Nội dung '.repeat(180);
    await render(<ProfileRecord profile={{ ...profile, displayName: longName, bio, skills: ['Responsive implementation with TypeScript'] }} />);
    expect(host.querySelector('h2')?.textContent).toBe(longName); expect(host.querySelector('.profile-bio')?.textContent).toBe(bio); expect(host.textContent).not.toContain(profile.email);
  });
  it('locks grouped editor fields and duplicate profile writes while pending', async () => {
    reads(); const patch = vi.spyOn(api, 'patchProfile').mockReturnValue(new Promise(() => {})); await render(<OwnProfile user={freelancer} />);
    await act(async () => click('Chỉnh sửa hồ sơ').click()); expect(field('Tên hiển thị').value).toBe(profile.displayName); expect(host.querySelectorAll('.profile-field-group > legend')).toHaveLength(4);
    await save(); await save(); expect(patch).toHaveBeenCalledOnce(); expect(field('Tên hiển thị').matches(':disabled')).toBe(true); expect(click('Lưu kỹ năng').disabled).toBe(true); expect(click('Đóng bản nháp').disabled).toBe(true);
    expect(patch.mock.calls[0][0]).toMatchObject({ version: 7, languages: profile.languages });
  });
  it('does not submit an invalid grouped-editor draft', async () => {
    reads(); const patch = vi.spyOn(api, 'patchProfile'); await render(<OwnProfile user={freelancer} />); await act(async () => click('Chỉnh sửa hồ sơ').click()); await change('Tên hiển thị', 'x'); await save(); expect(patch).not.toHaveBeenCalled(); expect(field('Tên hiển thị').value).toBe('x');
  });
  it('distinguishes a successful server save from failed session reconciliation', async () => {
    reads(); const updated = { ...profile, displayName: 'Saved Name', version: 8 }; const reconcile = vi.fn().mockRejectedValueOnce(new Error('Session offline')).mockResolvedValue(undefined);
    const patch = vi.spyOn(api, 'patchProfile').mockImplementation(async () => { vi.mocked(api.ownProfile).mockResolvedValue(updated); return updated; });
    await render(<Account user={freelancer} onLogout={vi.fn()} loggingOut={false} onReconcileUser={reconcile} />); await act(async () => click('Chỉnh sửa hồ sơ').click()); await change('Tên hiển thị', 'Saved Name'); await save();
    expect(host.querySelector('.profile-identity-retry')?.textContent).toContain('Hồ sơ đã lưu trên máy chủ'); expect(host.querySelector('#account-identity-title')?.textContent).toBe(freelancer.displayName);
    expect(host.querySelector('.profile-record-heading h2')?.textContent).toBe('Saved Name'); expect(host.querySelector('.profile-reconciliation')).toBeNull(); await act(async () => click('Đọc lại danh tính phiên').click()); expect(patch).toHaveBeenCalledOnce(); expect(reconcile).toHaveBeenCalledTimes(2);
  });
  it('uses local folio plates and safe links without fetching remote images', async () => {
    reads(profile, [item]); await render(<Portfolio userId={freelancer.id} />); expect(host.querySelector('.portfolio-plate')?.getAttribute('aria-hidden')).toBe('true'); expect(host.querySelector('img')).toBeNull();
    expect(host.querySelectorAll('a[target="_blank"][rel="noopener noreferrer"]')).toHaveLength(2); expect(host.textContent).toContain('1 công trình'); expect(host.querySelector('button.portfolio-add')).toBeNull(); expect(host.textContent).not.toContain('Hoàn thành:');
  });
  it('honors the twelve-item cap and does not infer project skills', async () => {
    reads(profile, Array.from({ length: 12 }, (_, i) => ({ ...item, id: String(i), skills: [], thumbnailUrl: null }))); await render(<Portfolio userId={freelancer.id} editable />);
    expect(click('Thêm công trình').disabled).toBe(true); expect(host.querySelectorAll('.portfolio-record')).toHaveLength(12); expect(host.querySelectorAll('.profile-tokens')).toHaveLength(0);
  });
  it('rejects a future portfolio date before persistence', async () => {
    reads(); const mutation = vi.spyOn(api, 'savePortfolio'); await render(<Portfolio userId={freelancer.id} editable />); await act(async () => click('Thêm công trình').click());
    await change('Tiêu đề công trình', 'Valid title'); await change('Mô tả công trình', 'Valid description'); await change('Ngày hoàn thành', '2999-01-01'); await save(); expect(mutation).not.toHaveBeenCalled();
  });
  it('reconciles an uncertain delete that already succeeded without repeating DELETE', async () => {
    reads(profile, [item]); vi.spyOn(api, 'deletePortfolio').mockImplementation(async () => { vi.mocked(api.portfolio).mockResolvedValue([]); throw new ApiError('Timeout', 0); });
    await render(<Portfolio userId={freelancer.id} editable />); await act(async () => click('Xóa Server project').click()); await act(async () => click('Xác nhận xóa công trình').click());
    expect(api.deletePortfolio).toHaveBeenCalledOnce(); expect(host.textContent).toContain('Danh sách máy chủ không còn mục này'); expect(host.querySelector('.portfolio-delete-confirmation')).toBeNull();
  });
  it.each([403, 404])('does not expose a partner profile after HTTP %s', async status => {
    reads(); vi.mocked(api.profile).mockRejectedValue(new ApiError('Unavailable profile', status)); await render(<Routes><Route path="/profiles/:userId" element={<PublicProfile user={client} />} /></Routes>, '/profiles/' + freelancer.id);
    expect(host.querySelector('.profile-document')).toBeNull(); expect(click('Thử đọc lại hồ sơ')).toBeTruthy(); expect(host.textContent).not.toContain('Marketplace Name');
  });
  it('keeps partner profile private and reviews subject to publication/moderation', async () => {
    reads(profile, [item]); const base = { id: 'r', contractId: 'contract', reviewerId: client.id, revieweeId: freelancer.id, submitted: true, publishedAt: '2026-10-07', overall: 4, contentHidden: false, reported: false };
    vi.mocked(api.publicReviews).mockResolvedValue([{ ...base, comment: 'Published server comment' }, { ...base, id: 'hidden', contentHidden: true, comment: 'Hidden server comment' }, { ...base, id: 'draft', publishedAt: null, comment: 'Unpublished server comment' }]);
    await render(<Routes><Route path="/profiles/:userId" element={<PublicProfile user={client} />} /></Routes>, '/profiles/' + freelancer.id);
    expect(host.textContent).not.toContain(profile.email); expect(host.textContent).not.toContain('Chỉnh sửa hồ sơ'); expect(host.textContent).not.toContain('Thêm công trình');
    expect(host.textContent).toContain('Published server comment'); expect(host.textContent).not.toContain('Hidden server comment'); expect(host.textContent).not.toContain('Unpublished server comment'); expect(api.publicReviews).toHaveBeenCalledWith(freelancer.id, 0);
  });
  it('clears the prior partner document while a different userId is loading', async () => {
    reads(); vi.mocked(api.profile).mockImplementation(id => id === freelancer.id ? Promise.resolve(profile) : new Promise(() => {}));
    await render(<><Link to="/profiles/other-partner">Different partner</Link><Routes><Route path="/profiles/:userId" element={<PublicProfile user={client} />} /></Routes></>, '/profiles/' + freelancer.id);
    expect(host.textContent).toContain('Marketplace Name'); await act(async () => (host.querySelector('a[href="/profiles/other-partner"]') as HTMLAnchorElement).click());
    expect(host.querySelector('.profile-document')).toBeNull(); expect(host.textContent).not.toContain('Marketplace Name'); expect(api.profile).toHaveBeenLastCalledWith('other-partner');
  });
  it('keeps Client partner company facts without a Freelancer portfolio slot', async () => {
    reads({ ...profile, userId: client.id, userType: 'CLIENT', companyName: 'Server Studio', companyWebsite: 'https://studio.example.test' });
    await render(<Routes><Route path="/profiles/:userId" element={<PublicProfile user={freelancer} />} /></Routes>, '/profiles/' + client.id);
    expect(host.textContent).toContain('Server Studio'); expect(host.querySelector('.portfolio-section')).toBeNull(); expect(api.portfolio).not.toHaveBeenCalled(); expect(host.querySelector('.profile-language-skills')?.textContent).not.toContain('React');
  });
  it('keeps existing twenty-record review pagination without fabricated totals', async () => {
    reads(); vi.mocked(api.publicReviews).mockImplementation(async (_, page) => page === 0 ? Array.from({ length: 20 }, (_, i) => ({ id: 'review-' + i, contractId: 'contract-' + i, reviewerId: client.id, revieweeId: freelancer.id, submitted: true, publishedAt: '2026-10-07', contentHidden: false, reported: false })) : []);
    await render(<Routes><Route path="/profiles/:userId" element={<PublicProfile user={client} />} /></Routes>, '/profiles/' + freelancer.id);
    expect(click('Trang tiếp').disabled).toBe(false); await act(async () => click('Trang tiếp').click()); expect(api.publicReviews).toHaveBeenLastCalledWith(freelancer.id, 1); expect(click('Trang tiếp').disabled).toBe(true); expect(host.textContent).toContain('Trang 2'); expect(host.textContent).not.toContain('Trang 2 /');
  });
});
