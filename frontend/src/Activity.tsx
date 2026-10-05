import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from './api';
import { PageHeading, StatePanel } from './components';
import type { Notification as MarketplaceNotification, Page } from './types';

const labels: Record<string, string> = {
  JOB_ASSIGNED: 'Được giao việc',
  JOB_CANCELLED: 'Công việc đã hủy',
  WORK_SUBMITTED: 'Có bản bàn giao',
  REVISION_REQUESTED: 'Yêu cầu chỉnh sửa',
  WORK_APPROVED: 'Bàn giao được duyệt',
  FUNDING_CONFIRMED: 'Funding đã xác nhận',
  REVIEW_GRACE_STARTED: 'Gia hạn review',
  REVIEW_AUTO_APPROVED: 'Máy chủ tự duyệt',
  DISPUTE_OPENED: 'Đã mở tranh chấp',
  PAYMENT_SENT: 'Thanh toán Client',
  PAYMENT_RECEIVED: 'Chi trả mô phỏng',
  TAX_EXPORT_FAILED: 'Chứng từ thuế',
  PAYOUT_FAILED: 'Chi trả cần xử lý',
};
const stamp = (value: string | null) => value ? value.slice(0, 16).replace('T', ' · ') : 'Chưa có thời gian';

export function Activity() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<MarketplaceNotification> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const pendingRef = useRef<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setActionError('');
    api.notifications(page).then(
      data => { if (active) { setResult(data); setLoading(false); } },
      cause => { if (active) { setError(cause instanceof Error ? cause.message : 'Không thể tải thông báo.'); setLoading(false); } },
    );
    return () => { active = false; };
  }, [page, attempt]);

  async function markRead(item: MarketplaceNotification) {
    if (item.read || pendingRef.current) return;
    pendingRef.current = item.id;
    setPendingId(item.id);
    setActionError('');
    try {
      const updated = await api.markNotificationRead(item.id);
      if (item.jobId && ['WORK_SUBMITTED', 'REVISION_REQUESTED', 'WORK_APPROVED', 'REVIEW_GRACE_STARTED', 'REVIEW_AUTO_APPROVED', 'DISPUTE_OPENED'].includes(item.type)) {
        window.dispatchEvent(new CustomEvent('freelax:review-update', { detail: { jobId: item.jobId } }));
      }
      setResult(current => current ? { ...current, data: current.data.map(entry =>
        entry.id === item.id ? updated : entry) } : current);
    } catch (cause) {
      setActionError(cause instanceof Error ? cause.message : 'Không thể đánh dấu đã đọc.');
    } finally {
      pendingRef.current = null;
      setPendingId(null);
    }
  }

  const items = result?.data ?? [];
  const unread = items.filter(item => !item.read).length;
  return <>
    <PageHeading eyebrow="Marketplace / Hoạt động" title="Thông báo về công việc của bạn."
      description="Đây là thông báo do Marketplace gửi cho tài khoản này, không phải nhật ký mọi sự kiện trên hệ thống."
      aside={result ? result.totalElements + ' thông báo trong tài khoản' : undefined} />
    {loading && <StatePanel kind="loading" title="Đang tải thông báo" body="Marketplace đang trả hoạt động của tài khoản." />}
    {!loading && error && <StatePanel kind="error" title="Không thể tải hoạt động" body={error}
      action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
    {!loading && !error && result && <>
      <div className="activity-intro"><span className="category-label">Thông báo Marketplace</span>
        <strong>{unread} chưa đọc trên trang này</strong></div>
      {items.length === 0 ? <StatePanel kind="empty" title="Chưa có thông báo"
        body="Khi công việc, bàn giao hoặc thanh toán có cập nhật, thông báo từ Marketplace sẽ xuất hiện tại đây." /> :
        <div className="activity-list">{items.map(item => <article key={item.id}
          className={'activity-row' + (item.read ? '' : ' unread')}>
          <div className="activity-row-marker" aria-hidden="true" />
          <div className="activity-row-content"><div className="activity-row-meta">
            <span>{labels[item.type] || 'Cập nhật từ Marketplace'}</span><time>{stamp(item.createdAt)}</time></div>
            <h2>{item.title}</h2><p>{item.message}</p>
            {item.jobId && <Link to={'/work/' + encodeURIComponent(item.jobId)}>Xem công việc →</Link>}
          </div>
          <div className="activity-row-action">{item.read ? <span>Đã đọc</span> :
            <button className="text-button" type="button" disabled={!!pendingId}
              onClick={() => void markRead(item)}>{pendingId === item.id ? 'Đang lưu…' : 'Đánh dấu đã đọc'}</button>}</div>
        </article>)}</div>}
      {actionError && <p className="form-error activity-action-error" role="alert">{actionError}</p>}
      <nav className="pagination" aria-label="Phân trang thông báo">
        <span>Trang {result.totalPages ? page + 1 : 0}/{result.totalPages}</span>
        <div><button className="button button-secondary" type="button" disabled={page === 0}
          onClick={() => setPage(value => value - 1)}>Trang trước</button>
          <button className="button button-secondary" type="button"
            disabled={page >= result.totalPages - 1} onClick={() => setPage(value => value + 1)}>Trang sau</button></div>
      </nav>
    </>}
  </>;
}
