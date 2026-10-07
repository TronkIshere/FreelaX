// @vitest-environment jsdom
import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
import { AdminReview, AdminReviews } from './AdminReviews';
import { Activity } from './Activity';
import { ContractReviews, ReviewRecord, ReviewReport } from './ContractReviews';
import { Overview } from './Overview';
import { PublicProfile } from './PublicProfile';
import type { AdminReviewDetail, ContractSettlement, Job, Profile, Review, User } from './types';
(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const client: User = { id: 'client', email: 'client@example.test', displayName: 'Client', userType: 'CLIENT' };
const freelancer: User = { ...client, id: 'freelancer', userType: 'FREELANCER' };
const admin: User = { ...client, id: 'admin', authorities: ['ROLE_ADMIN'] };
const job: Job = { id: 'job', title: 'Completed job', description: 'Real brief', budgetUsd: 500, status: 'COMPLETED', clientUserId: 'client', freelancerId: 'freelancer', createdAt: null, contract: { id: 'contract', status: 'COMPLETED', milestoneId: 'milestone', milestoneStatus: 'RELEASED', amount: 500, currency: 'USD', deliveryDueAt: null, reviewWindowHours: 48, maxRevisions: 2, revisionsUsed: 0, deliverables: [], acceptanceCriteria: [] } };
const settlement: ContractSettlement = { contractId: 'contract', milestoneId: 'milestone', jobId: 'job', amount: 500, currency: 'USD', simulation: true, moneyStatus: 'SUCCEEDED', onChainStatus: 'FAILED', offRampStatus: 'NOT_STARTED', taxStatus: 'NOT_STARTED', releaseReference: 'release', onChainReference: null, offRampReference: null, taxReference: null, onChainError: null, offRampError: null, taxError: null, retryable: false, lastError: null, createdAt: '2026-10-06', updatedAt: '2026-10-06' };
const invite: Review = { id: 'own', contractId: 'contract', reviewerId: 'client', revieweeId: 'freelancer', submitted: false, contentHidden: false, reported: false };
const published: Review = { ...invite, submitted: true, overall: 5, dimensions: { communication: 4, requirementsOrQuality: 3, timeliness: 2 }, comment: 'Published comment', submittedAt: '2026-10-06T00:00:00Z', publishedAt: '2026-10-06T00:00:00Z' };
const profile: Profile = { userId: 'freelancer', userType: 'FREELANCER', displayName: 'Freelancer', version: 1, languages: [], skills: [], verification: { email: 'UNVERIFIED', identity: 'UNVERIFIED', paymentMethod: 'UNVERIFIED', source: 'NOT_CONFIGURED' }, reputation: { completedContracts: 1, disputeCount: 0, reviewCount: 1, averageRating: 5, calculatedAt: '2026-10-06' } };
let host: HTMLDivElement, root: Root, rows: Review[], detail: AdminReviewDetail;
beforeEach(() => {
  host = document.createElement('div'); document.body.append(host); root = createRoot(host); rows = [structuredClone(invite)]; detail = { review: structuredClone(published), audit: [] };
  vi.spyOn(api, 'contractReviews').mockImplementation(async () => rows); vi.spyOn(api, 'adminReview').mockImplementation(async () => detail);
  vi.spyOn(api, 'reportedReviews').mockResolvedValue([published]); vi.spyOn(api, 'profile').mockResolvedValue(profile); vi.spyOn(api, 'publicReviews').mockResolvedValue([published]);
  vi.spyOn(api, 'portfolio').mockResolvedValue([]); vi.spyOn(api, 'job').mockResolvedValue(job); vi.spyOn(api, 'settlement').mockResolvedValue(settlement); vi.spyOn(api, 'cancellation').mockResolvedValue(null);
});
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); });
async function mount(node: ReactNode, path = '/') { await act(async () => root.render(<MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{node}</MemoryRouter>)); }
const button = (text: string) => [...host.querySelectorAll('button')].find(b => b.textContent === text)!;
async function click(text: string) { await act(async () => button(text).click()); }
async function value(selector: string, text: string) { const el = host.querySelector(selector)! as HTMLInputElement; const ctor = el.tagName === 'SELECT' ? HTMLSelectElement : HTMLTextAreaElement; await act(async () => { Object.getOwnPropertyDescriptor(ctor.prototype, 'value')!.set!.call(el, text); el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true })); }); }
async function submit(selector = '.review-composer') { await act(async () => host.querySelector(selector)!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); }
async function fill() { for (const label of ['Tổng thể', 'Giao tiếp', 'Chất lượng bàn giao', 'Đúng hạn']) await value('select[aria-label="' + label + '"]', '5'); await value('.review-composer textarea', 'Real review'); }
const workflow = (user = client, blocked = false) => <ContractReviews job={job} user={user} settlement={settlement} blocked={blocked} />;
const adminDetail = (user = admin) => <Routes><Route path="/admin/reviews/:reviewId" element={<AdminReview user={user} />} /></Routes>;
describe('Step 9 reviews and moderation', () => {
  it('renders own submitted review before publication without a composer', async () => { rows = [{ ...published, publishedAt: null }]; await mount(workflow()); expect(host.textContent).toContain('Published comment'); expect(host.textContent).toContain('chưa công bố'); expect(host.querySelector('.review-composer')).toBeNull(); });
  it('conceals counterpart scores/comment/timestamps even if unexpected masked fields arrive', async () => {
    rows = [{ ...published, reviewerId: 'freelancer', revieweeId: 'client', submitted: false, publishedAt: null, comment: 'MASKED SECRET', submittedAt: '2001-01-01T00:00:00Z' }]; await mount(workflow()); expect(host.textContent).not.toContain('MASKED SECRET'); expect(host.textContent).not.toContain('2001'); expect(host.textContent).not.toContain('5 / 5'); expect(host.textContent).not.toContain('chưa đánh giá'); expect(host.textContent).toContain('chưa được công bố');
  });
  it('sends exact review payload and reads authoritative submitted state', async () => {
    const post = vi.spyOn(api, 'submitReview').mockImplementation(async (_id, payload) => { const r = { ...invite, ...payload, submitted: true }; rows = [r]; return r; }); await mount(workflow()); await fill(); await submit();
    expect(post).toHaveBeenCalledWith('contract', { overall: 5, dimensions: { communication: 5, requirementsOrQuality: 5, timeliness: 5 }, comment: 'Real review' }); expect(host.querySelector('.review-composer')).toBeNull(); expect(host.textContent).toContain('Đã gửi đánh giá');
  });
  it('does not send incomplete/out-of-range form ratings', async () => { const post = vi.spyOn(api, 'submitReview'); await mount(workflow()); await submit(); expect(post).not.toHaveBeenCalled(); expect(host.textContent).toContain('bốn điểm nguyên'); });
  it('locks duplicate submit clicks', async () => { const post = vi.spyOn(api, 'submitReview').mockReturnValue(new Promise(() => {})); await mount(workflow()); await fill(); await act(async () => { const form = host.querySelector('form')!; form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); }); expect(post).toHaveBeenCalledOnce(); });
  it('ambiguous POST reconciles GET and avoids replay if review was saved', async () => {
    const post = vi.spyOn(api, 'submitReview').mockImplementation(async () => { rows = [{ ...published, publishedAt: null }]; throw new ApiError('timeout', 0); }); await mount(workflow()); await fill(); await submit(); expect(api.contractReviews).toHaveBeenCalledTimes(2); expect(post).toHaveBeenCalledOnce(); expect(host.textContent).toContain('không gửi lại'); expect(host.querySelector('.review-composer')).toBeNull();
  });
  it('ambiguous unsaved review freezes exact payload and requires GET before replay', async () => {
    const post = vi.spyOn(api, 'submitReview').mockRejectedValueOnce(new ApiError('timeout', 0)).mockImplementation(async () => { rows = [published]; return published; }); await mount(workflow()); await fill(); await submit(); expect(button('Gửi đánh giá').disabled).toBe(true); await click('Thử lại bản đánh giá đã đối chiếu'); expect((host.querySelector('.review-composer textarea') as HTMLTextAreaElement).disabled).toBe(true); await submit(); expect(post.mock.calls[1]).toEqual(post.mock.calls[0]);
  });
  it('REVIEW_INELIGIBLE keeps safe state and refreshes job/financial truth', async () => {
    vi.spyOn(api, 'submitReview').mockRejectedValue(new ApiError('ineligible', 409, null, 'REVIEW_INELIGIBLE', 'request-id')); await mount(workflow()); await fill(); await submit(); expect(host.querySelector('.review-composer')).toBeNull(); expect(host.textContent).not.toContain('Đã gửi đánh giá'); expect(host.textContent).toContain('REVIEW_INELIGIBLE'); expect(api.job).toHaveBeenCalledWith('job'); expect(api.settlement).toHaveBeenCalledWith('contract');
  });
  it.each([true, false])('never enables composer without server invitation or while refund/cancel blocked (%s)', async blocked => { if (!blocked) rows = []; await mount(workflow(client, blocked)); expect(host.querySelector('.review-composer')).toBeNull(); });
  it('Admin cannot submit participant reviews', async () => { rows = [{ ...invite, reviewerId: 'admin' }]; await mount(workflow(admin)); expect(host.querySelector('.review-composer')).toBeNull(); });
  it('a nonparticipant cannot compose even if a malformed invitation names them', async () => { rows = [{ ...invite, reviewerId: 'outsider', revieweeId: 'client' }]; await mount(workflow({ ...client, id: 'outsider' })); expect(host.querySelector('.review-composer')).toBeNull(); });
  it('does not enable review for mismatched settlement identity', async () => { await mount(<ContractReviews job={job} user={client} settlement={{ ...settlement, milestoneId: 'other' }} />); expect(host.querySelector('.review-composer')).toBeNull(); });
  it('allows Freelancer two-sided review on its server invitation', async () => { rows = [{ ...invite, reviewerId: 'freelancer', revieweeId: 'client' }]; await mount(workflow(freelancer)); expect(host.querySelector('select[aria-label="Độ rõ ràng của yêu cầu"]')).not.toBeNull(); });
  it('public reviews render published content without invented deadline', async () => { await mount(<ReviewRecord review={published} user={client} />); expect(host.textContent).toContain('Published comment'); expect(host.textContent).toContain('Công bố'); expect(host.textContent).not.toContain('14 ngày'); });
  it('hidden public comment remains hidden while numeric rating remains', async () => { await mount(<ReviewRecord review={{ ...published, contentHidden: true }} user={freelancer} />); expect(host.textContent).not.toContain('Published comment'); expect(host.textContent).toContain('5 / 5'); expect(host.textContent).toContain('điểm số vẫn được giữ'); });
  it('report requires reviewee and published review', async () => { const refresh = vi.fn(); await mount(<ReviewReport review={published} user={client} onRefresh={refresh} />); expect(button('Báo cáo nhận xét')).toBeUndefined(); });
  it('report requires nonblank bounded reason and does not claim removal', async () => {
    const post = vi.spyOn(api, 'reportReview').mockResolvedValue({ ...published, reported: true }); const refresh = vi.fn().mockResolvedValue(undefined); await mount(<ReviewReport review={published} user={freelancer} onRefresh={refresh} />); await click('Báo cáo nhận xét'); await submit('.review-report form'); expect(post).not.toHaveBeenCalled(); await value('.review-report textarea', '  Inappropriate content  '); await submit('.review-report form'); expect(post).toHaveBeenCalledWith('contract', 'own', 'Inappropriate content'); expect(host.textContent).toContain('không tự ẩn nội dung'); expect(refresh).toHaveBeenCalledOnce();
  });
  it('rejects report reason over 2,000 without posting', async () => {
    const post = vi.spyOn(api, 'reportReview'); await mount(<ReviewReport review={published} user={freelancer} onRefresh={async () => {}} />); await click('Báo cáo nhận xét'); await value('.review-report textarea', 'x'.repeat(2001)); await submit('.review-report form'); expect(post).not.toHaveBeenCalled();
  });
  it('rejects moderation reason over 2,000 before confirmation', async () => {
    const post = vi.spyOn(api, 'moderateReview'); await mount(adminDetail(), '/admin/reviews/own'); await value('.review-composer textarea', 'x'.repeat(2001)); await submit(); expect(button('Xác nhận kiểm duyệt')).toBeUndefined(); expect(post).not.toHaveBeenCalled();
  });
  it('non-Admin cannot access queue or detail APIs', async () => { await mount(<><AdminReviews user={client} /><AdminReview user={client} /></>); expect(api.reportedReviews).not.toHaveBeenCalled(); expect(api.adminReview).not.toHaveBeenCalled(); expect(host.textContent).toContain('Không có quyền quản trị'); });
  it('trusted Admin sees reported queue and detail link', async () => { await mount(<AdminReviews user={admin} />); expect(host.querySelector('a[href="/admin/reviews/own"]')).not.toBeNull(); expect(api.reportedReviews).toHaveBeenCalledWith(20); });
  it.each(['HIDE_CONTENT', 'INVALIDATE'] as const)('requires reason and explicit %s confirmation, refetches reputation', async action => {
    const post = vi.spyOn(api, 'moderateReview').mockImplementation(async (_id, chosen, reason) => { detail = { review: { ...published, contentHidden: chosen === 'HIDE_CONTENT' }, audit: [{ action: chosen, reason, actorId: 'admin', beforeState: 'PUBLISHED', afterState: chosen === 'INVALIDATE' ? 'INVALIDATED' : 'HIDDEN', requestId: 'audit', createdAt: '2026-10-06' }] }; return detail.review; });
    await mount(adminDetail(), '/admin/reviews/own'); await submit(); expect(post).not.toHaveBeenCalled(); await value('.review-composer select', action); await value('.review-composer textarea', 'Policy reason'); await submit(); expect(post).not.toHaveBeenCalled(); expect(host.textContent).toContain(action === 'HIDE_CONTENT' ? 'Xác nhận ẩn nhận xét?' : 'Xác nhận loại đánh giá?'); await click('Xác nhận kiểm duyệt'); expect(post).toHaveBeenCalledWith('own', action, 'Policy reason'); expect(api.profile).toHaveBeenCalledWith('freelancer'); expect(api.publicReviews).toHaveBeenCalledWith('freelancer', 0);
    expect(host.textContent).toContain(action === 'HIDE_CONTENT' ? 'điểm số vẫn được giữ' : 'Đánh giá đã bị loại');
  });
  it('Admin contract participant cannot moderate', async () => { await mount(adminDetail({ ...client, authorities: ['ROLE_ADMIN'] }), '/admin/reviews/own'); expect(host.querySelector('.review-composer')).toBeNull(); expect(host.textContent).toContain('không thể kiểm duyệt'); });
  it('uncertain moderation does not repeat automatically and drops revoked cached access', async () => {
    const post = vi.spyOn(api, 'moderateReview').mockRejectedValue(new ApiError('timeout', 0)); await mount(adminDetail(), '/admin/reviews/own'); await value('.review-composer textarea', 'Reason'); await submit(); await click('Xác nhận kiểm duyệt'); expect(post).toHaveBeenCalledOnce(); expect(host.textContent).toContain('không tự gửi lại'); vi.mocked(api.adminReview).mockRejectedValue(new ApiError('forbidden', 403)); await click('Đọc lại hồ sơ kiểm duyệt'); expect(host.textContent).not.toContain('Published comment');
  });
  it('invalidated review disappears after public list and reputation refetch', async () => {
    await mount(<Routes><Route path="/profiles/:userId" element={<PublicProfile user={client} />} /></Routes>, '/profiles/freelancer'); expect(host.textContent).toContain('Published comment'); vi.mocked(api.publicReviews).mockResolvedValue([]); vi.mocked(api.profile).mockResolvedValue({ ...profile, reputation: { ...profile.reputation, reviewCount: 0, averageRating: null } }); await act(async () => window.dispatchEvent(new Event('freelax:rating-update'))); expect(host.textContent).not.toContain('Published comment'); expect(host.textContent).toContain('Chưa có đánh giá công bố');
  });
  it('Activity rating notifications point to actual job review region and keep fallback', async () => {
    vi.spyOn(api, 'notifications').mockResolvedValue({ currentPage: 0, pageSize: 10, totalPages: 1, totalElements: 3, data: ['REVIEW_INVITED', 'REVIEW_PUBLISHED', 'FUTURE_EVENT'].map((type, i) => ({ id: String(i), type, jobId: 'job', title: 'Update', message: 'Server notice', read: true, amount: null, createdAt: null })) }); await mount(<Activity />);
    expect(host.querySelectorAll('a[href="/work/job#contract-reviews"]').length).toBe(2); expect(host.textContent).toContain('Cập nhật từ Marketplace'); expect(host.querySelector('a[href="/work/job"]')).not.toBeNull();
  });
  it('Overview shows review attention only for a server invitation, not submitted review', async () => {
    vi.spyOn(api, 'myJobs').mockResolvedValue({ currentPage: 0, pageSize: 8, totalPages: 1, totalElements: 1, data: [job] }); await mount(<Overview user={client} />); expect(host.textContent).toContain('Đánh giá đối tác'); rows = [published]; await act(async () => window.dispatchEvent(new Event('freelax:rating-update'))); expect(host.textContent).not.toContain('Đánh giá đối tác');
  });
});

describe('Completed review opportunity notification contract', () => {
  it('reports only proven opportunity, resets on read failure and does not fetch twice', async () => {
    const notify = vi.fn(); await mount(<ContractReviews job={job} user={client} settlement={settlement} onOpportunityChange={notify} />);
    expect(notify).toHaveBeenLastCalledWith(true); expect(api.contractReviews).toHaveBeenCalledOnce();
    vi.mocked(api.contractReviews).mockRejectedValue(new ApiError('Read failed', 503)); await click('Đọc lại đánh giá');
    expect(notify).toHaveBeenLastCalledWith(false); expect(host.querySelector('.review-composer')).toBeNull(); expect(api.contractReviews).toHaveBeenCalledTimes(2);
  });
  it('withdraws notification when blocked truth changes without another review read', async () => {
    const notify = vi.fn(); await mount(<ContractReviews job={job} user={client} settlement={settlement} onOpportunityChange={notify} />);
    await mount(<ContractReviews job={job} user={client} settlement={settlement} blocked onOpportunityChange={notify} />);
    expect(notify).toHaveBeenLastCalledWith(false); expect(host.querySelector('.review-composer')).toBeNull(); expect(api.contractReviews).toHaveBeenCalledOnce();
  });
  it('cannot reuse prior user read proof while a different reviewer is loading', async () => {
    rows = [invite, { ...invite, id: 'other-invite', reviewerId: freelancer.id, revieweeId: client.id }];
    const notify = vi.fn(); await mount(<ContractReviews job={job} user={client} settlement={settlement} onOpportunityChange={notify} />); expect(notify).toHaveBeenLastCalledWith(true);
    vi.mocked(api.contractReviews).mockReturnValue(new Promise(() => {}));
    await mount(<ContractReviews job={job} user={freelancer} settlement={settlement} onOpportunityChange={notify} />);
    expect(notify).toHaveBeenLastCalledWith(false); expect(host.querySelector('.review-composer')).toBeNull(); expect(api.contractReviews).toHaveBeenCalledTimes(2);
  });
  it('resets parent opportunity on unmount', async () => {
    const notify = vi.fn(); await mount(<ContractReviews job={job} user={client} settlement={settlement} onOpportunityChange={notify} />);
    expect(notify).toHaveBeenLastCalledWith(true); await act(async () => root.unmount()); root = createRoot(host); expect(notify).toHaveBeenLastCalledWith(false);
  });
  it('reports false while re-reading invitation evidence, then restores only the server result', async () => {
    const notify = vi.fn(); await mount(<ContractReviews job={job} user={client} settlement={settlement} onOpportunityChange={notify} />);
    let finish!: (rows: Review[]) => void; vi.mocked(api.contractReviews).mockReturnValue(new Promise(resolve => { finish = resolve; }));
    await click('Đọc lại đánh giá'); expect(notify).toHaveBeenLastCalledWith(false);
    await act(async () => finish([{ ...invite, submitted: true, publishedAt: null }])); expect(notify).toHaveBeenLastCalledWith(false); expect(host.querySelector('.review-composer')).toBeNull();
  });
});
