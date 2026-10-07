export type ActivityFamily = 'work' | 'finance' | 'refund' | 'review' | 'dispute' | 'tax' | 'neutral';
export type ActivityTone = 'success' | 'active' | 'attention' | 'error' | 'closed' | 'neutral';
type Presentation = Readonly<{ label: string; family: ActivityFamily; tone: ActivityTone }>;

// Presentation only. Read state, routing and mutations remain independent of this mapping.
const presentations: Readonly<Record<string, Presentation>> = {
  JOB_ASSIGNED: { label: 'Được giao việc', family: 'work', tone: 'success' },
  JOB_CANCELLED: { label: 'Công việc đã hủy', family: 'work', tone: 'closed' },
  WORK_SUBMITTED: { label: 'Có bản bàn giao', family: 'work', tone: 'active' },
  REVISION_REQUESTED: { label: 'Yêu cầu chỉnh sửa', family: 'work', tone: 'attention' },
  WORK_APPROVED: { label: 'Bàn giao được duyệt', family: 'work', tone: 'success' },
  FUNDING_CONFIRMED: { label: 'Funding đã xác nhận', family: 'finance', tone: 'success' },
  RELEASE_CONFIRMED: { label: 'Bản ghi release đã xác nhận', family: 'finance', tone: 'success' },
  CANCELLATION_REQUESTED: { label: 'Đề nghị hủy — công việc tiếp tục', family: 'refund', tone: 'attention' },
  CANCELLATION_REJECTED: { label: 'Đề nghị hủy bị từ chối — công việc tiếp tục', family: 'refund', tone: 'neutral' },
  REFUND_PENDING: { label: 'Hoàn tiền đang đối soát', family: 'refund', tone: 'active' },
  REFUND_CONFIRMED: { label: 'Bản ghi hoàn tiền đã xác nhận', family: 'refund', tone: 'success' },
  REVIEW_GRACE_STARTED: { label: 'Gia hạn review', family: 'review', tone: 'attention' },
  REVIEW_AUTO_APPROVED: { label: 'Máy chủ tự duyệt', family: 'review', tone: 'success' },
  DISPUTE_OPENED: { label: 'Đã mở tranh chấp', family: 'dispute', tone: 'attention' },
  DISPUTE_DECIDED: { label: 'Admin đã quyết định tranh chấp', family: 'dispute', tone: 'neutral' },
  REVIEW_INVITED: { label: 'Mời đánh giá hợp đồng', family: 'review', tone: 'attention' },
  REVIEW_PUBLISHED: { label: 'Đánh giá đã công bố', family: 'review', tone: 'success' },
  PAYMENT_SENT: { label: 'Thanh toán Client', family: 'finance', tone: 'neutral' },
  PAYMENT_RECEIVED: { label: 'Chi trả mô phỏng', family: 'finance', tone: 'success' },
  TAX_EXPORT_FAILED: { label: 'Chứng từ thuế', family: 'tax', tone: 'error' },
  PAYOUT_FAILED: { label: 'Chi trả cần xử lý', family: 'finance', tone: 'error' },
};
const unknown: Presentation = { label: 'Cập nhật từ Marketplace', family: 'neutral', tone: 'neutral' };

export function activityPresentation(type: string): Presentation {
  return Object.prototype.hasOwnProperty.call(presentations, type) ? presentations[type] : unknown;
}
