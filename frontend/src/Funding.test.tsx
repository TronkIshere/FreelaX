// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
import { FundingPanel } from './Funding';
import { attemptScope, contractAmount } from './workflowContracts';
import type { FundingResponse, Job, User } from './types';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const client: User = { id: 'client', displayName: 'Client', email: 'c@example.test', userType: 'CLIENT' };
const freelancer: User = { ...client, id: 'freelancer', userType: 'FREELANCER' };
const job: Job = { id: 'job', title: 'Work', description: 'Brief', budgetUsd: 999, status: 'AWAITING_PAYMENT', clientUserId: client.id, freelancerId: freelancer.id, createdAt: null,
  contract: { id: 'contract', milestoneId: 'milestone', status: 'PENDING_FUNDING', milestoneStatus: 'PENDING_FUNDING', amount: '500.00', currency: 'USD', deliveryDueAt: null, reviewWindowHours: 72, maxRevisions: 2, revisionsUsed: 0, deliverables: [], acceptanceCriteria: [] } };
const ready = { paymentMethodId: 'BANK_ACCOUNT_ON_FILE' as const, ready: true, bankCode: 'BIDV' as const, maskedAccountNumber: '••••1234' };
const result = (status: FundingResponse['fundingStatus']): FundingResponse => ({ fundingTransactionId: 'tx', fundingStatus: status, simulation: true, providerReference: null, nextAction: 'WAIT', retryAfterSeconds: 5 });
let host: HTMLDivElement, root: Root;
let updated: ReturnType<typeof vi.fn>;
beforeEach(() => {
  sessionStorage.clear(); host = document.createElement('div'); document.body.append(host); root = createRoot(host); updated = vi.fn();
  vi.spyOn(api, 'clientBank').mockResolvedValue(ready); vi.spyOn(api, 'funding').mockResolvedValue(null); vi.spyOn(api, 'job').mockResolvedValue(job);
});
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.useRealTimers(); });
async function mount(user = client) { await act(async () => root.render(<FundingPanel job={job} user={user} onJobUpdated={updated} />)); }
function button(text: string) { return [...host.querySelectorAll('button')].find(b => b.textContent === text)!; }
async function click(text: string) { await act(async () => button(text).click()); }
async function fund() { await click('Funding mô phỏng'); await click('Xác nhận funding'); }
async function input(index: number, value: string) { const el = host.querySelectorAll('input')[index]; await act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, value); el.dispatchEvent(new Event('input', { bubbles: true })); }); }

describe('P06.4A funding', () => {
  it('formats immutable decimals without rounding arithmetic', () => { expect(contractAmount('500')).toBe('500.00'); expect(contractAmount('500.01')).toBe('500.01'); expect(() => contractAmount('1.005')).toThrow(); });
  it('reads latest and requires bank readiness', async () => {
    vi.mocked(api.clientBank).mockResolvedValue({ ...ready, ready: false, maskedAccountNumber: null }); await mount();
    expect(api.funding).toHaveBeenCalledWith('contract', 'milestone'); expect(button('Funding mô phỏng').disabled).toBe(true);
  });
  it('clears full banking values after successful save and renders masked response only', async () => {
    vi.spyOn(api, 'saveClientBank').mockResolvedValue(ready); await mount(); await click('Cập nhật ngân hàng');
    await input(0, '123456781234'); await input(1, 'Client Holder');
    await act(async () => host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    expect(host.querySelector('input')).toBeNull(); expect(host.textContent).toContain('••••1234'); expect(host.textContent).not.toContain('123456781234'); expect(sessionStorage.length).toBe(0);
  });
  it('never reads Client bank or exposes bank/fund controls to Freelancer', async () => { await mount(freelancer); expect(api.clientBank).not.toHaveBeenCalled(); expect(button('Funding mô phỏng')).toBeUndefined(); expect(host.textContent).toContain('Đang chờ Client'); });
  it('confirms contract amount and prevents double-click mutations', async () => {
    const post = vi.spyOn(api, 'fund').mockReturnValue(new Promise(() => {})); await mount(); await click('Funding mô phỏng');
    expect(host.textContent).toContain('500.00 USD · Mô phỏng');
    await act(async () => { button('Xác nhận funding').click(); button('Xác nhận funding').click(); });
    expect(post).toHaveBeenCalledTimes(1); expect(post.mock.calls[0].slice(3)).toEqual(['500.00', 'USD']);
  });
  it('keeps same key after timeout and remount, reconciles before same-key retry', async () => {
    const post = vi.spyOn(api, 'fund').mockRejectedValue(new ApiError('timeout', 0)); await mount(); await fund();
    const key = post.mock.calls[0][2]; expect(api.funding).toHaveBeenCalledTimes(2);
    await act(async () => { root.unmount(); root = createRoot(host); }); await mount();
    await click('Tiếp tục lần funding trước'); await click('Xác nhận funding'); expect(post.mock.calls[1][2]).toBe(key);
  });
  it.each(['UNKNOWN', 'PROCESSING', 'PENDING'] as const)('polls %s exact attempt, disables new mutation and cleans up', async status => {
    vi.useFakeTimers(); vi.mocked(api.funding).mockResolvedValue(result(status)); await mount();
    expect(button('Funding mô phỏng').disabled).toBe(true); if (status === 'UNKNOWN') expect(host.textContent).toContain('Đang đối soát');
    await act(async () => { await vi.advanceTimersByTimeAsync(5000); }); expect(api.funding).toHaveBeenCalledWith('contract', 'milestone', 'tx');
    await act(async () => root.unmount()); root = createRoot(host); const count = vi.mocked(api.funding).mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(60000); }); expect(api.funding).toHaveBeenCalledTimes(count);
  });
  it('FAILED retries with a fresh key', async () => {
    const post = vi.spyOn(api, 'fund').mockResolvedValue(result('FAILED')); await mount(); await fund(); const first = post.mock.calls[0][2];
    await click('Thử funding lại'); await click('Xác nhận funding'); expect(post.mock.calls[1][2]).not.toBe(first);
  });
  it('SUCCEEDED clears recovery key and trusts job refetch', async () => {
    const fresh = { ...job, status: 'IN_PROGRESS' }; vi.mocked(api.job).mockResolvedValue(fresh); vi.spyOn(api, 'fund').mockResolvedValue(result('SUCCEEDED'));
    await mount(); await fund(); expect(updated).toHaveBeenCalledWith(fresh); expect(sessionStorage.getItem(attemptScope('fund', client.id, 'contract', 'milestone'))).toBeNull();
  });
  it('409 refetches job/latest without blindly retrying', async () => {
    const post = vi.spyOn(api, 'fund').mockRejectedValue(new ApiError('conflict', 409, 4017)); await mount(); await fund();
    expect(post).toHaveBeenCalledTimes(1); expect(api.job).toHaveBeenCalled(); expect(api.funding).toHaveBeenCalledTimes(2);
  });
  it('does not update a departed job screen after funding refetch resolves', async () => {
    let resolve!: (job: Job) => void;
    vi.mocked(api.job).mockReturnValue(new Promise(done => { resolve = done; })); vi.spyOn(api, 'fund').mockResolvedValue(result('SUCCEEDED'));
    await mount(); await fund(); await act(async () => root.unmount()); root = createRoot(host);
    await act(async () => resolve({ ...job, status: 'IN_PROGRESS' })); expect(updated).not.toHaveBeenCalled();
  });
  it('4021 directs Client to bank setup without exposing server banking error', async () => {
    vi.spyOn(api, 'fund').mockRejectedValue(new ApiError('123456781234', 422, 4021)); await mount(); await fund();
    expect(host.querySelector('.bank-form')).not.toBeNull(); expect(host.textContent).not.toContain('123456781234');
  });
});

describe('unified payment flow draft', () => {
  it('shows the shared flow and no legacy funding action', async () => {
    const unified: Job = { ...job, contract: { ...job.contract!, paymentRail: 'UNIFIED_USDC_PAYOUT' } };
    const timeline = vi.spyOn(api, 'paymentFlow').mockResolvedValue({
      paymentFlowId: 'flow-1', version: 0, jobId: job.id, contractId: 'contract', milestoneId: 'milestone',
      paymentRail: 'UNIFIED_USDC_PAYOUT', termsStatus: 'DRAFT', grossUsd: '100.00',
      escrowUsdc: '100.000000', platformFeeUsd: '3.00', network: null, mint: null,
      quoteSource: null, quoteExpiresAt: null, fundingExpiresAt: null, deliveryDueAt: null,
      reviewWindowHours: 72, maxRevisions: 2, jobStatus: 'AWAITING_PAYMENT',
      contractStatus: 'PENDING_FUNDING', steps: [{ kind: 'USD_ORDER', status: 'NOT_STARTED',
        amount: null, currency: null, provider: null, reference: null, evidenceSource: null,
        retryAfter: null, confirmedAt: null }], evidence: [], simulation: true,
    });

    await act(async () => root.render(<FundingPanel job={unified} user={client} onJobUpdated={updated} />));

    expect(timeline).toHaveBeenCalledWith('contract', 'milestone');
    expect(host.textContent).toContain('Bắt đầu thanh toán');
    expect(host.textContent).toContain('Giữ tiền cho công việc');
    expect(host.querySelectorAll('.payment-journey-stage')).toHaveLength(8);
    expect(host.textContent).not.toContain('flow-1');
    expect(button('Funding mô phỏng')).toBeUndefined();
    expect(button('Chọn ký quỹ Solana')).toBeUndefined();
    expect(button('Chọn ký quỹ đối tác mock')).toBeUndefined();
    expect(api.funding).not.toHaveBeenCalled();
    expect(button('Bắt đầu thanh toán')?.disabled).toBe(false);
  });

  it('asks a new Client for the payer bank before the USD order can be opened', async () => {
    const unified: Job = { ...job, contract: { ...job.contract!, paymentRail: 'UNIFIED_USDC_PAYOUT' } };
    vi.spyOn(api, 'clientBank').mockResolvedValue({ ...ready, ready: false, bankCode: null, maskedAccountNumber: null } as never);
    const save = vi.spyOn(api, 'saveClientBank').mockResolvedValue(ready);
    vi.spyOn(api, 'paymentFlow').mockResolvedValue({
      paymentFlowId: 'flow-1', version: 0, jobId: job.id, contractId: 'contract', milestoneId: 'milestone',
      paymentRail: 'UNIFIED_USDC_PAYOUT', termsStatus: 'DRAFT', grossUsd: '100.00',
      escrowUsdc: '100.000000', platformFeeUsd: '3.00', network: 'localnet', mint: 'mint',
      quoteSource: null, quoteExpiresAt: null, fundingExpiresAt: null, deliveryDueAt: null,
      reviewWindowHours: 72, maxRevisions: 2, jobStatus: 'AWAITING_PAYMENT',
      contractStatus: 'PENDING_FUNDING', steps: [{ kind: 'USD_ORDER', status: 'NOT_STARTED',
        amount: null, currency: null, provider: null, reference: null, evidenceSource: null,
        retryAfter: null, confirmedAt: null }], evidence: [], simulation: true,
    });

    await act(async () => root.render(<FundingPanel job={unified} user={client} onJobUpdated={updated} />));
    expect(button('Bắt đầu thanh toán')?.disabled).toBe(true);
    const form = host.querySelector<HTMLFormElement>('form[aria-label="Tài khoản ngân hàng Client"]')!;
    const [number, holder] = Array.from(form.querySelectorAll<HTMLInputElement>('input'));
    const setValue = (input: HTMLInputElement, value: string) => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value);
      input.dispatchEvent(new Event('input', { bubbles: true }));
    };
    await act(async () => { setValue(number, '123456789'); setValue(holder, 'CLIENT NAME'); });
    await act(async () => { form.requestSubmit(); });

    expect(save).toHaveBeenCalledWith({ bankCode: 'VIETCOMBANK', bankAccountNumber: '123456789', bankAccountHolderName: 'CLIENT NAME' });
    expect(button('Bắt đầu thanh toán')?.disabled).toBe(false);
  });
});
