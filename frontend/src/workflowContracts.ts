import type { ContractSummary, DecimalValue, SubmissionPayload } from './types';

// Decimal formatting is lexical: no rounding or floating-point multiplication.
export function contractAmount(value: DecimalValue): string {
  const text = String(value);
  if (!/^\d+(\.\d{1,2})?$/.test(text)) throw new Error('Số tiền hợp đồng không hợp lệ. Hãy tải lại từ Marketplace.');
  const [whole, fraction = ''] = text.split('.');
  return whole.replace(/^0+(?=\d)/, '') + '.' + fraction.padEnd(2, '0');
}
export function smallReview(amount: DecimalValue): boolean {
  return BigInt(contractAmount(amount).replace('.', '')) <= 50000n;
}
export function localInstant(value: string | null | undefined): string {
  if (!value) return 'Chưa có thời điểm';
  const instant = new Date(value);
  return Number.isNaN(instant.getTime()) ? 'Thời điểm không hợp lệ' : new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium', timeStyle: 'short', timeZoneName: undefined,
  }).format(instant);
}
export function httpsUrl(value: string): URL | null {
  try { const url = new URL(value.trim()); return url.protocol === 'https:' && !url.username && !url.password ? url : null; }
  catch { return null; }
}
export function validateSubmission(payload: SubmissionPayload, contract: ContractSummary): string | null {
  if (!payload.summary.trim() || payload.summary.length > 10000) return 'Tóm tắt là bắt buộc, tối đa 10.000 ký tự.';
  if (!payload.deliverables.length && !payload.acceptanceEvidence.length) return 'Cần ít nhất một bằng chứng bàn giao.';
  if (payload.deliverables.length > 10 || payload.acceptanceEvidence.length > 20) return 'Quá số lượng bằng chứng cho phép.';
  const seen = new Set<string>();
  for (const item of payload.deliverables) {
    if (!contract.deliverables.some(r => r.id === item.requirementId) || seen.has(item.requirementId)) return 'Sản phẩm phải thuộc hợp đồng và không được chọn trùng.';
    seen.add(item.requirementId);
    if (item.url.length > 2048 || !httpsUrl(item.url) || item.description.length > 2000) return 'Sản phẩm cần URL HTTPS hợp lệ; mô tả tối đa 2.000 ký tự.';
  }
  seen.clear();
  for (const item of payload.acceptanceEvidence) {
    if (!contract.acceptanceCriteria.some(r => r.id === item.criterionId) || seen.has(item.criterionId)) return 'Tiêu chí phải thuộc hợp đồng và không được chọn trùng.';
    seen.add(item.criterionId);
    if ((!item.note.trim() && !item.url.trim()) || item.note.length > 2000 || item.url.length > 2048 || (item.url.trim() && !httpsUrl(item.url))) return 'Tiêu chí cần ghi chú hoặc URL HTTPS hợp lệ; ghi chú tối đa 2.000 ký tự.';
  }
  return null;
}

export function attemptScope(kind: 'fund' | 'submit' | 'escrow-review', userId: string, contractId: string, milestoneId: string) {
  return 'freelax:' + kind + ':' + userId + ':' + contractId + ':' + milestoneId;
}
export function readAttempt<T>(scope: string): T | null {
  const value = sessionStorage.getItem(scope);
  if (!value) return null;
  try { return JSON.parse(value) as T; } catch { throw new Error('Không thể đọc lần gửi trước. Chưa gửi yêu cầu mới.'); }
}
export function saveAttempt<T>(scope: string, value: T) {
  sessionStorage.setItem(scope, JSON.stringify(value));
}
export function clearAttempt(scope: string) { sessionStorage.removeItem(scope); }
export const fundingUnresolved = (status: string | undefined) => ['PENDING', 'PROCESSING', 'UNKNOWN'].includes(status || '');
export const retryDelay = (seconds: number | null | undefined) => Math.min(60, Math.max(5, Number.isFinite(seconds) ? seconds! : 30)) * 1000;
