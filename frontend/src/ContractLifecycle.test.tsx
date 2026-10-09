// @vitest-environment jsdom
import { act, useState } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
import { WorkLifecycle } from './WorkLifecycle';
import { ContractLifecycle, ReviewTiming } from './ContractLifecycle';
import { FinanceHome } from './Finance';
import { attemptScope, localInstant, saveAttempt, smallReview, validateSubmission } from './workflowContracts';
import type { ContractCancellationRecord, ContractSettlement, ContractSubmission, ContractSummary, Job, Review, SubmissionPayload, User } from './types';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const client: User = { id: 'client', displayName: 'Client', email: 'c@example.test', userType: 'CLIENT' };
const freelancer: User = { ...client, id: 'freelancer', userType: 'FREELANCER' };
const contract: ContractSummary = { id: 'contract', milestoneId: 'milestone', status: 'ACTIVE', milestoneStatus: 'FUNDED', amount: '500.00', currency: 'USD', deliveryDueAt: '2030-01-01T00:00:00Z', reviewWindowHours: 48, maxRevisions: 2, revisionsUsed: 0,
  deliverables: [{ id: 'deliverable', title: 'Source code', description: 'Source', required: true, order: 0 }], acceptanceCriteria: [{ id: 'criterion', title: 'Responsive', description: 'Works on mobile', required: true, order: 0 }] };
const working: Job = { id: 'job', title: 'Work', description: 'Brief', budgetUsd: 999, status: 'IN_PROGRESS', clientUserId: client.id, freelancerId: freelancer.id, createdAt: null, contract };
const review: Job = { ...working, status: 'SUBMITTED_FOR_REVIEW', contract: { ...contract, status: 'UNDER_REVIEW', milestoneStatus: 'SUBMITTED' } };
const v1: ContractSubmission = { id: 'v1', contractId: contract.id, milestoneId: 'milestone', freelancerId: freelancer.id, version: 1, status: 'SUBMITTED', summary: 'First delivery', submittedAt: '2026-10-05T00:00:00Z', submittedLate: false, reviewDueAt: '2030-01-03T00:00:00Z', reviewGraceDueAt: null, reviewedAutomatically: false, disputeId: null, reviewerFeedback: null, reviewCriterionIds: [], reviewDeliverableIds: [], deliverables: [{ requirementId: 'deliverable', description: 'Source', url: 'https://example.test/source' }], acceptanceEvidence: [] };
const payload: SubmissionPayload = { summary: 'Delivered', deliverables: [{ requirementId: 'deliverable', url: 'https://example.test/source', description: 'Source' }], acceptanceEvidence: [] };
let host: HTMLDivElement, root: Root;
beforeEach(() => {
  vi.spyOn(api, 'contractReviews').mockResolvedValue([]);
  sessionStorage.clear(); host = document.createElement('div'); document.body.append(host); root = createRoot(host);
  vi.spyOn(api, 'contractSubmissions').mockResolvedValue([]); vi.spyOn(api, 'job').mockResolvedValue(working);
  vi.spyOn(api, 'cancellation').mockResolvedValue(null); vi.spyOn(api, 'settlement').mockResolvedValue(null);
  vi.spyOn(api, 'dispute').mockResolvedValue(null);
  vi.spyOn(api, 'funding').mockResolvedValue({ fundingTransactionId: 'funding', fundingStatus: 'SUCCEEDED', simulation: true, providerReference: null, nextAction: 'WAIT', retryAfterSeconds: null });
});
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.useRealTimers(); });
function Harness({ job, user }: { job: Job; user: User }) { const [current, setJob] = useState(job); return <WorkLifecycle job={current} user={user} onJobUpdated={setJob}><p>Job document</p></WorkLifecycle>; }
async function mount(job = working, user = freelancer) { await act(async () => root.render(<MemoryRouter><Harness job={job} user={user} /></MemoryRouter>)); }
const button = (text: string) => [...host.querySelectorAll('button')].find(b => b.textContent === text);
async function click(text: string) { expect(button(text)).toBeTruthy(); await act(async () => button(text)!.click()); }
async function input(selector: string, value: string) {
  const el = host.querySelector(selector) as HTMLInputElement | HTMLTextAreaElement;
  expect(el).toBeTruthy(); await act(async () => { Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value')!.set!.call(el, value); el.dispatchEvent(new Event('input', { bubbles: true })); });
}
async function submit(selector = '.work-composer form') { await act(async () => host.querySelector(selector)!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); }
async function fill() { await input('.work-composer textarea', 'Delivered'); await act(async () => (host.querySelector('.evidence-input input[type=checkbox]') as HTMLInputElement).click()); await input('.evidence-input input[type=url]', 'https://example.test/source'); }

describe('unified contract lifecycle', () => {
  it('uses the wallet-signed Solana escrow path, not the simulated ledger or legacy cancellation', async () => {
    const unified: Job = { ...working, contract: { ...contract, paymentRail: 'UNIFIED_USDC_PAYOUT' } };
    const escrow = vi.spyOn(api, 'escrowFunding').mockResolvedValue({ paymentRail: 'UNIFIED_USDC_PAYOUT', status: 'Funded',
      settlementStatus: 'FUNDED', escrowAddress: 'escrow-pda', clientWallet: 'client-wallet', freelancerWallet: 'freelancer-wallet',
      mint: 'mint', amountBaseUnits: '500000000', vaultAddress: 'vault', vaultBalanceBaseUnits: '500000000',
      deliveryDueAt: '1893456000' } as never);
    vi.spyOn(api, 'pendingMutualRefund').mockResolvedValue(null);
    vi.mocked(api.job).mockResolvedValue(unified);

    await mount(unified, client);

    expect(escrow).toHaveBeenCalledWith('contract', 'milestone');
    expect(host.querySelector('[aria-label="Trạng thái escrow Solana"]')).toBeTruthy();
    expect(host.querySelector('[aria-label="Hoàn tiền escrow theo thỏa thuận"]')).toBeTruthy();
    expect(api.cancellation).not.toHaveBeenCalled();
    expect(api.settlement).not.toHaveBeenCalled();
  });
});

describe('unified contract awaiting funding', () => {
  it('treats a missing escrow record as no vault yet instead of a reconciliation failure', async () => {
    const pending: Job = { ...working, status: 'AWAITING_PAYMENT', contract: { ...contract, status: 'PENDING_FUNDING',
      milestoneStatus: 'PENDING_FUNDING', paymentRail: 'UNIFIED_USDC_PAYOUT' } };
    vi.spyOn(api, 'escrowFunding').mockRejectedValue(new ApiError('not found', 404));
    vi.spyOn(api, 'pendingMutualRefund').mockResolvedValue(null);
    vi.mocked(api.job).mockResolvedValue(pending);

    await mount(pending, client);

    expect(host.textContent).not.toContain('Không thể đối chiếu lịch sử');
    expect(host.querySelector('[aria-label="Trạng thái escrow Solana"]')).toBeNull();
  });
});

describe('P06.4B contract workflow', () => {
  it('uses structured contract API and server version, never legacy mutation', async () => {
    const legacy = vi.spyOn(api, 'submitWork'); const returned = { ...v1, version: 7 };
    vi.spyOn(api, 'submitContract').mockResolvedValue(returned); vi.mocked(api.contractSubmissions).mockResolvedValueOnce([]).mockResolvedValue([returned]); vi.mocked(api.job).mockResolvedValue(review);
    await mount(); await fill(); await submit(); expect(legacy).not.toHaveBeenCalled(); expect(api.submitContract).toHaveBeenCalledWith('contract', expect.any(String), { summary: 'Delivered', deliverables: [{ requirementId: 'deliverable', url: 'https://example.test/source', description: '' }], acceptanceEvidence: [] }); expect(host.querySelector('.latest-submission h2')?.textContent).toBe('Bản bàn giao #7');
  });
  it('requires evidence and rejects HTTP without POST', async () => {
    const post = vi.spyOn(api, 'submitContract'); await mount(); await input('.work-composer textarea', 'Delivered'); await submit(); expect(host.textContent).toContain('ít nhất một bằng chứng');
    await fill(); await input('.evidence-input input[type=url]', 'http://example.test'); await submit(); expect(host.textContent).toContain('HTTPS'); expect(post).not.toHaveBeenCalled();
  });
  it('freezes exact payload/key through timeout, reconciles before same-key replay', async () => {
    const post = vi.spyOn(api, 'submitContract').mockRejectedValue(new ApiError('timeout', 0)); await mount(); await fill(); await submit();
    const first = post.mock.calls[0]; expect((host.querySelector('.work-composer textarea') as HTMLTextAreaElement).disabled).toBe(true);
    await submit(); expect(post.mock.calls[1]).toEqual(first); expect(api.contractSubmissions).toHaveBeenCalledTimes(4);
  });
  it('reload recovers exact draft and avoids duplicate if backend already recorded it', async () => {
    saveAttempt(attemptScope('submit', freelancer.id, 'contract', 'milestone'), { key: 'same-key', payload, baselineVersion: 0 });
    vi.mocked(api.contractSubmissions).mockResolvedValue([v1]); vi.mocked(api.job).mockResolvedValue(review); const post = vi.spyOn(api, 'submitContract');
    await mount(); expect(post).not.toHaveBeenCalled(); expect(host.querySelector('.latest-submission h2')?.textContent).toContain('#1'); expect(sessionStorage.length).toBe(0);
  });
  it('same-key payload conflict retains recovery state and does not auto resend', async () => {
    const post = vi.spyOn(api, 'submitContract').mockRejectedValue(new ApiError('conflict', 409, 4026)); await mount(); await fill(); await submit(); expect(post).toHaveBeenCalledTimes(1); expect(sessionStorage.length).toBe(1); expect(host.textContent).toContain('xung đột nội dung');
  });
  it('prevents double submission while request is in flight', async () => {
    const post = vi.spyOn(api, 'submitContract').mockReturnValue(new Promise(() => {})); await mount(); await fill(); await act(async () => { const f = host.querySelector('form')!; f.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); f.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); }); expect(post).toHaveBeenCalledTimes(1);
  });
  it('keeps latest primary, older feedback/version in collapsed ledger, safe evidence and late marker', async () => {
    vi.mocked(api.contractSubmissions).mockResolvedValue([{ ...v1, status: 'REVISION_REQUESTED', reviewerFeedback: 'Fix mobile', reviewCriterionIds: ['criterion'] }, { ...v1, id: 'v2', version: 2, submittedLate: true }]); await mount(review, client);
    expect(host.querySelector('.latest-submission h2')?.textContent).toContain('#2'); expect(host.querySelector('.ledger-row')?.hasAttribute('open')).toBe(false); expect(host.querySelector('.ledger-row')?.textContent).toContain('Fix mobile'); expect(host.textContent).toContain('Nộp muộn'); expect(host.querySelector('a[target=_blank]')?.getAttribute('rel')).toBe('noopener noreferrer');
  });
  it('Client cannot submit and Freelancer cannot review', async () => { vi.mocked(api.contractSubmissions).mockResolvedValue([v1]); await mount(review, freelancer); expect(button('Duyệt bàn giao')).toBeUndefined(); expect(host.querySelector('.work-composer')).toBeNull(); });
  it('Client working state has no submission form', async () => { await mount(working, client); expect(host.querySelector('.work-composer')).toBeNull(); expect(host.textContent).toContain('Đang chờ Freelancer bàn giao'); });
  it('Freelancer revision shows related feedback and resubmits as a new server-owned version', async () => {
    const old = { ...v1, status: 'REVISION_REQUESTED' as const, reviewerFeedback: 'Fix mobile', reviewCriterionIds: ['criterion'] };
    const revisedJob = { ...working, status: 'REVISION_REQUESTED', contract: { ...contract, status: 'REVISION', milestoneStatus: 'IN_PROGRESS', revisionsUsed: 1 } };
    vi.mocked(api.contractSubmissions).mockResolvedValueOnce([old]).mockResolvedValue([old, { ...v1, id: 'v2', version: 2 }]); vi.mocked(api.job).mockResolvedValue(review); vi.spyOn(api, 'submitContract').mockResolvedValue({ ...v1, id: 'v2', version: 2 });
    await mount(revisedJob); expect(host.querySelector('.feedback-document')?.textContent).toContain('Fix mobile'); expect(host.querySelector('.feedback-document')?.textContent).toContain('Responsive'); await fill(); await submit();
    expect(host.querySelector('.latest-submission h2')?.textContent).toContain('#2'); expect(host.querySelector('.ledger-row')?.textContent).toContain('Fix mobile');
  });
  it('does not read submissions or render actions for outsider', async () => { await mount(working, { ...freelancer, id: 'outsider' }); expect(api.contractSubmissions).not.toHaveBeenCalled(); expect(host.textContent).toBe(''); });
  it('revision requires feedback and related snapshot ID, sends exact decision', async () => {
    vi.mocked(api.contractSubmissions).mockResolvedValue([v1]); const post = vi.spyOn(api, 'decideSubmission').mockResolvedValue({ ...v1, status: 'REVISION_REQUESTED' }); await mount(review, client); await click('Yêu cầu chỉnh sửa'); await submit('.decision-form'); expect(post).not.toHaveBeenCalled();
    await input('.decision-form textarea', 'Fix mobile'); await submit('.decision-form'); expect(post).not.toHaveBeenCalled();
    await act(async () => (host.querySelector('.decision-form input[type=checkbox]') as HTMLInputElement).click()); await submit('.decision-form'); expect(post).toHaveBeenCalledWith('contract', 'v1', { decision: 'REQUEST_REVISION', feedback: 'Fix mobile', criterionIds: ['criterion'], deliverableIds: [] });
  });
  it('revision limit disables revision while approve/dispute remain valid', async () => {
    vi.mocked(api.contractSubmissions).mockResolvedValue([v1]); await mount({ ...review, contract: { ...review.contract!, revisionsUsed: 2 } }, client); expect(button('Yêu cầu chỉnh sửa')?.disabled).toBe(true); expect(button('Duyệt bàn giao')?.disabled).toBe(false); expect(button('Mở tranh chấp')?.disabled).toBe(false);
  });
  it.each([4027, 4028])('conflict %s refreshes and removes stale decisions', async code => {
    vi.mocked(api.contractSubmissions).mockResolvedValueOnce([v1]).mockResolvedValue([{ ...v1, status: 'APPROVED' }]); vi.mocked(api.job).mockResolvedValue({ ...review, contract: { ...review.contract!, milestoneStatus: 'RELEASE_PENDING' } }); const post = vi.spyOn(api, 'decideSubmission').mockRejectedValue(new ApiError('changed', 409, code));
    await mount(review, client); await click('Duyệt bàn giao'); await submit('.decision-form'); expect(post).toHaveBeenCalledTimes(1); expect(api.job).toHaveBeenCalled(); expect(button('Duyệt bàn giao')).toBeUndefined(); expect(host.textContent).toContain('đang đối soát release'); expect(host.textContent).not.toContain('payout completed');
  });
  it('approval confirmation prevents double decisions', async () => {
    vi.mocked(api.contractSubmissions).mockResolvedValue([v1]); const post = vi.spyOn(api, 'decideSubmission').mockReturnValue(new Promise(() => {})); await mount(review, client); await click('Duyệt bàn giao'); expect(host.textContent).toContain('chưa giải ngân'); await act(async () => { const f = host.querySelector('.decision-form')!; f.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); f.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); }); expect(post).toHaveBeenCalledTimes(1);
  });
  it('minimal dispute validates description and freezes all incompatible controls', async () => {
    const disputed = { ...v1, status: 'DISPUTED' as const, disputeId: 'dispute' }; vi.mocked(api.contractSubmissions).mockResolvedValueOnce([v1]).mockResolvedValue([disputed]); vi.mocked(api.job).mockResolvedValue({ ...review, contract: { ...review.contract!, status: 'DISPUTED', milestoneStatus: 'DISPUTED' } }); const post = vi.spyOn(api, 'decideSubmission').mockResolvedValue(disputed);
    await mount(review, client); await click('Mở tranh chấp'); await submit('.decision-form'); expect(post).not.toHaveBeenCalled(); await input('.decision-form textarea', 'Does not meet scope'); await submit('.decision-form'); expect(post).toHaveBeenCalledWith('contract', 'v1', { decision: 'OPEN_DISPUTE', reasonCode: 'QUALITY', description: 'Does not meet scope' }); expect(host.textContent).toContain('Đang chờ Admin'); expect(host.querySelector('.review-action')).toBeNull();
  });
  it('history failure preserves document/ownership and prevents unverified mutation', async () => {
    vi.mocked(api.contractSubmissions).mockRejectedValue(new ApiError('down', 503)); await mount(); expect(host.textContent).toContain('Job document'); expect(host.querySelector('.ownership-band')).not.toBeNull(); expect(button('Gửi bàn giao')).toBeUndefined(); expect(host.querySelector('[role=alert]')).not.toBeNull();
  });
  it('unfunded Finance reads funding without legacy payment-status or tax calls', async () => {
    const pendingJob = { ...working, status: 'AWAITING_PAYMENT', contract: { ...contract, status: 'PENDING_FUNDING', milestoneStatus: 'PENDING_FUNDING' } }; vi.mocked(api.job).mockResolvedValue(pendingJob); vi.spyOn(api, 'funding').mockResolvedValue(null); vi.spyOn(api, 'clientBank').mockResolvedValue({ paymentMethodId: 'BANK_ACCOUNT_ON_FILE', ready: false, bankCode: null, maskedAccountNumber: null }); const old = vi.spyOn(api, 'paymentStatus'); const tax = vi.spyOn(api, 'taxRecordForJob');
    await act(async () => root.render(<MemoryRouter initialEntries={['/finance?jobId=job']}><FinanceHome user={client} /></MemoryRouter>)); expect(old).not.toHaveBeenCalled(); expect(tax).not.toHaveBeenCalled(); expect(api.funding).toHaveBeenCalled();
  });
});

describe('P06.4 evidence validation', () => {
  it('accepts structured evidence', () => expect(validateSubmission(payload, contract)).toBeNull());
  it('accepts criterion note without URL', () => expect(validateSubmission({ ...payload, deliverables: [], acceptanceEvidence: [{ criterionId: 'criterion', note: 'Verified mobile', url: '' }] }, contract)).toBeNull());
  it('rejects foreign and duplicate deliverable IDs', () => { expect(validateSubmission({ ...payload, deliverables: [{ ...payload.deliverables[0], requirementId: 'foreign' }] }, contract)).toContain('thuộc hợp đồng'); expect(validateSubmission({ ...payload, deliverables: [payload.deliverables[0], payload.deliverables[0]] }, contract)).toContain('trùng'); });
  it('rejects foreign/duplicate criteria and missing evidence', () => { const item = { criterionId: 'criterion', note: 'Verified', url: '' }; expect(validateSubmission({ ...payload, acceptanceEvidence: [item, item] }, contract)).toContain('trùng'); expect(validateSubmission({ ...payload, acceptanceEvidence: [{ ...item, criterionId: 'foreign' }] }, contract)).toContain('thuộc hợp đồng'); expect(validateSubmission({ ...payload, deliverables: [], acceptanceEvidence: [] }, contract)).toContain('ít nhất'); });
  it.each(['http://example.test', 'javascript:alert(1)', 'https://user:pass@example.test'])('rejects unsafe evidence %s', url => expect(validateSubmission({ ...payload, deliverables: [{ ...payload.deliverables[0], url }] }, contract)).toContain('HTTPS'));
});

describe('P06.4C server review timing', () => {
  it('uses exact 500.00 / 500.01 policy boundary', () => { expect(smallReview('500.00')).toBe(true); expect(smallReview('500.01')).toBe(false); });
  it('formats UTC instants in user locale without changing them', () => expect(localInstant('2026-10-05T00:00:00Z')).toBe(new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date('2026-10-05T00:00:00Z'))));
  it('shows actual custom window, null/present grace and automatic source only from server', async () => {
    await act(async () => root.render(<ReviewTiming submission={v1} contract={contract} now={Date.now()} />)); expect(host.textContent).toContain('48 giờ'); expect(host.textContent).not.toContain('Hạn gia hạn');
    await act(async () => root.render(<ReviewTiming submission={{ ...v1, reviewGraceDueAt: '2030-01-04T00:00:00Z' }} contract={{ ...contract, amount: '500.01' }} now={Date.now()} />)); expect(host.textContent).toContain('Hạn gia hạn'); expect(host.textContent).toContain('24 giờ');
  });
  it('deadline boundary only refetches and waits for server, cleans timers', async () => {
    vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-05T00:00:00Z')); const due = { ...v1, reviewDueAt: '2026-10-05T00:00:02Z' }; vi.mocked(api.contractSubmissions).mockResolvedValue([due]); vi.mocked(api.job).mockResolvedValue(review); const approve = vi.spyOn(api, 'decideSubmission');
    await mount(review, client); await act(async () => { await vi.advanceTimersByTimeAsync(3000); }); expect(api.job).toHaveBeenCalledTimes(1); expect(host.textContent).toContain('Đang chờ máy chủ xử lý'); expect(approve).not.toHaveBeenCalled();
    await act(async () => root.unmount()); root = createRoot(host); const count = vi.mocked(api.job).mock.calls.length; await act(async () => { await vi.advanceTimersByTimeAsync(60000); }); expect(api.job).toHaveBeenCalledTimes(count);
  });
  it('focus refetches authoritative state and shows server auto-approved result', async () => {
    vi.mocked(api.contractSubmissions).mockResolvedValueOnce([v1]).mockResolvedValue([{ ...v1, status: 'APPROVED', reviewedAutomatically: true }]); vi.mocked(api.job).mockResolvedValue({ ...review, contract: { ...review.contract!, milestoneStatus: 'RELEASE_PENDING' } }); await mount(review, client);
    await act(async () => window.dispatchEvent(new Event('focus'))); expect(api.job).toHaveBeenCalled(); expect(host.textContent).toContain('Máy chủ tự động duyệt'); expect(host.querySelector('.review-action')).toBeNull();
  });
  it('matching notification triggers refetch, not approval', async () => {
    vi.mocked(api.contractSubmissions).mockResolvedValue([v1]); vi.mocked(api.job).mockResolvedValue(review); const decision = vi.spyOn(api, 'decideSubmission'); await mount(review, client);
    await act(async () => window.dispatchEvent(new CustomEvent('freelax:review-update', { detail: { jobId: 'job' } }))); expect(api.job).toHaveBeenCalledTimes(1); expect(decision).not.toHaveBeenCalled();
  });
  it('grace boundary triggers a bounded authoritative refetch', async () => {
    vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-05T00:00:00Z'));
    vi.mocked(api.contractSubmissions).mockResolvedValue([{ ...v1, reviewDueAt: '2026-10-04T00:00:00Z', reviewGraceDueAt: '2026-10-05T00:00:05Z' }]); vi.mocked(api.job).mockResolvedValue(review);
    await mount(review, client); await act(async () => { await vi.advanceTimersByTimeAsync(6000); }); expect(api.job).toHaveBeenCalledTimes(2); expect(host.textContent).toContain('Đang chờ máy chủ xử lý');
  });
});

const completedJob: Job = { ...working, status: 'COMPLETED', contract: { ...contract, status: 'COMPLETED', milestoneStatus: 'RELEASED' } };
const confirmedRelease: ContractSettlement = { contractId: 'contract', jobId: 'job', milestoneId: 'milestone', amount: 500, currency: 'USD', moneyStatus: 'SUCCEEDED', simulation: true, onChainStatus: 'NOT_STARTED', offRampStatus: 'NOT_STARTED', taxStatus: 'NOT_STARTED', retryable: false, releaseReference: 'release', onChainReference: null, offRampReference: null, taxReference: null, lastError: null, onChainError: null, offRampError: null, taxError: null, createdAt: '2026-10-07T00:00:00Z', updatedAt: '2026-10-07T00:00:00Z' };
const invitation: Review = { id: 'invitation', contractId: 'contract', reviewerId: client.id, revieweeId: freelancer.id, submitted: false, contentHidden: false, reported: false };
const cancellationRecord: ContractCancellationRecord = { cancellationId: 'cancellation', contractId: 'contract', milestoneId: 'milestone', cancellationStatus: 'REFUND_PENDING', refundStatus: 'PROCESSING', refundReference: null, simulation: true, requestedBy: client.id, decidedBy: freelancer.id, reasonCode: 'MUTUAL_CANCELLATION', reason: 'Real reason', amount: '500', currency: 'USD', requestedAt: '2026-10-07T00:00:00Z', decidedAt: null, updatedAt: '2026-10-07T00:00:00Z', retryable: false, lastError: null, allowedActions: [] };
function reviewReads(user = client) {
  vi.mocked(api.job).mockResolvedValue(completedJob); vi.mocked(api.settlement).mockResolvedValue(confirmedRelease);
  vi.mocked(api.contractSubmissions).mockResolvedValue([{ ...v1, status: 'APPROVED' }]);
  vi.mocked(api.contractReviews).mockResolvedValue([{ ...invitation, reviewerId: user.id, revieweeId: user.id === client.id ? freelancer.id : client.id }]);
}
const callout = () => host.querySelector('.completed-review-callout');
async function fillRating(user = client) {
  const labels = ['Tổng thể', 'Giao tiếp', user.id === client.id ? 'Chất lượng bàn giao' : 'Độ rõ ràng của yêu cầu', 'Đúng hạn'];
  for (const label of labels) {
    const el = host.querySelector('select[aria-label="' + label + '"]')!;
    await act(async () => { Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!.call(el, '4'); el.dispatchEvent(new Event('change', { bubbles: true })); });
  }
  await input('.review-composer textarea', 'Optional post-completion feedback');
}

describe('Pre-P06.7 completed Job review entry point', () => {
  it.each([client, freelancer])('surfaces one eligible $userType entry point high in the completed workflow', async user => {
    reviewReads(user); await mount(completedJob, user);
    const counterpart = user.id === client.id ? 'Freelancer' : 'Client';
    expect(callout()?.textContent).toContain('Chia sẻ đánh giá về ' + counterpart); expect(callout()?.textContent).toContain('Bạn có thể');
    expect(callout()?.querySelector('a')?.textContent).toContain('Đánh giá ' + counterpart); expect(callout()?.querySelector('a')?.getAttribute('href')).toBe('#contract-reviews');
    expect(callout()?.previousElementSibling?.className).toBe('ownership-band'); expect(host.querySelectorAll('#contract-reviews')).toHaveLength(1); expect(host.querySelectorAll('.review-composer')).toHaveLength(1);
    expect(host.querySelector('select[aria-label="' + (user.id === client.id ? 'Chất lượng bàn giao' : 'Độ rõ ràng của yêu cầu') + '"]')).toBeTruthy();
    expect(api.contractReviews).toHaveBeenCalledExactlyOnceWith('contract'); expect(callout()?.querySelectorAll('img')).toHaveLength(0);
    expect(callout()?.textContent).not.toMatch(/Đánh giá để hoàn tất|Cần đánh giá để nhận tiền|Bắt buộc|5.star/);
  });
  it('does not advertise an opportunity before review rows load', async () => {
    reviewReads(); let resolve!: (rows: Review[]) => void; vi.mocked(api.contractReviews).mockReturnValue(new Promise(r => { resolve = r; }));
    await mount(completedJob, client); expect(callout()).toBeNull(); expect(host.querySelector('.review-composer')).toBeNull();
    await act(async () => resolve([invitation])); expect(callout()).toBeTruthy(); expect(api.contractReviews).toHaveBeenCalledOnce();
  });
  it('does not infer invitation from completion/release/settlement success', async () => {
    reviewReads(); vi.mocked(api.contractReviews).mockResolvedValue([]); await mount(completedJob, client); expect(callout()).toBeNull(); expect(host.textContent).toContain('Chưa có lời mời đánh giá');
  });
  it.each([
    ['job', { ...completedJob, status: 'IN_PROGRESS' }],
    ['contract', { ...completedJob, contract: { ...completedJob.contract!, status: 'ACTIVE' } }],
    ['milestone', { ...completedJob, contract: { ...completedJob.contract!, milestoneStatus: 'RELEASE_PENDING' } }],
  ])('does not infer readiness without completed %s truth', async (_, current) => {
    reviewReads(); await mount(current as Job, client); expect(callout()).toBeNull(); expect(host.querySelector('.review-composer')).toBeNull();
  });
  it('does not infer valid settlement from RELEASED alone', async () => {
    reviewReads(); vi.mocked(api.settlement).mockResolvedValue(null); await mount(completedJob, client); expect(callout()).toBeNull();
  });
  it.each(['PENDING', 'PROCESSING', 'FAILED_RETRYABLE', 'FAILED', 'UNKNOWN'] as const)('withholds entry point when release moneyStatus is %s', async moneyStatus => {
    reviewReads(); vi.mocked(api.settlement).mockResolvedValue({ ...confirmedRelease, moneyStatus }); await mount(completedJob, client); expect(callout()).toBeNull(); expect(host.querySelector('.review-composer')).toBeNull();
  });
  it.each(['contractId', 'jobId', 'milestoneId'] as const)('withholds entry point for mismatched settlement %s', async field => {
    reviewReads(); vi.mocked(api.settlement).mockResolvedValue({ ...confirmedRelease, [field]: 'other' }); await mount(completedJob, client); expect(callout()).toBeNull();
  });
  it.each(['REFUND_PENDING', 'CANCELLED'] as const)('blocks invitation during %s cancellation state', async cancellationStatus => {
    reviewReads(); vi.mocked(api.cancellation).mockResolvedValue({ ...cancellationRecord, cancellationStatus }); await mount(completedJob, client); expect(callout()).toBeNull(); expect(host.querySelector('.review-composer')).toBeNull();
  });
  it('blocks review entry point and existing composer during an active dispute', async () => {
    reviewReads(); vi.mocked(api.dispute).mockResolvedValue({ disputeId: 'dispute', contractId: 'contract', milestoneId: 'milestone', jobId: 'job', submissionId: 'v1', openedBy: client.id, reasonCode: 'QUALITY', description: 'Real dispute', status: 'OPEN', openedAt: '2026-10-07', claimedBy: null, claimedAt: null, resolvedBy: null, decisionAt: null, resolvedAt: null, resolutionReason: null, refundStatus: null, refundReference: null, evidence: [] });
    await mount(completedJob, client); expect(callout()).toBeNull(); expect(host.querySelector('.review-composer')).toBeNull();
  });
  it.each(['settlement', 'contractReviews', 'cancellation'] as const)('does not claim eligibility after %s read failure', async read => {
    reviewReads(); vi.mocked(api[read]).mockRejectedValue(new ApiError('Unavailable', 503)); await mount(completedJob, client); expect(callout()).toBeNull(); expect(host.querySelector('.review-composer')).toBeNull();
  });
  it('does not advertise participant review to Admin', async () => {
    reviewReads(); await mount(completedJob, { ...client, authorities: ['ROLE_ADMIN'] }); expect(callout()).toBeNull();
  });
  it('does not advertise review to an unrelated user named in a malformed invitation', async () => {
    const outsider = { ...client, id: 'outsider' }; reviewReads(outsider); await mount(completedJob, outsider); expect(callout()).toBeNull(); expect(api.contractReviews).not.toHaveBeenCalled();
  });
  it('does not use visual userType to offer an action to the wrong participant', async () => {
    reviewReads(); await mount(completedJob, { ...client, userType: 'FREELANCER' }); expect(callout()).toBeNull(); expect(api.contractReviews).not.toHaveBeenCalled();
  });
  it('does not show a callout for an already-submitted own review even before publication', async () => {
    reviewReads(); vi.mocked(api.contractReviews).mockResolvedValue([{ ...invitation, submitted: true, publishedAt: null, overall: 4 }]); await mount(completedJob, client); expect(callout()).toBeNull(); expect(host.querySelector('.review-composer')).toBeNull(); expect(host.textContent).toContain('Đã gửi; chưa công bố');
  });
  it('removes the entry point only after authoritative submit response, preserving payload/publication semantics', async () => {
    reviewReads(); let resolve!: (row: Review) => void;
    const post = vi.spyOn(api, 'submitReview').mockReturnValue(new Promise(r => { resolve = r; }));
    await mount(completedJob, client); await fillRating(); await submit('.review-composer'); expect(callout()).toBeTruthy();
    expect(post).toHaveBeenCalledExactlyOnceWith('contract', { overall: 4, dimensions: { communication: 4, requirementsOrQuality: 4, timeliness: 4 }, comment: 'Optional post-completion feedback' });
    const saved = { ...invitation, submitted: true, overall: 4, publishedAt: null };
    vi.mocked(api.contractReviews).mockResolvedValue([saved]); await act(async () => resolve(saved));
    expect(callout()).toBeNull(); expect(host.querySelector('.review-composer')).toBeNull(); expect(host.textContent).toContain('Nội dung công khai theo trạng thái công bố của máy chủ'); expect(api.contractReviews).toHaveBeenCalledTimes(2);
    expect(host.querySelector('.ownership-band')?.textContent).toContain('Đã xác nhận release mô phỏng');
  });
  it('keeps uncertain unsaved review opportunity without changing completion or payment truth', async () => {
    reviewReads(); const post = vi.spyOn(api, 'submitReview').mockRejectedValue(new ApiError('Timeout', 0));
    await mount(completedJob, client); await fillRating(); await submit('.review-composer');
    expect(callout()).toBeTruthy(); expect(button('Gửi đánh giá')?.disabled).toBe(true); expect(host.textContent).toContain('Chưa xác nhận được kết quả'); expect(post).toHaveBeenCalledOnce(); expect(host.textContent).not.toContain('Đã gửi đánh giá');
    expect(host.querySelector('.ownership-band')?.textContent).toContain('Đã xác nhận release mô phỏng'); expect(api.contractReviews).toHaveBeenCalledTimes(2);
  });
  it('removes the callout when uncertain submit is reconciled as already saved', async () => {
    reviewReads(); vi.spyOn(api, 'submitReview').mockImplementation(async () => { vi.mocked(api.contractReviews).mockResolvedValue([{ ...invitation, submitted: true, publishedAt: null }]); throw new ApiError('Timeout', 0); });
    await mount(completedJob, client); await fillRating(); await submit('.review-composer'); expect(callout()).toBeNull(); expect(host.textContent).toContain('Máy chủ đã có đánh giá của bạn');
  });
  it('does not carry the prior contract opportunity into a new contract read', async () => {
    reviewReads(); await act(async () => root.render(<MemoryRouter><ContractLifecycle job={completedJob} user={client} onJobUpdated={vi.fn()} /></MemoryRouter>)); expect(callout()).toBeTruthy();
    vi.mocked(api.contractReviews).mockReturnValue(new Promise(() => {}));
    const otherJob = { ...completedJob, id: 'other-job', contract: { ...completedJob.contract!, id: 'other-contract' } };
    await act(async () => root.render(<MemoryRouter><ContractLifecycle job={otherJob} user={client} onJobUpdated={vi.fn()} /></MemoryRouter>)); expect(callout()).toBeNull(); expect(api.contractReviews).toHaveBeenLastCalledWith('other-contract');
  });
});
