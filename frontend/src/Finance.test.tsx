// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from './api';
import { FinanceHome, TaxRecordDetail, TaxRecordsPage } from './Finance';
import type { Job, JobPaymentStatus, TaxRecord, User } from './types';

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
    expect(host.textContent).toContain('Thu nhập theo từng công việc');
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
