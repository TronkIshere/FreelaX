import { describe, expect, it } from 'vitest';
import { contractFinanceLabel, contractFinanceNeedsRefresh, maskedBank, paymentStages, paymentTerminal, rateSource, safeExplorerUrl, vnd } from './financeStatus';
import type { ContractCancellationRecord, ContractSettlement, Job, JobPaymentStatus, TaxRecord } from './types';

const status = (values: Partial<JobPaymentStatus> = {}) => ({
  checkoutOrderStatus: 'CAPTURED', simulation: true, network: 'devnet',
  onRampStatus: 'CONFIRMED', clientPaymentStatus: 'CONFIRMED',
  onChainOffRampStatus: 'CONFIRMED', offRampStatus: 'COMPLETED',
  taxExportStatus: 'SUCCESS', ...values,
}) as JobPaymentStatus;
const certificate = { status: 'ACCEPTED', statusLabel: 'Cơ quan thuế đã chấp nhận' } as TaxRecord;

describe('financial evidence mapping', () => {
  it('keeps checkout, Mock USDC, withdrawal, simulated VND and certificate as five distinct stages', () => {
    const stages = paymentStages(status(), certificate);
    expect(stages).toHaveLength(5);
    expect(stages.map(stage => stage.tone)).toEqual(['done', 'done', 'done', 'done', 'done']);
    expect(stages[1].status).toContain('Mock USDC');
    expect(stages[3].note).toContain('không xác nhận chuyển khoản ngân hàng thật');
    expect(stages[4].status).toBe('Cơ quan thuế đã chấp nhận');
    expect(paymentTerminal(status())).toBe(true);
  });

  it('preserves previous evidence when a later stage fails and stops polling terminal failures', () => {
    const failed = status({ onChainOffRampStatus: 'FAILED', onChainOffRampError: 'request rejected',
      offRampStatus: 'NOT_STARTED', taxExportStatus: 'NOT_ATTEMPTED' });
    const stages = paymentStages(failed, null);
    expect(stages[0].tone).toBe('done');
    expect(stages[1].tone).toBe('done');
    expect(stages[2]).toMatchObject({ tone: 'error', error: 'request rejected' });
    expect(stages[3].tone).toBe('pending');
    expect(paymentTerminal(failed)).toBe(true);
    expect(paymentTerminal(status({ offRampStatus: 'COMPLETION_SUBMITTED' }))).toBe(false);
  });

  it('never exposes a raw bank number or treats a placeholder rate as live', () => {
    expect(maskedBank('1234567890')).toBe('•••• 7890');
    expect(maskedBank('******6789')).toBe('•••• 6789');
    expect(rateSource('FALLBACK_PLACEHOLDER')).toContain('giả lập');
    expect(vnd(null)).toBe('—');
    expect(safeExplorerUrl('https://explorer.solana.com/tx/abc?cluster=devnet', 'devnet')).not.toBeNull();
    expect(safeExplorerUrl('javascript:alert(1)', 'devnet')).toBeNull();
    expect(safeExplorerUrl('https://explorer.solana.com/tx/abc?cluster=mainnet', 'devnet')).toBeNull();
  });
});

describe('P1 independent contract financial truth', () => {
  const job = { status: 'IN_PROGRESS', contract: { status: 'ACTIVE', milestoneStatus: 'FUNDED' } } as Job;
  const release = { moneyStatus: 'SUCCEEDED', onChainStatus: 'FAILED', offRampStatus: 'UNKNOWN', taxStatus: 'NOT_STARTED', retryable: false } as ContractSettlement;
  it('retains confirmed primary money while unresolved downstream evidence needs refresh', () => {
    const value = { settlement: release, cancellation: null, error: '' };
    expect(contractFinanceLabel(job, value)).toBe('Release đã xác nhận');
    expect(contractFinanceNeedsRefresh(job, value)).toBe(true);
    expect(contractFinanceNeedsRefresh(job, { ...value, settlement: { ...release, offRampStatus: 'FAILED', taxStatus: 'SUCCEEDED' } })).toBe(false);
  });
  it('never invents refund proof from a refund-pending milestone or cancelled contract', () => {
    const pending = { ...job, contract: { ...job.contract!, milestoneStatus: 'REFUND_PENDING' } };
    const value = { settlement: null, cancellation: null, error: '' };
    expect(contractFinanceLabel(pending, value)).toBe('Chưa có xác nhận hoàn tiền');
    expect(contractFinanceNeedsRefresh(pending, value)).toBe(true);
    expect(contractFinanceLabel({ ...job, status: 'CANCELLED' }, value)).toBe('Hợp đồng đã hủy');
  });
  it('stops financial polling only for proven refund success or non-retryable failure', () => {
    const cancellation = { cancellationStatus: 'REFUND_PENDING', refundStatus: 'UNKNOWN', retryable: true } as ContractCancellationRecord;
    const value = { settlement: null, cancellation, error: '' };
    expect(contractFinanceNeedsRefresh(job, value)).toBe(true);
    expect(contractFinanceNeedsRefresh(job, { ...value, cancellation: { ...cancellation, refundStatus: 'FAILED', retryable: false } })).toBe(false);
    const success = { ...value, cancellation: { ...cancellation, cancellationStatus: 'CANCELLED' as const, refundStatus: 'SUCCEEDED' as const, retryable: false } };
    expect(contractFinanceLabel(job, success)).toBe('Hoàn tiền đã xác nhận');
    expect(contractFinanceNeedsRefresh(job, success)).toBe(false);
  });
});
