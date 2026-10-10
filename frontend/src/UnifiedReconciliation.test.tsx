// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from './api';
import { UnifiedReconciliation } from './UnifiedReconciliation';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
let host: HTMLDivElement, root: Root;
const boundary = (status: string, code: string) => ({ status, code, evidenceSource: 'SRC', observedAt: '2026-10-09T00:00:00Z', blocksNextAction: status !== 'MATCHED' });
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); });

it('shows per-currency totals separately, groups flows and offers expired cancellation only for unfunded USDC', async () => {
  vi.spyOn(api, 'unifiedLedgerSummary').mockResolvedValue({ observedAt: '2026-10-09T00:00:00Z', flows: 2, simulation: true,
    currencies: [
      { currency: 'USD', unknownSteps: 0, buckets: [{ code: 'RECEIVED', label: 'Đối tác đã xác nhận nhận', amount: 30, flows: 2 }] },
      { currency: 'USDC', unknownSteps: 1, buckets: [{ code: 'VAULT', label: 'Khóa trong vault', amount: 20, flows: 1 }] },
      { currency: 'VND', unknownSteps: 0, buckets: [] }] });
  vi.spyOn(api, 'unifiedReconciliation').mockResolvedValue([
    { paymentFlowId: 'flow-unfunded', jobId: 'j1', contractId: 'c1', milestoneId: 'm1', grossUsd: 10, escrowUsdc: 10, simulation: true,
      usdToClientUsdc: boundary('MATCHED', 'USD_USDC_MATCH'), clientUsdcToVault: boundary('PENDING', 'VAULT_PENDING'),
      vaultToRecipient: boundary('PENDING', 'TERMINAL_PENDING'), withdrawalToFiat: boundary('PENDING', 'WITHDRAWAL_PENDING'), reviews: [] },
    { paymentFlowId: 'flow-vault', jobId: 'j2', contractId: 'c2', milestoneId: 'm2', grossUsd: 20, escrowUsdc: 20, simulation: true,
      usdToClientUsdc: boundary('MATCHED', 'USD_USDC_MATCH'), clientUsdcToVault: boundary('MATCHED', 'VAULT_MATCH'),
      vaultToRecipient: boundary('PENDING', 'TERMINAL_PENDING'), withdrawalToFiat: boundary('PENDING', 'WITHDRAWAL_PENDING'),
      reviews: [{ boundary: 'VAULT_TO_RECIPIENT', decision: 'ESCALATED', note: 'Checked provider', adminId: 'a', reviewedAt: '2026-10-09T00:00:00Z' }] }]);

  await act(async () => root.render(<MemoryRouter><UnifiedReconciliation /></MemoryRouter>));

  const totals = host.querySelector('[aria-label="Tổng hợp theo đồng tiền"]')!;
  expect(totals.textContent).toContain('30 USD');
  expect(totals.textContent).toContain('20 USDC');
  expect(totals.querySelectorAll('.recon-badge')).toHaveLength(1);
  expect(totals.querySelector('.recon-badge')?.closest('article')?.querySelector('h3')?.textContent).toBe('USDC');
  expect(totals.querySelector('.recon-badge')?.textContent).toBe('1 bước chưa rõ');
  expect(totals.textContent).not.toContain('50');
  // Nothing is mismatched, so the list opens on "Tất cả" with both flows as running rows.
  const filters = host.querySelector('[aria-label="Lọc theo tình trạng"]')!;
  expect(filters.querySelector('[aria-pressed="true"]')?.textContent).toBe('Tất cả2');
  expect(filters.textContent).toContain('Đang chạy2');
  const rows = host.querySelectorAll('.recon-row');
  expect(rows).toHaveLength(2);
  expect(rows[0].textContent).toContain('Ví Client → vaultĐang chờ');
  // Details, forms and history stay collapsed until the Admin opens a row.
  expect(host.querySelector('[aria-label="Hủy hợp đồng quá hạn ký quỹ c1"]')).toBeNull();
  for (const button of host.querySelectorAll<HTMLButtonElement>('.recon-row-actions button'))
    await act(async () => button.click());
  expect(host.querySelector('[aria-label="Hủy hợp đồng quá hạn ký quỹ c1"]')).toBeTruthy();
  expect(host.querySelector('[aria-label="Hủy hợp đồng quá hạn ký quỹ c2"]')).toBeNull();
  const history = host.querySelector('[aria-label="Lịch sử quyết định Admin"]')!;
  expect(history.textContent).toContain('Chuyển xử lý · Vault → người nhận');
  expect(history.textContent).toContain('Checked provider');
});

it('opens on flows that need attention and filters by id', async () => {
  vi.spyOn(api, 'unifiedLedgerSummary').mockRejectedValue(new Error('offline'));
  vi.spyOn(api, 'unifiedReconciliation').mockResolvedValue([
    { paymentFlowId: 'flow-bad', jobId: 'job-bad', contractId: 'c1', milestoneId: 'm1', grossUsd: 10, escrowUsdc: 10, simulation: true,
      usdToClientUsdc: boundary('MATCHED', 'USD_USDC_MATCH'), clientUsdcToVault: boundary('MISMATCH', 'VAULT_MISMATCH'),
      vaultToRecipient: boundary('PENDING', 'TERMINAL_PENDING'), withdrawalToFiat: boundary('PENDING', 'WITHDRAWAL_PENDING'), reviews: [] },
    { paymentFlowId: 'flow-ok', jobId: 'job-ok', contractId: 'c2', milestoneId: 'm2', grossUsd: 20, escrowUsdc: 20, simulation: true,
      usdToClientUsdc: boundary('MATCHED', 'A'), clientUsdcToVault: boundary('MATCHED', 'B'),
      vaultToRecipient: boundary('MATCHED', 'C'), withdrawalToFiat: boundary('MATCHED', 'D'), reviews: [] }]);

  await act(async () => root.render(<MemoryRouter><UnifiedReconciliation /></MemoryRouter>));

  const pressed = () => host.querySelector('[aria-label="Lọc theo tình trạng"] [aria-pressed="true"]')?.textContent;
  expect(pressed()).toBe('Cần xử lý1');
  expect(host.querySelectorAll('.recon-row')).toHaveLength(1);
  expect(host.querySelector('.recon-row')!.textContent).toContain('Lệch');
  const all = [...host.querySelectorAll<HTMLButtonElement>('.recon-kpi')].find(button => button.textContent?.startsWith('Tất cả'))!;
  await act(async () => all.click());
  expect(host.querySelectorAll('.recon-row')).toHaveLength(2);
  const search = host.querySelector<HTMLInputElement>('input[type="search"]')!;
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(search, 'job-ok');
    search.dispatchEvent(new Event('input', { bubbles: true }));
  });
  expect(host.querySelectorAll('.recon-row')).toHaveLength(1);
  expect(host.querySelector('.recon-row')!.textContent).toContain('Đã khớp đủ');
});
