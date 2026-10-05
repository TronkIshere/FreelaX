// @vitest-environment jsdom
import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
import { OwnProfile, ProfileRecord } from './Profile';
import { Portfolio } from './Portfolio';
import { PublicProfile } from './PublicProfile';
import type { PortfolioItem, Profile, User } from './types';
(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const freelancer: User = { id: 'u', email: 'private@example.test', displayName: 'Original', userType: 'FREELANCER' };
const original: Profile = { userId: 'u', userType: 'FREELANCER', displayName: 'Original', email: 'private@example.test', version: 4, headline: 'Designer', languages: [], skills: ['React'], verification: { email: 'UNVERIFIED', identity: 'UNVERIFIED', paymentMethod: 'UNVERIFIED', source: 'NOT_CONFIGURED' }, reputation: { completedContracts: 3, fundedContracts: null, disputeCount: 1, reviewCount: 0, averageRating: null, onTimeRate: null, paymentReleaseRate: null, medianReviewHours: null, calculatedAt: '2026-10-06' } };
const item: PortfolioItem = { id: 'item', userId: 'u', version: 9, title: 'Existing work', description: 'Work description', projectUrl: 'https://example.test/work', thumbnailUrl: null, skills: ['React'], completedAt: null, sortOrder: 0 };
let host: HTMLDivElement, root: Root, profile: Profile, items: PortfolioItem[];
beforeEach(() => {
  host = document.createElement('div'); document.body.append(host); root = createRoot(host); profile = structuredClone(original); items = [structuredClone(item)];
  vi.spyOn(api, 'ownProfile').mockImplementation(async () => profile); vi.spyOn(api, 'profile').mockImplementation(async () => profile);
  vi.spyOn(api, 'portfolio').mockImplementation(async () => items); vi.spyOn(api, 'publicReviews').mockResolvedValue([]);
});
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); });
async function mount(node: ReactNode, path = '/') { await act(async () => root.render(<MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{node}</MemoryRouter>)); }
const button = (text: string) => [...host.querySelectorAll('button')].find(b => b.textContent === text)!;
async function click(text: string) { await act(async () => button(text).click()); }
function field(text: string) { return [...host.querySelectorAll('label')].find(l => l.textContent?.startsWith(text))!.querySelector('input,textarea,select') as HTMLInputElement; }
async function input(text: string, value: string) { const element = field(text); const ctor = element.tagName === 'TEXTAREA' ? HTMLTextAreaElement : element.tagName === 'SELECT' ? HTMLSelectElement : HTMLInputElement; await act(async () => { Object.getOwnPropertyDescriptor(ctor.prototype, 'value')!.set!.call(element, value); element.dispatchEvent(new Event(element.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true })); }); }
async function submit(selector = '.profile-editor') { await act(async () => host.querySelector(selector)!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); }
describe('Step 8 profile and portfolio UI', () => {
  it('loads own profile and only Freelancer editing fields', async () => {
    await mount(<OwnProfile user={freelancer} />); expect(host.textContent).toContain('Designer'); await click('Chỉnh sửa hồ sơ');
    expect(field('Đơn giá USD')).toBeTruthy(); expect(field('Kỹ năng (')).toBeTruthy(); expect(host.textContent).not.toContain('Tên công ty');
    expect(host.querySelector('input[name=email]')).toBeNull();
  });
  it('Client editor excludes Freelancer controls and portfolio', async () => {
    profile = { ...profile, userType: 'CLIENT', companyName: 'Studio' }; await mount(<OwnProfile user={{ ...freelancer, userType: 'CLIENT' }} />); await click('Chỉnh sửa hồ sơ');
    expect(field('Tên công ty')).toBeTruthy(); expect(host.textContent).not.toContain('Đơn giá USD'); expect(host.textContent).not.toContain('Lưu kỹ năng'); expect(api.portfolio).not.toHaveBeenCalled();
  });
  it('PATCH uses current version then reads profile and trusted session', async () => {
    const reconcile = vi.fn().mockResolvedValue(undefined); const patch = vi.spyOn(api, 'patchProfile').mockImplementation(async payload => { profile = { ...profile, ...payload, version: 5 }; return profile; });
    await mount(<OwnProfile user={freelancer} onReconcileUser={reconcile} />); await click('Chỉnh sửa hồ sơ'); await input('Tên hiển thị', 'New server name'); await submit();
    expect(patch.mock.calls[0][0]).toMatchObject({ version: 4, displayName: 'New server name' }); expect(reconcile).toHaveBeenCalledOnce(); expect(api.ownProfile).toHaveBeenCalledTimes(2); expect(host.textContent).toContain('New server name');
  });
  it('stale response keeps draft and requires explicit version reconciliation', async () => {
    const patch = vi.spyOn(api, 'patchProfile').mockRejectedValueOnce(new ApiError('stale', 409, null, 'PROFILE_STALE', 'support-id')).mockImplementation(async p => ({ ...profile, ...p }));
    await mount(<OwnProfile user={freelancer} />); await click('Chỉnh sửa hồ sơ'); await input('Tên hiển thị', 'Local draft'); await submit();
    expect(field('Tên hiển thị').value).toBe('Local draft'); expect(button('Lưu hồ sơ').disabled).toBe(true); expect(host.textContent).toContain('PROFILE_STALE');
    profile = { ...profile, displayName: 'Server edit', version: 8 }; await click('Đọc phiên bản mới nhất'); expect(field('Tên hiển thị').value).toBe('Local draft'); await click('Giữ bản nháp, dùng phiên bản mới'); await submit();
    expect(patch.mock.calls[1][0]).toMatchObject({ version: 8, displayName: 'Local draft' });
  });
  it('session reconcile failure offers a read-only retry without repeating PATCH', async () => {
    const reconcile = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue(undefined); const patch = vi.spyOn(api, 'patchProfile').mockResolvedValue({ ...profile, displayName: 'New' });
    await mount(<OwnProfile user={freelancer} onReconcileUser={reconcile} />); await click('Chỉnh sửa hồ sơ'); await input('Tên hiển thị', 'New'); await submit(); await click('Đọc lại danh tính phiên'); expect(patch).toHaveBeenCalledOnce(); expect(reconcile).toHaveBeenCalledTimes(2);
  });
  it('skills replacement uses current profile version and authoritative returned skills', async () => {
    const save = vi.spyOn(api, 'replaceSkills').mockImplementation(async (version, skills) => { profile = { ...profile, version: version + 1, skills }; return profile; });
    await mount(<OwnProfile user={freelancer} />); await click('Chỉnh sửa hồ sơ'); await input('Kỹ năng (', 'React, Java'); await click('Lưu kỹ năng'); expect(save).toHaveBeenCalledWith(4, ['React', 'Java']); expect(host.textContent).toContain('React, Java');
  });
  it.each(['React, react', 'a'])('does not send invalid skills %s', async value => { const save = vi.spyOn(api, 'replaceSkills'); await mount(<OwnProfile user={freelancer} />); await click('Chỉnh sửa hồ sơ'); await input('Kỹ năng (', value); await click('Lưu kỹ năng'); expect(save).not.toHaveBeenCalled(); });
  it('public profile allowlists fields and never fakes null metrics or verification', async () => {
    await mount(<Routes><Route path="/profiles/:userId" element={<PublicProfile user={freelancer} />} /></Routes>, '/profiles/u');
    expect(host.textContent).not.toContain('private@example.test'); expect(host.textContent).toContain('Chưa có đánh giá công bố'); expect(host.textContent).not.toContain('0 / 5'); expect(host.textContent).not.toContain('Tỷ lệ đúng hạn'); expect(host.textContent).toContain('NOT_CONFIGURED'); expect(host.textContent).not.toContain('Top Rated'); expect(host.textContent).toContain('Existing work');
  });
  it('reads server reputation again after rating event without local aggregation', async () => {
    await mount(<Routes><Route path="/profiles/:userId" element={<PublicProfile user={freelancer} />} /></Routes>, '/profiles/u'); profile = { ...profile, reputation: { ...profile.reputation, reviewCount: 7, averageRating: '3.75' } };
    await act(async () => window.dispatchEvent(new Event('freelax:rating-update'))); expect(api.profile).toHaveBeenCalledTimes(2); expect(host.textContent).toContain('3.75 / 5');
  });
  it('portfolio create uses URL fields and omits item version', async () => {
    const save = vi.spyOn(api, 'savePortfolio').mockImplementation(async payload => { const result = { ...payload, id: 'new', userId: 'u', version: 0 }; items.push(result); return result; });
    await mount(<Portfolio userId="u" editable />); await click('Thêm công trình'); await input('Tiêu đề công trình', 'New work'); await input('Mô tả công trình', 'New description'); await input('Kỹ năng công trình', 'React, Java'); await submit();
    expect(save.mock.calls[0][0]).toMatchObject({ title: 'New work', skills: ['React', 'Java'] }); expect(save.mock.calls[0][1]).toBeUndefined(); expect(host.textContent).toContain('New work');
  });
  it('portfolio update sends current item version', async () => {
    const save = vi.spyOn(api, 'savePortfolio').mockResolvedValue(item); await mount(<Portfolio userId="u" editable />); await click('Sửa Existing work'); await input('Tiêu đề công trình', 'Updated work'); await submit(); expect(save.mock.calls[0][1]).toMatchObject({ id: 'item', version: 9 });
  });
  it('portfolio delete requires confirmation and locks duplicate clicks', async () => {
    const remove = vi.spyOn(api, 'deletePortfolio').mockReturnValue(new Promise(() => {})); await mount(<Portfolio userId="u" editable />); await click('Xóa Existing work'); expect(remove).not.toHaveBeenCalled();
    await act(async () => { button('Xác nhận xóa công trình').click(); button('Xác nhận xóa công trình').click(); }); expect(remove).toHaveBeenCalledOnce();
  });
  it('portfolio deletion reads authoritative remaining list', async () => {
    vi.spyOn(api, 'deletePortfolio').mockImplementation(async () => { items = []; }); await mount(<Portfolio userId="u" editable />); await click('Xóa Existing work'); await click('Xác nhận xóa công trình'); expect(host.textContent).not.toContain('Existing work'); expect(host.textContent).toContain('Đã xóa portfolio');
  });
  it('rejects unsafe URL before portfolio mutation', async () => {
    const save = vi.spyOn(api, 'savePortfolio'); await mount(<Portfolio userId="u" editable />); await click('Sửa Existing work'); await input('URL công trình', 'http://example.test'); await submit(); expect(save).not.toHaveBeenCalled(); expect(host.textContent).toContain('HTTPS');
  });
  it('uncertain create reconciles list before enabling explicit retry', async () => {
    const save = vi.spyOn(api, 'savePortfolio').mockRejectedValue(new ApiError('timeout', 0)); await mount(<Portfolio userId="u" editable />); await click('Thêm công trình'); await input('Tiêu đề công trình', 'Draft work'); await input('Mô tả công trình', 'Draft description'); await submit();
    expect(field('Tiêu đề công trình').value).toBe('Draft work'); expect(button('Lưu công trình').disabled).toBe(true); expect(api.portfolio).toHaveBeenCalledTimes(2); expect(save).toHaveBeenCalledOnce();
  });
  it('ProfileRecord never renders private fields even from an unexpected response', async () => { await mount(<ProfileRecord profile={{ ...profile, email: 'secret@example.test' }} />); expect(host.textContent).not.toContain('secret@example.test'); });
});
