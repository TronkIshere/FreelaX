// @vitest-environment jsdom
import { act, useRef, useState } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
import { AdminDisputes, AdminDisputeDetail } from './AdminDisputes';
import { ContractDispute, DisputeRecord } from './ContractDispute';
import { WorkLifecycle } from './WorkLifecycle';
import type { AdminDisputeDetail as Detail, Dispute, ContractSubmission, ContractSettlement, Job, User } from './types';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const client: User = { id: 'client', email: 'client@example.test', displayName: 'Client', userType: 'CLIENT' };
const freelancer: User = { ...client, id: 'freelancer', userType: 'FREELANCER' };
const admin: User = { ...client, id: 'admin', authorities: ['ROLE_ADMIN'] };
const original: Dispute = { disputeId: 'case', contractId: 'contract', milestoneId: 'milestone', jobId: 'job', submissionId: null,
  openedBy: 'client', reasonCode: 'QUALITY', description: 'Original case description', status: 'OPEN', openedAt: '2026-10-06T00:00:00Z',
  claimedBy: null, claimedAt: null, resolvedBy: null, decisionAt: null, resolvedAt: null, resolutionReason: null, refundStatus: null, refundReference: null, evidence: [] };
const originalDetail: Detail = { dispute: original, contract: { contractId: 'contract', clientId: 'client', freelancerId: 'freelancer', title: 'Contract snapshot title', description: 'Frozen work brief', amount: '500.00', currency: 'USD', deliveryDueAt: '2030-01-01T00:00:00Z', maxRevisions: 2, revisionsUsed: 1 },
  fundingStatus: 'SUCCEEDED', deliverables: [{ id: 'req', title: 'Source', description: 'Source code', required: true }],
  acceptanceCriteria: [{ id: 'crit', description: 'All acceptance tests pass', required: true }],
  submissions: [{ id: 'v1', version: 1, status: 'DISPUTED', summary: 'First version', reviewerFeedback: 'Mismatch', submittedAt: '2026-10-05T00:00:00Z', reviewedAt: null, evidence: [{ requirementId: 'req', kind: 'DELIVERABLE', description: 'Code', url: 'https://example.test/code' }] }],
  audit: [{ actorId: 'client', action: 'OPENED', beforeStatus: null, afterStatus: 'OPEN', reason: 'Case opened', requestId: 'audit-reference', createdAt: '2026-10-06T00:00:00' }] };
const working: Job = { id: 'job', title: 'Real work', description: 'Document', budgetUsd: 500, status: 'IN_PROGRESS', clientUserId: 'client', freelancerId: 'freelancer', createdAt: null,
  contract: { id: 'contract', milestoneId: 'milestone', status: 'ACTIVE', milestoneStatus: 'FUNDED', amount: '500.00', currency: 'USD', deliveryDueAt: null, reviewWindowHours: 48, maxRevisions: 2, revisionsUsed: 0, deliverables: [], acceptanceCriteria: [] } };
const oldSubmission: ContractSubmission = { id: 'v1', contractId: 'contract', milestoneId: 'milestone', freelancerId: 'freelancer', version: 1, status: 'DISPUTED', summary: 'Historical disputed delivery', submittedAt: '2026-10-05T00:00:00Z', submittedLate: false, reviewDueAt: null, reviewGraceDueAt: null, reviewedAutomatically: false, disputeId: 'case', reviewerFeedback: null, reviewCriterionIds: [], reviewDeliverableIds: [], deliverables: [], acceptanceEvidence: [] };
const settlement: ContractSettlement = { contractId: 'contract', milestoneId: 'milestone', jobId: 'job', amount: 500, currency: 'USD', simulation: true, moneyStatus: 'SUCCEEDED', onChainStatus: 'FAILED', offRampStatus: 'NOT_STARTED', taxStatus: 'NOT_STARTED', releaseReference: 'sim-release', onChainReference: null, offRampReference: null, taxReference: null, retryable: false, lastError: null, onChainError: 'Provider unavailable', offRampError: null, taxError: null, createdAt: '2026-10-06T00:00:00', updatedAt: '2026-10-06T00:00:00' };
let host: HTMLDivElement, root: Root, serverCase: Dispute | null, detail: Detail, job: Job;
beforeEach(() => {
  sessionStorage.clear(); serverCase = null; detail = structuredClone(originalDetail); job = structuredClone(working);
  host = document.createElement('div'); document.body.append(host); root = createRoot(host);
  vi.spyOn(api, 'dispute').mockImplementation(async () => serverCase);
  vi.spyOn(api, 'adminDispute').mockImplementation(async () => detail);
  vi.spyOn(api, 'contractSubmissions').mockResolvedValue([]);
  vi.spyOn(api, 'job').mockImplementation(async () => job);
  vi.spyOn(api, 'cancellation').mockResolvedValue(null); vi.spyOn(api, 'settlement').mockResolvedValue(null);
});
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.useRealTimers(); });
async function mount(node: React.ReactNode, path = '/') {
  await act(async () => root.render(<MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{node}</MemoryRouter>));
}
function Participant({ user = freelancer }: { user?: User }) {
  const [current, setCurrent] = useState(serverCase); const lock = useRef(false);
  return <ContractDispute contractId="contract" user={user} dispute={current} ready eligible={!current} blocked={false} operationLock={lock}
    onBusy={() => {}} onRefresh={async () => setCurrent(await api.dispute('contract'))} />;
}
function Workflow() { const [current, setCurrent] = useState(job); return <WorkLifecycle job={current} user={freelancer} onJobUpdated={setCurrent}><p>Work document</p></WorkLifecycle>; }
async function adminCase(user = admin) { await mount(<Routes><Route path="/admin/disputes/:disputeId" element={<AdminDisputeDetail user={user} />} /></Routes>, '/admin/disputes/case'); }
const button = (label: string) => [...host.querySelectorAll('button')].find(b => b.textContent === label);
async function click(label: string) { expect(button(label)).toBeTruthy(); await act(async () => button(label)!.click()); }
async function input(selector: string, value: string) {
  const el = host.querySelector(selector) as HTMLInputElement;
  expect(el).toBeTruthy(); await act(async () => { Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value')!.set!.call(el, value); el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true })); });
}
async function submit(selector: string) { await act(async () => host.querySelector(selector)!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); }
async function addText(text = 'Evidence text') { await input('.dispute-form fieldset textarea', text); await click('Thêm vào lần gửi'); }
function claimed() { detail = { ...detail, dispute: { ...original, status: 'UNDER_REVIEW', claimedBy: admin.id, claimedAt: '2026-10-06T01:00:00Z' } }; }
async function prepareDecision(label = 'Release cho Freelancer') {
  await click(label); await input('.admin-resolution-form textarea', 'Evidence reviewed');
  await act(async () => (host.querySelector('.admin-resolution-form input[type=checkbox]') as HTMLInputElement).click());
}

describe('Step 6 participant dispute UI', () => {
  it.each([client, freelancer])('eligible $userType opens a real case with the maximum description', async user => {
    const post = vi.spyOn(api, 'openDispute').mockImplementation(async (_, payload) => (serverCase = { ...original, description: payload.description }));
    await mount(<Participant user={user} />); await click('Mở hồ sơ tranh chấp');
    expect(host.querySelector('.dispute-form > label textarea')?.getAttribute('maxlength')).toBe('2000');
    await input('.dispute-form > label textarea', 'x'.repeat(2000)); await submit('.dispute-form');
    expect(post).toHaveBeenCalledWith('contract', { reasonCode: 'QUALITY', description: 'x'.repeat(2000), evidence: [] });
    expect(host.textContent).toContain('Tranh chấp đang mở');
  });
  it('does not send a 2,001-character description', async () => {
    const post = vi.spyOn(api, 'openDispute'); await mount(<Participant />); await click('Mở hồ sơ tranh chấp');
    await input('.dispute-form > label textarea', 'x'.repeat(2001)); await submit('.dispute-form');
    expect(post).not.toHaveBeenCalled(); expect(host.textContent).toContain('2.000');
  });
  it('preserves minimal Client OPEN_DISPUTE and refetches the authoritative case after review', async () => {
    job = { ...working, status: 'SUBMITTED_FOR_REVIEW', contract: { ...working.contract!, status: 'UNDER_REVIEW', milestoneStatus: 'SUBMITTED' } };
    let latest: ContractSubmission = { ...oldSubmission, status: 'SUBMITTED', disputeId: null };
    vi.mocked(api.contractSubmissions).mockImplementation(async () => [latest]);
    const post = vi.spyOn(api, 'decideSubmission').mockImplementation(async () => {
      latest = { ...oldSubmission }; serverCase = { ...original, description: 'Authoritative server case' };
      job = { ...job, contract: { ...job.contract!, status: 'DISPUTED', milestoneStatus: 'DISPUTED' } }; return latest;
    });
    function ClientWorkflow() { const [current, setCurrent] = useState(job); return <WorkLifecycle job={current} user={client} onJobUpdated={setCurrent} />; }
    await mount(<ClientWorkflow />); await click('Mở tranh chấp'); await input('.decision-form textarea', 'x'.repeat(2001)); await submit('.decision-form');
    expect(post).not.toHaveBeenCalled(); expect(host.querySelector('.decision-form textarea')?.getAttribute('maxlength')).toBe('2000');
    await input('.decision-form textarea', 'Quality mismatch'); await submit('.decision-form');
    expect(post).toHaveBeenCalledWith('contract', 'v1', { decision: 'OPEN_DISPUTE', reasonCode: 'QUALITY', description: 'Quality mismatch' });
    expect(host.textContent).toContain('Authoritative server case'); expect(api.dispute).toHaveBeenCalledTimes(2);
    expect(host.querySelector('.review-action')).toBeNull();
  });
  it('adds safe initial LINK evidence and prevents unsafe HTTPS credentials', async () => {
    const post = vi.spyOn(api, 'openDispute').mockImplementation(async (_, body) => (serverCase = { ...original, description: body.description }));
    await mount(<Participant />); await click('Mở hồ sơ tranh chấp'); await input('.dispute-form > label textarea', 'Mismatch');
    await input('.dispute-form select', 'LINK'); await input('.dispute-form input[type=url]', 'https://user:pass@example.test'); await click('Thêm vào lần gửi');
    expect(host.textContent).toContain('HTTPS'); expect(post).not.toHaveBeenCalled();
    await input('.dispute-form input[type=url]', 'https://example.test/proof'); await click('Thêm vào lần gửi'); await submit('.dispute-form');
    expect(post.mock.calls[0][1].evidence).toEqual([{ kind: 'LINK', url: 'https://example.test/proof' }]);
  });
  it('OPEN permits append and preserves exact intent/key through an ambiguous error and reload', async () => {
    serverCase = { ...original }; const post = vi.spyOn(api, 'appendDisputeEvidence').mockRejectedValue(new ApiError('timeout', 0));
    await mount(<Participant />); await addText(); await submit('.dispute-form');
    const first = post.mock.calls[0]; expect(first[3]).toEqual([{ kind: 'TEXT', text: 'Evidence text' }]);
    expect((host.querySelector('.dispute-form fieldset') as HTMLFieldSetElement).disabled).toBe(true);
    act(() => root.unmount()); root = createRoot(host); await mount(<Participant />); await submit('.dispute-form');
    expect(post.mock.calls[1]).toEqual(first); expect(api.dispute).toHaveBeenCalled();
    expect(vi.mocked(api.dispute).mock.invocationCallOrder.at(-2)!).toBeLessThan(post.mock.invocationCallOrder[1]);
  });
  it('preserves inputs after a recoverable validation error', async () => {
    vi.spyOn(api, 'openDispute').mockRejectedValue(new ApiError('invalid', 400)); await mount(<Participant />);
    await click('Mở hồ sơ tranh chấp'); await input('.dispute-form > label textarea', 'Keep this description'); await submit('.dispute-form');
    const textarea = host.querySelector('.dispute-form > label textarea') as HTMLTextAreaElement;
    expect(textarea.value).toBe('Keep this description'); expect(textarea.disabled).toBe(false);
  });
  it('prevents duplicate evidence submit while pending', async () => {
    serverCase = { ...original }; const post = vi.spyOn(api, 'appendDisputeEvidence').mockReturnValue(new Promise(() => {}));
    await mount(<Participant />); await addText(); await act(async () => { const form = host.querySelector('.dispute-form')!; form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
    expect(post).toHaveBeenCalledOnce();
  });
  it('claim freezes evidence; participant never sees Admin audit or fake cancel controls', async () => {
    serverCase = { ...original, status: 'UNDER_REVIEW', claimedBy: admin.id, claimedAt: '2026-10-06T01:00:00Z' };
    await mount(<Participant />); expect(host.querySelector('.dispute-form')).toBeNull();
    expect(host.textContent).toContain('bằng chứng đã khóa'); expect(host.querySelector('.admin-audit')).toBeNull();
    expect(button('Hủy tranh chấp')).toBeUndefined();
  });
  it.each(['DECISION_PENDING_RELEASE', 'DECISION_PENDING_REFUND', 'RESOLVED_RELEASE', 'RESOLVED_REFUND'] as const)('renders truthful %s despite historical DISPUTED submission', async status => {
    serverCase = { ...original, status, refundStatus: status === 'RESOLVED_REFUND' ? 'SUCCEEDED' : 'PENDING' };
    const released = status === 'RESOLVED_RELEASE'; const refunded = status === 'RESOLVED_REFUND';
    job = { ...working, status: released ? 'COMPLETED' : refunded ? 'CANCELLED' : 'SUBMITTED_FOR_REVIEW', contract: { ...working.contract!, status: released ? 'COMPLETED' : refunded ? 'CANCELLED' : 'DISPUTED', milestoneStatus: released ? 'RELEASED' : refunded ? 'REFUNDED' : status === 'DECISION_PENDING_RELEASE' ? 'RELEASE_PENDING' : 'REFUND_PENDING' } };
    vi.mocked(api.contractSubmissions).mockResolvedValue([oldSubmission]);
    if (released) vi.mocked(api.settlement).mockResolvedValue(settlement);
    await mount(<Workflow />);
    expect(host.querySelector('.ownership-band')?.textContent).not.toContain('Đang chờ Admin');
    expect(host.textContent).toContain('Historical disputed delivery'); expect(host.querySelector('.work-composer, .review-action')).toBeNull();
    if (released) { expect(api.settlement).toHaveBeenCalled(); expect(host.textContent).toContain('Mô phỏng'); expect(host.textContent).toContain('sim-release'); expect(host.textContent).not.toContain('Release chưa thành công'); }
    if (status === 'DECISION_PENDING_RELEASE') expect(host.textContent).toContain('Chưa xác nhận release thành công');
    if (status === 'DECISION_PENDING_REFUND') expect(host.textContent).toContain('Chưa xác nhận hoàn tiền hoàn tất');
    if (refunded) { expect(host.textContent).toContain('Hồ sơ đã kết thúc'); expect(api.settlement).not.toHaveBeenCalled(); }
  });
  it('renders untrusted content as text and suppresses unsafe links', async () => {
    await mount(<DisputeRecord dispute={{ ...original, description: '<img src=x onerror=alert(1)>', evidence: [{ id: 'e', actorId: 'client', createdAt: null, kind: 'LINK', url: 'javascript:alert(1)', text: '<script>bad</script>' }] }} />);
    expect(host.querySelector('img, script, a')).toBeNull(); expect(host.textContent).toContain('<img');
  });
});

describe('Step 6 Admin workspace', () => {
  it('missing authorities denies direct access without requesting privileged data', async () => {
    await adminCase(client); expect(host.textContent).toContain('Không có quyền quản trị'); expect(api.adminDispute).not.toHaveBeenCalled();
  });
  it('queue renders Spring Page content and requests filters/pagination', async () => {
    const read = vi.spyOn(api, 'adminDisputes').mockResolvedValue({ content: [original], number: 0, size: 20, totalPages: 2, totalElements: 21 });
    await mount(<AdminDisputes user={admin} />); expect(host.querySelector('a[href="/admin/disputes/case"]')).not.toBeNull();
    expect(host.textContent).toContain('21 hồ sơ'); await click('Trang sau'); expect(read).toHaveBeenLastCalledWith('OPEN', 1);
    await input('.admin-filter select', 'UNDER_REVIEW'); expect(read).toHaveBeenLastCalledWith('UNDER_REVIEW', 0);
  });
  it('renders safe contract facts, evidence, full submissions, and separate audit references', async () => {
    await adminCase(); expect(host.textContent).toContain('500.00 USD'); expect(host.textContent).toContain('Source code'); expect(host.textContent).toContain('All acceptance tests pass');
    expect(host.textContent).toContain('First version'); expect(host.textContent).toContain('audit-reference');
    expect(host.querySelector('.admin-audit')?.textContent).toContain('khác mã yêu cầu HTTP'); expect(host.querySelector('a[target=_blank]')?.getAttribute('rel')).toBe('noopener noreferrer');
  });
  it('Admin claims before resolution and becomes the valid resolver', async () => {
    const claim = vi.spyOn(api, 'claimDispute').mockImplementation(async () => { claimed(); return detail.dispute; });
    await adminCase(); expect(button('Release cho Freelancer')).toBeUndefined(); await click('Tiếp nhận hồ sơ');
    expect(claim).toHaveBeenCalledWith('case'); expect(button('Release cho Freelancer')).toBeTruthy(); expect(host.textContent).toContain('Bằng chứng participant đã khóa');
  });
  it('other Admin and participant Admin cannot resolve or claim', async () => {
    claimed(); detail.dispute.claimedBy = 'other-admin'; await adminCase(); expect(button('Release cho Freelancer')).toBeUndefined();
    expect(host.textContent).toContain('Chỉ Admin đã tiếp nhận');
    act(() => root.unmount()); root = createRoot(host); detail = structuredClone(originalDetail);
    await adminCase({ ...client, authorities: ['ROLE_ADMIN'] }); expect(button('Tiếp nhận hồ sơ')).toBeUndefined(); expect(host.textContent).toContain('không được tiếp nhận');
  });
  it.each([['Release cho Freelancer', 'RELEASE_TO_FREELANCER', 'DECISION_PENDING_RELEASE'], ['Hoàn tiền cho Client', 'REFUND_TO_CLIENT', 'DECISION_PENDING_REFUND']] as const)(
    '%s requires reason and explicit confirmation; decision is not financial completion', async (label, outcome, status) => {
      claimed(); const post = vi.spyOn(api, 'resolveDispute').mockImplementation(async () => { detail = { ...detail, dispute: { ...detail.dispute, status } }; return detail.dispute; });
      await adminCase(); await click(label); expect(button('Xác nhận quyết định')?.disabled).toBe(true);
      await submit('.admin-resolution-form'); expect(post).not.toHaveBeenCalled();
      await act(async () => (host.querySelector('.admin-resolution-form input[type=checkbox]') as HTMLInputElement).click());
      await submit('.admin-resolution-form'); expect(post).not.toHaveBeenCalled(); expect(host.textContent).toContain('Lý do quyết định là bắt buộc');
      await input('.admin-resolution-form textarea', 'Evidence reviewed'); await submit('.admin-resolution-form');
      expect(post).toHaveBeenCalledWith('case', expect.any(String), { outcome, reason: 'Evidence reviewed' }); expect(host.textContent).toContain('đang đối soát');
      expect(host.querySelector('.admin-resolution-form')).toBeNull();
    },
  );
  it('prevents double resolution clicks', async () => {
    claimed(); const post = vi.spyOn(api, 'resolveDispute').mockReturnValue(new Promise(() => {})); await adminCase(); await prepareDecision();
    await act(async () => { const form = host.querySelector('.admin-resolution-form')!; form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); }); expect(post).toHaveBeenCalledOnce();
  });
  it('ambiguous resolution rereads first and preserves the same key and payload for manual replay', async () => {
    claimed(); const post = vi.spyOn(api, 'resolveDispute').mockRejectedValue(new ApiError('timeout', 0)); await adminCase(); await prepareDecision(); await submit('.admin-resolution-form');
    const first = post.mock.calls[0]; const readCount = vi.mocked(api.adminDispute).mock.calls.length;
    expect(readCount).toBe(2); expect((host.querySelector('.admin-resolution-form textarea') as HTMLTextAreaElement).disabled).toBe(true);
    await submit('.admin-resolution-form'); expect(post.mock.calls[1]).toEqual(first);
    expect(vi.mocked(api.adminDispute).mock.calls.length).toBe(4);
    expect(vi.mocked(api.adminDispute).mock.invocationCallOrder[2]).toBeLessThan(post.mock.invocationCallOrder[1]);
  });
  it('lost response followed by authoritative decision never replays the mutation', async () => {
    claimed(); const post = vi.spyOn(api, 'resolveDispute').mockImplementation(async () => { detail = { ...detail, dispute: { ...detail.dispute, status: 'DECISION_PENDING_RELEASE' } }; throw new ApiError('timeout', 0); });
    await adminCase(); await prepareDecision(); await submit('.admin-resolution-form');
    expect(post).toHaveBeenCalledOnce(); expect(host.querySelector('.admin-resolution-form')).toBeNull(); expect(sessionStorage.length).toBe(0);
  });
  it('403 hides privileged data and exposes only collapsed safe HTTP support metadata', async () => {
    vi.mocked(api.adminDispute).mockRejectedValue(new ApiError('SECRET STACK TRACE', 403, 4030, 'ACCESS_DENIED', 'http-request-1', false));
    await adminCase(); expect(host.textContent).toContain('Không có quyền'); expect(host.textContent).not.toContain('SECRET STACK TRACE');
    expect(host.querySelector('.admin-case-document')).toBeNull(); expect(host.textContent).toContain('http-request-1');
    expect(host.querySelector('details.technical-evidence')?.hasAttribute('open')).toBe(false);
  });
  it('a later 403 discards previously rendered privileged case facts', async () => {
    const claim = vi.spyOn(api, 'claimDispute').mockImplementation(async () => {
      vi.mocked(api.adminDispute).mockRejectedValue(new ApiError('revoked', 403)); throw new ApiError('revoked', 403);
    });
    await adminCase(); expect(host.querySelector('.admin-case-document')).not.toBeNull(); await click('Tiếp nhận hồ sơ');
    expect(claim).toHaveBeenCalledOnce(); expect(host.querySelector('.admin-case-document')).toBeNull();
    expect(host.textContent).toContain('Không có quyền');
  });
});
