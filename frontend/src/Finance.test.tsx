// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
import { FinanceHome, TaxRecordDetail, TaxRecordsPage } from './Finance';
import { contractMoneyStages, legacyMoneyStages, resolveMoneySpine } from './financeSpine';
import type { ContractCancellationRecord, ContractSettlement, Job, JobPaymentStatus, TaxRecord, User } from './types';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const freelancer: User = { id: 'freelancer-1', email: 'freelancer@example.test', displayName: 'Freelancer', userType: 'FREELANCER' };
const client: User = { id: 'client-1', email: 'client@example.test', displayName: 'Client', userType: 'CLIENT' };
const job: Job = { id: 'job-1', title: 'Editorial work', description: 'Real job', budgetUsd: 300,
  status: 'COMPLETED', clientUserId: client.id, freelancerId: freelancer.id,
  createdAt: '2026-09-29T01:00:00' };
const payment = { jobId: job.id, checkoutOrderId: 'checkout-1', checkoutOrderStatus: 'CAPTURED',
  simulation: true, network: 'devnet', onRampStatus: 'CONFIRMED', clientPaymentStatus: 'CONFIRMED',
  onChainOffRampStatus: 'CONFIRMED', offRampStatus: 'COMPLETED', taxExportStatus: 'SUCCESS',
  amountUsdcReceived: 298.5, estimatedAmountVnd: 7440112, payoutBankCode: 'BIDV',
  payoutBankAccountNumber: '1234567890', offRampFeeVnd: 22388,
  usdcToVndRateSource: 'FALLBACK_PLACEHOLDER', usdcToVndRate: 25000,
} as JobPaymentStatus;
const tax = { id: 'tax-1', jobId: job.id, jobTitle: job.title, freelancerId: freelancer.id,
  clientUserId: client.id, status: 'ACCEPTED', statusLabel: 'Cơ quan thuế đã chấp nhận',
  taxableIncomeVnd: 7779918, taxWithheldVnd: 777992, misaCertificateId: 'misa-1',
  amountUsd: 300, usdToVndRate: 25933.06, rateSource: 'LIVE_OPEN_ER_API',
} as TaxRecord;
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
  vi.useRealTimers();
  vi.restoreAllMocks();
});
async function render(element: React.ReactNode, path = '/finance') {
  await act(async () => { root.render(<MemoryRouter initialEntries={[path]}>{element}</MemoryRouter>); });
}
function button(label: string) {
  return [...host.querySelectorAll('button')].find(item => item.textContent?.trim() === label);
}

describe('P05.4 financial screens', () => {
  it('shows only completed participant jobs as Freelancer income with real summaries', async () => {
    vi.spyOn(api, 'myJobs').mockResolvedValue({ currentPage: 0, pageSize: 100, totalPages: 1,
      totalElements: 2, data: [job, { ...job, id: 'working-job', title: 'Unfinished work', status: 'IN_PROGRESS' }] });
    vi.spyOn(api, 'paymentStatus').mockResolvedValue(payment);
    await render(<FinanceHome user={freelancer} />);
    expect(host.textContent).toContain('Thu nhập theo công việc');
    expect(host.textContent).toContain('Editorial work');
    expect(host.textContent).not.toContain('Unfinished work');
    expect(host.textContent).toContain('Mock USDC');
    expect(host.textContent).toContain('Mô phỏng');
    expect(host.querySelector('a[href="/finance?jobId=job-1"]')).not.toBeNull();
  });

  it('renders five source-backed stages, simulated outcome, masked bank and collapsed proof', async () => {
    vi.spyOn(api, 'job').mockResolvedValue(job);
    vi.spyOn(api, 'paymentStatus').mockResolvedValue(payment);
    vi.spyOn(api, 'taxRecordForJob').mockResolvedValue(tax);
    await render(<FinanceHome user={client} />, '/finance?jobId=job-1');
    expect(host.querySelectorAll('.finance-stage')).toHaveLength(5);
    expect(host.textContent).toContain('Mô phỏng');
    expect(host.textContent).toContain('DEVNET');
    expect(host.textContent).toContain('Cơ quan thuế đã chấp nhận');
    expect(host.textContent).toContain('Tỷ giá giả lập / placeholder');
    expect(host.querySelector('.finance-support')?.textContent).toContain('•••• 7890');
    expect(host.querySelector('.finance-support')?.textContent).not.toContain('1234567890');
    expect(host.querySelector('.technical-evidence')?.hasAttribute('open')).toBe(false);
    expect(host.querySelector('a[href="/finance/tax-records/tax-1"]')).not.toBeNull();
  });

  it('lists real tax records and exposes accepted status', async () => {
    vi.spyOn(api, 'taxRecords').mockResolvedValue({ currentPage: 0, pageSize: 10,
      totalPages: 1, totalElements: 1, data: [tax] });
    await render(<TaxRecordsPage />, '/finance/tax-records');
    expect(host.textContent).toContain('Cơ quan thuế đã chấp nhận');
    expect(host.textContent).toContain('7.779.918 VND');
    expect(host.querySelector('a[href="/finance/tax-records/tax-1"]')).not.toBeNull();
  });

  it('gates certificate actions by status and completed payout', async () => {
    const taxRead = vi.spyOn(api, 'taxRecord').mockResolvedValue(tax);
    vi.spyOn(api, 'paymentStatus').mockResolvedValue(payment);
    await render(<Routes><Route path="/finance/tax-records/:taxRecordId" element={<TaxRecordDetail />} /></Routes>,
      '/finance/tax-records/tax-1');
    expect(taxRead).toHaveBeenCalledExactlyOnceWith('tax-1');
    expect(host.querySelector('.finance-nav a[aria-current="page"]')?.getAttribute('href')).toBe('/finance/tax-records');
    expect(button('Tải PDF')).toBeTruthy();
    expect(button('Tải XML')).toBeTruthy();
    expect(button('Đồng bộ trạng thái')).toBeUndefined();
    expect(button('Lập lại chứng từ')).toBeUndefined();
  });

  it('offers retry only for failed export when payout is completed', async () => {
    vi.spyOn(api, 'taxRecord').mockResolvedValue({ ...tax, status: 'EXPORT_FAILED',
      statusLabel: 'Lập chứng từ thất bại', misaCertificateId: null });
    vi.spyOn(api, 'paymentStatus').mockResolvedValue(payment);
    vi.spyOn(api, 'retryTaxExport').mockResolvedValue({ ...tax, status: 'DRAFT',
      statusLabel: 'Đã lập chứng từ, chưa phát hành' });
    await render(<Routes><Route path="/finance/tax-records/:taxRecordId" element={<TaxRecordDetail />} /></Routes>,
      '/finance/tax-records/tax-1');
    expect(button('Lập lại chứng từ')).toBeTruthy();
    expect(button('Tải PDF')).toBeUndefined();
    await act(async () => { button('Lập lại chứng từ')!.click(); });
    expect(api.retryTaxExport).toHaveBeenCalledExactlyOnceWith('tax-1');
    expect(host.textContent).toContain('Đã lập chứng từ, chưa phát hành');
  });

  it('does not schedule a refresh after terminal payment state', async () => {
    vi.useFakeTimers();
    vi.spyOn(api, 'job').mockResolvedValue(job);
    const paymentRead = vi.spyOn(api, 'paymentStatus').mockResolvedValue(payment);
    vi.spyOn(api, 'taxRecordForJob').mockResolvedValue(tax);
    await render(<FinanceHome user={freelancer} />, '/finance?jobId=job-1');
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
    expect(paymentRead).toHaveBeenCalledTimes(1);
  });
});

it('polls a non-terminal payment once and stops after a terminal response', async () => {
  vi.useFakeTimers();
  vi.spyOn(api, 'job').mockResolvedValue(job);
  const paymentRead = vi.spyOn(api, 'paymentStatus')
    .mockResolvedValueOnce({ ...payment, offRampStatus: 'SIMULATED', taxExportStatus: 'NOT_ATTEMPTED' })
    .mockResolvedValue(payment);
  vi.spyOn(api, 'taxRecordForJob').mockResolvedValue(tax);
  await render(<FinanceHome user={freelancer} />, '/finance?jobId=job-1');
  expect(paymentRead).toHaveBeenCalledTimes(1);
  await act(async () => { await vi.advanceTimersByTimeAsync(15000); });
  expect(paymentRead).toHaveBeenCalledTimes(2);
  await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
  expect(paymentRead).toHaveBeenCalledTimes(2);
});

const contractJob: Job = { ...job, status: 'SUBMITTED_FOR_REVIEW', checkoutOrderId: 'checkout-1',
  contract: { id: 'contract', milestoneId: 'milestone', status: 'UNDER_REVIEW', milestoneStatus: 'RELEASE_PENDING',
    amount: '300.00', currency: 'USD', deliveryDueAt: null, reviewWindowHours: 72, maxRevisions: 2,
    revisionsUsed: 0, deliverables: [], acceptanceCriteria: [] } };
const release: ContractSettlement = { contractId: 'contract', milestoneId: 'milestone', jobId: job.id,
  amount: 300, currency: 'USD', simulation: true, moneyStatus: 'SUCCEEDED', onChainStatus: 'SUCCEEDED',
  offRampStatus: 'SUCCEEDED', taxStatus: 'SUCCEEDED', releaseReference: 'sim-release', onChainReference: null,
  offRampReference: null, taxReference: null, lastError: null, onChainError: null, offRampError: null,
  taxError: null, retryable: false, createdAt: '2026-10-05T00:00:00', updatedAt: '2026-10-05T00:00:00' };
const cancellation: ContractCancellationRecord = { cancellationId: 'cancel', contractId: 'contract', milestoneId: 'milestone',
  cancellationStatus: 'REQUESTED', refundStatus: null, refundReference: null, simulation: true, requestedBy: client.id,
  decidedBy: null, reasonCode: 'MUTUAL_CANCELLATION', reason: 'Scope changed', amount: '300.00', currency: 'USD',
  requestedAt: '2026-10-05T00:00:00Z', decidedAt: null, updatedAt: '2026-10-05T00:00:00Z', retryable: false,
  lastError: null, allowedActions: ['ACCEPT', 'REJECT'] };

describe('P1 contract financial evidence', () => {
  beforeEach(() => {
    vi.spyOn(api, 'job').mockResolvedValue(contractJob);
    vi.spyOn(api, 'settlement').mockResolvedValue(null);
    vi.spyOn(api, 'cancellation').mockResolvedValue(null);
    vi.spyOn(api, 'funding').mockResolvedValue({ fundingTransactionId: 'funding', fundingStatus: 'SUCCEEDED', simulation: true,
      providerReference: null, nextAction: 'WAIT', retryAfterSeconds: null });
    vi.spyOn(api, 'paymentStatus').mockResolvedValue(payment);
    vi.spyOn(api, 'taxRecordForJob').mockRejectedValue(new ApiError('No tax record', 404));
  });
  const evidence = () => render(<FinanceHome user={client} />, '/finance?jobId=job-1');

  it('shows a release-pending job in Finance before job completion', async () => {
    vi.spyOn(api, 'myJobs').mockResolvedValue({ currentPage: 0, pageSize: 20, totalPages: 1, totalElements: 1, data: [contractJob] });
    vi.mocked(api.settlement).mockResolvedValue({ ...release, moneyStatus: 'UNKNOWN', retryable: true });
    await render(<FinanceHome user={freelancer} />);
    expect(host.textContent).toContain('Editorial work');
    expect(host.textContent).toContain('Đang đối soát release');
    expect(api.myJobs).toHaveBeenCalledExactlyOnceWith(0, 20);
    expect(api.paymentStatus).not.toHaveBeenCalled();
  });
  it('does not infer release or refund from CAPTURED checkout or confirmed funding', async () => {
    await evidence();
    expect(host.querySelector('[aria-label="Tiền chính của hợp đồng"]')?.textContent).toContain('Chưa có bản ghi release');
    expect(host.textContent).toContain('Funding đã xác nhận');
    expect(host.textContent).not.toContain('Release đã xác nhận');
    expect(host.textContent).not.toContain('Hoàn tiền đã xác nhận');
    expect(host.querySelector('.finance-stages')).toBeNull();
    expect(host.querySelectorAll('.contract-money-spine > li')).toHaveLength(5);
  });
  it('distinguishes failed reads from empty records and offers one read-only reconciliation action', async () => {
    vi.mocked(api.settlement).mockRejectedValue(new Error('Settlement unavailable'));
    vi.mocked(api.cancellation).mockRejectedValue(new Error('Cancellation unavailable'));
    const request = vi.spyOn(api, 'requestCancellation');
    const decision = vi.spyOn(api, 'decideCancellation');
    await evidence();
    const primary = host.querySelector('[aria-label="Tiền chính của hợp đồng"]')!;
    expect(primary.textContent).toContain('Chưa xác minh hồ sơ tài chính');
    expect(primary.textContent).toContain('Chưa xác minh release');
    expect(primary.textContent).toContain('Chưa xác minh hoàn tiền');
    expect(primary.textContent).not.toContain('Chưa có bản ghi release');
    expect(primary.classList.contains('finance-primary-error')).toBe(true);
    expect([...host.querySelectorAll('button')].filter(item => item.textContent === 'Đối chiếu lại')).toHaveLength(1);
    expect(button('Thử lại')).toBeUndefined();
    vi.mocked(api.settlement).mockResolvedValue(null);
    vi.mocked(api.cancellation).mockResolvedValue(null);
    await act(async () => button('Đối chiếu lại')!.click());
    expect(api.settlement).toHaveBeenCalledTimes(2);
    expect(request).not.toHaveBeenCalled(); expect(decision).not.toHaveBeenCalled();
    expect(host.querySelector('.finance-primary')?.textContent).toContain('Chưa có bản ghi release');
  });
  it('keeps technical evidence empty and collapsed without a wall of missing fields', async () => {
    await evidence();
    const disclosure = host.querySelector('details')!;
    expect(disclosure.hasAttribute('open')).toBe(false);
    expect(disclosure.textContent).toContain('Chưa có tham chiếu kỹ thuật');
    expect(disclosure.querySelector('dl')).toBeNull();
  });
  it('shows only allowlisted technical references, never unexpected sensitive response fields', async () => {
    vi.mocked(api.settlement).mockResolvedValue({ ...release, providerSecret: 'TEST_ONLY_PROVIDER_SECRET',
      releaseKey: 'TEST_ONLY_INTERNAL_KEY', accessToken: 'TEST_ONLY_TOKEN', bankAccountNumber: '9876543210987654321' } as ContractSettlement);
    await evidence();
    const disclosure = host.querySelector('details')!;
    expect(disclosure.textContent).toContain('Tham chiếu release');
    expect(disclosure.textContent).toContain('sim-release');
    expect(disclosure.querySelectorAll('dt')).toHaveLength(1);
    expect(host.textContent).not.toContain('TEST_ONLY_');
    expect(host.textContent).not.toContain('9876543210987654321');
    expect(disclosure.hasAttribute('open')).toBe(false);
  });
  it('keeps an active refund-pending record visible on the financial list', async () => {
    const active = { ...contractJob, status: 'IN_PROGRESS', contract: { ...contractJob.contract!, status: 'ACTIVE', milestoneStatus: 'REFUND_PENDING' } };
    vi.spyOn(api, 'myJobs').mockResolvedValue({ currentPage: 0, pageSize: 20, totalPages: 1, totalElements: 1, data: [active] });
    vi.mocked(api.cancellation).mockResolvedValue({ ...cancellation, cancellationStatus: 'REFUND_PENDING', refundStatus: 'FAILED_RETRYABLE', retryable: true });
    await render(<FinanceHome user={client} />);
    expect(host.textContent).toContain('Editorial work');
    expect(host.textContent).toContain('Máy chủ đang thử hoàn tiền lại');
    expect(host.querySelector('a[href="/finance?jobId=job-1"]')).not.toBeNull();
  });
  it('keeps primary release confirmed through downstream failure without claiming ACCEPTED', async () => {
    vi.mocked(api.settlement).mockResolvedValue({ ...release, onChainStatus: 'FAILED', onChainError: 'chain failure', retryable: true });
    await evidence();
    expect(host.querySelector('[aria-label="Tiền chính của hợp đồng"]')?.textContent).toContain('Release đã xác nhận');
    expect(host.querySelector('.finance-primary-done')).not.toBeNull();
    expect(host.textContent).toContain('Đã xác nhận bản ghi release mô phỏng');
    expect(host.textContent).toContain('Không xác nhận tiền đã về ngân hàng thật');
    expect(host.textContent).toContain('Mô phỏng');
    expect(host.textContent).not.toContain('Cơ quan thuế đã chấp nhận');
    expect(host.querySelector('details')?.hasAttribute('open')).toBe(false);
    expect(host.querySelector('[aria-label="Xử lý sau release"]')?.textContent).toContain('•••• 7890');
    expect(host.textContent).not.toContain('1234567890');
  });
  it('uses the actual accepted certificate and its existing destination', async () => {
    vi.mocked(api.settlement).mockResolvedValue(release);
    vi.mocked(api.taxRecordForJob).mockResolvedValue(tax);
    await evidence();
    expect(host.textContent).toContain('Cơ quan thuế đã chấp nhận');
    expect(host.querySelector('a[href="/finance/tax-records/tax-1"]')).not.toBeNull();
  });
  it.each(['REQUESTED', 'REJECTED'] as const)('keeps %s cancellation distinct from final cancellation/refund', async status => {
    vi.mocked(api.cancellation).mockResolvedValue({ ...cancellation, cancellationStatus: status });
    await evidence();
    const region = host.querySelector('[aria-label="Đề nghị hủy và hoàn tiền"]')!;
    expect(region.textContent).toContain('công việc tiếp tục');
    expect(region.textContent).not.toContain('Hoàn tiền đã xác nhận');
    expect(region.textContent).not.toContain('Hợp đồng đã hủy');
  });
  it('shows refund pending in Finance with an active job and no false bank refund', async () => {
    vi.mocked(api.job).mockResolvedValue({ ...contractJob, status: 'IN_PROGRESS', contract: { ...contractJob.contract!, status: 'ACTIVE', milestoneStatus: 'FUNDED' } });
    vi.mocked(api.cancellation).mockResolvedValue({ ...cancellation, cancellationStatus: 'REFUND_PENDING', refundStatus: 'UNKNOWN', retryable: true });
    await evidence();
    expect(host.textContent).toContain('Đang đối soát hoàn tiền');
    expect(host.textContent).toContain('chưa phải hủy và hoàn tiền cuối cùng');
    expect(host.querySelector('.finance-statement')?.textContent).not.toContain('Hợp đồng đã hủy');
    expect(button('Funding mô phỏng')).toBeUndefined();
  });
  it('renders confirmed refund and stops polling after final cancellation', async () => {
    vi.useFakeTimers();
    vi.mocked(api.job).mockResolvedValue({ ...contractJob, status: 'CANCELLED', contract: { ...contractJob.contract!, status: 'CANCELLED', milestoneStatus: 'REFUNDED' } });
    vi.mocked(api.cancellation).mockResolvedValue({ ...cancellation, cancellationStatus: 'CANCELLED', refundStatus: 'SUCCEEDED', refundReference: 'sim-refund' });
    await evidence();
    expect(host.querySelector('.finance-statement')?.textContent).toContain('Hoàn tiền đã xác nhận');
    expect(host.textContent).toContain('Đã xác nhận bản ghi hoàn tiền mô phỏng');
    expect(host.textContent).toContain('Không xác nhận hoàn tiền ngân hàng thật');
    expect(host.textContent).toContain('Hợp đồng đã hủy');
    await act(async () => { await vi.advanceTimersByTimeAsync(90000); });
    expect(api.cancellation).toHaveBeenCalledTimes(1);
    expect(api.paymentStatus).not.toHaveBeenCalled();
  });
  it('keeps polling contract recovery even if the legacy payment projection is terminal', async () => {
    vi.useFakeTimers();
    vi.mocked(api.settlement).mockResolvedValue({ ...release, offRampStatus: 'FAILED_RETRYABLE', retryable: true });
    await evidence();
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
    expect(api.settlement).toHaveBeenCalledTimes(2);
    expect(host.querySelector('.finance-statement')?.textContent).toContain('Release đã xác nhận');
    vi.mocked(api.settlement).mockResolvedValue(release);
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
    await act(async () => { await vi.advanceTimersByTimeAsync(60000); });
    expect(api.settlement).toHaveBeenCalledTimes(3);
  });
  it('pauses automatic reads while hidden and resumes once visible', async () => {
    vi.useFakeTimers();
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    await evidence();
    await act(async () => { await vi.advanceTimersByTimeAsync(60000); });
    expect(api.settlement).toHaveBeenCalledTimes(1);
    visibility.mockReturnValue('visible');
    await act(async () => document.dispatchEvent(new Event('visibilitychange')));
    expect(api.settlement).toHaveBeenCalledTimes(2);
  });
  it('does not offer funding for pre-funding cancellation without a refund record', async () => {
    vi.mocked(api.job).mockResolvedValue({ ...contractJob, status: 'CANCELLED', checkoutOrderId: null,
      contract: { ...contractJob.contract!, status: 'CANCELLED', milestoneStatus: 'CANCELLED' } });
    vi.mocked(api.cancellation).mockResolvedValue({ ...cancellation, cancellationStatus: 'CANCELLED' });
    vi.mocked(api.funding).mockResolvedValue(null);
    await evidence();
    expect(host.querySelector('.finance-statement')?.textContent).toContain('Hợp đồng đã hủy');
    expect(host.textContent).not.toContain('Hoàn tiền đã xác nhận');
    expect(host.querySelector('.funding-panel')).toBeNull();
  });
  it('polls pending release to confirmed, preserves proof on a later read error and cancels on unmount', async () => {
    vi.useFakeTimers();
    vi.mocked(api.settlement).mockResolvedValueOnce(null).mockResolvedValueOnce({ ...release, onChainStatus: 'UNKNOWN' })
      .mockRejectedValue(new Error('Read unavailable'));
    await evidence();
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
    expect(host.querySelector('.finance-statement')?.textContent).toContain('Release đã xác nhận');
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
    expect(host.querySelector('.finance-statement')?.textContent).toContain('Release đã xác nhận');
    expect(host.textContent).toContain('Read unavailable');
    act(() => root.unmount()); root = createRoot(host);
    await act(async () => { await vi.advanceTimersByTimeAsync(90000); });
    expect(api.settlement).toHaveBeenCalledTimes(3);
  });
  it('ignores late evidence from a previously opened job', async () => {
    let resolve!: (value: ContractSettlement) => void;
    vi.mocked(api.settlement).mockImplementationOnce(() => new Promise(done => { resolve = done; }));
    await evidence();
    act(() => root.unmount()); root = createRoot(host);
    vi.mocked(api.job).mockResolvedValue({ ...contractJob, id: 'other', title: 'Other contract' });
    await render(<FinanceHome user={client} />, '/finance?jobId=other');
    await act(async () => resolve(release));
    expect(host.textContent).toContain('Other contract');
    expect(host.querySelector('.finance-statement')?.textContent).not.toContain('Release đã xác nhận');
  });
});

describe('P06.5B single-job money evidence spine', () => {
  const working = { ...payment, offRampStatus: 'SIMULATED', taxExportStatus: 'NOT_ATTEMPTED',
    clientPaymentConfirmedAt: '2026-10-06T01:15:00', withdrawalConfirmedAt: '2026-10-06T01:18:00',
    paymentTransactionSignature: 'real-returned-signature' };
  beforeEach(() => {
    vi.spyOn(api, 'job').mockResolvedValue({ ...job, category: 'SEO_CONTENT' });
    vi.spyOn(api, 'paymentStatus').mockResolvedValue(working);
    vi.spyOn(api, 'taxRecordForJob').mockRejectedValue(new ApiError('No record', 404));
  });
  const detail = () => render(<FinanceHome user={client} />, '/finance?jobId=job-1');

  it.each([client, freelancer])('keeps the shared detail grammar and role-correct context for $userType', async user => {
    vi.mocked(api.paymentStatus).mockResolvedValue({ ...payment, network: 'localnet' });
    vi.mocked(api.taxRecordForJob).mockResolvedValue({ ...tax, status: 'EXPORT_FAILED', statusLabel: 'Lập chứng từ thất bại' });
    await render(<FinanceHome user={user} />, '/finance?jobId=job-1');
    const receiver = user.userType === 'FREELANCER';
    expect(host.querySelector('.eyebrow')?.textContent).toBe(receiver ? 'Freelancer / Thu nhập' : 'Client / Thanh toán');
    expect(host.querySelector('h1')?.textContent).toBe('Chi tiết hồ sơ tiền.');
    expect(host.querySelector('.page-head p')?.textContent).toBe(receiver
      ? 'Theo dõi release, chi trả và chứng từ của công việc này.'
      : 'Theo dõi dòng tiền của một công việc, từ thanh toán đến chứng từ thuế.');
    expect(host.querySelector('.finance-detail-back a')?.textContent?.trim()).toBe(receiver ? 'Lịch sử thu nhập' : 'Danh sách thanh toán');
    expect(host.querySelector('.finance-detail-back a')?.getAttribute('href')).toBe('/finance');
    expect(host.querySelector('.finance-list-page')).toBeNull();
    expect(host.querySelectorAll('.money-spine-item')).toHaveLength(5);
    expect(host.querySelectorAll('.stage-done.money-spine-item')).toHaveLength(4);
    const current = host.querySelector('[aria-current="step"]')!;
    expect(current.getAttribute('data-stage')).toBe('tax');
    expect(current.querySelector('.money-stage-badge')?.textContent).toContain('Cần kiểm tra');
    expect(current.textContent).not.toContain('Đang xử lý');
    expect(host.querySelector('.finance-statement-value')?.textContent).toContain('$300.00');
    expect(host.querySelector('.finance-statement-value')?.textContent).toContain('298,5 Mock USDC');
    expect(host.querySelector('.finance-statement-value')?.textContent).toContain('7.440.112 VND dự kiến');
    expect(host.querySelector('.finance-case-note')?.textContent).toContain('Mô phỏng');
    expect(host.querySelector('[data-stage="settlement"]')?.textContent).toContain('localnet');
    expect(host.textContent).not.toContain('DEVNET');
    expect(host.textContent).not.toContain('1234567890');
    expect(host.querySelector('.technical-evidence')?.hasAttribute('open')).toBe(false);
    expect([...host.querySelectorAll('button')].map(item => item.textContent?.trim())).toEqual(['Làm mới từ Marketplace']);
  });

  it('renders one job statement and spine, never the frozen Finance list', async () => {
    await detail();
    expect(api.job).toHaveBeenCalledExactlyOnceWith('job-1');
    expect(host.querySelectorAll('.finance-job-statement')).toHaveLength(1);
    expect(host.querySelector('.finance-list-page')).toBeNull();
    expect(host.querySelectorAll('.money-spine-item')).toHaveLength(5);
    expect(host.querySelector('.job-family-art')?.getAttribute('data-family')).toBe('seo');
    expect(host.querySelector('.finance-case-summary')?.textContent).toContain('Editorial work');
    expect(host.querySelector('a[href="/finance"]')?.textContent).toContain('Danh sách thanh toán');
  });

  it('resolves completed/current/upcoming from actual stage statuses with semantic connectors', async () => {
    await detail();
    const items = [...host.querySelectorAll('.money-spine-item')];
    expect(items.map(item => item.getAttribute('data-state'))).toEqual(['done', 'done', 'done', 'active', 'pending']);
    expect(host.querySelector('[aria-current="step"]')?.getAttribute('data-stage')).toBe('vnd');
    expect(host.querySelectorAll('.connector-completed')).toHaveLength(3);
    expect(host.querySelectorAll('.connector-current')).toHaveLength(1);
    expect(host.querySelectorAll('.connector-upcoming')).toHaveLength(1);
    expect(host.querySelector('[data-stage="withdrawal"] h3')?.textContent).toBe('Rút on-chain');
  });

  it('does not advance when local time or animation completes; advances after a new API response', async () => {
    vi.useFakeTimers();
    await detail();
    await act(async () => {
      host.querySelector('.money-stage-record')!.dispatchEvent(new Event('animationend', { bubbles: true }));
      await vi.advanceTimersByTimeAsync(14000);
    });
    expect(host.querySelector('[aria-current="step"]')?.getAttribute('data-stage')).toBe('vnd');
    expect(api.paymentStatus).toHaveBeenCalledTimes(1);
    vi.mocked(api.paymentStatus).mockResolvedValue(payment);
    vi.mocked(api.taxRecordForJob).mockResolvedValue({ ...tax, status: 'SUBMITTED', statusLabel: 'Đã gửi cơ quan thuế' });
    await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
    expect(host.querySelector('[aria-current="step"]')?.getAttribute('data-stage')).toBe('tax');
    expect(host.querySelector('[data-stage="vnd"]')?.getAttribute('data-state')).toBe('done');
    expect(host.textContent).not.toContain('Cơ quan thuế đã chấp nhận');
  });

  it('shows simulation/devnet truth, real timestamps and only returned technical references', async () => {
    await detail();
    expect(host.querySelector('.finance-job-statement')?.textContent).toContain('Mô phỏng');
    expect(host.querySelector('.finance-job-statement')?.textContent).toContain('DEVNET');
    expect(host.querySelector('[data-stage="settlement"] time')?.getAttribute('datetime')).toBe(working.clientPaymentConfirmedAt);
    expect(host.querySelector('[data-stage="checkout"] time')).toBeNull();
    const technical = host.querySelector('.technical-evidence')!;
    expect(technical.hasAttribute('open')).toBe(false);
    expect(technical.textContent).toContain('real-returned-signature');
    expect(host.querySelector('.money-spine')?.textContent).not.toContain('real-returned-signature');
    expect(host.textContent).not.toContain('1234567890');
  });

  it('omits unavailable optional money, dates, bank and technical evidence rather than fabricating it', async () => {
    vi.mocked(api.paymentStatus).mockResolvedValue({ ...working, amountUsdcReceived: null, estimatedAmountVnd: null,
      amountVndBeforeOffRampFee: null, offRampFeeVnd: null, payoutBankAccountNumber: null,
      paymentTransactionSignature: null, clientPaymentConfirmedAt: null, withdrawalConfirmedAt: null });
    await detail();
    expect(host.querySelector('.finance-job-statement')?.textContent).not.toContain('VND');
    expect(host.querySelector('.finance-case-summary')?.textContent).not.toContain('Mock USDC');
    expect(host.querySelector('.money-spine time')).toBeNull();
    expect(host.querySelector('[data-stage="vnd"]')?.textContent).not.toContain('BIDV');
    expect(host.querySelector('.technical-evidence')?.textContent).not.toContain('real-returned-signature');
  });

  it('never treats successful export as an accepted certificate; all accepted stages have no invented current step', async () => {
    vi.mocked(api.paymentStatus).mockResolvedValue(payment);
    await detail();
    expect(host.querySelector('[data-stage="tax"]')?.getAttribute('data-state')).toBe('pending');
    expect(host.textContent).not.toContain('Cơ quan thuế đã chấp nhận');
    vi.mocked(api.taxRecordForJob).mockResolvedValue(tax);
    await act(async () => button('Làm mới từ Marketplace')!.click());
    expect(host.querySelectorAll('.stage-done.money-spine-item')).toHaveLength(5);
    expect(host.querySelector('[aria-current="step"]')).toBeNull();
    expect(host.querySelector('.finance-case-focus')?.textContent).toContain('Các chặng đã xác nhận');
  });

  it('emphasizes actual failed evidence without changing already confirmed stages', async () => {
    vi.mocked(api.taxRecordForJob).mockResolvedValue({ ...tax, status: 'EXPORT_FAILED', statusLabel: 'Lập chứng từ thất bại' });
    await detail();
    expect(host.querySelector('[aria-current="step"]')?.getAttribute('data-stage')).toBe('tax');
    expect(host.querySelector('[data-stage="tax"]')?.getAttribute('data-state')).toBe('error');
    expect(host.querySelectorAll('.stage-done.money-spine-item')).toHaveLength(3);
  });

  it.each(['clientPayment', 'tax'] as const)('keeps current %s failure as attention, never processing', async source => {
    vi.mocked(api.paymentStatus).mockResolvedValue(source === 'clientPayment'
      ? { ...payment, clientPaymentStatus: 'FAILED' } : payment);
    if (source === 'tax') vi.mocked(api.taxRecordForJob).mockResolvedValue({ ...tax,
      status: 'EXPORT_FAILED', statusLabel: 'Lập chứng từ thất bại' });
    await detail();
    const current = host.querySelector('[aria-current="step"]')!;
    expect(current.getAttribute('data-stage')).toBe(source === 'tax' ? 'tax' : 'settlement');
    expect(current.classList.contains('money-spine-current')).toBe(true);
    expect(current.getAttribute('data-state')).toBe('error');
    expect(current.querySelector('.money-stage-badge')?.textContent).toContain('Cần kiểm tra');
    expect(current.textContent).not.toContain('Đang xử lý');
    expect(current.querySelector('.money-stage-badge')?.textContent).not.toContain('Đang đối soát');
  });

  it('keeps the first unfinished NOT_STARTED stage current and pending without claiming processing', async () => {
    vi.mocked(api.paymentStatus).mockResolvedValue({ ...payment, onRampStatus: 'NOT_STARTED',
      clientPaymentStatus: 'NOT_STARTED', onChainOffRampStatus: 'NOT_STARTED',
      offRampStatus: 'NOT_STARTED', taxExportStatus: 'NOT_ATTEMPTED' });
    await detail();
    const current = host.querySelector('[aria-current="step"]')!;
    expect(current.getAttribute('data-stage')).toBe('settlement');
    expect(current.getAttribute('data-state')).toBe('pending');
    expect(current.querySelector('.money-stage-badge')?.textContent).toContain('Chờ bằng chứng');
    expect(current.querySelector('.money-stage-status')?.textContent).toBe('Chưa bắt đầu · Chưa bắt đầu');
    expect(current.textContent).not.toContain('Đang xử lý');
    expect(current.textContent).not.toContain('Cần kiểm tra');
  });

  it('does not propagate an earlier payment failure into upcoming NOT_STARTED or NOT_ATTEMPTED stages', async () => {
    vi.mocked(api.paymentStatus).mockResolvedValue({ ...payment, clientPaymentStatus: 'FAILED',
      onChainOffRampStatus: 'NOT_STARTED', offRampStatus: 'NOT_STARTED', taxExportStatus: 'NOT_ATTEMPTED' });
    await detail();
    expect(host.querySelector('[aria-current="step"]')?.getAttribute('data-stage')).toBe('settlement');
    for (const key of ['withdrawal', 'vnd', 'tax']) {
      const upcoming = host.querySelector('[data-stage="' + key + '"]')!;
      expect(upcoming.getAttribute('data-state')).toBe('pending');
      expect(upcoming.hasAttribute('aria-current')).toBe(false);
      expect(upcoming.querySelector('.money-stage-badge')?.textContent).toContain('Chờ bằng chứng');
      expect(upcoming.textContent).not.toContain('Cần kiểm tra');
    }
    expect(host.querySelector('[data-stage="tax"] .money-stage-status')?.textContent).toBe('Chưa lập chứng từ');
  });

  it('changes attention selection without mutating an unchanged stage financial tone or status', () => {
    const stages = legacyMoneyStages(job, { ...payment, offRampStatus: 'NOT_STARTED', taxExportStatus: 'NOT_ATTEMPTED' }, null);
    const before = JSON.stringify(stages);
    expect(resolveMoneySpine(stages).current).toBe(3);
    expect(JSON.stringify(stages)).toBe(before);
    const failed = legacyMoneyStages(job, { ...payment, clientPaymentStatus: 'FAILED',
      offRampStatus: 'NOT_STARTED', taxExportStatus: 'NOT_ATTEMPTED' }, null);
    const failedBefore = JSON.stringify(failed);
    expect(resolveMoneySpine(failed).current).toBe(1);
    expect(JSON.stringify(failed)).toBe(failedBefore);
    expect(failed[3]).toEqual(stages[3]); // Same pending evidence, now upcoming instead of current.
  });

  it('distinguishes an unreadable certificate from a confirmed missing record after reconciliation', async () => {
    vi.useFakeTimers();
    vi.mocked(api.taxRecordForJob).mockRejectedValueOnce(new Error('Tax unavailable'));
    await detail();
    expect(host.querySelector('.finance-tax-callout')?.textContent).toContain('Chưa đọc được chứng từ');
    expect(host.querySelector('.finance-tax-callout')?.textContent).not.toContain('Không có bản ghi');
    expect(host.querySelector('[data-stage="tax"]')?.getAttribute('data-state')).toBe('error');
    await act(async () => { await vi.advanceTimersByTimeAsync(15000); });
    expect(host.querySelector('.finance-tax-callout')?.textContent).toContain('Chưa có chứng từ');
    expect(host.querySelector('[data-stage="tax"]')?.getAttribute('data-state')).toBe('pending');
    expect(host.textContent).not.toContain('Tax unavailable');
  });

  it('removes stale legacy evidence after failed reconciliation and retains a read-only retry', async () => {
    await detail();
    vi.mocked(api.paymentStatus).mockRejectedValue(new Error('Payment unavailable'));
    await act(async () => button('Làm mới từ Marketplace')!.click());
    expect(host.querySelector('.money-spine')).toBeNull();
    expect(host.textContent).toContain('Payment unavailable');
    expect(button('Tải lại')).toBeTruthy();
  });

  it.each([client, freelancer])('preserves participant authority for $userType', async user => {
    await render(<FinanceHome user={{ ...user, id: 'outsider' }} />, '/finance?jobId=job-1');
    expect(host.textContent).toContain('Không có quyền xem');
    expect(host.querySelector('.finance-job-statement')).toBeNull();
  });

  it('keeps reduced-motion updates immediate without any animation-controlled state', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
    try {
      await detail();
      vi.mocked(api.paymentStatus).mockResolvedValue(payment);
      vi.mocked(api.taxRecordForJob).mockResolvedValue(tax);
      await act(async () => button('Làm mới từ Marketplace')!.click());
      expect(host.querySelector('[aria-current="step"]')).toBeNull();
      expect((host.querySelector('.money-stage-record') as HTMLElement).style.transform).not.toContain('-3');
    } finally { vi.unstubAllGlobals(); }
  });

  it('keeps not-started legacy evidence pending even when status strings exist', () => {
    const stages = legacyMoneyStages(job, { ...payment, checkoutOrderStatus: 'CREATED', onRampStatus: 'NOT_STARTED',
      clientPaymentStatus: 'NOT_STARTED', onChainOffRampStatus: 'NOT_STARTED', offRampStatus: 'NOT_STARTED', taxExportStatus: 'NOT_ATTEMPTED' }, null);
    expect(stages.every(stage => stage.tone === 'pending')).toBe(true);
    expect(resolveMoneySpine(stages).current).toBe(0);
  });

  it('uses independent contract settlement statuses and keeps final refunds outside the release path', () => {
    const stages = contractMoneyStages(contractJob, { settlement: { ...release, onChainStatus: 'FAILED', onChainError: 'Chain error' }, cancellation: null, error: '' }, null, null);
    expect(stages[1].tone).toBe('done');
    expect(stages[2].tone).toBe('error');
    expect(stages[4].tone).toBe('active'); // Export succeeded; certificate is not yet ACCEPTED.
    expect(resolveMoneySpine(stages).current).toBe(2);
    const refund = contractMoneyStages({ ...contractJob, contract: { ...contractJob.contract!, status: 'CANCELLED' } },
      { settlement: null, cancellation: { ...cancellation, cancellationStatus: 'CANCELLED', refundStatus: 'SUCCEEDED' }, error: '' }, null, null);
    expect(refund[1].title).toBe('Hoàn tiền Client');
    expect(refund.slice(2).every(stage => stage.applicable === false)).toBe(true);
    expect(resolveMoneySpine(refund).current).toBe(-1);
  });
});


describe('P06.5A source-backed finance ledger', () => {
  const list = (jobs: Job[]) => vi.spyOn(api, 'myJobs').mockResolvedValue({ currentPage: 0,
    pageSize: 20, totalPages: 1, totalElements: 98, data: jobs });

  it.each([client, freelancer])('renders the role heading and four honest modules for $userType', async user => {
    list([job]);
    vi.spyOn(api, 'paymentStatus').mockResolvedValue(payment);
    await render(<FinanceHome user={user} />);
    expect(host.querySelector('h1')?.textContent).toBe(user.userType === 'CLIENT'
      ? 'Thanh toán theo công việc.' : 'Thu nhập theo công việc.');
    expect(host.querySelectorAll('.finance-summary-card')).toHaveLength(4);
    expect(host.querySelector('.finance-summary-count')?.textContent).toBe('1');
    expect(host.querySelector('.finance-summary')?.textContent).not.toContain('$');
    expect(host.querySelector('.finance-summary')?.textContent).not.toContain('98');
    expect(host.querySelector('.finance-ledger-heading h2')?.textContent).toBe(user.userType === 'CLIENT'
      ? 'Danh sách thanh toán' : 'Lịch sử thu nhập');
    expect(host.querySelector('.finance-ledger-header')?.textContent).toContain('Ngày cập nhật');
    expect(host.querySelectorAll('.finance-process')).toHaveLength(1);
    expect(host.querySelector('.finance-process details')?.hasAttribute('open')).toBe(false);
    expect(host.querySelectorAll('select')).toHaveLength(0);
  });

  it('reuses real category/skills, detail navigation, simulation and latest financial timestamp', async () => {
    list([{ ...job, category: 'BACKEND_API', skills: ['Java', 'REST API'] }]);
    vi.spyOn(api, 'paymentStatus').mockResolvedValue({ ...payment,
      withdrawalConfirmedAt: '2026-10-04T12:00:00Z', offRampCompletedAt: '2026-10-05T12:00:00Z' });
    await render(<FinanceHome user={client} />);
    expect(host.querySelector('.job-family-art')?.getAttribute('data-family')).toBe('backend');
    expect(host.querySelector('.finance-job-skills')?.textContent).toContain('REST API');
    expect(host.querySelector('time')?.getAttribute('datetime')).toBe('2026-10-05T12:00:00Z');
    expect(host.querySelector('.finance-detail-link')?.getAttribute('href')).toBe('/finance?jobId=job-1');
    expect(host.querySelector('.finance-detail-link')?.textContent).toContain('Xem chi tiết');
    expect(host.querySelector('.finance-detail-link')?.classList.contains('button-primary')).toBe(false);
    expect(host.textContent).not.toContain(payment.payoutBankAccountNumber);
  });

  it('uses a refund rail only for confirmed refunds and keeps cancelled-without-refund neutral', async () => {
    const cancelled = { ...contractJob, contract: { ...contractJob.contract!, status: 'CANCELLED' as const } };
    list([cancelled]);
    vi.spyOn(api, 'settlement').mockResolvedValue(null);
    const read = vi.spyOn(api, 'cancellation').mockResolvedValue({ ...cancellation,
      cancellationStatus: 'CANCELLED', refundStatus: null });
    const downstream = vi.spyOn(api, 'paymentStatus');
    await render(<FinanceHome user={client} />);
    expect(host.querySelector('.finance-ledger-row')?.classList.contains('finance-color-neutral')).toBe(true);
    expect(host.querySelector('.finance-status-badge')?.textContent).toContain('Hợp đồng đã hủy');
    read.mockResolvedValue({ ...cancellation, cancellationStatus: 'CANCELLED', refundStatus: 'SUCCEEDED' });
    await act(async () => { button('Làm mới từ Marketplace')!.click(); });
    expect(host.querySelector('.finance-ledger-row')?.classList.contains('finance-color-refund')).toBe(true);
    expect(host.querySelector('.finance-status-badge')?.textContent).toContain('Hoàn tiền đã xác nhận');
    expect(downstream).not.toHaveBeenCalled();
  });

  it('preserves read errors, absent timestamps and the role shell without claiming release success', async () => {
    list([contractJob]);
    vi.spyOn(api, 'settlement').mockRejectedValue(new Error('Release unavailable'));
    vi.spyOn(api, 'cancellation').mockResolvedValue(null);
    await render(<FinanceHome user={freelancer} />);
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Release unavailable');
    expect(host.querySelector('.finance-status-badge')?.textContent).toContain('Chưa xác minh');
    expect(host.querySelector('.finance-ledger-date')?.textContent).toContain('Chưa có cập nhật');
    expect(host.querySelector('time')).toBeNull();
    expect(host.querySelector('.finance-process')).not.toBeNull();
  });
});


it('keeps exactly two folder destinations and changes current-page semantics on tax navigation', async () => {
  vi.spyOn(api, 'myJobs').mockResolvedValue({ currentPage: 0, pageSize: 20, totalPages: 1, totalElements: 0, data: [] });
  vi.spyOn(api, 'taxRecords').mockResolvedValue({ currentPage: 0, pageSize: 10, totalPages: 1, totalElements: 0, data: [] });
  await render(<Routes>
    <Route path="/finance" element={<FinanceHome user={client} />} />
    <Route path="/finance/tax-records" element={<TaxRecordsPage />} />
  </Routes>);
  const nav = () => host.querySelector('nav[aria-label="Khu vực tài chính"]')!;
  expect(nav().querySelectorAll('a')).toHaveLength(2);
  expect(nav().querySelector('a[aria-current="page"]')?.getAttribute('href')).toBe('/finance');
  expect([...nav().querySelectorAll('.finance-tab-index')].map(index => index.textContent)).toEqual(['01', '02']);
  await act(async () => { nav().querySelector<HTMLAnchorElement>('a[href="/finance/tax-records"]')!.click(); });
  expect(nav().querySelector('a[aria-current="page"]')?.getAttribute('href')).toBe('/finance/tax-records');
  expect(nav().querySelectorAll('a[aria-current="page"]')).toHaveLength(1);
  expect(nav().querySelector('a[href="/finance"]')?.hasAttribute('aria-current')).toBe(false);
});
