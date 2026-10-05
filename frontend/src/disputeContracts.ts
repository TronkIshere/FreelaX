import type { Dispute, DisputeEvidenceInput, OpenDisputeInput } from './types';
import { httpsUrl } from './workflowContracts';

export const activeDispute = (value: Dispute | null) => !!value && ['OPEN', 'UNDER_REVIEW'].includes(value.status);
export const finalDispute = (value: Dispute | null) => !!value && ['RESOLVED_RELEASE', 'RESOLVED_REFUND', 'CANCELLED'].includes(value.status);
export function validateDispute(input: OpenDisputeInput): string | null {
  if (!input.reasonCode.trim() || input.reasonCode.length > 60) return 'Mã lý do là bắt buộc, tối đa 60 ký tự.';
  if (!input.description.trim() || input.description.length > 2000) return 'Mô tả là bắt buộc, tối đa 2.000 ký tự.';
  return validateDisputeEvidence(input.evidence || []);
}
export function validateDisputeEvidence(items: DisputeEvidenceInput[]): string | null {
  if (items.length > 10) return 'Mỗi lần gửi tối đa 10 bằng chứng.';
  for (const item of items) {
    if (item.sha256 !== undefined && !/^[a-fA-F0-9]{64}$/.test(item.sha256)) return 'SHA-256 phải có đúng 64 ký tự hex.';
    if (item.kind === 'TEXT') {
      if (!item.text?.trim() || item.text.length > 2000 || item.url != null) return 'Bằng chứng TEXT cần nội dung, tối đa 2.000 ký tự và không có URL.';
    } else if (item.kind === 'LINK') {
      const url = item.url ? httpsUrl(item.url) : null;
      // URL() normalizes malformed slash/backslash forms that the backend URI validator rejects.
      const explicitHost = /^https:\/\/[a-zA-Z0-9.:[\]-]+(?:[/?#]|$)/i.test(item.url || '');
      if (!url?.hostname || !explicitHost || /[\s\\<>"{}|^`]/.test(item.url!) || item.url!.length > 2048 || (item.text?.length || 0) > 2000) return 'LINK cần URL HTTPS có hostname, không chứa tài khoản; tối đa 2.048 ký tự.';
    } else return 'Loại bằng chứng không hợp lệ.';
  }
  return null;
}
