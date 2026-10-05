export const jobLabels: Record<string, string> = {
  OPEN: 'Đang tuyển', AWAITING_PAYMENT: 'Chờ funding', IN_PROGRESS: 'Đang thực hiện',
  SUBMITTED_FOR_REVIEW: 'Chờ duyệt bàn giao', REVISION_REQUESTED: 'Cần chỉnh sửa',
  COMPLETED: 'Hoàn thành', CANCELLED: 'Đã hủy',
};

export const applicationLabels: Record<string, string> = {
  PENDING: 'Đang chờ', ACCEPTED: 'Đã được chọn',
  REJECTED: 'Không được chọn', CANCELLED: 'Đã hủy',
};

export const submissionLabels: Record<string, string> = {
  SUBMITTED: 'Đã gửi bàn giao',
  REVISION_REQUESTED: 'Cần chỉnh sửa',
  APPROVED: 'Đã duyệt',
  DISPUTED: 'Đang tranh chấp',
};

export const fundingLabel = (status: string) => ({ PENDING: 'Chờ xử lý funding', PROCESSING: 'Đang xử lý funding', UNKNOWN: 'Đang đối soát funding', FAILED: 'Funding thất bại — có thể thử lại', SUCCEEDED: 'Funding đã xác nhận' }[status] || 'Chưa xác minh funding');

export const cancellationLabel = (status: string) => ({
  REQUESTED: 'Đang chờ đối tác quyết định', REJECTED: 'Đề nghị bị từ chối; hợp đồng tiếp tục',
  REFUND_PENDING: 'Đang đối soát hoàn tiền', CANCELLED: 'Hợp đồng đã hủy',
}[status] || 'Chưa xác minh đề nghị hủy');
export const refundLabel = (status: string | null) => ({
  PENDING: 'Chờ xác nhận hoàn tiền', PROCESSING: 'Đang xử lý hoàn tiền', UNKNOWN: 'Đang đối soát hoàn tiền',
  SUCCEEDED: 'Hoàn tiền đã xác nhận', FAILED_RETRYABLE: 'Máy chủ đang thử hoàn tiền lại', FAILED: 'Hoàn tiền chưa thành công',
}[status || ''] || 'Không có hoàn tiền được ghi nhận');

export const jobLabel = (status: string) => jobLabels[status] ?? 'Trạng thái khác';
export const submissionLabel = (status: string) => submissionLabels[status] ?? 'Trạng thái bàn giao khác';
export const applicationLabel = (status: string) => applicationLabels[status] ?? 'Trạng thái khác';
export const money = (value: number) => Number.isFinite(value)
  ? new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 }).format(value) : '—';
export const date = (value: string | null | undefined) => value?.slice(0, 10) || '—';
export const shortId = (value: string) => value.length > 15 ? value.slice(0, 8) + '…' + value.slice(-6) : value;
