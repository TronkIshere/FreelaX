import type { ContractCancellationRecord, ContractSettlement, DecimalValue, Job, JobPaymentStatus, TaxRecord } from './types';
import { cancellationLabel, refundLabel } from './status';

export type ContractFinance = { settlement: ContractSettlement | null; cancellation: ContractCancellationRecord | null; error: string };
export const financePath = (jobId: string) => '/finance?jobId=' + encodeURIComponent(jobId);
export const releaseOwned = (job: Job) => job.contract?.milestoneStatus === 'RELEASE_PENDING' ||
  job.contract?.milestoneStatus === 'RELEASED' || job.contract?.status === 'COMPLETED' || job.status === 'COMPLETED';
export const cancelledContract = (job: Job) => job.contract?.status === 'CANCELLED' ||
  job.contract?.milestoneStatus === 'REFUNDED' || job.status === 'CANCELLED';
export const refundOwned = (job: Job) => job.contract?.milestoneStatus === 'REFUND_PENDING';
export const financialCopy = {
  fundingVsRelease: 'Funding đã xác nhận không có nghĩa Freelancer đã nhận release.',
  releaseSimulation: 'Đã xác nhận bản ghi release mô phỏng cho Freelancer. Không xác nhận tiền đã về ngân hàng thật.',
  refundSimulation: 'Đã xác nhận bản ghi hoàn tiền mô phỏng cho Client. Không xác nhận hoàn tiền ngân hàng thật.',
  taxVsCertificate: 'Trạng thái tạo chứng từ không xác nhận cơ quan thuế đã ACCEPTED. Xem trạng thái riêng trong hồ sơ chứng từ.',
};

export const financialMoneyTone = (status: string | null | undefined): EvidenceTone => status === 'SUCCEEDED' ? 'done'
  : status === 'FAILED' || status === 'FAILED_RETRYABLE' ? 'error'
  : status === 'PROCESSING' || status === 'UNKNOWN' ? 'active' : 'pending';

export function contractFinanceTone(job: Job, value?: ContractFinance): EvidenceTone {
  const cancellation = value?.cancellation;
  if (cancellation?.refundStatus === 'SUCCEEDED') return 'done';
  if (cancellation?.cancellationStatus === 'REFUND_PENDING') return financialMoneyTone(cancellation.refundStatus);
  if (refundOwned(job)) return 'pending';
  if (cancelledContract(job) || cancellation?.cancellationStatus === 'CANCELLED') return 'done';
  if (value?.settlement) return financialMoneyTone(value.settlement.moneyStatus);
  return value?.error ? 'error' : 'pending';
}

export function contractFinanceLabel(job: Job, value?: ContractFinance): string {
  const cancellation = value?.cancellation;
  if (cancellation?.refundStatus === 'SUCCEEDED') return 'Hoàn tiền đã xác nhận';
  if (cancellation?.cancellationStatus === 'REFUND_PENDING') return refundLabel(cancellation.refundStatus);
  if (refundOwned(job)) return 'Chưa có xác nhận hoàn tiền';
  if (cancelledContract(job) || cancellation?.cancellationStatus === 'CANCELLED') return 'Hợp đồng đã hủy';
  if (value?.settlement) return settlementMoneyLabel(value.settlement.moneyStatus);
  if (value?.error) return 'Chưa xác minh hồ sơ tài chính';
  if (releaseOwned(job)) return 'Chưa có bản ghi release';
  if (cancellation) return cancellationLabel(cancellation.cancellationStatus);
  return job.status === 'AWAITING_PAYMENT' ? 'Chờ funding' : 'Funding và hồ sơ hợp đồng';
}

export function contractFinanceNeedsRefresh(job: Job, value: ContractFinance): boolean {
  if (value.error) return true;
  const cancellation = value.cancellation;
  if (cancellation?.refundStatus === 'SUCCEEDED') return false;
  if (cancellation?.cancellationStatus === 'REFUND_PENDING') return cancellation.refundStatus !== 'FAILED' || cancellation.retryable;
  if (refundOwned(job)) return true;
  if (cancelledContract(job) || cancellation?.cancellationStatus === 'CANCELLED') return false;
  if (value.settlement || releaseOwned(job)) return settlementNeedsRefresh(value.settlement);
  // Active contracts may receive a proposal or review decision while this page is open.
  return true;
}

export const settlementMoneyLabel = (status: string) => ({
  PENDING: 'Chờ xác nhận release', PROCESSING: 'Đang xử lý release', UNKNOWN: 'Đang đối soát release',
  SUCCEEDED: 'Release đã xác nhận', FAILED_RETRYABLE: 'Release cần đối soát lại', FAILED: 'Release chưa thành công',
}[status] || 'Chưa xác minh release');
export const settlementStageLabel = (status: string) => ({
  NOT_STARTED: 'Chưa bắt đầu', PROCESSING: 'Đang xử lý', SUCCEEDED: 'Đã xác nhận',
  FAILED_RETRYABLE: 'Máy chủ đang thử lại', FAILED: 'Cần xử lý lỗi', UNKNOWN: 'Đang đối soát',
}[status] || 'Chưa xác minh');

// Primary money and downstream evidence terminate independently.
export function settlementNeedsRefresh(value: ContractSettlement | null): boolean {
  if (!value) return true;
  if (value.moneyStatus === 'FAILED' && !value.retryable) return false;
  if (value.moneyStatus !== 'SUCCEEDED' || value.retryable) return true;
  return [value.onChainStatus, value.offRampStatus, value.taxStatus]
    .some(status => status !== 'SUCCEEDED' && status !== 'FAILED');
}

export type EvidenceTone = 'done' | 'active' | 'pending' | 'error';
export interface EvidenceStage {
  key: string;
  title: string;
  tone: EvidenceTone;
  status: string;
  note: string;
  error?: string | null;
}

const checkout: Record<string, string> = {
  CREATED: 'Đơn thanh toán đã tạo', CAPTURED: 'Đã ghi nhận thanh toán', FAILED: 'Thanh toán thất bại',
};
const onRamp: Record<string, string> = {
  NOT_STARTED: 'Chưa bắt đầu', SUBMITTED: 'Đang xác nhận Mock USDC',
  CONFIRMED: 'Mock USDC đã xác nhận', FAILED: 'Mock on-ramp thất bại',
};
const clientPayment: Record<string, string> = {
  NOT_STARTED: 'Chưa bắt đầu', RATE_SUBMITTED: 'Đã gửi tỷ giá',
  RATE_CONFIRMED: 'Tỷ giá đã xác nhận', INVOICE_SUBMITTED: 'Đang tạo hóa đơn',
  INVOICE_CREATED: 'Đã tạo hóa đơn', PAYMENT_SUBMITTED: 'Đã gửi thanh toán on-chain',
  CONFIRMED: 'Thanh toán on-chain đã xác nhận', FAILED: 'Thanh toán on-chain thất bại',
};
const withdrawal: Record<string, string> = {
  NOT_STARTED: 'Chưa bắt đầu', REQUEST_SUBMITTED: 'Đã gửi yêu cầu rút',
  CONFIRMED: 'Yêu cầu rút đã xác nhận', FAILED: 'Yêu cầu rút thất bại',
};
const offRamp: Record<string, string> = {
  NOT_STARTED: 'Chưa bắt đầu', SIMULATED: 'Đã lập chi trả mô phỏng',
  COMPLETION_SUBMITTED: 'Đã gửi bản ghi hoàn tất',
  COMPLETED: 'Bản ghi chi trả mô phỏng đã hoàn tất', FAILED: 'Chi trả VND mô phỏng thất bại',
};
const exportStatus: Record<string, string> = {
  NOT_ATTEMPTED: 'Chưa lập chứng từ', SUCCESS: 'Đã xuất sang MISA', FAILED: 'Xuất chứng từ thất bại',
};

export function sourceStatus(value: string | null | undefined, labels: Record<string, string>): string {
  return value ? labels[value] ?? 'Trạng thái máy chủ: ' + value : 'Chưa có dữ liệu';
}
export const checkoutLabel = (value: string | null | undefined) => sourceStatus(value, checkout);
export const onRampLabel = (value: string | null | undefined) => sourceStatus(value, onRamp);
export const clientPaymentLabel = (value: string | null | undefined) => sourceStatus(value, clientPayment);
export const withdrawalLabel = (value: string | null | undefined) => sourceStatus(value, withdrawal);
export const offRampLabel = (value: string | null | undefined) => sourceStatus(value, offRamp);
export const exportLabel = (value: string | null | undefined) => sourceStatus(value, exportStatus);

export function decimal(value: DecimalValue | null | undefined, digits = 2): string {
  if (value === null || value === undefined || value === '') return '—';
  const number = Number(value);
  return Number.isFinite(number)
    ? new Intl.NumberFormat('vi-VN', { maximumFractionDigits: digits }).format(number)
    : '—';
}
export const vnd = (value: DecimalValue | null | undefined) =>
  value === null || value === undefined ? '—' : decimal(value, 0) + ' VND';
export const usdc = (value: DecimalValue | null | undefined) =>
  value === null || value === undefined ? '—' : decimal(value, 6) + ' Mock USDC';

export function maskedBank(value: string | null | undefined): string {
  if (!value) return 'Chưa có tài khoản nhận';
  const digits = value.replace(/\D/g, '');
  return digits.length >= 4 ? '•••• ' + digits.slice(-4) : 'Đã che số tài khoản';
}

export function rateSource(value: string | null | undefined): string {
  if (!value) return 'Chưa có nguồn tỷ giá';
  if (value === 'FALLBACK_PLACEHOLDER') return 'Tỷ giá giả lập / placeholder';
  if (value === 'LIVE_COINGECKO') return 'CoinGecko (USDC/VND)';
  if (value === 'LIVE_OPEN_ER_API') return 'Open Exchange Rates (USD/VND)';
  if (value === 'LOCKED_PAYOUT_QUOTE') return 'Tỷ giá được xác nhận khi rút tiền';
  return 'Nguồn tỷ giá: ' + value;
}

export function paymentStages(payment: JobPaymentStatus, tax: TaxRecord | null): EvidenceStage[] {
  const clientFailed = payment.clientPaymentStatus === 'FAILED' || payment.onRampStatus === 'FAILED';
  const settlementDone = payment.onRampStatus === 'CONFIRMED' && payment.clientPaymentStatus === 'CONFIRMED';
  const taxStatus = tax?.status;
  const taxFailed = taxStatus === 'EXPORT_FAILED' || taxStatus === 'REJECTED' ||
    taxStatus === 'CANCELLED' || payment.taxExportStatus === 'FAILED';
  return [
    { key: 'checkout', title: 'Khách thanh toán',
      tone: payment.checkoutOrderStatus === 'CAPTURED' ? 'done' : payment.checkoutOrderStatus === 'FAILED' ? 'error' : 'pending',
      status: checkoutLabel(payment.checkoutOrderStatus),
      note: payment.checkoutOrderStatus === 'CAPTURED' ? 'Hệ thống đã nhận khoản thanh toán của khách.' : 'Đang chờ khoản thanh toán của khách được xác nhận.' },
    { key: 'settlement', title: 'Tiền vào ví',
      tone: clientFailed ? 'error' : settlementDone ? 'done'
        : payment.onRampStatus || payment.clientPaymentStatus ? 'active' : 'pending',
      status: onRampLabel(payment.onRampStatus) + ' · ' + clientPaymentLabel(payment.clientPaymentStatus),
      note: settlementDone ? 'Tiền đã được đổi và xác nhận trong ví.' : 'Đang chờ tiền được đổi và xác nhận trong ví.',
      error: payment.clientPaymentError },
    { key: 'withdrawal', title: 'Người làm rút tiền',
      tone: payment.onChainOffRampStatus === 'FAILED' ? 'error'
        : payment.onChainOffRampStatus === 'CONFIRMED' ? 'done'
          : payment.onChainOffRampStatus === 'REQUEST_SUBMITTED' ? 'active' : 'pending',
      status: withdrawalLabel(payment.onChainOffRampStatus),
      note: payment.onChainOffRampStatus === 'CONFIRMED' ? 'Người làm đã xác nhận rút tiền từ ví.' : 'Đang chờ người làm xác nhận rút tiền từ ví.',
      error: payment.onChainOffRampError },
    { key: 'vnd', title: 'Chi trả VND',
      tone: payment.offRampStatus === 'FAILED' ? 'error'
        : payment.offRampStatus === 'COMPLETED' ? 'done'
          : payment.offRampStatus === 'SIMULATED' || payment.offRampStatus === 'COMPLETION_SUBMITTED' ? 'active' : 'pending',
      status: offRampLabel(payment.offRampStatus),
      note: (payment.offRampStatus === 'COMPLETED' ? 'Khoản chi VND đã được xác nhận.' : 'Đang chờ xác nhận khoản chi VND.')
        + (payment.simulation ? ' Mô phỏng · không xác nhận chuyển khoản ngân hàng thật.' : ''),
      error: payment.offRampError },
    { key: 'tax', title: 'Chứng từ thuế',
      tone: taxFailed ? 'error' : taxStatus === 'ACCEPTED' ? 'done' : tax ? 'active' : 'pending',
      status: tax ? tax.statusLabel || tax.status : exportLabel(payment.taxExportStatus),
      note: taxStatus === 'ACCEPTED' ? 'Chứng từ đã được ghi nhận; hãy xem số tiền và trạng thái trên chứng từ.' : 'Chứng từ được lập sau khi khoản chi VND được xác nhận.' },
  ];
}

export function paymentTerminal(payment: JobPaymentStatus): boolean {
  if (payment.checkoutOrderStatus === 'FAILED' || payment.onRampStatus === 'FAILED' ||
      payment.clientPaymentStatus === 'FAILED' || payment.onChainOffRampStatus === 'FAILED' ||
      payment.offRampStatus === 'FAILED') return true;
  return payment.offRampStatus === 'COMPLETED' &&
    (payment.taxExportStatus === 'SUCCESS' || payment.taxExportStatus === 'FAILED');
}

export const syncableTaxStatuses = new Set(['DRAFT', 'SIGNED', 'SUBMITTING', 'SUBMITTED', 'CORRECTION_REQUIRED']);

export function safeExplorerUrl(value: string | null | undefined, network: string | null): string | null {
  if (!value || network?.toLowerCase() !== 'devnet') return null;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname === 'explorer.solana.com' &&
      url.searchParams.get('cluster') === 'devnet' ? url.href : null;
  } catch {
    return null;
  }
}
