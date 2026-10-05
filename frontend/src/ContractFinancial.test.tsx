// @vitest-environment jsdom
import { act, useState } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
import { WorkLifecycle } from './WorkLifecycle';
import { JobDetail } from './Workflow';
import { attemptScope, saveAttempt } from './workflowContracts';
import type { ContractCancellationRecord, ContractSettlement, ContractSubmission, Job, User } from './types';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const client: User = { id: 'client', displayName: 'Client', email: 'c@example.test', userType: 'CLIENT' };
const freelancer: User = { ...client, id: 'freelancer', userType: 'FREELANCER' };
const working: Job = { id: 'job', title: 'Delivery', description: 'Work document', status: 'IN_PROGRESS', budgetUsd: 500,
  clientUserId: client.id, freelancerId: freelancer.id, createdAt: null, checkoutOrderId: 'checkout',
  contract: { id: 'contract', milestoneId: 'milestone', status: 'ACTIVE', milestoneStatus: 'FUNDED', amount: '500.00', currency: 'USD',
    deliveryDueAt: null, reviewWindowHours: 72, maxRevisions: 2, revisionsUsed: 0, deliverables: [], acceptanceCriteria: [] } };
const preFunding: Job = { ...working, status: 'AWAITING_PAYMENT', checkoutOrderId: null,
  contract: { ...working.contract!, status: 'PENDING_FUNDING', milestoneStatus: 'PENDING_FUNDING' } };
const approved: ContractSubmission = { id: 'submission', contractId: 'contract', milestoneId: 'milestone', freelancerId: 'freelancer',
  version: 1, status: 'APPROVED', summary: 'Delivered work', submittedAt: '2026-10-05T00:00:00Z', submittedLate: false,
  reviewDueAt: null, reviewGraceDueAt: null, reviewedAutomatically: false, disputeId: null, reviewerFeedback: null,
  reviewCriterionIds: [], reviewDeliverableIds: [], deliverables: [], acceptanceEvidence: [] };
const releasing: Job = { ...working, status: 'SUBMITTED_FOR_REVIEW', contract: { ...working.contract!, status: 'UNDER_REVIEW', milestoneStatus: 'RELEASE_PENDING' } };
const completed: Job = { ...working, status: 'COMPLETED', contract: { ...working.contract!, status: 'COMPLETED', milestoneStatus: 'RELEASED' } };
const settlement: ContractSettlement = { contractId: 'contract', milestoneId: 'milestone', jobId: 'job', amount: 500.00, currency: 'USD', simulation: true,
  moneyStatus: 'SUCCEEDED', onChainStatus: 'UNKNOWN', offRampStatus: 'NOT_STARTED', taxStatus: 'NOT_STARTED', retryable: false,
  releaseReference: 'sim-release-reference', onChainReference: null, offRampReference: null, taxReference: null,
  lastError: null, onChainError: 'RECONCILIATION_PENDING', offRampError: null, taxError: null,
  createdAt: '2026-10-05T00:00:00', updatedAt: '2026-10-05T00:01:00' };
const proposal: ContractCancellationRecord = { cancellationId: 'cancel', contractId: 'contract', milestoneId: 'milestone',
  cancellationStatus: 'REQUESTED', refundStatus: null, refundReference: null, simulation: true,
  requestedBy: client.id, decidedBy: null, reasonCode: 'MUTUAL_CANCELLATION', reason: 'Scope no longer needed', amount: '500.00', currency: 'USD',
  requestedAt: '2026-10-05T00:00:00Z', decidedAt: null, updatedAt: '2026-10-05T00:00:00Z', retryable: false, lastError: null, allowedActions: ['ACCEPT', 'REJECT'] };
const refundPending: ContractCancellationRecord = { ...proposal, cancellationStatus: 'REFUND_PENDING', refundStatus: 'UNKNOWN', decidedBy: freelancer.id, retryable: true, allowedActions: [] };
const refunded: ContractCancellationRecord = { ...refundPending, cancellationStatus: 'CANCELLED', refundStatus: 'SUCCEEDED', refundReference: 'sim-refund-reference', retryable: false };
let host: HTMLDivElement, root: Root, serverJob: Job, cancellation: ContractCancellationRecord | null;
beforeEach(() => {
  sessionStorage.clear(); serverJob = working; cancellation = null;
  host = document.createElement('div'); document.body.append(host); root = createRoot(host);
  vi.spyOn(api, 'job').mockImplementation(async () => serverJob);
  vi.spyOn(api, 'contractSubmissions').mockResolvedValue([]);
  vi.spyOn(api, 'cancellation').mockImplementation(async () => cancellation);
  vi.spyOn(api, 'settlement').mockResolvedValue(null);
  vi.spyOn(api, 'funding').mockResolvedValue({ fundingTransactionId: 'funding', fundingStatus: 'SUCCEEDED', simulation: true, providerReference: null, nextAction: 'WAIT', retryAfterSeconds: null });
  vi.spyOn(api, 'clientBank').mockResolvedValue({ paymentMethodId: 'BANK_ACCOUNT_ON_FILE', ready: true, bankCode: 'BIDV', maskedAccountNumber: '••••1234' });
});
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.useRealTimers(); });
function Harness({ initial, user }: { initial: Job; user: User }) {
  const [job, setJob] = useState(initial);
  return <WorkLifecycle job={job} user={user} onJobUpdated={setJob}><p>Work document</p></WorkLifecycle>;
}
async function mount(job = working, user = client) {
  serverJob = job;
  await act(async () => root.render(<MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><Harness initial={job} user={user} /></MemoryRouter>));
}
const button = (label: string) => [...host.querySelectorAll('button')].find(item => item.textContent?.trim() === label);
async function click(label: string) { expect(button(label), label).toBeTruthy(); await act(async () => button(label)!.click()); }
async function reason(value = proposal.reason) {
  const el = host.querySelector('.cancellation-form textarea') as HTMLTextAreaElement;
  expect(el).toBeTruthy(); await act(async () => { Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(el, value); el.dispatchEvent(new Event('input', { bubbles: true })); });
}
async function request() { await act(async () => host.querySelector('.cancellation-form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); }

describe('P0 approval and settlement authority', () => {
  beforeEach(() => { vi.mocked(api.contractSubmissions).mockResolvedValue([approved]); });
  it('handles null settlement without treating approval/funding as release proof', async () => {
    await mount(releasing); expect(api.settlement).toHaveBeenCalledWith('contract');
    expect(host.textContent).toContain('Chưa có bản ghi release'); expect(host.textContent).not.toContain('Đã xác nhận release mô phỏng');
    expect(button('Duyệt bàn giao')).toBeUndefined();
  });
  it('does not call a failed settlement read an empty record', async () => {
    vi.mocked(api.settlement).mockRejectedValue(new Error('Read unavailable'));
    await mount(releasing);
    const document = host.querySelector('.settlement-document')!;
    expect(document.textContent).toContain('Chưa đối chiếu đầy đủ release/workflow');
    expect(document.textContent).not.toContain('Chưa có bản ghi release');
    expect(document.querySelector('.financial-status-done')).toBeNull();
    expect(button('Duyệt bàn giao')).toBeUndefined();
  });
  it('keeps APPROVED submission but leaves pending ownership when server confirms release', async () => {
    vi.mocked(api.settlement).mockResolvedValue(settlement); await mount(completed);
    expect(host.querySelector('.ownership-band')?.textContent).toContain('Đã xác nhận release mô phỏng');
    expect(host.querySelector('.ownership-band')?.textContent).not.toContain('đang đối soát release');
    expect(host.textContent).toContain('Đã duyệt'); expect(host.querySelector('.work-composer')).toBeNull();
  });
  it('uses completed contract/milestone truth even if release evidence is not yet available', async () => {
    await mount(completed); expect(host.querySelector('.ownership-band')?.textContent).toContain('Phần việc đã hoàn tất');
    expect(host.querySelector('.ownership-band')?.textContent).not.toContain('đang đối soát release');
    expect(host.textContent).toContain('Chưa có bản ghi release');
  });
  it('refetches workflow once when the first release read is already confirmed', async () => {
    vi.mocked(api.settlement).mockResolvedValue({ ...settlement, onChainStatus: 'SUCCEEDED', offRampStatus: 'SUCCEEDED', taxStatus: 'SUCCEEDED' });
    vi.mocked(api.job).mockResolvedValue(completed); await mount(releasing);
    expect(api.job).toHaveBeenCalled(); expect(host.querySelector('.ownership-band')?.textContent).toContain('Đã xác nhận release mô phỏng');
    expect(host.querySelector('.review-action')).toBeNull();
  });
  it('labels ledger simulation and does not equate tax creation with ACCEPTED', async () => {
    vi.mocked(api.settlement).mockResolvedValue({ ...settlement, taxStatus: 'SUCCEEDED' }); await mount(completed);
    expect(host.textContent).toContain('Không xác nhận tiền đã về ngân hàng thật');
    expect(host.querySelector('.settlement-document .section-heading')?.textContent).toContain('Mô phỏng');
    expect(host.textContent).toContain('không xác nhận cơ quan thuế đã ACCEPTED');
    expect(host.querySelector('.settlement-document details')?.hasAttribute('open')).toBe(false);
    expect(host.querySelector('.settlement-document .financial-status-done')?.textContent).toContain('Release đã xác nhận');
    expect(host.querySelector('.settlement-document details')?.textContent).toContain('Tham chiếu release');
    expect(host.querySelector('.settlement-document details')?.textContent).not.toContain('releaseReference');
  });
  it('polls null settlement to confirmed release and stops after all stages become terminal', async () => {
    vi.useFakeTimers(); await mount(releasing);
    vi.mocked(api.settlement).mockResolvedValue({ ...settlement, onChainStatus: 'SUCCEEDED', offRampStatus: 'SUCCEEDED', taxStatus: 'SUCCEEDED' }); serverJob = completed;
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
    expect(host.querySelector('.ownership-band')?.textContent).toContain('Đã xác nhận release mô phỏng');
    const count = vi.mocked(api.settlement).mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(90000); }); expect(api.settlement).toHaveBeenCalledTimes(count);
  });
  it('keeps confirmed money through downstream failure and continues retryable-stage reads', async () => {
    vi.useFakeTimers(); vi.mocked(api.settlement).mockResolvedValue({ ...settlement, onChainStatus: 'FAILED', offRampStatus: 'UNKNOWN' }); await mount(completed);
    const count = vi.mocked(api.settlement).mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); }); expect(vi.mocked(api.settlement).mock.calls.length).toBeGreaterThan(count);
    expect(host.querySelector('.ownership-band')?.textContent).toContain('Đã xác nhận release mô phỏng');
  });
  it('renders a terminal release failure without an endless processing heading or automatic retry', async () => {
    vi.useFakeTimers(); vi.mocked(api.settlement).mockResolvedValue({ ...settlement, moneyStatus: 'FAILED', retryable: false }); await mount(releasing);
    expect(host.querySelector('.ownership-band')?.textContent).toContain('release cần xử lý lỗi');
    const count = vi.mocked(api.settlement).mock.calls.length; await act(async () => { await vi.advanceTimersByTimeAsync(90000); }); expect(api.settlement).toHaveBeenCalledTimes(count);
  });
  it('keeps polling UNKNOWN and FAILED_RETRYABLE primary money without claiming a release', async () => {
    vi.useFakeTimers(); vi.mocked(api.settlement).mockResolvedValue({ ...settlement, moneyStatus: 'UNKNOWN', retryable: true }); await mount(releasing);
    const count = vi.mocked(api.settlement).mock.calls.length; vi.mocked(api.settlement).mockResolvedValue({ ...settlement, moneyStatus: 'FAILED_RETRYABLE', retryable: true });
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); }); expect(vi.mocked(api.settlement).mock.calls.length).toBeGreaterThan(count);
    expect(host.querySelector('.ownership-band')?.textContent).not.toContain('Đã xác nhận release');
  });
  it('preserves confirmed release on a later read error and cleans polling on unmount', async () => {
    vi.useFakeTimers(); vi.mocked(api.settlement).mockResolvedValue(settlement); await mount(completed);
    vi.mocked(api.settlement).mockRejectedValue(new ApiError('down', 503));
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
    expect(host.querySelector('.ownership-band')?.textContent).toContain('Đã xác nhận release mô phỏng');
    await act(async () => { root.unmount(); root = createRoot(host); }); const count = vi.mocked(api.settlement).mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(90000); }); expect(api.settlement).toHaveBeenCalledTimes(count);
  });
});

describe('P0 contract cancellation and refund', () => {
  it('initiates eligible Client pre-funding cancellation and renders no-refund final record', async () => {
    vi.mocked(api.funding).mockResolvedValue(null);
    const post = vi.spyOn(api, 'requestCancellation').mockImplementation(async () => {
      cancellation = { ...proposal, cancellationStatus: 'CANCELLED', allowedActions: [] };
      serverJob = { ...preFunding, status: 'CANCELLED', contract: { ...preFunding.contract!, status: 'CANCELLED', milestoneStatus: 'CANCELLED' } }; return cancellation;
    });
    await mount(preFunding); await click('Hủy trước funding'); await reason(); await request();
    expect(post).toHaveBeenCalledExactlyOnceWith('contract', { reasonCode: proposal.reasonCode, description: proposal.reason });
    expect(host.textContent).toContain('Đã hủy trước funding. Không có hoàn tiền'); expect(button('Funding mô phỏng')).toBeUndefined();
  });
  it.each(['FAILED', 'UNKNOWN', 'PENDING', 'PROCESSING', 'SUCCEEDED'] as const)('does not offer pre-funding cancel after a %s funding attempt', async fundingStatus => {
    vi.mocked(api.funding).mockResolvedValue({ fundingTransactionId: 'attempt', fundingStatus, simulation: true, providerReference: null, nextAction: 'WAIT', retryAfterSeconds: 30 });
    await mount(preFunding); expect(button('Hủy trước funding')).toBeUndefined();
  });
  it('blocks pre-funding cancel while a persisted funding attempt has no known outcome', async () => {
    vi.mocked(api.funding).mockResolvedValue(null); saveAttempt(attemptScope('fund', client.id, 'contract', 'milestone'), { key: 'prior', amount: '500.00', currency: 'USD' });
    await mount(preFunding); expect(button('Hủy trước funding')).toBeUndefined();
  });
  it('Freelancer cannot cancel before funding', async () => {
    vi.mocked(api.funding).mockResolvedValue(null); await mount(preFunding, freelancer); expect(button('Hủy trước funding')).toBeUndefined(); expect(button('Funding mô phỏng')).toBeUndefined();
  });
  it('creates a funded proposal without changing the continuing-work ownership to cancelled', async () => {
    const post = vi.spyOn(api, 'requestCancellation').mockImplementation(async () => { cancellation = { ...proposal, allowedActions: [] }; return cancellation; });
    await mount(); await click('Đề nghị hủy hợp đồng'); await reason(); await request();
    expect(post).toHaveBeenCalledExactlyOnceWith('contract', { reasonCode: proposal.reasonCode, description: proposal.reason });
    expect(host.textContent).toContain('Công việc vẫn tiếp tục'); expect(host.querySelector('.ownership-band')?.textContent).toContain('Đang chờ Freelancer bàn giao');
    expect(host.querySelector('.ownership-band')?.textContent).not.toContain('đã hủy');
  });
  it('requires a reason and rechecks a newly created funding attempt before cancellation POST', async () => {
    vi.mocked(api.funding).mockResolvedValue(null); const post = vi.spyOn(api, 'requestCancellation'); await mount(preFunding); await click('Hủy trước funding'); await request(); expect(post).not.toHaveBeenCalled();
    await reason(); vi.mocked(api.funding).mockResolvedValue({ fundingTransactionId: 'new-attempt', fundingStatus: 'FAILED', simulation: true, providerReference: null, nextAction: 'RETRY', retryAfterSeconds: null });
    await request(); expect(post).not.toHaveBeenCalled(); expect(host.textContent).toContain('không còn cho phép');
  });
  it('requester cannot accept/reject own proposal even if stale allowedActions says otherwise', async () => {
    cancellation = proposal; await mount(); expect(button('Đồng ý hủy')).toBeUndefined(); expect(button('Từ chối hủy')).toBeUndefined();
  });
  it('counterparty accepts only the allowed decision and pending refund locks submission', async () => {
    cancellation = { ...proposal, allowedActions: ['ACCEPT'] };
    const post = vi.spyOn(api, 'decideCancellation').mockImplementation(async () => {
      cancellation = refundPending; serverJob = { ...working, contract: { ...working.contract!, milestoneStatus: 'REFUND_PENDING' } }; return cancellation;
    });
    await mount(working, freelancer); expect(button('Từ chối hủy')).toBeUndefined(); await click('Đồng ý hủy');
    expect(button('Xác nhận đồng ý hủy')?.classList.contains('button-caution')).toBe(true);
    expect(host.querySelector('[aria-label="Xác nhận quyết định hủy"]')?.textContent).toContain('Đây chưa phải hoàn tiền đã xác nhận');
    await click('Xác nhận đồng ý hủy');
    expect(post).toHaveBeenCalledExactlyOnceWith('contract', 'cancel', 'ACCEPT'); expect(host.textContent).toContain('chưa được xác nhận hủy cuối cùng');
    expect(host.querySelector('.work-composer')).toBeNull(); expect(host.textContent).not.toContain('Đã xác nhận bản ghi hoàn tiền mô phỏng');
  });
  it('counterparty rejects when allowed and returns to continuing work without refund', async () => {
    cancellation = { ...proposal, allowedActions: ['REJECT'] };
    const post = vi.spyOn(api, 'decideCancellation').mockImplementation(async () => { cancellation = { ...proposal, cancellationStatus: 'REJECTED', decidedBy: freelancer.id, allowedActions: [] }; return cancellation; });
    await mount(working, freelancer); expect(button('Đồng ý hủy')).toBeUndefined(); await click('Từ chối hủy'); await click('Xác nhận từ chối hủy');
    expect(post).toHaveBeenCalledExactlyOnceWith('contract', 'cancel', 'REJECT'); expect(host.textContent).toContain('Công việc tiếp tục, không có hoàn tiền');
    expect(host.querySelector('.work-composer')).not.toBeNull();
  });
  it('rechecks stale allowedActions immediately before a decision POST', async () => {
    cancellation = proposal; const post = vi.spyOn(api, 'decideCancellation'); await mount(working, freelancer); await click('Đồng ý hủy');
    cancellation = { ...proposal, allowedActions: [] }; await click('Xác nhận đồng ý hủy'); expect(post).not.toHaveBeenCalled();
  });
  it('withdraws ACCEPT after submission starts but leaves REJECT available', async () => {
    cancellation = proposal; vi.mocked(api.contractSubmissions).mockResolvedValue([{ ...approved, status: 'SUBMITTED' }]);
    await mount({ ...working, status: 'SUBMITTED_FOR_REVIEW', contract: { ...working.contract!, status: 'UNDER_REVIEW', milestoneStatus: 'SUBMITTED' } }, freelancer);
    expect(button('Đồng ý hủy')).toBeUndefined(); expect(button('Từ chối hủy')).toBeTruthy();
  });
  it('does not POST ACCEPT if a submission arrived after opening confirmation', async () => {
    cancellation = proposal; const post = vi.spyOn(api, 'decideCancellation'); await mount(working, freelancer); await click('Đồng ý hủy');
    vi.mocked(api.contractSubmissions).mockResolvedValue([{ ...approved, status: 'SUBMITTED' }]);
    await click('Xác nhận đồng ý hủy'); expect(post).not.toHaveBeenCalled();
  });
  it('polls REFUND_PENDING to final simulated refund and stops only after confirmation', async () => {
    vi.useFakeTimers(); cancellation = refundPending; await mount({ ...working, contract: { ...working.contract!, milestoneStatus: 'REFUND_PENDING' } }, freelancer);
    expect(host.querySelector('.ownership-band')?.textContent).toContain('chưa hủy cuối cùng');
    cancellation = refunded; serverJob = { ...working, status: 'CANCELLED', contract: { ...working.contract!, status: 'CANCELLED', milestoneStatus: 'REFUNDED' } };
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); }); expect(host.textContent).toContain('Đã xác nhận bản ghi hoàn tiền mô phỏng');
    expect(host.textContent).toContain('Không xác nhận hoàn tiền ngân hàng thật'); expect(host.querySelector('.work-composer')).toBeNull();
    const count = vi.mocked(api.cancellation).mock.calls.length; await act(async () => { await vi.advanceTimersByTimeAsync(90000); }); expect(api.cancellation).toHaveBeenCalledTimes(count);
  });
  it('reconciles an ambiguous request by GET and never blindly reposts', async () => {
    const post = vi.spyOn(api, 'requestCancellation').mockImplementation(async () => { cancellation = { ...proposal, allowedActions: [] }; throw new ApiError('lost response', 0); });
    await mount(); await click('Đề nghị hủy hợp đồng'); await reason(); const reads = vi.mocked(api.cancellation).mock.calls.length; await request();
    expect(post).toHaveBeenCalledTimes(1); expect(vi.mocked(api.cancellation).mock.calls.length).toBeGreaterThan(reads);
    expect(host.textContent).toContain('Đã đối chiếu kết quả'); expect(host.textContent).toContain('Công việc vẫn tiếp tục'); expect(sessionStorage.length).toBe(0);
  });
  it('preserves exact intent across reload and blocks other work until ambiguity is resolved', async () => {
    saveAttempt('freelax:cancellation:freelancer:contract', { kind: 'request', payload: { reasonCode: 'MUTUAL_CANCELLATION', description: 'Scope no longer needed' } });
    const post = vi.spyOn(api, 'requestCancellation'); await mount(working, freelancer); expect(post).not.toHaveBeenCalled(); expect(host.querySelector('.work-composer')).toBeNull();
    expect(button('Đối chiếu và tiếp tục ý định trước')).toBeTruthy();
  });
  it('reconciles lost ACCEPT response by reading refund state without repeating the decision', async () => {
    cancellation = proposal;
    const post = vi.spyOn(api, 'decideCancellation').mockImplementation(async () => {
      cancellation = refundPending; serverJob = { ...working, contract: { ...working.contract!, milestoneStatus: 'REFUND_PENDING' } }; throw new ApiError('lost response', 0);
    });
    await mount(working, freelancer); await click('Đồng ý hủy'); await click('Xác nhận đồng ý hủy');
    expect(post).toHaveBeenCalledTimes(1); expect(host.textContent).toContain('chưa được xác nhận hủy cuối cùng'); expect(sessionStorage.length).toBe(0);
  });
  it('resolves a conflicting persisted request from the immutable server proposal', async () => {
    cancellation = proposal; saveAttempt('freelax:cancellation:freelancer:contract', { kind: 'request', payload: { reasonCode: 'OTHER_SCOPE', description: 'Different intent' } });
    await mount(working, freelancer); expect(sessionStorage.length).toBe(0); expect(host.textContent).toContain('một đề nghị khác');
    expect(button('Đồng ý hủy')).toBeTruthy(); expect(host.querySelector('.work-composer')).not.toBeNull();
  });
  it('retries the exact ambiguous request only after explicit reconciliation, without a new intent', async () => {
    const post = vi.spyOn(api, 'requestCancellation').mockRejectedValue(new ApiError('lost response', 0));
    await mount(); await click('Đề nghị hủy hợp đồng'); await reason(); await request(); const first = post.mock.calls[0];
    expect(post).toHaveBeenCalledTimes(1); await click('Đối chiếu và tiếp tục ý định trước'); expect(post.mock.calls[1]).toEqual(first);
  });
  it('cleans null/pending cancellation polling on unmount', async () => {
    vi.useFakeTimers(); await mount(); await act(async () => { root.unmount(); root = createRoot(host); }); const count = vi.mocked(api.cancellation).mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(90000); }); expect(api.cancellation).toHaveBeenCalledTimes(count);
  });
  it('prevents double request and keeps funding disabled during an unresolved cancellation', async () => {
    vi.mocked(api.funding).mockResolvedValue(null); const post = vi.spyOn(api, 'requestCancellation').mockReturnValue(new Promise(() => {}));
    await mount(preFunding); await click('Hủy trước funding'); await reason();
    await act(async () => { const f = host.querySelector('.cancellation-form')!; f.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); f.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
    expect(post).toHaveBeenCalledTimes(1); expect(button('Funding mô phỏng')?.disabled).toBe(true);
  });
  it('prevents double counterparty decision', async () => {
    cancellation = proposal; const post = vi.spyOn(api, 'decideCancellation').mockReturnValue(new Promise(() => {})); await mount(working, freelancer); await click('Đồng ý hủy');
    const confirm = button('Xác nhận đồng ý hủy')!; await act(async () => { confirm.click(); confirm.click(); }); expect(post).toHaveBeenCalledTimes(1);
  });
  it('keeps the workflow document on read failure and does not offer unverified work mutations', async () => {
    vi.mocked(api.cancellation).mockRejectedValue(new ApiError('down', 503)); await mount(working, freelancer);
    expect(host.textContent).toContain('Work document'); expect(host.querySelector('.work-composer')).toBeNull(); expect(host.querySelector('[role=alert]')).not.toBeNull();
  });
  it('renders the cancelled/refunded record through the actual shared JobDetail route', async () => {
    cancellation = refunded; serverJob = { ...working, status: 'CANCELLED', contract: { ...working.contract!, status: 'CANCELLED', milestoneStatus: 'REFUNDED' } };
    await act(async () => root.render(<MemoryRouter initialEntries={['/work/job']}><Routes><Route path="/work/:jobId" element={<JobDetail user={client} />} /></Routes></MemoryRouter>));
    expect(host.textContent).toContain('Đã xác nhận bản ghi hoàn tiền mô phỏng'); expect(host.textContent).toContain(proposal.reason);
    expect(host.querySelector('.work-composer')).toBeNull(); expect(button('Funding mô phỏng')).toBeUndefined();
  });
});
