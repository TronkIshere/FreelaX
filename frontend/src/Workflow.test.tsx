// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './api';
import { ClientApplicants, JobDetail, MyApplications } from './Workflow';
import type { DiscoverJob, Job, JobApplication, MyApplication, User } from './types';

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

  it('renders real applicant UUID and confirmation without fake profile data', async () => {
    vi.spyOn(api, 'job').mockResolvedValue(job);
    vi.spyOn(api, 'applicants').mockResolvedValue([pending]);
    await render(<ClientApplicants user={client} />, '/work/job-1/applications');
    expect(host.textContent).toContain(freelancer.id);
    expect(host.textContent).not.toMatch(/rating|avatar|kỹ năng/i);
    expect(button('Chọn Freelancer')).toBeTruthy();
    await click('Chọn Freelancer');
    expect(host.textContent).toContain('Việc thực hiện bắt đầu');
    expect(host.textContent).toContain('ứng tuyển đang chờ khác');
  });

  it('assigns the real applicant once and renders server-refreshed IN_PROGRESS/ACCEPTED', async () => {
    vi.spyOn(api, 'job').mockResolvedValueOnce(job)
      .mockResolvedValueOnce({ ...job, status: 'IN_PROGRESS', freelancerId: freelancer.id });
    vi.spyOn(api, 'applicants').mockResolvedValueOnce([pending])
      .mockResolvedValueOnce([{ ...pending, status: 'ACCEPTED' }]);
    const assign = vi.spyOn(api, 'assign').mockResolvedValue({ ...job, status: 'IN_PROGRESS', freelancerId: freelancer.id });
    await render(<ClientApplicants user={client} />, '/work/job-1/applications');
    await click('Chọn Freelancer');
    await click('Xác nhận chọn');
    expect(assign).toHaveBeenCalledExactlyOnceWith(job.id, freelancer.id);
    expect(host.textContent).toContain('Đang thực hiện');
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
