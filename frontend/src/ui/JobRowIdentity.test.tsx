// @vitest-environment jsdom
import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api';
import { ClientJobs, FreelancerDiscovery } from '../Jobs';
import { MyApplications } from '../Workflow';
import { MyWork } from '../WorkLifecycle';
import { applicationLabel, jobLabel } from '../status';
import type { DiscoverJob, Job, JobCategory, MyApplication, Page } from '../types';
import { jobVisualIdentity } from './job-thumbnails/jobFamily';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const job: Job = { id: 'stable-job', title: 'REST API title with misleading skills', description: 'Real job description',
  category: 'WEB_FRONTEND', skills: ['Java'], budgetUsd: 200, status: 'COMPLETED',
  clientUserId: 'client', freelancerId: 'freelancer', createdAt: '2026-10-01T12:00:00' };
const first = { ...job, id: 'first', title: 'First server record' };
const page = <T,>(data: T[]): Page<T> => ({ data, totalElements: data.length, currentPage: 0, totalPages: 1, pageSize: 10 });
const app = (value: Job, status: MyApplication['status']): MyApplication => ({
  id: 'app-' + value.id, status, createdAt: '2026-10-02T12:00:00', updatedAt: null,
  job: { ...value, status: value.status as MyApplication['job']['status'], clientDisplayName: 'Real Client' },
});
const discover = (value: Job): DiscoverJob => ({ ...value, client: { id: 'client', displayName: 'Real Client' },
  hasApplied: false, applicationId: null, applicationStatus: null });
let host: HTMLDivElement;
let root: Root;
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
async function render(view: ReactNode) {
  await act(async () => root.render(<MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{view}</MemoryRouter>));
}

describe('shared secondary job identity', () => {
  it.each([
    ['OPEN', 'state-open'], ['AWAITING_PAYMENT', 'state-awaiting-payment'], ['IN_PROGRESS', 'state-in-progress'],
    ['SUBMITTED_FOR_REVIEW', 'state-review'], ['REVISION_REQUESTED', 'state-revision'],
    ['COMPLETED', 'state-completed'], ['CANCELLED', 'state-cancelled'],
  ])('renders %s as a secondary job rail with matching visible status and real category', async (status, tone) => {
    vi.spyOn(api, 'clientJobs').mockResolvedValue(page([first, { ...job, status }]));
    await render(<ClientJobs />);
    const row = host.querySelector('.client-job-ledger-row')!;
    expect(row.classList.contains(tone)).toBe(true);
    expect(row.querySelector('.job-progress-marker')?.classList.contains(tone)).toBe(true);
    expect(row.querySelector('.job-progress-marker')?.textContent).toBe(jobLabel(status));
    expect(row.querySelector('.job-category-plate')?.textContent).toBe('WEB / FRONTEND');
    expect(row.querySelector('.job-identity-cluster .job-family-art')?.getAttribute('data-family')).toBe('web');
    expect(row.querySelector('.client-job-skills')?.textContent).toBe('Java');
    expect(row.querySelector('a')?.getAttribute('href')).toBe('/work/stable-job' + (status === 'OPEN' ? '/applications' : ''));
    expect(row.querySelector('button,form')).toBeNull();
    expect(host.querySelector('.client-job-primary-record .job-category-plate')).toBeNull();
  });

  it.each([
    ['PENDING', 'application-pending'], ['ACCEPTED', 'application-accepted'],
    ['REJECTED', 'application-rejected'], ['CANCELLED', 'application-cancelled'],
  ] as const)('uses application %s rather than completed Job status for the rail', async (status, tone) => {
    vi.spyOn(api, 'myApplications').mockResolvedValue(page([app(first, 'PENDING'), app(job, status)]));
    await render(<MyApplications />);
    const row = host.querySelector('.applications-ledger-row')!;
    expect(row.classList.contains(tone)).toBe(true);
    expect(row.classList.contains('state-completed')).toBe(false);
    expect(row.querySelector('.job-progress-marker')?.textContent).toBe(applicationLabel(status));
    expect(row.querySelector('.job-progress-marker')?.classList.contains(tone)).toBe(true);
    expect(row.querySelector('.job-category-plate')?.textContent).toBe('WEB / FRONTEND');
    expect(row.querySelector('.applications-skills')?.textContent).toBe('Java');
    expect(host.querySelector('.applications-primary-record .job-category-plate')).toBeNull();
  });

  it.each([
    ['WEB_FRONTEND', 'WEB / FRONTEND'], ['BACKEND_API', 'BACKEND / API'], ['SEO_CONTENT', 'SEO / NỘI DUNG'],
    ['MOBILE_APP', 'MOBILE APP'], ['UI_UX_DESIGN', 'UI / UX'], ['ECOMMERCE', 'E-COMMERCE'],
    ['DATA_ANALYTICS', 'DATA / ANALYTICS'], ['BRANDING_GRAPHIC', 'BRANDING / GRAPHIC'], ['OTHER', 'BACKEND / API'],
  ] as [JobCategory, string][])('uses the resolved %s family for the plate, preserving thumbnail classification', async (category, label) => {
    const item = { ...job, category };
    vi.spyOn(api, 'clientJobs').mockResolvedValue(page([first, item]));
    await render(<ClientJobs />);
    const row = host.querySelector('.client-job-ledger-row')!;
    expect(row.querySelector('.job-category-plate')?.textContent).toBe(label);
    expect(row.querySelector('.job-category-plate')?.getAttribute('data-category')).toBe(category);
    expect(row.classList.contains('state-completed')).toBe(true);
    const identity = jobVisualIdentity(item);
    expect(row.querySelector('.job-category-plate')?.getAttribute('data-family')).toBe(identity.family);
    expect(row.querySelector('.job-family-art')?.getAttribute('data-visual-key')).toBe(identity.family + ':' + identity.variant);
  });

  it('keeps identical Job thumbnail/Rough identities across all four surfaces and server order', async () => {
    vi.spyOn(api, 'clientJobs').mockResolvedValue(page([first, job]));
    vi.spyOn(api, 'myJobs').mockResolvedValue(page([first, job]));
    vi.spyOn(api, 'myApplications').mockResolvedValue(page([app(first, 'PENDING'), app(job, 'ACCEPTED')]));
    vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([discover(first), discover(job)]));
    const identities: string[] = [];
    for (const view of [<ClientJobs />, <MyWork />, <MyApplications />, <FreelancerDiscovery />]) {
      await render(view);
      const row = host.querySelector('.job-identity-row')!;
      expect(row.querySelector('.job-category-plate')?.textContent).toBe('WEB / FRONTEND');
      identities.push(row.querySelector('.job-family-art')!.outerHTML);
      expect([...host.querySelectorAll('h3')].map(node => node.textContent)).toEqual([first.title, job.title]);
      expect(host.querySelectorAll('.job-identity-row')).toHaveLength(1);
    }
    expect(new Set(identities).size).toBe(1);
    expect(identities[0]).toContain('data-family="web"');
  });

  it('shares legacy OTHER skills/title fallback without mutating stored category', async () => {
    const item = { ...job, category: 'OTHER' as const, skills: [] };
    vi.spyOn(api, 'myJobs').mockResolvedValue(page([first, item]));
    await render(<MyWork />);
    expect(host.querySelector('.job-category-plate')?.textContent).toBe('BACKEND / API');
    expect(item.category).toBe('OTHER');
    expect(host.querySelector('.job-identity-row .job-family-art')?.getAttribute('data-family')).toBe('backend');
    expect(host.querySelector('.job-identity-row .my-work-skills')).toBeNull();
    expect(host.querySelector('.my-work-primary-record')?.classList.contains('ku-surface--mint')).toBe(true);
    expect(host.querySelector('.my-work-primary-record .job-progress-marker')?.classList.contains('state-completed')).toBe(true);
  });

  it('does not invent a stored category or business state for incomplete legacy responses', async () => {
    vi.spyOn(api, 'clientJobs').mockResolvedValue(page([first, { ...job, category: null, status: 'UNKNOWN' }]));
    await render(<ClientJobs />);
    expect(host.querySelector('.job-identity-row')?.classList.contains('state-unknown')).toBe(true);
    expect(host.querySelector('.job-category-plate')?.textContent).toBe('BACKEND / API');
    expect(host.querySelector('.job-category-plate')?.getAttribute('data-category')).toBe('');
  });

  it.each([
    ['Viet REST API cho module giao dịch', [], 'backend', 'BACKEND / API'],
    ['Tối ưu SEO trang chủ', [], 'seo', 'SEO / NỘI DUNG'],
    ['Một công việc', ['Java'], 'backend', 'BACKEND / API'],
    ['Một công việc', [], 'development', 'KHÁC'],
  ] as [string, string[], string, string][])('resolves legacy OTHER %s consistently across surfaces and IDs', async (title, skills, family, label) => {
    const item = { ...job, category: 'OTHER' as const, title, skills };
    const otherId = { ...item, id: 'another-job' };
    vi.spyOn(api, 'clientJobs').mockResolvedValue(page([first, item, otherId]));
    vi.spyOn(api, 'myJobs').mockResolvedValue(page([first, item, otherId]));
    vi.spyOn(api, 'myApplications').mockResolvedValue(page([app(first, 'PENDING'), app(item, 'ACCEPTED'), app(otherId, 'ACCEPTED')]));
    vi.spyOn(api, 'discoverJobs').mockResolvedValue(page([discover(first), discover(item), discover(otherId)]));
    const visualKeys: string[][] = [];
    for (const view of [<ClientJobs />, <MyWork />, <MyApplications />, <FreelancerDiscovery />]) {
      await render(view);
      const rows = [...host.querySelectorAll('.job-identity-row')];
      for (const row of rows) {
        expect(row.querySelector('.job-category-plate')?.textContent).toBe(label);
        expect(row.querySelector('.job-category-plate')?.getAttribute('data-family')).toBe(family);
        expect(row.querySelector('.job-category-plate')?.getAttribute('data-category')).toBe('OTHER');
        expect(row.querySelector('.job-family-art')?.getAttribute('data-family')).toBe(family);
      }
      visualKeys.push(rows.map(row => row.querySelector('.job-family-art')!.outerHTML));
    }
    expect(visualKeys.every(keys => JSON.stringify(keys) === JSON.stringify(visualKeys[0]))).toBe(true);
    expect(item.category).toBe('OTHER');
  });

  it.each([
    ['PENDING', 'acid', 'application-pending'], ['ACCEPTED', 'mint', 'application-accepted'],
    ['REJECTED', 'vermilion', 'application-rejected'], ['CANCELLED', 'cream', 'application-cancelled'],
  ] as const)('uses the %s primary application surface without changing access', async (status, surface, tone) => {
    vi.spyOn(api, 'myApplications').mockResolvedValue(page([app(job, status)]));
    await render(<MyApplications />);
    const primary = host.querySelector('.applications-primary-record')!;
    expect(primary.classList.contains('ku-surface--' + surface)).toBe(true);
    expect(primary.classList.contains(tone)).toBe(true);
    expect(primary.querySelector('.job-progress-marker')?.textContent).toBe(applicationLabel(status));
    expect(primary.querySelector('.job-progress-marker')?.classList.contains(tone)).toBe(true);
    expect(!!primary.querySelector('a')).toBe(status === 'ACCEPTED');
    expect(primary.querySelector('form,button')).toBeNull();
  });
});
