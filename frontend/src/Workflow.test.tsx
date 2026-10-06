// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './api';
import { ClientApplicants, JobDetail, MyApplications } from './Workflow';
import type { DiscoverJob, Job, JobApplication, MyApplication, Profile, User } from './types';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const client: User = { id: 'client-1', email: 'client@example.test', displayName: 'Client', userType: 'CLIENT' };
const freelancer: User = { id: 'freelancer-1', email: 'freelancer@example.test', displayName: 'Freelancer', userType: 'FREELANCER' };
const discover: DiscoverJob = { id: 'job-1', title: 'Editorial brief', description: 'Write an article.',
  budgetUsd: 120, status: 'OPEN', client: { id: client.id, displayName: 'Client' },
  hasApplied: false, applicationId: null, applicationStatus: null, createdAt: '2026-09-29T10:00:00' };
const job: Job = { id: 'job-1', title: 'Editorial brief', description: 'Write an article.',
  budgetUsd: 120, status: 'OPEN', clientUserId: client.id, freelancerId: null, createdAt: '2026-09-29T10:00:00' };
const pending: JobApplication = { id: 'application-1', jobId: job.id, freelancerId: freelancer.id,
  status: 'PENDING', createdAt: '2026-09-29T11:00:00' };
const myApplication: MyApplication = { id: pending.id, status: 'PENDING', createdAt: pending.createdAt,
  updatedAt: pending.createdAt, job: { id: job.id, title: job.title, description: job.description,
    budgetUsd: job.budgetUsd, status: 'OPEN', clientDisplayName: 'Client', createdAt: job.createdAt } };

let host: HTMLDivElement;
let root: Root;
beforeEach(() => {
  host = document.createElement('div'); document.body.append(host); root = createRoot(host);
  vi.spyOn(api, 'profile').mockRejectedValue(new Error('Hồ sơ chưa khả dụng'));
});

const publicProfile: Profile = { userId: freelancer.id, userType: 'FREELANCER', displayName: 'Mai Nguyễn',
  headline: 'Frontend Developer', countryCode: 'VN', availability: 'Sẵn sàng nhận việc',
  version: 1, skills: ['React', 'TypeScript'], languages: [],
  verification: { email: 'UNVERIFIED', identity: 'UNVERIFIED', paymentMethod: 'UNVERIFIED', source: 'NOT_CONFIGURED' },
  reputation: { completedContracts: 12, disputeCount: 2, reviewCount: 9, averageRating: '4.8', onTimeRate: null, calculatedAt: '2026-10-07T08:00:00Z' } };

describe('Client C3 candidate decision desk', () => {
  async function roster(list: JobApplication[] = [pending], current: Job = job) {
    vi.spyOn(api, 'job').mockResolvedValue(current);
    vi.spyOn(api, 'applicants').mockResolvedValue(list);
    await render(<ClientApplicants user={client} />, '/work/job-1/applications');
  }

  it('checks ownership before applications or public profiles, keeping private roster hidden', async () => {
    vi.spyOn(api, 'job').mockResolvedValue({ ...job, clientUserId: 'other-client' });
    const applications = vi.spyOn(api, 'applicants');
    await render(<ClientApplicants user={client} />, '/work/job-1/applications');
    expect(applications).not.toHaveBeenCalled(); expect(api.profile).not.toHaveBeenCalled();
    expect(host.querySelector('.candidates-roster')).toBeNull();
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('không sở hữu');
  });

  it('uses the Application endpoint for status/date and the matched public profile for identity/evidence', async () => {
    vi.mocked(api.profile).mockResolvedValue({ ...publicProfile, email: 'PRIVATE_EMAIL_MUST_NOT_RENDER' });
    await roster();
    expect(api.applicants).toHaveBeenCalledExactlyOnceWith(job.id);
    expect(api.profile).toHaveBeenCalledExactlyOnceWith(freelancer.id);
    expect(host.textContent).toContain('Mai Nguyễn'); expect(host.textContent).toContain('Frontend Developer');
    expect(host.textContent).toContain('VN'); expect(host.textContent).toContain('Sẵn sàng nhận việc');
    expect(host.querySelector('.candidate-status')?.textContent).toContain('Đang chờ');
    expect(host.querySelector('.candidate-date')?.textContent).toContain('2026-09-29 11:00');
    expect(host.textContent).not.toContain('PRIVATE_EMAIL');
    expect(host.querySelector('a[href="/profiles/freelancer-1"]')?.textContent).toContain('Xem hồ sơ');
  });

  it('fetches unique Freelancer IDs in parallel without portfolio/review fan-out', async () => {
    const ids: string[] = []; const resolves: ((value: Profile) => void)[] = [];
    vi.mocked(api.profile).mockImplementation(id => { ids.push(id); return new Promise(resolve => resolves.push(resolve)); });
    const portfolio = vi.spyOn(api, 'portfolio'); const reviews = vi.spyOn(api, 'publicReviews');
    await roster([pending, { ...pending, id: 'duplicate' }, { ...pending, id: 'second', freelancerId: 'freelancer-2' }]);
    expect(ids).toEqual([freelancer.id, 'freelancer-2']);
    expect(host.querySelectorAll('.candidate-row')).toHaveLength(3);
    await act(async () => resolves[0](publicProfile));
    expect(host.textContent).toContain('Mai Nguyễn');
    expect(host.textContent).toContain('Đang đọc hồ sơ công khai');
    await act(async () => resolves[1]({ ...publicProfile, userId: 'freelancer-2', displayName: 'Lan Trần' }));
    expect(host.textContent).toContain('Lan Trần');
    expect(portfolio).not.toHaveBeenCalled(); expect(reviews).not.toHaveBeenCalled();
  });

  it.each([
    { ...publicProfile, userId: 'someone-else' },
    { ...publicProfile, userType: 'CLIENT' as const },
  ])('rejects mismatched profile authority while preserving the Application', async profile => {
    vi.mocked(api.profile).mockResolvedValue(profile);
    await roster();
    expect(host.textContent).not.toContain('Mai Nguyễn');
    expect(host.textContent).toContain('Không đọc được tóm tắt hồ sơ');
    expect(host.textContent).toContain(freelancer.id);
    expect(host.querySelector('.candidate-status')?.textContent).toContain('Đang chờ');
    expect(button('Chọn Freelancer')).toBeTruthy();
  });

  it('isolates a failed profile and preserves server order and equal row weight', async () => {
    vi.mocked(api.profile).mockImplementation(id => id === freelancer.id ? Promise.reject(new Error('404'))
      : Promise.resolve({ ...publicProfile, userId: id }));
    await roster([{ ...pending, id: 'server-first', freelancerId: 'freelancer-2' }, pending]);
    const rows = [...host.querySelectorAll('.candidate-row')];
    expect(rows[0].textContent).toContain('Mai Nguyễn'); expect(rows[1].textContent).toContain(freelancer.id);
    expect(rows.map(row => row.className)).toEqual(['candidate-row job-identity-row application-pending', 'candidate-row job-identity-row application-pending']);
    expect(host.querySelector('.state-error')).toBeNull();
    expect(host.textContent).not.toMatch(/recommended|best match|top candidate|phù hợp nhất|ưu tiên|match score|rank score/i);
    expect(host.querySelectorAll('input, select, [role="progressbar"]')).toHaveLength(0);
    expect(host.querySelector('.candidates-results-heading')?.textContent).toContain('2 ứng tuyển');
  });

  it('separates real Job skills/category from candidate profile skills without match scoring', async () => {
    vi.mocked(api.profile).mockResolvedValue(publicProfile);
    await roster([pending], { ...job, category: 'BACKEND_API', skills: ['Java', 'Spring'] });
    expect([...host.querySelectorAll('.candidates-job-skills li')].map(e => e.textContent)).toEqual(['Java', 'Spring']);
    expect([...host.querySelectorAll('.candidate-skills li')].map(e => e.textContent)).toEqual(['React', 'TypeScript']);
    expect(host.querySelector('.candidates-job-context .job-family-art')?.getAttribute('data-family')).toBe('backend');
    expect(host.querySelectorAll('.candidate-row .job-category-plate')).toHaveLength(0);
    expect(host.querySelector('.candidates-roster')?.textContent).not.toMatch(/%|match/i);
  });

  it.each([
    ['OPEN', 'state-open'], ['AWAITING_PAYMENT', 'state-awaiting-payment'],
    ['IN_PROGRESS', 'state-in-progress'], ['SUBMITTED_FOR_REVIEW', 'state-review'],
    ['REVISION_REQUESTED', 'state-revision'], ['COMPLETED', 'state-completed'], ['CANCELLED', 'state-cancelled'],
  ])('uses the real %s Job state for the editorial banner, separately from applicant status', async (status, tone) => {
    vi.mocked(api.profile).mockResolvedValue(publicProfile);
    await roster([pending], { ...job, status });
    expect(host.querySelector('.candidates-job-context')?.classList.contains(tone)).toBe(true);
    expect(host.querySelector('.candidate-row')?.classList.contains('application-pending')).toBe(true);
    expect(host.querySelectorAll('.candidate-reputation li')).toHaveLength(2);
    expect(host.querySelector('.candidate-identity-tile')?.textContent).toContain('MN');
    expect(host.textContent).not.toMatch(/phản hồi nhanh|năm kinh nghiệm|vài giờ|phù hợp nhất/i);
  });

  it('shows at most three neutral reputation facts from the server', async () => {
    vi.mocked(api.profile).mockResolvedValue(publicProfile);
    await roster();
    expect(host.textContent).toContain('12 hợp đồng hoàn thành');
    expect(host.textContent).toContain('4.8 / 5 · 9 đánh giá');
    expect(host.querySelectorAll('.candidate-reputation li').length).toBeLessThanOrEqual(3);
    expect(host.querySelector('.candidate-reputation')?.textContent).not.toMatch(/tranh chấp|rủi ro|danger|risk/i);
  });

  it('does not turn nullable ratings/rates or missing profile fields into invented evidence', async () => {
    vi.mocked(api.profile).mockResolvedValue({ ...publicProfile, headline: null, availability: null, countryCode: null, skills: [],
      reputation: { ...publicProfile.reputation, completedContracts: 0, reviewCount: 0, averageRating: null, onTimeRate: null } });
    await roster();
    expect(host.textContent).toContain('0 hợp đồng hoàn thành'); expect(host.textContent).toContain('0 đánh giá công bố');
    expect(host.textContent).not.toMatch(/0 \/ 5|0%|Senior|Chuyên gia|Top Freelancer/);
    expect(host.querySelector('.candidate-headline')).toBeNull(); expect(host.querySelector('.candidate-skills')).toBeNull();
    expect(host.querySelector('.candidate-profile-context')).toBeNull();
  });

  it.each(['PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'] as const)('retains %s rail/status with selection only for pending', async status => {
    await roster([{ ...pending, status }]);
    expect(host.querySelector('.candidate-row')?.classList.contains('application-' + status.toLowerCase())).toBe(true);
    expect(host.querySelector('.candidate-status')?.textContent).toContain({ PENDING: 'Đang chờ', ACCEPTED: 'Đã được chọn', REJECTED: 'Không được chọn', CANCELLED: 'Đã hủy' }[status]);
    expect(!!button('Chọn Freelancer')).toBe(status === 'PENDING');
  });

  it.each(['AWAITING_PAYMENT', 'IN_PROGRESS', 'SUBMITTED_FOR_REVIEW', 'REVISION_REQUESTED', 'COMPLETED', 'CANCELLED'])('makes owned %s roster read-only', async status => {
    await roster([pending], { ...job, status });
    expect(button('Chọn Freelancer')).toBeUndefined(); expect(button('Xác nhận chọn')).toBeUndefined();
    expect(host.textContent).toContain('Danh sách chỉ đọc');
    expect(host.querySelector('.candidates-read-only a')?.getAttribute('href')).toBe('/work/job-1');
  });

  it('opens a local confirmation with accurate consequences and no premature accepted state', async () => {
    const assign = vi.spyOn(api, 'assign');
    await roster(); await click('Chọn Freelancer');
    expect(assign).not.toHaveBeenCalled();
    expect(host.querySelector('.candidate-confirmation h4')?.textContent).toBe('Xác nhận lựa chọn');
    expect(host.querySelector('.candidate-confirmation')?.textContent).toContain('các hồ sơ đang chờ còn lại sẽ được đóng');
    expect(host.querySelector('.candidate-confirmation')?.textContent).toContain('chỉ bắt đầu sau khi milestone được funding');
    expect(host.querySelector('.candidate-row')?.classList.contains('application-pending')).toBe(true);
    expect(host.querySelector('.candidate-status')?.textContent).not.toContain('Đã được chọn');
    await click('Quay lại'); expect(host.querySelector('.candidate-confirmation')).toBeNull(); expect(assign).not.toHaveBeenCalled();
  });

  it('refreshes all server statuses after assignment without manufacturing acceptance/rejection', async () => {
    const second = { ...pending, id: 'second', freelancerId: 'freelancer-2' };
    vi.spyOn(api, 'job').mockResolvedValueOnce(job).mockResolvedValue({ ...job, status: 'AWAITING_PAYMENT' });
    vi.spyOn(api, 'applicants').mockResolvedValueOnce([pending, second])
      .mockResolvedValue([{ ...pending, status: 'ACCEPTED' }, { ...second, status: 'REJECTED' }]);
    const assign = vi.spyOn(api, 'assign').mockResolvedValue({ ...job, status: 'AWAITING_PAYMENT' });
    await render(<ClientApplicants user={client} />, '/work/job-1/applications');
    await click('Chọn Freelancer'); await click('Xác nhận chọn');
    expect(assign).toHaveBeenCalledExactlyOnceWith(job.id, freelancer.id);
    expect(api.job).toHaveBeenCalledTimes(2); expect(api.applicants).toHaveBeenCalledTimes(2);
    expect([...host.querySelectorAll('.candidate-status')].map(e => e.textContent)).toEqual(['Đã được chọn', 'Không được chọn']);
    expect(host.querySelector('.candidates-job-context')?.textContent).toContain('Chờ funding');
    expect(button('Chọn Freelancer')).toBeUndefined();
  });

  it('keeps mutation error and reconciles a job closed by another request', async () => {
    vi.spyOn(api, 'job').mockResolvedValueOnce(job).mockResolvedValue({ ...job, status: 'AWAITING_PAYMENT' });
    vi.spyOn(api, 'applicants').mockResolvedValueOnce([pending]).mockResolvedValue([{ ...pending, status: 'REJECTED' }]);
    vi.spyOn(api, 'assign').mockRejectedValue(new ApiError('Công việc không còn mở', 409, 4001));
    await render(<ClientApplicants user={client} />, '/work/job-1/applications');
    await click('Chọn Freelancer'); await click('Xác nhận chọn');
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Công việc không còn mở');
    expect(host.querySelector('.candidate-confirmation')).toBeNull(); expect(button('Chọn Freelancer')).toBeUndefined();
  });

  it('preserves global error/retry and truthful empty roster without fetching profiles', async () => {
    vi.spyOn(api, 'job').mockResolvedValue(job);
    vi.spyOn(api, 'applicants').mockRejectedValueOnce(new Error('Không thể đọc ứng tuyển')).mockResolvedValueOnce([]);
    await render(<ClientApplicants user={client} />, '/work/job-1/applications');
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Không thể đọc ứng tuyển');
    expect(api.profile).not.toHaveBeenCalled();
    await click('Thử lại'); expect(host.textContent).toContain('Chưa có ứng viên');
    expect(host.querySelector('.candidates-roster')).toBeNull(); expect(api.profile).not.toHaveBeenCalled();
  });

  it('keeps reduced-motion primitives static and exposes accessible confirmation controls', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
    await roster(); await click('Chọn Freelancer');
    expect(button('Chọn Freelancer')?.getAttribute('aria-expanded')).toBe('true');
    expect(host.querySelector('[role="group"][aria-label="Xác nhận chọn Freelancer"]')).not.toBeNull();
    expect([...host.querySelectorAll('.client-applicants-page [data-motion]')].every(e => e.getAttribute('data-motion') === 'off')).toBe(true);
  });
});
afterEach(() => {
  act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.unstubAllGlobals();
});
async function render(element: React.ReactNode, path = '/work/job-1', state?: unknown) {
  await act(async () => {
    root.render(<MemoryRouter initialEntries={[{ pathname: path, state }]}>
      <Routes><Route path="/work/:jobId" element={element} />
        <Route path="/work/:jobId/applications" element={element} />
        <Route path="/work/applications" element={element} />
        <Route path="/work" element={<p>Danh sách</p>} /></Routes>
    </MemoryRouter>);
  });
}
const button = (label: string) => [...host.querySelectorAll('button')].find(item => item.textContent?.includes(label));
async function click(label: string) {
  const target = button(label);
  expect(target, label).toBeTruthy();
  await act(async () => { target!.click(); });
}

describe('P05.2 job detail and workflow', () => {
  it('opens a discovered job using the real discovery contract, with Apply for OPEN/unapplied', async () => {
    vi.spyOn(api, 'findDiscoverJob').mockResolvedValue(discover);
    const participant = vi.spyOn(api, 'job');
    await render(<JobDetail user={freelancer} />, undefined, { job: discover });
    expect(host.textContent).toContain('Editorial brief');
    expect(host.textContent).toContain('Write an article.');
    expect(host.textContent).toContain('Client');
    expect(button('Ứng tuyển')?.disabled).toBe(false);
    expect(participant).not.toHaveBeenCalled();
  });

  it('submits Apply once and shows applied/PENDING after server refresh', async () => {
    vi.spyOn(api, 'findDiscoverJob').mockResolvedValueOnce(discover)
      .mockResolvedValueOnce({ ...discover, hasApplied: true, applicationId: pending.id, applicationStatus: 'PENDING' });
    const apply = vi.spyOn(api, 'apply').mockResolvedValue(pending);
    await render(<JobDetail user={freelancer} />, undefined, { job: discover });
    await click('Ứng tuyển');
    expect(apply).toHaveBeenCalledExactlyOnceWith(job.id);
    expect(host.textContent).toContain('Đang chờ');
    expect(button('Đã ứng tuyển')?.disabled).toBe(true);
    expect(host.querySelector('a[href="/work/applications"]')).not.toBeNull();
  });

  it('prevents repeated Apply while pending', async () => {
    vi.spyOn(api, 'findDiscoverJob').mockResolvedValue(discover);
    let complete!: (value: JobApplication) => void;
    const apply = vi.spyOn(api, 'apply').mockImplementation(() => new Promise(resolve => { complete = resolve; }));
    await render(<JobDetail user={freelancer} />, undefined, { job: discover });
    const target = button('Ứng tuyển')!;
    await act(async () => { target.click(); target.click(); });
    expect(apply).toHaveBeenCalledTimes(1);
    await act(async () => complete(pending));
  });

  it('reconciles the backend already-applied response without sending again', async () => {
    vi.spyOn(api, 'findDiscoverJob').mockResolvedValueOnce(discover)
      .mockResolvedValueOnce({ ...discover, hasApplied: true, applicationId: pending.id, applicationStatus: 'PENDING' });
    const apply = vi.spyOn(api, 'apply').mockRejectedValue(new ApiError('Bạn đã ứng tuyển', 409, 4008));
    await render(<JobDetail user={freelancer} />, undefined, { job: discover });
    await click('Ứng tuyển');
    expect(apply).toHaveBeenCalledTimes(1);
    expect(button('Đã ứng tuyển')?.disabled).toBe(true);
  });

  it('shows backend apply error and offers reload for a job no longer OPEN', async () => {
    vi.spyOn(api, 'findDiscoverJob').mockResolvedValueOnce(discover).mockResolvedValueOnce(null);
    vi.spyOn(api, 'job').mockRejectedValue(new ApiError('Không thể mở công việc', 403));
    vi.spyOn(api, 'apply').mockRejectedValue(new ApiError('Trạng thái công việc không cho phép', 409, 4001));
    await render(<JobDetail user={freelancer} />, undefined, { job: discover });
    await click('Ứng tuyển');
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Trạng thái công việc không cho phép');
  });

  it('shows Client applicant action only on owned OPEN jobs and never exposes Apply', async () => {
    vi.spyOn(api, 'job').mockResolvedValue(job);
    await render(<JobDetail user={client} />);
    expect(host.querySelector('a[href="/work/job-1/applications"]')).not.toBeNull();
    expect(button('Ứng tuyển')).toBeUndefined();
  });

  it('does not show applicant action when Client does not own job', async () => {
    vi.spyOn(api, 'job').mockResolvedValue({ ...job, clientUserId: 'other-client' });
    await render(<JobDetail user={client} />);
    expect(host.querySelector('a[href="/work/job-1/applications"]')).toBeNull();
  });

  it('does not show applicant action once the job is IN_PROGRESS', async () => {
    vi.spyOn(api, 'job').mockResolvedValue({ ...job, status: 'IN_PROGRESS', freelancerId: freelancer.id });
    await render(<JobDetail user={client} />);
    expect(host.textContent).toContain('Đang thực hiện');
    expect(host.querySelector('a[href="/work/job-1/applications"]')).toBeNull();
  });

  it('loads paginated my applications and filters by backend status', async () => {
    const page = { currentPage: 0, pageSize: 10, totalPages: 1, totalElements: 1, data: [myApplication] };
    const list = vi.spyOn(api, 'myApplications').mockResolvedValue(page);
    await render(<MyApplications />, '/work/applications');
    expect(host.textContent).toContain('Editorial brief');
    expect(host.textContent).toContain('Nộp 2026-09-29');
    expect(list).toHaveBeenCalledWith(0, 'ALL');
    await click('Đã được chọn');
    expect(list).toHaveBeenLastCalledWith(0, 'ACCEPTED');
  });

  it('shows recoverable application loading error', async () => {
    vi.spyOn(api, 'myApplications').mockRejectedValue(new Error('Dịch vụ bận'));
    await render(<MyApplications />, '/work/applications');
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Dịch vụ bận');
    expect(button('Thử lại')).toBeTruthy();
  });

  it('renders all four server statuses without invented states or per-status page counts', async () => {
    const statuses = ['PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'] as const;
    vi.spyOn(api, 'myApplications').mockResolvedValue({ currentPage: 0, pageSize: 10, totalPages: 10,
      totalElements: 97, data: statuses.map((status, index) => ({ ...myApplication, id: 'app-' + index,
        status, job: { ...myApplication.job, id: 'job-' + index, title: 'Job ' + index } })) });
    await render(<MyApplications />, '/work/applications');
    expect([...host.querySelectorAll('.applications-status')].map(node => node.textContent))
      .toEqual(['Đang chờ', 'Đã được chọn', 'Không được chọn', 'Đã hủy']);
    expect(host.querySelector('.applications-results-heading p')?.textContent).toBe('97 ứng tuyển');
    expect([...host.querySelectorAll('.applications-status-rail button')].map(node => node.textContent))
      .toEqual(['Tất cả', 'Đang chờ', 'Đã được chọn', 'Không được chọn', 'Đã hủy']);
    expect(host.textContent).not.toMatch(/Cần phản hồi|Client đã xem|phỏng vấn|Featured|Recommended|Priority/);
    expect(host.querySelectorAll('input, select')).toHaveLength(0);
  });

  it('keeps the active filter in the rail without repeating it in the result heading', async () => {
    vi.spyOn(api, 'myApplications').mockResolvedValue({ currentPage: 0, pageSize: 10,
      totalPages: 1, totalElements: 1, data: [myApplication] });
    await render(<MyApplications />, '/work/applications');
    const heading = host.querySelector('.applications-results-heading')!;
    expect(heading.textContent).toBe('Ứng tuyển gần đây1 ứng tuyển');
    expect(heading.querySelector('.ku-label')).toBeNull();
    expect(button('Tất cả')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('groups the first application budget with its status and action, preserving lower-row facts', async () => {
    vi.spyOn(api, 'myApplications').mockResolvedValue({ currentPage: 0, pageSize: 10,
      totalPages: 1, totalElements: 2, data: [myApplication, { ...myApplication, id: 'app-2' }] });
    await render(<MyApplications />, '/work/applications');
    const primary = host.querySelector('.applications-primary-record')!;
    const action = primary.querySelector('.applications-record-action')!;
    expect(action.querySelector('.applications-status')?.textContent).toBe('Đang chờ');
    expect(action.querySelector('.applications-budget')?.textContent).toContain('$120.00');
    expect(action.querySelector('a')?.getAttribute('href')).toBe('/work/job-1');
    expect(primary.querySelector('.applications-record-copy .applications-budget')).toBeNull();
    expect(host.querySelector('.applications-ledger-row .applications-facts .applications-budget')?.textContent)
      .toContain('$120.00');
    expect(host.querySelectorAll('.applications-budget')).toHaveLength(2);
  });

  it.each(['PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'] as const)('filters %s through the unchanged server call', async status => {
    const load = vi.spyOn(api, 'myApplications').mockResolvedValue({ currentPage: 0, pageSize: 10,
      totalPages: 0, totalElements: 0, data: [] });
    await render(<MyApplications />, '/work/applications');
    const label = { PENDING: 'Đang chờ', ACCEPTED: 'Đã được chọn', REJECTED: 'Không được chọn', CANCELLED: 'Đã hủy' }[status];
    await click(label);
    expect(load).toHaveBeenLastCalledWith(0, status);
    expect(button(label)?.getAttribute('aria-pressed')).toBe('true');
    expect(host.querySelector('.applications-results-heading p')?.textContent).toBe('0 ứng tuyển trong bộ lọc này');
  });

  it('reuses category-first semantic thumbnails and renders only actual job skills', async () => {
    vi.spyOn(api, 'myApplications').mockResolvedValue({ currentPage: 0, pageSize: 10, totalPages: 1,
      totalElements: 2, data: [
        { ...myApplication, job: { ...myApplication.job, category: 'WEB_FRONTEND', skills: ['HTML', 'CSS'] } },
        { ...myApplication, id: 'app-2', job: { ...myApplication.job, title: 'React landing page', category: 'OTHER', skills: [] } },
      ] });
    await render(<MyApplications />, '/work/applications');
    expect([...host.querySelectorAll('.job-family-art')].map(node => node.getAttribute('data-family')))
      .toEqual(['web', 'web']);
    expect([...host.querySelectorAll('.applications-skills li')].map(node => node.textContent)).toEqual(['HTML', 'CSS']);
    expect(host.querySelectorAll('.applications-skills')).toHaveLength(1);
    expect(host.querySelectorAll('.applications-ledger > li')).toHaveLength(2);
  });

  it.each([
    ['ACCEPTED', 'IN_PROGRESS', true], ['PENDING', 'OPEN', true], ['REJECTED', 'OPEN', true],
    ['PENDING', 'COMPLETED', false], ['REJECTED', 'IN_PROGRESS', false], ['CANCELLED', 'CANCELLED', false],
  ] as const)('preserves existing access for %s application / %s job', async (status, jobStatus, accessible) => {
    vi.spyOn(api, 'myApplications').mockResolvedValue({ currentPage: 0, pageSize: 10, totalPages: 1,
      totalElements: 1, data: [{ ...myApplication, status, job: { ...myApplication.job, status: jobStatus } }] });
    await render(<MyApplications />, '/work/applications');
    expect(!!host.querySelector('a[href="/work/job-1"]')).toBe(accessible);
    expect(host.querySelectorAll('a[href="/work/job-1"]')).toHaveLength(accessible ? 1 : 0);
    expect(host.textContent).not.toMatch(/Rút ứng tuyển|Sửa ứng tuyển|Ứng tuyển lại|Nhắn tin/);
    if (status === 'REJECTED' || status === 'CANCELLED') {
      expect(host.querySelector('.applications-primary-record--attention')).toBeNull();
    }
  });

  it('uses only actual submitted/updated timestamps and omits unchanged updates', async () => {
    vi.spyOn(api, 'myApplications').mockResolvedValue({ currentPage: 0, pageSize: 10, totalPages: 1,
      totalElements: 2, data: [myApplication, { ...myApplication, id: 'app-2',
        updatedAt: '2026-10-06T08:15:00' }] });
    await render(<MyApplications />, '/work/applications');
    expect(host.textContent).toContain('Nộp 2026-09-29 11:00');
    expect(host.textContent).toContain('Cập nhật 2026-10-06 08:15');
    expect(host.querySelector('.applications-primary')?.textContent).not.toContain('Cập nhật');
  });

  it('preserves server pagination and resets it when the status changes', async () => {
    const load = vi.spyOn(api, 'myApplications').mockResolvedValue({ currentPage: 0, pageSize: 10,
      totalPages: 2, totalElements: 11, data: [myApplication] });
    await render(<MyApplications />, '/work/applications');
    await click('Trang sau');
    expect(load).toHaveBeenLastCalledWith(1, 'ALL');
    await click('Đang chờ');
    expect(load).toHaveBeenLastCalledWith(0, 'PENDING');
  });

  it('keeps the tracker shell during loading and recovers from an API error', async () => {
    let reject!: (cause: Error) => void;
    const load = vi.spyOn(api, 'myApplications').mockImplementationOnce(() => new Promise((_resolve, failure) => { reject = failure; }))
      .mockResolvedValueOnce({ currentPage: 0, pageSize: 10, totalPages: 1, totalElements: 1, data: [myApplication] });
    await render(<MyApplications />, '/work/applications');
    expect(host.querySelector('[role="status"]')?.textContent).toContain('Đang tải ứng tuyển');
    expect(host.querySelector('.applications-status-rail')).not.toBeNull();
    expect(host.querySelector('.applications-results')?.getAttribute('aria-busy')).toBe('true');
    await act(async () => reject(new Error('Máy chủ tạm thời chưa phản hồi')));
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Máy chủ tạm thời chưa phản hồi');
    await click('Thử lại');
    expect(load).toHaveBeenCalledTimes(2);
    expect(host.querySelector('.applications-primary')?.textContent).toContain('Editorial brief');
  });

  it('renders the truthful empty state and real Explore destination without recommendations', async () => {
    vi.spyOn(api, 'myApplications').mockResolvedValue({ currentPage: 0, pageSize: 10, totalPages: 0,
      totalElements: 0, data: [] });
    await render(<MyApplications />, '/work/applications');
    expect(host.textContent).toContain('Chưa có ứng tuyển trong bộ lọc này');
    expect(host.querySelector('a[href="/work"]')?.textContent).toContain('Khám phá công việc');
    expect(host.querySelector('.applications-ledger')).toBeNull();
  });

  it('inherits reduced-motion handling for the tracker primitives', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
    vi.spyOn(api, 'myApplications').mockResolvedValue({ currentPage: 0, pageSize: 10, totalPages: 1,
      totalElements: 2, data: [myApplication, { ...myApplication, id: 'app-2' }] });
    await render(<MyApplications />, '/work/applications');
    expect(host.querySelector('.applications-primary-record')?.getAttribute('data-motion')).toBe('off');
    expect(host.querySelector('.applications-ledger-row')?.getAttribute('data-motion')).toBe('off');
    expect([...host.querySelectorAll('.applications-page [data-motion]')].every(node => node.getAttribute('data-motion') === 'off')).toBe(true);
  });

  it('renders real applicant UUID and confirmation without fake profile data', async () => {
    vi.spyOn(api, 'job').mockResolvedValue(job);
    vi.spyOn(api, 'applicants').mockResolvedValue([pending]);
    await render(<ClientApplicants user={client} />, '/work/job-1/applications');
    expect(host.textContent).toContain(freelancer.id);
    expect(host.textContent).not.toMatch(/rating|avatar|kỹ năng/i);
    expect(button('Chọn Freelancer')).toBeTruthy();
    await click('Chọn Freelancer');
    expect(host.textContent).toContain('chỉ bắt đầu sau khi milestone được funding');
  });

  it('assigns the real applicant once and renders server-refreshed AWAITING_PAYMENT/ACCEPTED', async () => {
    vi.spyOn(api, 'job').mockResolvedValueOnce(job)
      .mockResolvedValueOnce({ ...job, status: 'AWAITING_PAYMENT', freelancerId: freelancer.id });
    vi.spyOn(api, 'applicants').mockResolvedValueOnce([pending])
      .mockResolvedValueOnce([{ ...pending, status: 'ACCEPTED' }]);
    const assign = vi.spyOn(api, 'assign').mockResolvedValue({ ...job, status: 'AWAITING_PAYMENT', freelancerId: freelancer.id });
    await render(<ClientApplicants user={client} />, '/work/job-1/applications');
    await click('Chọn Freelancer');
    await click('Xác nhận chọn');
    expect(assign).toHaveBeenCalledExactlyOnceWith(job.id, freelancer.id);
    expect(host.textContent).toContain('Chờ funding');
    expect(host.textContent).toContain('Đã được chọn');
    expect(button('Chọn Freelancer')).toBeUndefined();
  });

  it('prevents duplicate assignment taps while pending', async () => {
    vi.spyOn(api, 'job').mockResolvedValue(job);
    vi.spyOn(api, 'applicants').mockResolvedValue([pending]);
    let complete!: (value: Job) => void;
    const assign = vi.spyOn(api, 'assign').mockImplementation(() => new Promise(resolve => { complete = resolve; }));
    await render(<ClientApplicants user={client} />, '/work/job-1/applications');
    await click('Chọn Freelancer');
    const target = button('Xác nhận chọn')!;
    await act(async () => { target.click(); target.click(); });
    expect(assign).toHaveBeenCalledTimes(1);
    await act(async () => complete(job));
  });

  it('renders actionable backend assignment error', async () => {
    vi.spyOn(api, 'job').mockResolvedValue(job);
    vi.spyOn(api, 'applicants').mockResolvedValue([pending]);
    vi.spyOn(api, 'assign').mockRejectedValue(new ApiError('Công việc không còn mở', 409, 4001));
    await render(<ClientApplicants user={client} />, '/work/job-1/applications');
    await click('Chọn Freelancer');
    await click('Xác nhận chọn');
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Công việc không còn mở');
    expect(button('Tải lại')).toBeTruthy();
  });
  it('does not expose Apply for an assigned Freelancer participant', async () => {
    vi.spyOn(api, 'job').mockResolvedValue({ ...job, status: 'IN_PROGRESS', freelancerId: freelancer.id });
    await render(<JobDetail user={freelancer} />);
    expect(host.textContent).toContain('Đang thực hiện');
    expect(button('Ứng tuyển')).toBeUndefined();
  });

  it('rejects a non-Client applicant route before requesting backend data', async () => {
    const load = vi.spyOn(api, 'job');
    await render(<ClientApplicants user={freelancer} />, '/work/job-1/applications');
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Chỉ Client');
    expect(load).not.toHaveBeenCalled();
  });

});
