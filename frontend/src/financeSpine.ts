import type { FundingResponse, Job, JobPaymentStatus, TaxRecord } from './types';
import { type ContractFinance, type EvidenceStage, paymentStages, financialMoneyTone,
  settlementMoneyLabel, settlementStageLabel, decimal, usdc, vnd, maskedBank } from './financeStatus';
import { fundingLabel, refundLabel } from './status';

export interface MoneyStage extends EvidenceStage {
  facts: { label: string; value: string }[];
  timestamp?: string | null;
  applicable?: boolean;
}

// Presentation only. Each stage keeps its own server evidence; completing funding
// never confirms release, and export SUCCESS never confirms certificate ACCEPTED.
export function legacyMoneyStages(job: Job, payment: JobPaymentStatus, tax: TaxRecord | null): MoneyStage[] {
  const stages = paymentStages(payment, tax);
  if ((!payment.onRampStatus || payment.onRampStatus === 'NOT_STARTED') &&
      (!payment.clientPaymentStatus || payment.clientPaymentStatus === 'NOT_STARTED')) stages[1].tone = 'pending';
  const present = (label: string, value: string | number | null | undefined, format: (value: string | number) => string) =>
    value == null || value === '' ? [] : [{ label, value: format(value) }];
  return stages.map((stage, index) => ({ ...stage, facts: [
    [{ label: 'Giá trị công việc', value: '$' + job.budgetUsd.toFixed(2) }],
    [...present('USDC theo bản ghi', payment.amountUsdcReceived, usdc),
      ...present('Mạng', payment.network, String)],
    [...present('Mạng', payment.network, String)],
    [...present('VND dự kiến', payment.estimatedAmountVnd, vnd),
      ...present('VND trước phí off-ramp', payment.amountVndBeforeOffRampFee, vnd),
      ...present('Phí off-ramp', payment.offRampFeeVnd, vnd),
      ...(payment.payoutBankAccountNumber ? [{ label: 'Ngân hàng nhận',
        value: (payment.payoutBankCode || '') + ' · ' + maskedBank(payment.payoutBankAccountNumber) }] : [])],
    [...present('Thu nhập chịu thuế', tax?.taxableIncomeVnd, vnd),
      ...present('Thuế khấu trừ', tax?.taxWithheldVnd, vnd)],
  ][index], timestamp: [null, payment.clientPaymentConfirmedAt || payment.clientPaymentSubmittedAt,
    payment.withdrawalConfirmedAt || payment.withdrawalSubmittedAt,
    payment.offRampCompletedAt || payment.offRampCompletionSubmittedAt || payment.simulatedPayoutAt,
    tax?.lastSyncedAt || tax?.updatedAt || tax?.createdAt][index] }));
}

export function contractMoneyStages(job: Job, record: ContractFinance, funding: FundingResponse | null,
  tax: TaxRecord | null, fundingError = '', taxError = ''): MoneyStage[] {
  const { settlement, cancellation } = record;
  const cancelled = job.contract?.status === 'CANCELLED' || cancellation?.cancellationStatus === 'CANCELLED';
  const refund = cancelled || job.contract?.milestoneStatus === 'REFUND_PENDING' || cancellation?.cancellationStatus === 'REFUND_PENDING';
  const amount = [{ label: 'Giá trị hợp đồng', value: decimal(job.contract!.amount) + ' ' + job.contract!.currency }];
  const taxFailure = tax && ['EXPORT_FAILED', 'REJECTED', 'CANCELLED'].includes(tax.status);
  const downstream = (key: string, title: string, status?: string, error?: string | null): MoneyStage => ({
    key, title, tone: refund ? 'pending' : status ? status === 'NOT_STARTED' ? 'pending' : financialMoneyTone(status) : 'pending',
    status: refund ? 'Không thuộc luồng hoàn tiền' : status ? settlementStageLabel(status) : record.error ? 'Chưa xác minh' : 'Chưa bắt đầu',
    note: refund ? 'Hồ sơ hủy/hoàn tiền không xác nhận release hoặc chi trả cho Freelancer.' : 'Bằng chứng riêng từ bản ghi settlement của Marketplace.',
    error: refund ? null : error, facts: [], applicable: !refund,
  });
  return [
    { key: 'funding', title: 'Funding Client', tone: fundingError ? 'error' : funding ? financialMoneyTone(funding.fundingStatus) : 'pending',
      status: fundingError ? 'Chưa đọc được funding' : funding ? fundingLabel(funding.fundingStatus) : 'Chưa có bản ghi funding',
      note: 'Funding đã xác nhận không có nghĩa Freelancer đã nhận release.', error: fundingError, facts: amount, applicable: !cancelled },
    { key: refund ? 'refund' : 'release', title: refund ? 'Hoàn tiền Client' : 'Release cho Freelancer',
      tone: record.error && !(refund ? cancellation : settlement) ? 'error' : financialMoneyTone(refund ? cancellation?.refundStatus : settlement?.moneyStatus),
      status: refund ? cancellation?.refundStatus ? refundLabel(cancellation.refundStatus) : cancelled ? 'Hợp đồng đã hủy; chưa có xác nhận hoàn tiền' : 'Chưa có xác nhận hoàn tiền'
        : settlement ? settlementMoneyLabel(settlement.moneyStatus) : record.error ? 'Chưa xác minh release' : 'Chưa có bản ghi release',
      note: 'Bản ghi tiền chính độc lập với funding và các chặng xử lý sau đó.',
      error: refund ? cancellation?.lastError : settlement?.lastError,
      facts: amount, applicable: !(cancelled && !cancellation?.refundStatus) },
    downstream('onchain', 'Bằng chứng on-chain', settlement?.onChainStatus, settlement?.onChainError),
    downstream('vnd', 'Chi trả VND', settlement?.offRampStatus, settlement?.offRampError),
    { ...downstream('tax', 'Chứng từ thuế', settlement?.taxStatus, settlement?.taxError),
      ...(!refund ? { tone: taxError || taxFailure || ['FAILED', 'FAILED_RETRYABLE'].includes(settlement?.taxStatus || '') ? 'error' as const
        : tax?.status === 'ACCEPTED' ? 'done' as const : tax || settlement?.taxStatus === 'SUCCEEDED' ? 'active' as const : settlement?.taxStatus === 'PROCESSING' || settlement?.taxStatus === 'UNKNOWN' ? 'active' as const : 'pending' as const,
      status: taxError ? 'Chưa đọc được chứng từ' : tax ? tax.statusLabel || tax.status : settlement?.taxStatus === 'SUCCEEDED' ? 'Đã lập / khôi phục; chưa xác minh chứng từ' : settlement ? settlementStageLabel(settlement.taxStatus) : 'Chưa có chứng từ',
      timestamp: tax?.lastSyncedAt || tax?.updatedAt || tax?.createdAt,
      error: taxError || settlement?.taxError } : {}),
      note: 'Kết quả lập/xuất chứng từ không xác nhận cơ quan thuế đã ACCEPTED.' },
  ];
}

// Current focus is the first actual failure, otherwise the first unfinished
// applicable stage. A pending stage is waiting for evidence, NOT "processing".
// Current is attention order only; it never changes financial tone/status/finality.
// No clock, animation callback or mutation optimism participates in this resolver.
export function resolveMoneySpine(stages: MoneyStage[]) {
  let current = stages.findIndex(stage => stage.applicable !== false && stage.tone === 'error');
  if (current < 0) current = stages.findIndex(stage => stage.applicable !== false && stage.tone !== 'done');
  return { current, resolved: stages.every(stage => stage.applicable === false || stage.tone === 'done') };
}
