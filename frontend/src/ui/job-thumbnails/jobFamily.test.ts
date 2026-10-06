// @vitest-environment jsdom
import { act, createElement as h } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { jobFamily, jobVisualIdentity, jobVariantCounts, type JobFamily } from './jobFamily';
import { JobThumbnail } from './JobThumbnail';
import { FreelancerDiscovery } from '../../Jobs';
import { MyApplications } from '../../Workflow';
import { MyWork } from '../../WorkLifecycle';
import { api } from '../../api';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
afterEach(() => vi.restoreAllMocks());

describe('semantic job thumbnail mapping', () => {
  it.each([
    ['WEB_FRONTEND', 'web'], ['BACKEND_API', 'backend'], ['SEO_CONTENT', 'seo'], ['MOBILE_APP', 'mobile'],
    ['UI_UX_DESIGN', 'uiux'], ['ECOMMERCE', 'ecommerce'], ['DATA_ANALYTICS', 'data'],
    ['BRANDING_GRAPHIC', 'branding'],
  ] as const)('treats persisted category %s as authoritative over skills and title', (category, expected) => {
    expect(jobFamily({ category, skills: ['React'], title: 'Landing page redesign' })).toBe(expected);
  });
  it.each([
    ['Landing page redesign', 'web'], ['Viết REST API cho module giao dịch', 'backend'],
    ['Tối ưu SEO trang chủ', 'seo'], ['React Native app', 'mobile'], ['Figma wireframe', 'uiux'],
    ['E-commerce checkout', 'ecommerce'], ['Power BI dashboard', 'data'], ['Graphic design logo', 'branding'],
    ['P04 full E2E 1790721570419', 'development'],
  ] as const)('maps real title %s to %s', (title, expected) => {
    expect(jobFamily({ title })).toBe(expected);
  });
  it('gives an available real category/type priority over skills and title', () => {
    expect(jobFamily({ category: 'Mobile App', skills: ['Java'], title: 'SEO content' })).toBe('mobile');
    expect(jobFamily({ type: 'REST API', skills: ['Figma'], title: 'Landing page' })).toBe('backend');
  });
  it('uses skills before title, and falls through an unrecognized category', () => {
    expect(jobFamily({ category: 'Other', skills: ['Spring', 'PostgreSQL'], title: 'Web design' })).toBe('backend');
    expect(jobFamily({ skills: ['Flutter'], title: 'SEO content' })).toBe('mobile');
  });
  it('matches specific skill phrases and whole words instead of misleading substrings', () => {
    expect(jobFamily({ skills: ['JavaScript'] })).toBe('web');
    expect(jobFamily({ skills: ['React Native'] })).toBe('mobile');
    expect(jobFamily({ skills: ['Graphic Design'] })).toBe('branding');
    expect(jobFamily({ skills: ['database'] })).toBe('backend');
    expect(jobFamily({ title: 'Build and restore a guide' })).toBe('development');
  });
  it('falls back for missing/null/unknown information without invented classification', () => {
    expect(jobFamily({})).toBe('development');
    expect(jobFamily({ title: null, category: null, skills: null })).toBe('development');
    expect(jobFamily({ title: 'E2E 123', category: 'Unspecified', skills: [] })).toBe('development');
  });
  it('treats stored OTHER as unspecified decoration, using skills before title without changing category', () => {
    const source = { id: 'web-looking-id', category: 'OTHER', skills: ['Spring'], title: 'Tối ưu SEO trang chủ' };
    expect(jobFamily(source)).toBe('backend');
    expect(source.category).toBe('OTHER');
    expect(jobFamily({ ...source, skills: [] })).toBe('seo');
    expect(jobFamily({ ...source, skills: [], title: 'P04 E2E' })).toBe('development');
  });
  it('is deterministic across ids, repeated calls, and skill order', () => {
    const a = { id: 'job-a', title: 'P04 E2E', skills: ['React', 'CSS'] };
    const b = { ...a, id: 'job-b', skills: [...a.skills].reverse() };
    expect(jobFamily(a)).toBe('web');
    expect(jobFamily(a)).toBe(jobFamily(b));
    const misleadingId = { id: 'landing-page', title: 'P04 E2E' };
    expect(jobFamily(misleadingId)).toBe('development');
  });
});

describe('stable job visual identity', () => {
  it('resolves the same identity across calls and nonsemantic data changes', () => {
    const source = { id: 'job-shared-1', category: 'WEB_FRONTEND', title: 'Server database' };
    expect(jobVisualIdentity(source)).toEqual(jobVisualIdentity({ ...source, title: 'SEO content' }));
    expect(jobVisualIdentity(source).family).toBe('web');
    expect(jobVisualIdentity(source).roughKey).not.toContain(source.id);
    expect(jobVisualIdentity({ ...source, category: 'BACKEND_API', id: 'landing-page' }).family).toBe('backend');
  });

  it('uses variant zero and stable semantic decoration when id is absent', () => {
    expect(jobVisualIdentity({ category: 'MOBILE_APP' })).toEqual({ family: 'mobile', variant: 0,
      visualKey: 'mobile:0', roughKey: 'job-family:mobile:default' });
    expect(jobVisualIdentity({ category: 'MOBILE_APP', id: null }).visualKey).toBe('mobile:0');
  });

  it.each(Object.keys(jobVariantCounts) as JobFamily[])('selects distinct approved artwork inside %s without using ids to classify', async family => {
    const titles: Record<JobFamily, string> = { web: 'Landing page', backend: 'REST API', seo: 'SEO content',
      mobile: 'Mobile app', uiux: 'Figma wireframe', ecommerce: 'Storefront', data: 'Analytics',
      branding: 'Branding logo', development: 'P04 E2E' };
    const variants = new Map<number, string>();
    for (let i = 0; i < 200 && variants.size < jobVariantCounts[family]; i++) {
      const id = 'job-' + i;
      const identity = jobVisualIdentity({ id, title: titles[family] });
      expect(identity.family).toBe(family);
      expect(identity.variant).toBeGreaterThanOrEqual(0);
      expect(identity.variant).toBeLessThan(jobVariantCounts[family]);
      variants.set(identity.variant, id);
    }
    expect(variants.size).toBe(3);
    const host = document.createElement('div');
    const root = createRoot(host);
    try {
      const drawings = new Set<string>();
      for (const [variant, id] of variants) {
        await act(async () => root.render(h(JobThumbnail, { job: { id, title: titles[family] } })));
        expect(host.querySelector('.job-family-art')?.getAttribute('data-visual-key')).toBe(family + ':' + variant);
        drawings.add(host.querySelector('.job-family-drawing')!.innerHTML);
      }
      expect(drawings.size).toBe(3);
    } finally { act(() => root.unmount()); }
  });

  it('preserves exact artwork and Rough paths across rerender and remount', async () => {
    const job = { id: 'job-stable', category: 'SEO_CONTENT' };
    const host = document.createElement('div');
    let root = createRoot(host);
    try {
      await act(async () => root.render(h(JobThumbnail, { job })));
      const first = host.innerHTML;
      await act(async () => root.render(h(JobThumbnail, { job: { ...job, title: 'Changed title' } })));
      expect(host.innerHTML).toBe(first);
      act(() => root.unmount());
      root = createRoot(host);
      await act(async () => root.render(h(JobThumbnail, { job })));
      expect(host.innerHTML).toBe(first);
    } finally { act(() => root.unmount()); }
  });

  it('passes the same job id through real Explore, Applications and My Work adapters', async () => {
    const job = { id: 'job-cross-screen', title: 'Shared backend job', description: 'Real response shape',
      category: 'BACKEND_API' as const, skills: ['Java'], budgetUsd: 300, createdAt: '2026-10-06',
      clientUserId: 'client-1', freelancerId: 'freelancer-1', status: 'IN_PROGRESS' as const };
    const page = { currentPage: 0, pageSize: 10, totalPages: 1, totalElements: 1 };
    vi.spyOn(api, 'discoverJobs').mockResolvedValue({ ...page, data: [{ ...job,
      client: { id: 'client-1', displayName: 'Client' }, hasApplied: true, applicationId: 'application-1', applicationStatus: 'ACCEPTED' }] });
    vi.spyOn(api, 'myApplications').mockResolvedValue({ ...page, data: [{ id: 'application-1', status: 'ACCEPTED',
      createdAt: '2026-10-06', updatedAt: '2026-10-06', job: { ...job, clientDisplayName: 'Client' } }] });
    vi.spyOn(api, 'myJobs').mockResolvedValue({ ...page, data: [job] });
    const host = document.createElement('div');
    const expected = jobVisualIdentity(job);
    const artworks: string[] = [];
    for (const Component of [FreelancerDiscovery, MyApplications, MyWork]) {
      const root = createRoot(host);
      try {
        await act(async () => root.render(h(MemoryRouter, {}, h(Component))));
        const thumbnail = host.querySelector('.job-family-art')!;
        expect(thumbnail.getAttribute('data-family')).toBe(expected.family);
        expect(thumbnail.getAttribute('data-visual-key')).toBe(expected.visualKey);
        expect(thumbnail.getAttribute('data-rough-key')).toBe(expected.roughKey);
        expect(thumbnail.getAttribute('data-rough-key')).not.toBe(jobVisualIdentity({ ...job, id: null }).roughKey);
        artworks.push(thumbnail.outerHTML);
      } finally { act(() => root.unmount()); }
    }
    expect(new Set(artworks).size).toBe(1);
  });
});
