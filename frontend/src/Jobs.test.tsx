// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ClientJobs, FreelancerDiscovery, Pagination } from './Jobs';
import { api } from './api';
import { StatePanel } from './components';
import type { DiscoverJob, Job, Page } from './types';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

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

const job: Job = { id: 'job-test', title: 'Thiết kế tài liệu', description: 'A long brief from the server.',
  budgetUsd: 525, clientUserId: 'client', freelancerId: null, status: 'OPEN', createdAt: '2026-10-04' };
const discovered: DiscoverJob = { ...job, client: { id: 'client', displayName: 'Client identity' },
  hasApplied: false, applicationId: null, applicationStatus: null };
const page = <T,>(data: T[], currentPage = 0): Page<T> => ({
  currentPage, pageSize: 10, totalPages: 2, totalElements: 12, data,
});
async function render(element: React.ReactNode) {
  await act(async () => root.render(<MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
    {element}</MemoryRouter>));
}
function edit(label: string, value: string) {
  const field = [...host.querySelectorAll('.filter-panel label')].find(item => item.textContent?.startsWith(label))!
    .querySelector('input, select') as HTMLInputElement | HTMLSelectElement;
  const prototype = field instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  act(() => {
    Object.getOwnPropertyDescriptor(prototype, 'value')!.set!.call(field, value);
    field.dispatchEvent(new Event(field instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
  });
}
async function apply() {
  await act(async () => host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
}

describe('P06.3 Jobs and Discovery', () => {
  it('shows Client budget and status once, with a real applicants destination and optional contract terms', async () => {
    vi.spyOn(api, 'clientJobs').mockResolvedValue(page([{ ...job,
      deliveryDueAt: '2026-10-15', maxRevisions: 2,
      contract: { id: 'contract', status: 'PENDING_FUNDING', milestoneId: 'milestone',
        milestoneStatus: 'PENDING_FUNDING', amount: 525, currency: 'USD', deliveryDueAt: '2026-10-18',
        reviewWindowHours: 48, maxRevisions: 0, revisionsUsed: 0, deliverables: [], acceptanceCriteria: [] },
    }]));
    await render(<ClientJobs />);
    const row = host.querySelector('.job-row')!;
    expect(row.querySelector('h3 a')?.getAttribute('href')).toBe('/work/job-test');
    expect(row.querySelector('.amount')?.textContent).toBe('$525.00');
    expect(row.querySelectorAll('.state-mark')).toHaveLength(1);
    expect(row.textContent?.match(/Đang tuyển/g)).toHaveLength(1);
    expect(row.querySelector('.job-terms')?.textContent).toContain('2026-10-18');
    expect(row.querySelector('.job-terms')?.textContent).toContain('0/0 lần');
    expect(row.querySelector('.button')?.getAttribute('href')).toBe('/work/job-test/applications');
  });

  it.each([
    ['SUBMITTED_FOR_REVIEW', 'Duyệt bàn giao'], ['AWAITING_PAYMENT', 'Xem công việc'],
    ['COMPLETED', 'Xem công việc'],
  ])('keeps the existing detail destination for Client %s', async (status, label) => {
    vi.spyOn(api, 'clientJobs').mockResolvedValue(page([{ ...job, status }]));
    await render(<ClientJobs />);
    expect(host.querySelector('.job-row .button')?.textContent).toContain(label);
    expect(host.querySelector('.job-row .button')?.getAttribute('href')).toBe('/work/job-test');
    expect(host.querySelector('.job-terms')).toBeNull();
  });

  it.each([
    [false, null, 'Chưa ứng tuyển', 'Chi tiết & ứng tuyển'],
    [true, null, 'Đã ứng tuyển', 'Xem công việc'],
    [true, 'PENDING', 'Đang chờ', 'Xem công việc'],
    [true, 'ACCEPTED', 'Đã được chọn', 'Xem công việc'],
    [true, 'REJECTED', 'Không được chọn', 'Xem công việc'],
    [true, 'CANCELLED', 'Đã hủy', 'Xem công việc'],
  ])('keeps server application context (%s/%s) and uses detail before applying', async (hasApplied, applicationStatus, context, action) => {
    vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([{ ...discovered, hasApplied, applicationStatus }]));
    await render(<FreelancerDiscovery />);
    expect(host.querySelector('.job-row-next')?.textContent).toContain(context);
    expect(host.querySelector('.job-row-next')?.textContent).toContain('Client identity');
    expect(host.querySelector('.job-row .button')?.textContent).toContain(action);
    expect(host.querySelector('.job-row .button')?.getAttribute('href')).toBe('/work/job-test');
    expect(host.querySelector('.job-terms')).toBeNull();
  });

  it('preserves draft/apply filters, page semantics and page reset after a filter change', async () => {
    const discover = vi.spyOn(api, 'discoverJobs').mockImplementation(async requested => page([discovered], requested));
    await render(<FreelancerDiscovery />);
    edit('Từ khóa', 'editorial'); edit('USD từ', '100'); edit('USD đến', '900');
    edit('Sắp xếp', 'BUDGET_DESC'); edit('Ứng tuyển', 'NOT_APPLIED');
    expect(discover).toHaveBeenCalledTimes(1);
    await apply();
    const filters = { keyword: 'editorial', minBudgetUsd: '100', maxBudgetUsd: '900', sort: 'BUDGET_DESC', application: 'NOT_APPLIED' };
    expect(discover).toHaveBeenLastCalledWith(0, filters);
    await act(async () => (host.querySelectorAll('.pagination button')[1] as HTMLButtonElement).click());
    expect(discover).toHaveBeenLastCalledWith(1, filters);
    edit('Sắp xếp', 'BUDGET_ASC'); edit('Ứng tuyển', 'APPLIED');
    await apply();
    expect(discover).toHaveBeenLastCalledWith(0, { ...filters, sort: 'BUDGET_ASC', application: 'APPLIED' });
  });

  it('rejects reversed budgets locally and preserves editable filter values', async () => {
    const discover = vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([discovered]));
    await render(<FreelancerDiscovery />);
    edit('USD từ', '800'); edit('USD đến', '100'); await apply();
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('tối thiểu');
    expect(discover).toHaveBeenCalledOnce();
    edit('USD đến', '900'); await apply();
    expect(host.querySelector('[role="alert"]')).toBeNull();
    expect(discover).toHaveBeenLastCalledWith(0, expect.objectContaining({ minBudgetUsd: '800', maxBudgetUsd: '900' }));
  });

  it('preserves the filter form and heading through loading and recoverable API errors', async () => {
    let reject!: (value: Error) => void;
    vi.spyOn(api, 'discoverJobs').mockImplementationOnce(() => new Promise((_, fail) => { reject = fail; }))
      .mockResolvedValue(page([discovered]));
    await render(<FreelancerDiscovery />);
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    await act(async () => reject(new Error('Marketplace unavailable')));
    expect(host.querySelector('h1')).not.toBeNull();
    expect(host.querySelector('form')).not.toBeNull();
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Marketplace unavailable');
    await act(async () => (host.querySelector('[role="alert"] button') as HTMLButtonElement).click());
    expect(host.querySelector('.job-row')).not.toBeNull();
  });
});

describe('job list controls', () => {
  it('moves to the previous and next server page', () => {
    const onPage = vi.fn();
    act(() => root.render(<Pagination page={{
      currentPage: 1, pageSize: 10, totalPages: 3, totalElements: 25, data: [],
    }} onPage={onPage} />));
    const buttons = host.querySelectorAll('button');
    expect(host.textContent).toContain('Trang 2/3');
    expect(buttons[0].disabled).toBe(false);
    expect(buttons[1].disabled).toBe(false);
    act(() => buttons[0].click());
    act(() => buttons[1].click());
    expect(onPage.mock.calls).toEqual([[0], [2]]);
  });

  it('disables page controls when the server returns no results', () => {
    act(() => root.render(<Pagination page={{
      currentPage: 0, pageSize: 10, totalPages: 0, totalElements: 0, data: [],
    }} onPage={vi.fn()} />));
    expect([...host.querySelectorAll('button')].every(button => button.disabled)).toBe(true);
    expect(host.textContent).toContain('Trang 0/0');
  });

  it('shows a recoverable API error with an operable retry', () => {
    const retry = vi.fn();
    act(() => root.render(<StatePanel kind="error" title="Không thể tải công việc"
      body="Máy chủ tạm thời không phản hồi." action={{ label: 'Thử lại', onClick: retry }} />));
    expect(host.querySelector('[role="alert"]')).not.toBeNull();
    act(() => host.querySelector('button')!.click());
    expect(retry).toHaveBeenCalledOnce();
  });
});
