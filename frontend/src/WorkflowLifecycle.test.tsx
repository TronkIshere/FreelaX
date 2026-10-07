// @vitest-environment jsdom
import { act, useState } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './api';
import { MyWork, WorkLifecycle, safeDeliverableUrl } from './WorkLifecycle';
import type { ContractSummary, Job, JobSubmission, User } from './types';

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

const workContract: ContractSummary = { id: 'contract-1', status: 'ACTIVE', milestoneId: 'milestone-1',
  milestoneStatus: 'IN_PROGRESS', amount: '300.00', currency: 'USD', deliveryDueAt: '2026-10-15T12:00:00Z',
  reviewWindowHours: 48, maxRevisions: 2, revisionsUsed: 1, deliverables: [], acceptanceCriteria: [] };

describe('VP.5 assigned work tracker', () => {
  function jobs(data: Job[], totalElements = data.length) {
    return vi.spyOn(api, 'myJobs').mockResolvedValue({ currentPage: 0, pageSize: 10,
      totalPages: Math.ceil(totalElements / 10), totalElements, data });
  }

  it('uses the real server total, preserving record order and pagination rather than page-derived counts', async () => {
    const load = jobs([completed, { ...revision, id: 'job-2', title: 'Second server record' }], 23);
    await render(<MyWork />);
    expect(load).toHaveBeenCalledExactlyOnceWith(0);
    expect(host.querySelector('.my-work-results-heading p')?.textContent).toBe('23 công việc được giao');
    expect([...host.querySelectorAll('.my-work-ledger h3')].map(node => node.textContent))
      .toEqual(['Editorial work', 'Second server record']);
    expect(host.querySelector('.my-work-primary-record')?.classList.contains('ku-surface--mint')).toBe(true);
    await click('Trang sau');
    expect(load).toHaveBeenLastCalledWith(1);
    expect(load).toHaveBeenCalledTimes(2);
    expect(host.querySelectorAll('input, select')).toHaveLength(0);
    expect(host.textContent).not.toMatch(/đang làm\s*\d|ưu tiên|khẩn cấp|Recommended|PROGRESS_PERCENT/i);
  });

  it('reuses category-first thumbnails and actual skills without title-derived tags', async () => {
    jobs([{ ...working, category: 'BACKEND_API', skills: ['Java', 'REST API'] },
      { ...working, id: 'job-2', title: 'React landing page', category: 'OTHER', skills: [] }]);
    await render(<MyWork />);
    expect([...host.querySelectorAll('.job-family-art')].map(node => node.getAttribute('data-family')))
      .toEqual(['backend', 'web']);
    expect([...host.querySelectorAll('.my-work-skills li')].map(node => node.textContent)).toEqual(['Java', 'REST API']);
    expect(host.querySelector('.my-work-ledger-row .my-work-skills')).toBeNull();
  });

  it.each([
    ['AWAITING_PAYMENT', 'cream', 'Xem trạng thái', 'Đang chờ Client hoàn tất funding'],
    ['IN_PROGRESS', 'cobalt', 'Tiếp tục công việc', 'Bạn có thể tiếp tục chuẩn bị'],
    ['SUBMITTED_FOR_REVIEW', 'acid', 'Xem bàn giao', 'Bàn giao đang chờ Client phản hồi'],
    ['REVISION_REQUESTED', 'vermilion', 'Xem phản hồi', 'Client đã yêu cầu chỉnh sửa'],
    ['COMPLETED', 'mint', 'Xem hồ sơ', 'Phần công việc đã hoàn tất'],
    ['CANCELLED', 'cream', 'Xem hồ sơ', 'Công việc đã được hủy'],
  ])('presents %s with its real next destination and %s treatment', async (status, surface, action, copy) => {
    jobs([{ ...working, status }]);
    await render(<MyWork />);
    const card = host.querySelector('.my-work-primary-record')!;
    expect(card.classList.contains('ku-surface--' + surface)).toBe(true);
    expect(card.querySelector('a')?.textContent?.trim()).toBe(action);
    expect(card.querySelector('a')?.getAttribute('href')).toBe('/work/job-1');
    expect(card.textContent).toContain(copy);
    expect(card.textContent).not.toMatch(/Đã thanh toán|Tiền đã về|số dư|trễ \d|còn \d|Fund milestone|Thanh toán ngay/);
    expect(card.querySelectorAll('a')).toHaveLength(1);
    expect(card.querySelectorAll('button, form')).toHaveLength(0);
  });

  it('renders real contract deadline and revision usage, omitting absent values', async () => {
    jobs([{ ...working, deliveryDueAt: '2026-10-10T12:00:00Z', contract: workContract },
      { ...working, id: 'job-2', maxRevisions: 2 }]);
    await render(<MyWork />);
    expect(host.querySelector('time')?.getAttribute('dateTime')).toBe(workContract.deliveryDueAt);
    expect(host.querySelector('time')?.textContent).toBe('2026-10-15');
    expect(host.querySelector('.my-work-facts')?.textContent).toContain('Chỉnh sửa 1 / 2');
    expect(host.querySelector('.my-work-ledger-row .my-work-facts')?.textContent).toBe('');
    expect(host.textContent).not.toMatch(/còn \d|trễ \d|reviewWindowHours|contract-1|milestone-1/);
  });

  it('shows a real legacy job deadline without inventing revision usage', async () => {
    jobs([{ ...working, deliveryDueAt: '2026-10-20T12:00:00Z', maxRevisions: 2 }]);
    await render(<MyWork />);
    expect(host.querySelector('time')?.textContent).toBe('2026-10-20');
    expect(host.textContent).not.toContain('Chỉnh sửa');
  });

  it.each([
    ['RELEASE_PENDING', 'UNDER_REVIEW', 'Đã duyệt · đang xử lý tiền', 'Phần bàn giao đã được duyệt; xử lý tiền chưa hoàn tất.'],
    ['REFUND_PENDING', 'ACTIVE', 'Đang đối soát hoàn tiền', 'Hoàn tiền đang được xử lý, chưa được xác nhận hoàn tất.'],
    ['DISPUTED', 'DISPUTED', 'Hợp đồng đang tranh chấp', 'Mở công việc để xem hồ sơ tranh chấp và trạng thái xử lý.'],
    ['REFUNDED', 'CANCELLED', 'Hợp đồng đã hủy', 'Mở hồ sơ để xem bằng chứng và trạng thái tài chính riêng.'],
  ])('keeps %s contract truth distinct from stale job state', async (milestoneStatus, status, context, copy) => {
    jobs([{ ...working, contract: { ...workContract, status, milestoneStatus } }]);
    await render(<MyWork />);
    expect(host.textContent).toContain(context);
    expect(host.textContent).toContain(copy);
    expect(host.textContent).not.toContain('Bạn có thể tiếp tục chuẩn bị');
    expect(host.textContent).not.toMatch(/Đã thanh toán|Đã hoàn tiền|Tiền đã về/);
    expect(host.querySelector('a')?.textContent).not.toContain('Tiếp tục công việc');
  });

  it('honors disputed contract status even when milestone remains IN_PROGRESS', async () => {
    jobs([{ ...working, contract: { ...workContract, status: 'DISPUTED' } }]);
    await render(<MyWork />);
    expect(host.textContent).toContain('Hợp đồng đang tranh chấp');
    expect(host.querySelector('a')?.textContent?.trim()).toBe('Xem tranh chấp');
  });

  it('keeps completed budget labelled as job value without claiming settlement', async () => {
    jobs([{ ...completed, contract: { ...workContract, status: 'COMPLETED', milestoneStatus: 'RELEASED' } }]);
    await render(<MyWork />);
    expect(host.querySelector('.my-work-budget')?.textContent).toBe('Ngân sách $300.00');
    expect(host.textContent).toContain('Phần công việc đã hoàn tất');
    expect(host.textContent).not.toMatch(/Đã thanh toán|Tiền đã về|Đã nhận|số dư/i);
  });

  it('offers existing Applications and Explore destinations in the truthful empty state', async () => {
    jobs([]);
    await render(<MyWork />);
    expect(host.textContent).toContain('Chưa có công việc được giao');
    expect(host.querySelector('a[href="/work/applications"]')?.textContent).toContain('Xem ứng tuyển');
    expect(host.querySelector('a[href="/work"]')?.textContent).toContain('Khám phá công việc');
    expect(host.querySelector('.my-work-ledger')).toBeNull();
    expect(host.querySelector('.my-work-results-heading p')?.textContent).toBe('0 công việc được giao');
  });

  it('keeps the hero during loading/error and retries the existing read', async () => {
    let reject!: (cause: Error) => void;
    const load = vi.spyOn(api, 'myJobs').mockImplementationOnce(() => new Promise((_resolve, failure) => { reject = failure; }))
      .mockResolvedValueOnce({ currentPage: 0, pageSize: 10, totalPages: 1, totalElements: 1, data: [working] });
    await render(<MyWork />);
    expect(host.querySelector('[role="status"]')?.textContent).toContain('Đang tải công việc');
    expect(host.querySelector('h1')?.textContent).toContain('Công việc của bạn.');
    expect(host.querySelector('.my-work-results')?.getAttribute('aria-busy')).toBe('true');
    await act(async () => reject(new Error('Marketplace tạm thời chưa phản hồi')));
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Marketplace tạm thời chưa phản hồi');
    expect(host.querySelector('.my-work-results-heading p')).toBeNull();
    await click('Thử lại');
    expect(load).toHaveBeenCalledTimes(2);
    expect(host.querySelector('.my-work-primary-record')?.textContent).toContain(working.title);
  });

  it('inherits reduced-motion handling for primary, ledger and action nodes', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
    jobs([working, { ...completed, id: 'job-2' }]);
    await render(<MyWork />);
    expect(host.querySelector('.my-work-primary-record')?.getAttribute('data-motion')).toBe('off');
    expect(host.querySelector('.my-work-ledger-row')?.getAttribute('data-motion')).toBe('off');
    expect([...host.querySelectorAll('.my-work-page [data-motion]')].every(node => node.getAttribute('data-motion') === 'off')).toBe(true);
  });
});


