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
  vi.unstubAllGlobals();
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
  const controlLabel = [...host.querySelectorAll<HTMLLabelElement>('.filter-panel label')]
    .find(item => item.textContent?.startsWith(label))!;
  const field = (controlLabel.control ?? controlLabel.querySelector('input, select')) as HTMLInputElement | HTMLSelectElement;
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
    expect(host.querySelector('.explore-job-row')?.textContent).toContain(context);
    expect(host.querySelector('.explore-job-row')?.textContent).toContain('Client identity');
    expect(host.querySelector('.job-row .button')?.textContent).toContain(action);
    expect(host.querySelector('.job-row .button')?.getAttribute('href')).toBe('/work/job-test');
    expect(host.querySelector('.job-terms')).toBeNull();
  });

  it('preserves draft/apply filters, page semantics and page reset after a filter change', async () => {
    const discover = vi.spyOn(api, 'discoverJobs').mockImplementation(async requested => page([discovered], requested));
    await render(<FreelancerDiscovery />);
    edit('Từ khóa', 'editorial'); edit('Tối thiểu', '100'); edit('Tối đa', '900');
    edit('Sắp xếp', 'BUDGET_DESC'); edit('Trạng thái ứng tuyển', 'NOT_APPLIED');
    expect(discover).toHaveBeenCalledTimes(1);
    await apply();
    const filters = { keyword: 'editorial', minBudgetUsd: '100', maxBudgetUsd: '900', sort: 'BUDGET_DESC', application: 'NOT_APPLIED' };
    expect(discover).toHaveBeenLastCalledWith(0, filters);
    await act(async () => (host.querySelectorAll('.pagination button')[1] as HTMLButtonElement).click());
    expect(discover).toHaveBeenLastCalledWith(1, filters);
    edit('Sắp xếp', 'BUDGET_ASC'); edit('Trạng thái ứng tuyển', 'APPLIED');
    await apply();
    expect(discover).toHaveBeenLastCalledWith(0, { ...filters, sort: 'BUDGET_ASC', application: 'APPLIED' });
  });

  it('rejects reversed budgets locally and preserves editable filter values', async () => {
    const discover = vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([discovered]));
    await render(<FreelancerDiscovery />);
    edit('Tối thiểu', '800'); edit('Tối đa', '100'); await apply();
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('tối thiểu');
    expect(discover).toHaveBeenCalledOnce();
    edit('Tối đa', '900'); await apply();
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

describe('B1 Freelancer Explore', () => {
  it('keeps the exact category taxonomy, accessible native selects and both decorative heading rays', async () => {
    vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([discovered]));
    await render(<FreelancerDiscovery />);
    expect([...host.querySelectorAll('.explore-filters select:first-of-type option')].slice(0, 10).map(el => el.getAttribute('value')))
      .toEqual(['', 'WEB_FRONTEND', 'BACKEND_API', 'SEO_CONTENT', 'MOBILE_APP', 'UI_UX_DESIGN', 'ECOMMERCE', 'DATA_ANALYTICS', 'BRANDING_GRAPHIC', 'OTHER']);
    expect(host.querySelectorAll('h1 .explore-title-rays')).toHaveLength(2);
    expect([...host.querySelectorAll('h1 svg')].every(el => el.getAttribute('aria-hidden') === 'true')).toBe(true);
    expect(host.querySelector('.explore-results-heading select')).not.toBeNull();
    expect(host.querySelector('.explore-filters')?.textContent).toContain('Áp dụng bộ lọc');
  });

  it('adds skills with Enter/comma without applying, removes with Backspace/button, then submits exact strings', async () => {
    const discover = vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([discovered]));
    await render(<FreelancerDiscovery />);
    const press = (key: string) => act(() => host.querySelector('#explore-skill-entry')!
      .dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })));
    edit('Kỹ năng', ' React '); press('Enter');
    edit('Kỹ năng', 'CSS'); press(',');
    expect([...host.querySelectorAll('.explore-skill-tokens li > span')].map(el => el.textContent)).toEqual(['React', 'CSS']);
    expect(discover).toHaveBeenCalledOnce();
    press('Backspace');
    expect(host.querySelector('.explore-skill-tokens')?.textContent).toBe('React');
    edit('Kỹ năng', 'Java'); press('Enter');
    await act(async () => (host.querySelector('[aria-label="Xóa kỹ năng React"]') as HTMLButtonElement).click());
    await apply();
    expect(discover).toHaveBeenLastCalledWith(0, expect.objectContaining({ skills: ['Java'] }));
    expect((host.querySelector('#explore-skill-entry') as HTMLInputElement).value).toBe('');
  });

  it('handles pasted delimiters and pending text on Apply without a fake skill taxonomy', async () => {
    const discover = vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([discovered]));
    await render(<FreelancerDiscovery />);
    edit('Kỹ năng', ' Vue, TypeScript, CSS ');
    expect([...host.querySelectorAll('.explore-skill-tokens li > span')].map(el => el.textContent)).toEqual(['Vue', 'TypeScript']);
    await apply();
    expect(discover).toHaveBeenLastCalledWith(0, expect.objectContaining({ skills: ['Vue', 'TypeScript', 'CSS'] }));
    await act(async () => (host.querySelector('.explore-clear') as HTMLButtonElement).click());
    expect(host.querySelector('.explore-skill-tokens')).toBeNull();
    expect((host.querySelector('#explore-skill-entry') as HTMLInputElement).value).toBe('');
    expect(discover).toHaveBeenLastCalledWith(0, expect.not.objectContaining({ skills: expect.anything() }));
  });

  it.each([
    ['X', '2–40'], ['x'.repeat(41), '2–40'], ['React, react', 'trùng'],
    [Array.from({ length: 11 }, (_, i) => 'Skill ' + i).join(', '), '10'],
    ['React,,CSS', '2–40'],
  ])('rejects invalid token input before querying: %s', async (input, message) => {
    const discover = vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([discovered]));
    await render(<FreelancerDiscovery />);
    edit('Kỹ năng', input); await apply();
    expect(host.querySelector('[role="alert"]')?.textContent).toContain(message);
    expect(discover).toHaveBeenCalledOnce();
    expect((host.querySelector('#explore-skill-entry') as HTMLInputElement).value).not.toBe('');
  });

  it('does not tokenize Enter during IME composition or delete a token while text remains', async () => {
    vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([discovered]));
    await render(<FreelancerDiscovery />);
    edit('Kỹ năng', 'TypeScript');
    act(() => host.querySelector('#explore-skill-entry')!.dispatchEvent(new KeyboardEvent('keydown', {
      key: 'Enter', isComposing: true, bubbles: true, cancelable: true,
    })));
    expect(host.querySelector('.explore-skill-tokens')).toBeNull();
    await apply(); edit('Kỹ năng', 'CSS');
    act(() => host.querySelector('#explore-skill-entry')!.dispatchEvent(new KeyboardEvent('keydown', {
      key: 'Backspace', bubbles: true, cancelable: true,
    })));
    expect(host.querySelector('.explore-skill-tokens')?.textContent).toBe('TypeScript');
  });

  it('applies real category/ANY-skill filters with pagination, resets them, and renders actual skill tags', async () => {
    const discover = vi.spyOn(api, 'discoverJobs').mockImplementation(async requested => page([
      { ...discovered, category: 'BACKEND_API', skills: ['Spring', 'Java'], title: 'Landing page ambiguous title' },
    ], requested));
    await render(<FreelancerDiscovery />);
    edit('Danh mục', 'BACKEND_API'); edit('Kỹ năng', ' Spring, Java ');
    expect(discover).toHaveBeenCalledOnce(); await apply();
    expect(discover).toHaveBeenLastCalledWith(0, expect.objectContaining({ category: 'BACKEND_API', skills: ['Spring', 'Java'] }));
    expect([...host.querySelectorAll('.skill-tag')].map(el => el.textContent)).toEqual(['Spring', 'Java']);
    expect(host.querySelector('.job-family-art')?.getAttribute('data-family')).toBe('backend');
    await act(async () => (host.querySelectorAll('.pagination button')[1] as HTMLButtonElement).click());
    expect(discover).toHaveBeenLastCalledWith(1, expect.objectContaining({ category: 'BACKEND_API', skills: ['Spring', 'Java'] }));
    await act(async () => (host.querySelector('.explore-clear') as HTMLButtonElement).click());
    expect(discover).toHaveBeenLastCalledWith(0, { keyword: '', minBudgetUsd: '', maxBudgetUsd: '', sort: 'NEWEST', application: 'ALL' });
    expect((host.querySelector('.explore-filters select') as HTMLSelectElement).value).toBe('');
    expect(host.textContent).not.toMatch(/Remote|Hybrid|Onsite|Hình thức làm việc/);
  });
  it('rejects invalid selected skills without querying or losing the filter draft', async () => {
    const discover = vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([discovered]));
    await render(<FreelancerDiscovery />);
    edit('Kỹ năng', 'React, react'); await apply();
    expect(discover).toHaveBeenCalledOnce(); expect(host.querySelector('[role="alert"]')?.textContent).toContain('trùng');
  });
  it('renders a real editorial ledger, semantic thumbnails, counts and detail actions without invented skills', async () => {
    vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([
      { ...discovered, title: 'Landing page redesign' },
      { ...discovered, id: 'api-job', title: 'Viết REST API cho module giao dịch', hasApplied: true },
      { ...discovered, id: 'generic-job', title: 'P04 full E2E 1790721570419', createdAt: null },
    ]));
    await render(<FreelancerDiscovery />);
    expect(host.querySelector('h1')?.textContent).toBe('Tìm công việc phù hợp.');
    expect(host.querySelector('.explore-results-heading')?.textContent).toContain('12 công việc phù hợp');
    expect([...host.querySelectorAll('.job-family-art')].map(el => el.getAttribute('data-family')))
      .toEqual(['web', 'backend', 'development']);
    expect(host.querySelectorAll('ul.explore-ledger > li')).toHaveLength(3);
    expect(host.querySelector('.explore-excerpt')?.textContent).toBe(discovered.description);
    expect(host.querySelector('.explore-budget strong')?.textContent).toBe('$525.00');
    expect(host.textContent).toContain('Ngày đăng chưa có');
    expect(host.querySelectorAll('.skill-tag')).toHaveLength(0);
    expect([...host.querySelectorAll('.explore-job-action')].map(el => el.getAttribute('href')))
      .toEqual(['/work/job-test', '/work/api-job', '/work/generic-job']);
    expect(host.querySelector('.explore-search svg')?.getAttribute('aria-hidden')).toBe('true');
  });
  it('clears all filters and resets the real server request from an empty result', async () => {
    const discover = vi.spyOn(api, 'discoverJobs').mockResolvedValue({ ...page<DiscoverJob>([]), totalPages: 0, totalElements: 0 });
    await render(<FreelancerDiscovery />);
    edit('Từ khóa', 'No matching job'); edit('Tối thiểu', '500'); edit('Sắp xếp', 'BUDGET_DESC');
    edit('Trạng thái ứng tuyển', 'NOT_APPLIED'); await apply();
    expect(host.querySelector('.state-empty h2')?.textContent).toBe('Không tìm thấy công việc phù hợp.');
    await act(async () => (host.querySelector('.state-empty button') as HTMLButtonElement).click());
    expect(discover).toHaveBeenLastCalledWith(0, { keyword: '', minBudgetUsd: '', maxBudgetUsd: '', sort: 'NEWEST', application: 'ALL' });
    expect((host.querySelector('input[type="search"]') as HTMLInputElement).value).toBe('');
  });
  it('retains the page-reset recovery for a server page that no longer exists', async () => {
    const discover = vi.spyOn(api, 'discoverJobs').mockImplementation(async index => page(index ? [] : [discovered], index));
    await render(<FreelancerDiscovery />);
    await act(async () => (host.querySelectorAll('.pagination button')[1] as HTMLButtonElement).click());
    expect(host.textContent).toContain('Trang này không còn công việc');
    await act(async () => (host.querySelector('.state-empty button') as HTMLButtonElement).click());
    expect(discover).toHaveBeenLastCalledWith(0, expect.any(Object));
    expect(host.querySelector('.explore-job-row')).not.toBeNull();
  });
  it('keeps ledger, arrows and heading static for reduced motion', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([discovered]));
    await render(<FreelancerDiscovery />);
    expect(host.querySelector('.explore-job-row')?.getAttribute('data-motion')).toBe('off');
    expect([...host.querySelectorAll('.ku-action-arrow')].every(el => el.getAttribute('data-motion') === 'off')).toBe(true);
    expect(host.querySelector('.explore-filters .ku-label')?.getAttribute('data-motion')).toBe('off');
    expect(host.querySelector('.ku-moving-underline')?.getAttribute('style')).not.toContain('scaleX(0.65)');
  });
});
