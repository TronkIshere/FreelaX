import type { ContractSettlement, DecimalValue, JobPaymentStatus, TaxRecord } from './types';

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
  return 'Nguồn tỷ giá: ' + value;
}

export function paymentStages(payment: JobPaymentStatus, tax: TaxRecord | null): EvidenceStage[] {
  const clientFailed = payment.clientPaymentStatus === 'FAILED' || payment.onRampStatus === 'FAILED';
  const settlementDone = payment.onRampStatus === 'CONFIRMED' && payment.clientPaymentStatus === 'CONFIRMED';
  const taxStatus = tax?.status;
  const taxFailed = taxStatus === 'EXPORT_FAILED' || taxStatus === 'REJECTED' ||
    taxStatus === 'CANCELLED' || payment.taxExportStatus === 'FAILED';
  return [
    { key: 'checkout', title: 'Thanh toán Client',
      tone: payment.checkoutOrderStatus === 'CAPTURED' ? 'done' : payment.checkoutOrderStatus === 'FAILED' ? 'error' : 'pending',
      status: checkoutLabel(payment.checkoutOrderStatus),
      note: 'Trạng thái checkout riêng với thanh toán on-chain.' },
    { key: 'settlement', title: 'Quyết toán USDC',
      tone: clientFailed ? 'error' : settlementDone ? 'done'
        : payment.onRampStatus || payment.clientPaymentStatus ? 'active' : 'pending',
      status: onRampLabel(payment.onRampStatus) + ' · ' + clientPaymentLabel(payment.clientPaymentStatus),
      note: payment.simulation ? 'Mô phỏng · Mock USDC; không phải số dư có thể rút.' : 'Bằng chứng xử lý USDC từ Marketplace.',
      error: payment.clientPaymentError },
    { key: 'withdrawal', title: 'Rút on-chain',
      tone: payment.onChainOffRampStatus === 'FAILED' ? 'error'
        : payment.onChainOffRampStatus === 'CONFIRMED' ? 'done'
          : payment.onChainOffRampStatus === 'REQUEST_SUBMITTED' ? 'active' : 'pending',
      status: withdrawalLabel(payment.onChainOffRampStatus),
      note: 'Bản ghi yêu cầu rút trên mạng được cấu hình.',
      error: payment.onChainOffRampError },
    { key: 'vnd', title: 'Chi trả VND',
      tone: payment.offRampStatus === 'FAILED' ? 'error'
        : payment.offRampStatus === 'COMPLETED' ? 'done'
          : payment.offRampStatus === 'SIMULATED' || payment.offRampStatus === 'COMPLETION_SUBMITTED' ? 'active' : 'pending',
      status: offRampLabel(payment.offRampStatus),
      note: payment.simulation ? 'Mô phỏng · không xác nhận chuyển khoản ngân hàng thật.' : 'Theo dõi trạng thái chi trả từ Marketplace.',
      error: payment.offRampError },
    { key: 'tax', title: 'Chứng từ thuế',
      tone: taxFailed ? 'error' : taxStatus === 'ACCEPTED' ? 'done' : tax ? 'active' : 'pending',
      status: tax ? tax.statusLabel || tax.status : exportLabel(payment.taxExportStatus),
      note: 'Trạng thái chứng từ tách biệt với kết quả xuất thuế của job.' },
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
