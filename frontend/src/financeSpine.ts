import type { FundingResponse, Job, JobPaymentStatus, TaxRecord } from './types';
import type { AvalanchePayoutView } from './avalancheTypes';
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
  tax: TaxRecord | null, fundingError = '', taxError = '', payout: AvalanchePayoutView | null = null): MoneyStage[] {
  const stages = marketplaceMoneyStages(job, record, funding, tax, fundingError, taxError);
  if (!avalancheContract(job) || stages.some(stage => stage.key === 'refund')) return stages;
  // Avalanche rail (owner decision 2026-11-21): funding and release are chain records; at release the contract sends the
  // escrowed USDC straight to the freelancer's linked wallet; the VND payout is the freelancer's own request through the mock
  // partner; the certificate is a simulated MISA record under Decree 117/2025 (PIT 2% + VAT 5%), never deducted from the wallet.
  const released = avalancheReleased(job);
  const waiting = { tone: 'pending' as const, status: 'Chờ giải ngân trên chuỗi', error: null };
  return stages.map(stage => {
    if (stage.key === 'funding' && !funding && avalancheFunded(job))
      return { ...stage, tone: 'done' as const, status: avalancheFundedLabel, note: 'Khoản tiền được giữ trong ký quỹ Avalanche (mô phỏng).', error: null };
    if (stage.key === 'onchain') return { ...stage, ...(released
      ? { tone: 'done' as const, status: 'USDC đã vào ví Freelancer (mô phỏng)', error: null } : waiting),
      note: 'Khi giải ngân, contract chuyển toàn bộ khoản ký quỹ thẳng vào ví đã liên kết của Freelancer trong cùng giao dịch.' };
    if (stage.key === 'vnd') return { ...stage, ...avalancheVndStage(released, payout), timestamp: payout?.fiatConfirmedAt ?? payout?.transferConfirmedAt ?? null };
    if (stage.key === 'tax') return { ...stage, ...(released ? avalancheTaxStage(tax, taxError) : waiting),
      note: 'Sàn khấu trừ thay theo Nghị định 117/2025: TNCN 2% và GTGT 5% trên doanh thu quy đổi VND (mô phỏng). USDC trong ví Freelancer không bị trừ.',
      facts: [
        ...(tax?.taxableIncomeVnd != null ? [{ label: 'Doanh thu chịu thuế', value: vnd(tax.taxableIncomeVnd) }] : []),
        ...(tax?.taxWithheldVnd != null ? [{ label: 'TNCN 2%', value: vnd(tax.taxWithheldVnd) }] : []),
        ...(tax?.vatWithheldVnd != null ? [{ label: 'GTGT 5%', value: vnd(tax.vatWithheldVnd) }] : []),
      ] };
    return stage;
  });
}

function avalancheVndStage(released: boolean, payout: AvalanchePayoutView | null): Partial<MoneyStage> {
  const note = 'Tùy chọn: Freelancer tự yêu cầu đổi USDC sang VND qua đối tác mô phỏng ở mục “Chi trả VND”.';
  if (!released) return { tone: 'pending', status: 'Chờ giải ngân trên chuỗi', error: null, note, facts: [] };
  if (!payout) return { tone: 'pending', status: 'Freelancer chưa yêu cầu chi trả VND', error: null, note, facts: [] };
  const facts = payout.fiatReference ? [{ label: 'Tham chiếu chi trả', value: payout.fiatReference }] : [];
  if (payout.fiatPayoutConfirmed) return { tone: 'done', status: 'Đã chi trả VND (mô phỏng)', error: null, note, facts };
  if (payout.status === 'TRANSFER_FAILED' || payout.status === 'FIAT_PAYOUT_FAILED')
    return { tone: 'error', status: 'Chi trả VND chưa thành công', error: payout.failureReason, note, facts };
  return { tone: 'active', status: payout.status === 'AWAITING_USER_TRANSFER' ? 'Chờ Freelancer chuyển USDC cho đối tác'
    : 'Đang xử lý chi trả VND', error: null, note, facts };
}

function avalancheTaxStage(tax: TaxRecord | null, taxError: string): Partial<MoneyStage> {
  if (taxError) return { tone: 'error', status: 'Chưa đọc được chứng từ', error: taxError };
  if (!tax) return { tone: 'active', status: 'Đang lập chứng từ MISA (mô phỏng)', error: null };
  if (['EXPORT_FAILED', 'REJECTED', 'CANCELLED'].includes(tax.status))
    return { tone: 'error', status: tax.statusLabel || tax.status, error: null };
  return { tone: tax.status === 'ACCEPTED' ? 'done' : 'active', status: tax.statusLabel || tax.status, error: null,
    timestamp: tax.lastSyncedAt || tax.updatedAt || tax.createdAt };
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
