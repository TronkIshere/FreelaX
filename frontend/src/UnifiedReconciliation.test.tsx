// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from './api';
import { UnifiedReconciliation } from './UnifiedReconciliation';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
let host: HTMLDivElement, root: Root;
const boundary = (status: string, code: string) => ({ status, code, evidenceSource: 'SRC', observedAt: '2026-10-09T00:00:00Z', blocksNextAction: status !== 'MATCHED' });
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); });

it('shows per-currency totals separately and offers expired cancellation only for unfunded USDC', async () => {
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

  await act(async () => root.render(<UnifiedReconciliation />));

  const totals = host.querySelector('[aria-label="Tổng hợp theo đồng tiền"]')!;
  expect(totals.textContent).toContain('30 USD');
  expect(totals.textContent).toContain('20 USDC');
  expect(totals.textContent).toContain('USDC · 1 bước UNKNOWN');
  expect(totals.textContent).not.toContain('50');
  expect(host.querySelector('[aria-label="Hủy hợp đồng quá hạn ký quỹ c1"]')).toBeTruthy();
  expect(host.querySelector('[aria-label="Hủy hợp đồng quá hạn ký quỹ c2"]')).toBeNull();
  expect(host.textContent).toContain('Chuyển xử lý · VAULT_TO_RECIPIENT · Checked provider');
});
