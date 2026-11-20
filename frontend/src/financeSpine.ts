import type { FundingResponse, Job, JobPaymentStatus, TaxRecord } from './types';
import { type ContractFinance, type EvidenceStage, paymentStages, financialMoneyTone,
  settlementMoneyLabel, settlementStageLabel, decimal, usdc, vnd, maskedBank, avalancheContract, avalancheFunded, avalancheFundedLabel, avalancheReleased, avalancheReleaseLabel } from './financeStatus';
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
  const stages = marketplaceMoneyStages(job, record, funding, tax, fundingError, taxError);
  if (!avalancheContract(job) || stages.some(stage => stage.key === 'refund')) return stages;
  // Avalanche rail: funding and release are chain records (no marketplace funding/settlement record), and the freelancer
  // receives simulated USDC in their own wallet at release; the legacy wallet/VND/certificate pipeline does not apply.
  return stages.map(stage => stage.key === 'funding' && !funding && avalancheFunded(job)
    ? { ...stage, tone: 'done' as const, status: avalancheFundedLabel, note: 'Khoản tiền được giữ trong ký quỹ Avalanche (mô phỏng).', error: null }
    : ['onchain', 'vnd', 'tax'].includes(stage.key)
      ? { ...stage, tone: 'pending' as const, applicable: false, status: 'Không áp dụng cho ký quỹ Avalanche', error: null,
        note: 'Khi giải ngân, Freelancer nhận USDC mô phỏng thẳng vào ví đã liên kết; chi trả VND (nếu có) do Freelancer tự yêu cầu.' }
      : stage);
}

function marketplaceMoneyStages(job: Job, record: ContractFinance, funding: FundingResponse | null,
  tax: TaxRecord | null, fundingError: string, taxError: string): MoneyStage[] {
  const { settlement, cancellation } = record;
  const cancelled = job.contract?.status === 'CANCELLED' || cancellation?.cancellationStatus === 'CANCELLED';
  const refund = cancelled || job.contract?.milestoneStatus === 'REFUND_PENDING' || cancellation?.cancellationStatus === 'REFUND_PENDING';
  const amount = [{ label: 'Giá trị hợp đồng', value: decimal(job.contract!.amount) + ' ' + job.contract!.currency }];
  const taxFailure = tax && ['EXPORT_FAILED', 'REJECTED', 'CANCELLED'].includes(tax.status);
  const downstream = (key: string, title: string, status?: string, error?: string | null): MoneyStage => ({
    key, title, tone: refund ? 'pending' : status ? status === 'NOT_STARTED' ? 'pending' : financialMoneyTone(status) : 'pending',
    status: refund ? 'Không thuộc luồng hoàn tiền' : status ? settlementStageLabel(status) : record.error ? 'Chưa xác minh' : 'Chưa bắt đầu',
    note: refund ? 'Công việc đang đi theo hướng hoàn tiền cho Client.' : 'Đang chờ hệ thống xác nhận bước này.',
    error: refund ? null : error, facts: [], applicable: !refund,
  });
  return [
    { key: 'funding', title: 'Client giữ tiền cho công việc', tone: fundingError ? 'error' : funding ? financialMoneyTone(funding.fundingStatus) : 'pending',
      status: fundingError ? 'Chưa đọc được nạp tiền' : funding ? fundingLabel(funding.fundingStatus) : 'Chưa có bản ghi nạp tiền',
      note: funding?.fundingStatus === 'SUCCEEDED' ? 'Khoản tiền đã được giữ; Freelancer có thể bắt đầu.' : 'Đang chờ Client hoàn tất việc giữ tiền.', error: fundingError, facts: amount, applicable: !cancelled },
    { key: refund ? 'refund' : 'release', title: refund ? 'Hoàn tiền cho Client' : 'Trả tiền cho Freelancer',
      tone: !refund && !settlement && avalancheReleased(job) ? 'done' as const
        : record.error && !(refund ? cancellation : settlement) ? 'error' : financialMoneyTone(refund ? cancellation?.refundStatus : settlement?.moneyStatus),
      status: refund ? cancellation?.refundStatus ? refundLabel(cancellation.refundStatus) : cancelled ? 'Hợp đồng đã hủy; chưa có xác nhận hoàn tiền' : 'Chưa có xác nhận hoàn tiền'
        : settlement ? settlementMoneyLabel(settlement.moneyStatus) : avalancheReleased(job) ? avalancheReleaseLabel(job) : record.error ? 'Chưa xác minh giải ngân' : 'Chưa có bản ghi giải ngân',
      note: refund ? 'Sau khi hủy, tiền được trả lại cho Client khi khoản hoàn được xác nhận.' : 'Sau khi kết quả được duyệt, tiền bắt đầu chuyển cho Freelancer.',
      error: refund ? cancellation?.lastError : settlement?.lastError,
      facts: amount, applicable: !(cancelled && !cancellation?.refundStatus) },
    downstream('onchain', 'Xác nhận chuyển tiền trong ví', settlement?.onChainStatus, settlement?.onChainError),
    downstream('vnd', 'Chuyển VND', settlement?.offRampStatus, settlement?.offRampError),
    { ...downstream('tax', 'Chứng từ thuế', settlement?.taxStatus, settlement?.taxError),
      ...(!refund ? { tone: taxError || taxFailure || ['FAILED', 'FAILED_RETRYABLE'].includes(settlement?.taxStatus || '') ? 'error' as const
        : tax?.status === 'ACCEPTED' ? 'done' as const : tax || settlement?.taxStatus === 'SUCCEEDED' ? 'active' as const : settlement?.taxStatus === 'PROCESSING' || settlement?.taxStatus === 'UNKNOWN' ? 'active' as const : 'pending' as const,
      status: taxError ? 'Chưa đọc được chứng từ' : tax ? tax.statusLabel || tax.status : settlement?.taxStatus === 'SUCCEEDED' ? 'Đã lập / khôi phục; chưa xác minh chứng từ' : settlement ? settlementStageLabel(settlement.taxStatus) : 'Chưa có chứng từ',
      timestamp: tax?.lastSyncedAt || tax?.updatedAt || tax?.createdAt,
      error: taxError || settlement?.taxError } : {}),
      note: 'Chứng từ được lập sau khi khoản chi được xác nhận; xem chứng từ để kiểm tra số tiền.' },
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
