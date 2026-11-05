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
  UNKNOWN: { label: 'Chưa xác định', detail: 'Chưa xác minh được kết quả. Hệ thống đang đối soát, vui lòng không gửi lại.', tone: 'warning' },
};

export function stateText(state: string): { label: string; detail: string; tone: string } {
  return (STATE_TEXT as Record<string, { label: string; detail: string; tone: string }>)[state]
    ?? { label: 'Chưa xác định', detail: 'Trạng thái không nhận diện được; hãy làm mới.', tone: 'warning' };
}

/** Escrow status read from the chain, in Vietnamese; an unknown value is shown as received (never hidden). */
const CHAIN_STATUS_TEXT: Record<string, string> = {
  NONE: 'Chưa có ký quỹ', FUNDED: 'Đã nạp tiền', SUBMITTED: 'Đã bàn giao, chờ nghiệm thu', REVISION: 'Đang chỉnh sửa',
  DISPUTED: 'Đang tranh chấp', RELEASED: 'Đã giải ngân cho Freelancer', REFUNDED: 'Đã hoàn tiền cho Client',
};

export function chainStatusText(status: string | null | undefined): string {
  return status ? (CHAIN_STATUS_TEXT[status] ?? status) : '—';
}

/** The wallet adapter reports English messages; the user sees these by error code. An unknown code gets the generic text. */
const WALLET_ERROR_TEXT: Record<string, string> = {
  NO_WALLET: 'Không tìm thấy ví EVM trong trình duyệt này.',
  USER_REJECTED: 'Bạn đã từ chối yêu cầu trong ví. Chưa có gì được gửi.',
  CONNECT_FAILED: 'Không thể kết nối ví.',
  NO_ACCOUNT: 'Ví không trả về địa chỉ hợp lệ.',
  CHAIN_UNREADABLE: 'Không đọc được mạng mà ví đang dùng. Hãy kiểm tra lại ví.',
  CHAIN_NOT_ALLOWED: 'Mạng của ví không được hỗ trợ. Chỉ môi trường mô phỏng cục bộ và mạng thử nghiệm Avalanche Fuji được dùng; không có gì được ký hoặc gửi.',
  ACCOUNT_NOT_LINKED: 'Tài khoản đang chọn trong ví không phải ví đã liên kết với bạn. Hãy chuyển sang đúng tài khoản trong ví rồi thử lại; chưa có gì được ký hoặc gửi.',
  WRONG_CHAIN: 'Ví đang ở sai mạng. Hãy chuyển ví sang đúng mạng rồi thử lại.',
  CHAIN_MISMATCH: 'Yêu cầu nhắm tới mạng khác với môi trường hiện tại; không có gì được ký hoặc gửi.',
  TX_INVALID: 'Giao dịch được chuẩn bị không hợp lệ; không có gì được gửi.',
  NO_HASH: 'Ví không trả về mã giao dịch; chưa thể coi giao dịch là đã gửi.',
  SEND_FAILED: 'Ví không gửi được giao dịch.',
  TYPED_DATA_INVALID: 'Yêu cầu ký không hợp lệ; không có gì được ký.',
  BAD_SIGNATURE: 'Ví trả về chữ ký không hợp lệ.',
  SIGN_FAILED: 'Ví không ký được yêu cầu.',
  MESSAGE_INVALID: 'Yêu cầu liên kết ví không hợp lệ; không có gì được ký.',
};

const HAS_VIETNAMESE = /[\u00C0-\u1EF9]/;

/** Message for a wallet or API error: Vietnamese by code, an already Vietnamese message as is, otherwise a generic Vietnamese text. */
export function walletErrorText(error: unknown): string {
  const code = error && typeof error === 'object' && 'code' in error ? String((error as { code: unknown }).code) : '';
  if (WALLET_ERROR_TEXT[code]) return WALLET_ERROR_TEXT[code];
  const text = error instanceof Error ? error.message : '';
  return HAS_VIETNAMESE.test(text) ? text : 'Không thể hoàn tất thao tác. Hãy thử lại.';
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

/**
 * Per-record chain label for existing screens (register A7; legacy labelling of A1). Avalanche only when the contract's
 * network id says so; the two Solana rails are labelled as the legacy reference; mock and simulated rails have no chain.
 */
export function contractChainLabel(contract: { paymentNetworkId?: string | null; paymentRail?: string | null } | null | undefined): string | null {
  if (!contract) return null;
  if (contract.paymentNetworkId && contract.paymentNetworkId.toLowerCase().startsWith('avalanche')) return 'Ký quỹ Avalanche';
  if (contract.paymentRail === 'SOLANA_ESCROW' || contract.paymentRail === 'UNIFIED_USDC_PAYOUT') return 'Solana (tham chiếu cũ)';
  return null;
}

const ALERT_TEXT: Record<string, string> = {
  TOKEN_PAUSED: 'Nhà phát hành token đang tạm dừng mọi chuyển USDC',
  ESCROW_BLACKLISTED: 'Nhà phát hành token đã chặn địa chỉ hợp đồng ký quỹ',
  FREELANCER_BLACKLISTED: 'Nhà phát hành token đã chặn địa chỉ ví của Freelancer',
  CLIENT_BLACKLISTED: 'Nhà phát hành token đã chặn địa chỉ ví của Client',
};

/** P3-C4 (O-6): a stuck escrow is reported plainly; the text never suggests a workaround, because none exists. */
export function issuerAlertText(code: string, blocks: readonly string[]): string {
  const what = ALERT_TEXT[code] ?? 'Nhà phát hành token đang áp dụng một biện pháp kiểm soát';
  const blocked = blocks.map(b => actionText(b.split(':')[0])).filter((v, i, a) => a.indexOf(v) === i).join(', ');
  return what + (blocked ? '. Bị chặn: ' + blocked + '.' : '.');
}

export function shortHash(hash: string | null | undefined): string {
  return hash ? hash.slice(0, 10) + '…' + hash.slice(-6) : '—';
}

export const ACTION_TEXT: Record<string, string> = {
  fund: 'Nạp tiền ký quỹ', submit: 'Ghi nhận bàn giao', revise: 'Yêu cầu chỉnh sửa',
  'extension-request': 'Xin gia hạn', 'extension-approve': 'Chấp thuận gia hạn', dispute: 'Mở tranh chấp',
  release: 'Giải ngân', resolve: 'Thực thi quyết định tranh chấp', 'mutual-refund': 'Hoàn tiền thỏa thuận',
  'mutual-settle': 'Tất toán tranh chấp theo thỏa thuận',
};

/** Label of an offer, including the outcome of a mutual settlement. */
export function offerText(action: string, variant?: string): string {
  if (action === 'mutual-settle') return actionText(action) + (variant === 'refund' ? ' (hoàn cho Client)' : ' (giải ngân cho Freelancer)');
  return actionText(action);
}

export function actionText(action: string): string {
  return ACTION_TEXT[action] ?? action;
}
