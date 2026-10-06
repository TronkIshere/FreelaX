import { describe, expect, it } from 'vitest';
import { jobFamily } from './jobFamily';

describe('semantic job thumbnail mapping', () => {
  it.each([
    ['WEB_FRONTEND', 'web'], ['BACKEND_API', 'backend'], ['SEO_CONTENT', 'seo'], ['MOBILE_APP', 'mobile'],
    ['UI_UX_DESIGN', 'uiux'], ['ECOMMERCE', 'ecommerce'], ['DATA_ANALYTICS', 'data'],
    ['BRANDING_GRAPHIC', 'branding'], ['OTHER', 'development'],
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
  it('is deterministic across ids, repeated calls, and skill order', () => {
    const a = { id: 'job-a', title: 'P04 E2E', skills: ['React', 'CSS'] };
    const b = { ...a, id: 'job-b', skills: [...a.skills].reverse() };
    expect(jobFamily(a)).toBe('web');
    expect(jobFamily(a)).toBe(jobFamily(b));
    const misleadingId = { id: 'landing-page', title: 'P04 E2E' };
    expect(jobFamily(misleadingId)).toBe('development');
  });
});
