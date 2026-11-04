import type { AvalancheEnvironmentInfo, TransactionState } from './avalancheTypes';

export const TRANSACTION_STATES: readonly TransactionState[] = [
  'CREATED', 'SIGNED', 'SUBMITTED', 'PENDING', 'CONFIRMED', 'FINALIZED', 'REVERTED', 'FAILED', 'REPLACED', 'UNKNOWN',
];

export const STATE_TEXT: Record<TransactionState, { label: string; detail: string; tone: 'neutral' | 'progress' | 'success' | 'danger' | 'warning' }> = {
  CREATED: { label: 'Đã tạo', detail: 'Giao dịch đã được ghi nhận nhưng chưa được ký.', tone: 'neutral' },
  SIGNED: { label: 'Đã ký', detail: 'Giao dịch đã được ký nhưng chưa gửi lên mạng.', tone: 'neutral' },
  SUBMITTED: { label: 'Đã gửi', detail: 'Giao dịch đã gửi tới mạng, chưa thấy trong khối nào.', tone: 'progress' },
  PENDING: { label: 'Đang chờ', detail: 'Mạng đã nhận giao dịch và đang chờ đưa vào khối.', tone: 'progress' },
  CONFIRMED: { label: 'Đã vào khối', detail: 'Giao dịch đã vào khối nhưng chưa đủ chắc chắn; chưa có thay đổi nghiệp vụ.', tone: 'progress' },
  FINALIZED: { label: 'Đã chốt', detail: 'Giao dịch đã chốt trên mạng; kết quả nghiệp vụ mới được áp dụng ở trạng thái này.', tone: 'success' },
  REVERTED: { label: 'Bị hoàn tác', detail: 'Hợp đồng từ chối giao dịch; không có tiền nào chuyển.', tone: 'danger' },
  FAILED: { label: 'Thất bại', detail: 'Giao dịch bị từ chối trước khi vào khối; không có tiền nào chuyển.', tone: 'danger' },
  REPLACED: { label: 'Đã bị thay thế', detail: 'Một giao dịch khác cùng số thứ tự đã thay thế giao dịch này.', tone: 'warning' },
  UNKNOWN: { label: 'Chưa xác định', detail: 'Chưa xác minh được kết quả. Hệ thống đang đối soát, hãy không gửi lại.', tone: 'warning' },
};

export function stateText(state: string): { label: string; detail: string; tone: string } {
  return (STATE_TEXT as Record<string, { label: string; detail: string; tone: string }>)[state]
    ?? { label: 'Chưa xác định', detail: 'Trạng thái không nhận diện được; hãy làm mới.', tone: 'warning' };
}

export function environmentBadge(environment: Pick<AvalancheEnvironmentInfo, 'environment' | 'simulated'>): string {
  return environment.simulated ? 'Mô phỏng' : 'Giao dịch testnet (Fuji)';
}

export function environmentBanner(environment: Pick<AvalancheEnvironmentInfo, 'environment' | 'simulated'>): string {
  return environment.simulated
    ? 'Môi trường mô phỏng cục bộ: USDC mô phỏng, không có giá trị tiền thật.'
    : 'Mạng thử nghiệm Avalanche Fuji: giao dịch testnet, không có giá trị tiền thật.';
}

export function explorerTxUrl(baseUrl: string | null | undefined, hash: string | null | undefined): string | null {
  if (!baseUrl || !hash || !/^0x[0-9a-fA-F]{64}$/.test(hash)) return null;
  try {
    const url = new URL(baseUrl);
    if (url.protocol !== 'https:' && url.protocol !== 'http:') return null;
    return url.toString().replace(/\/+$/, '') + '/tx/' + hash;
  } catch {
    return null;
  }
}

/** USDC has 6 decimals. Exact string arithmetic: no floating point on amounts. */
export function usdcFromBaseUnits(baseUnits: string | null | undefined): string | null {
  if (!baseUnits || !/^\d+$/.test(baseUnits)) return null;
  const padded = baseUnits.padStart(7, '0');
  const whole = padded.slice(0, -6).replace(/^0+(?=\d)/, '');
  const fraction = padded.slice(-6).replace(/0+$/, '');
  return fraction ? whole + '.' + fraction : whole;
}

/** Token label per environment, using the scope document's terminology. */
export function tokenLabel(simulated: boolean): string {
  return simulated ? 'USDC mô phỏng' : 'Testnet USDC';
}

/** Chain deadline (epoch seconds) for display; '—' when unset (0). */
export function chainInstant(seconds: number | null | undefined): string {
  return seconds ? new Date(seconds * 1000).toLocaleString('vi-VN') : '—';
}

export function shortHash(hash: string | null | undefined): string {
  return hash ? hash.slice(0, 10) + '…' + hash.slice(-6) : '—';
}

export const ACTION_TEXT: Record<string, string> = {
  fund: 'Nạp tiền ký quỹ', submit: 'Ghi nhận bàn giao', revise: 'Yêu cầu chỉnh sửa',
  'extension-request': 'Xin gia hạn', 'extension-approve': 'Chấp thuận gia hạn', dispute: 'Mở tranh chấp',
  release: 'Giải ngân', resolve: 'Thực thi quyết định tranh chấp', 'mutual-refund': 'Hoàn tiền thỏa thuận',
};

export function actionText(action: string): string {
  return ACTION_TEXT[action] ?? action;
}
