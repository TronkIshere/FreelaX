// @vitest-environment jsdom
import { act, useState } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './api';
import { MyWork, WorkLifecycle, safeDeliverableUrl } from './WorkLifecycle';
import type { Job, JobSubmission, User } from './types';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const client: User = { id: 'client-1', email: 'client@example.test', displayName: 'Client', userType: 'CLIENT' };
const freelancer: User = { id: 'freelancer-1', email: 'freelancer@example.test', displayName: 'Freelancer', userType: 'FREELANCER' };
const working: Job = { id: 'job-1', title: 'Editorial work', description: 'Produce the agreed work.',
  budgetUsd: 300, clientUserId: client.id, freelancerId: freelancer.id, status: 'IN_PROGRESS',
  createdAt: '2026-09-29T10:00:00', updatedAt: '2026-09-29T11:00:00' };
const review: Job = { ...working, status: 'SUBMITTED_FOR_REVIEW' };
const revision: Job = { ...working, status: 'REVISION_REQUESTED' };
const completed: Job = { ...working, status: 'COMPLETED' };
const v1: JobSubmission = { id: 'submission-1', jobId: working.id, freelancerId: freelancer.id,
  version: 1, summary: 'Delivered first draft', deliverableUrl: 'https://example.test/v1',
  status: 'SUBMITTED', reviewerFeedback: null, reviewedAt: null,
  createdAt: '2026-09-30T01:00:00', updatedAt: '2026-09-30T01:00:00' };
const v1Revised: JobSubmission = { ...v1, status: 'REVISION_REQUESTED',
  reviewerFeedback: 'Add the missing edge case', reviewedAt: '2026-09-30T02:00:00',
  updatedAt: '2026-09-30T02:00:00' };
const v2: JobSubmission = { ...v1, id: 'submission-2', version: 2,
  summary: 'Delivered revision with edge case', deliverableUrl: 'https://example.test/v2',
  createdAt: '2026-09-30T03:00:00', updatedAt: '2026-09-30T03:00:00' };
const v2Approved: JobSubmission = { ...v2, status: 'APPROVED',
  reviewedAt: '2026-09-30T04:00:00', updatedAt: '2026-09-30T04:00:00' };

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
  vi.unstubAllGlobals();
});

function Harness({ initial, user }: { initial: Job; user: User }) {
  const [job, setJob] = useState(initial);
  return <WorkLifecycle job={job} user={user} onJobUpdated={setJob} />;
}
async function render(element: React.ReactNode) {
  await act(async () => { root.render(<MemoryRouter>{element}</MemoryRouter>); });
}
function button(label: string) {
  return [...host.querySelectorAll('button')].find(item => item.textContent?.trim() === label);
}
async function click(label: string) {
  const target = button(label);
  expect(target, label).toBeTruthy();
  await act(async () => { target!.click(); });
}
async function input(selector: string, value: string) {
  const target = host.querySelector(selector) as HTMLInputElement | HTMLTextAreaElement;
  expect(target, selector).toBeTruthy();
  const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(target), 'value')?.set;
  await act(async () => {
    setter!.call(target, value);
    target.dispatchEvent(new Event('input', { bubbles: true }));
  });
}
async function submit(selector: string) {
  const form = host.querySelector(selector) as HTMLFormElement;
  expect(form, selector).toBeTruthy();
  await act(async () => { form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
}

describe('P05.3 work lifecycle', () => {
  it('shows assigned Freelancer workspace and Client waiting state from participant identity', async () => {
    vi.spyOn(api, 'submissions').mockResolvedValue([]);
    await render(<Harness initial={working} user={freelancer} />);
    expect(host.textContent).toContain('Đến lượt bạn bàn giao công việc');
    expect(host.querySelector('.work-composer form')).not.toBeNull();
    expect(host.querySelector('.work-composer h2')?.textContent).toBe('Gửi bàn giao');
    expect(button('Gửi bàn giao')).toBeTruthy();
    expect(host.querySelector('[aria-current="step"]')?.textContent).toBe('Working');
    await render(<Harness initial={working} user={client} />);
    expect(host.textContent).toContain('Đang chờ Freelancer bàn giao');
    expect(button('Gửi bàn giao')).toBeUndefined();
  });

  it('does not expose work actions or fetch submissions to a nonparticipant', async () => {
    const submissions = vi.spyOn(api, 'submissions');
    await render(<WorkLifecycle job={working} user={{ ...client, id: 'other-client' }} onJobUpdated={vi.fn()} />);
    expect(host.textContent).toBe('');
    expect(submissions).not.toHaveBeenCalled();
  });

  it('submits V1 once and renders the refreshed review state from server responses', async () => {
    vi.spyOn(api, 'submissions').mockResolvedValueOnce([]).mockResolvedValueOnce([v1]);
    vi.spyOn(api, 'job').mockResolvedValue(review);
    let finish!: (value: JobSubmission) => void;
    const send = vi.spyOn(api, 'submitWork').mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    await render(<Harness initial={working} user={freelancer} />);
    await input('.work-composer textarea', '  Delivered first draft  ');
    await input('.work-composer input', 'https://example.test/v1');
    const target = button('Gửi bàn giao')!;
    await act(async () => { target.click(); target.click(); });
    expect(send).toHaveBeenCalledExactlyOnceWith(working.id, 'Delivered first draft', 'https://example.test/v1');
    await act(async () => { finish(v1); });
    expect(host.textContent).toContain('Đang chờ Client phản hồi');
    expect(host.textContent).toContain('Bản bàn giao #1');
    expect(button('Gửi bàn giao')).toBeUndefined();
    expect(host.querySelector('[aria-current="step"]')?.textContent).toBe('Review');
  });

  it('shows V1 to Client, requires feedback, and preserves server feedback after revision', async () => {
    vi.spyOn(api, 'submissions').mockResolvedValueOnce([v1]).mockResolvedValueOnce([v1Revised]);
    vi.spyOn(api, 'job').mockResolvedValue(revision);
    const request = vi.spyOn(api, 'requestRevision').mockResolvedValue(v1Revised);
    await render(<Harness initial={review} user={client} />);
    expect(host.textContent).toContain('Delivered first draft');
    expect(button('Duyệt bàn giao')).toBeTruthy();
    await click('Yêu cầu chỉnh sửa');
    await submit('.decision-form');
    expect(host.textContent).toContain('Phản hồi chỉnh sửa là bắt buộc');
    expect(request).not.toHaveBeenCalled();
    await input('.decision-form textarea', ' Add the missing edge case ');
    await submit('.decision-form');
    expect(request).toHaveBeenCalledExactlyOnceWith(working.id, 'Add the missing edge case');
    expect(host.textContent).toContain('Đang chờ Freelancer gửi bản sửa');
    expect(host.textContent).toContain('Add the missing edge case');
    expect(host.querySelector('[aria-current="step"]')?.textContent).toBe('Revision');
  });

  it('shows Client feedback, submits V2, and retains V1 in the history ledger', async () => {
    vi.spyOn(api, 'submissions').mockResolvedValueOnce([v1Revised]).mockResolvedValueOnce([v1Revised, v2]);
    vi.spyOn(api, 'job').mockResolvedValue(review);
    const send = vi.spyOn(api, 'submitWork').mockResolvedValue(v2);
    await render(<Harness initial={revision} user={freelancer} />);
    expect(host.textContent).toContain('Bạn cần chỉnh sửa theo phản hồi');
    expect(host.querySelector('.feedback-document')?.textContent).toContain('Add the missing edge case');
    expect(host.querySelector('.latest-submission')?.textContent).toContain('Delivered first draft');
    await input('.work-composer textarea', 'Delivered revision with edge case');
    await input('.work-composer input', 'https://example.test/v2');
    await submit('.work-composer form');
    expect(send).toHaveBeenCalledExactlyOnceWith(working.id, v2.summary, v2.deliverableUrl);
    expect(host.querySelector('.latest-submission')?.textContent).toContain('Bản bàn giao #2');
    expect(host.querySelector('.latest-submission')?.textContent).toContain(v2.summary);
    expect(host.querySelectorAll('.ledger-row')).toHaveLength(1);
    expect(host.querySelectorAll('.ledger-row')[0].textContent).toContain(v1.summary);
    expect(host.querySelector('[aria-current="step"]')?.textContent).toBe('Resubmitted');
  });

  it('confirms approval once, renders server approved state, and removes work mutation actions', async () => {
    vi.spyOn(api, 'submissions').mockResolvedValueOnce([v1Revised, v2])
      .mockResolvedValueOnce([v1Revised, v2Approved]);
    vi.spyOn(api, 'job').mockResolvedValue(completed);
    let finish!: (value: Job) => void;
    const approve = vi.spyOn(api, 'approveWork').mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    await render(<Harness initial={review} user={client} />);
    expect(host.querySelector('.latest-submission')?.textContent).toContain('Bản bàn giao #2');
    expect(host.querySelectorAll('.ledger-row')).toHaveLength(1);
    await click('Duyệt bàn giao');
    expect(host.textContent).toContain('kích hoạt xử lý thanh toán/chi trả');
    const target = button('Xác nhận duyệt')!;
    await act(async () => { target.click(); target.click(); });
    expect(approve).toHaveBeenCalledTimes(1);
    await act(async () => { finish(completed); });
    expect(host.textContent).toContain('Quy trình công việc đã hoàn thành');
    expect(host.querySelector('.latest-submission')?.textContent).toContain('Đã duyệt');
    expect(host.querySelectorAll('.ledger-row')).toHaveLength(1);
    expect(button('Duyệt bàn giao')).toBeUndefined();
    expect(button('Gửi bàn giao')).toBeUndefined();
    expect(host.querySelector('a[href="/finance?jobId=job-1"]')?.textContent).toContain('Xem bằng chứng thanh toán');
  });

  it('keeps revision input and workflow record visible after a recoverable API error', async () => {
    vi.spyOn(api, 'submissions').mockResolvedValue([v1]);
    vi.spyOn(api, 'job').mockResolvedValue(review);
    vi.spyOn(api, 'requestRevision').mockRejectedValue(new ApiError('Tạm thời không xử lý được', 503));
    await render(<Harness initial={review} user={client} />);
    await click('Yêu cầu chỉnh sửa');
    await input('.decision-form textarea', 'Please preserve this feedback');
    await submit('.decision-form');
    expect(host.querySelector('.decision-form textarea')).toHaveProperty('value', 'Please preserve this feedback');
    expect(host.textContent).toContain('Tạm thời không xử lý được');
    expect(host.textContent).toContain('Bản bàn giao #1');
    expect(button('Gửi yêu cầu chỉnh sửa')?.disabled).toBe(false);
  });

  it('shows a retryable history error without exposing Client review actions', async () => {
    vi.spyOn(api, 'submissions').mockRejectedValue(new ApiError('Không tải được lịch sử', 503));
    await render(<Harness initial={review} user={client} />);
    expect(host.textContent).toContain('Cần bạn duyệt bàn giao');
    expect(host.textContent).toContain('Không tải được lịch sử');
    expect(button('Duyệt bàn giao')).toBeUndefined();
    expect(button('Tải lại lịch sử')).toBeTruthy();
  });

  it('accepts only safe HTTP/HTTPS deliverable links', () => {
    expect(safeDeliverableUrl('https://example.test/v2')?.hostname).toBe('example.test');
    expect(safeDeliverableUrl('javascript:alert(1)')).toBeNull();
    expect(safeDeliverableUrl('https://user:pass@example.test/file')).toBeNull();
    expect(safeDeliverableUrl('file:///private/path')).toBeNull();
  });

  it('loads real participant jobs for Freelancer My Work', async () => {
    vi.spyOn(api, 'myJobs').mockResolvedValue({
      currentPage: 0, pageSize: 10, totalPages: 1, totalElements: 1, data: [working],
    });
    await render(<MyWork />);
    expect(host.textContent).toContain('Editorial work');
    expect(host.textContent).toContain('Đang thực hiện');
    expect(host.querySelector('a[href="/work/job-1"]')).not.toBeNull();
  });
});


