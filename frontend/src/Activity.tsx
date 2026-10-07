import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRight, Bell, Check, Coins, FileText, Gavel, RotateCcw, Star } from 'lucide-react';
import { api } from './api';
import { activityPresentation, isLongActivityMessage, type ActivityFamily } from './activityPresentation';
import { PageHeading, StatePanel } from './components';
import { financePath } from './financeStatus';
import { RoughBurst, RoughUnderline } from './ui/kinetic';
import type { Notification as MarketplaceNotification, Page } from './types';

const familyIcons: Record<ActivityFamily, typeof FileText> = {
  work: FileText, finance: Coins, refund: RotateCcw, review: Star,
  dispute: Gavel, tax: FileText, neutral: Bell,
};
const stamp = (value: string | null) => value ? value.slice(0, 16).replace('T', ' · ') : 'Chưa có thời gian';
const financialEvents = new Set(['RELEASE_CONFIRMED', 'REFUND_PENDING', 'REFUND_CONFIRMED']);
const ratingEvents = new Set(['REVIEW_INVITED', 'REVIEW_PUBLISHED']);

function ActivityMessage({ message }: { message: string }) {
  const [expanded, setExpanded] = useState(false);
  const contentId = useId();
  const long = isLongActivityMessage(message);
  // Only the original server text is clamped. Disclosure has no API/read-state side effects.
  return <div className="activity-message">
    <p id={contentId} className={'activity-message-text' + (long ? expanded ? ' is-expanded' : ' is-collapsed' : '')}>{message}</p>
    {long && <button className="activity-message-toggle" type="button" aria-expanded={expanded}
      aria-controls={contentId} onClick={() => setExpanded(value => !value)}>{expanded ? 'Thu gọn' : 'Xem thêm nội dung'}</button>}
  </div>;
}

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
      if (ratingEvents.has(item.type)) window.dispatchEvent(new Event('freelax:rating-update'));
      if (item.jobId && ['WORK_SUBMITTED', 'REVISION_REQUESTED', 'WORK_APPROVED', 'REVIEW_GRACE_STARTED', 'REVIEW_AUTO_APPROVED', 'DISPUTE_OPENED', 'DISPUTE_DECIDED'].includes(item.type)) {
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
  return <section className="activity-page" aria-label="Thông báo Marketplace">
    <PageHeading eyebrow="Marketplace / Hoạt động" title={<>Thông báo về <span className="activity-title-accent">công việc của bạn.
      <RoughUnderline seedKey="activity-title" className="activity-title-underline" />
      <RoughBurst seedKey="activity-title-rays" size={38} accent="ink" className="activity-title-rays" /></span></>}
      description="Đây là thông báo do Marketplace gửi cho tài khoản này, không phải nhật ký mọi sự kiện trên hệ thống."
      aside={result ? result.totalElements + ' thông báo trong tài khoản' : undefined} />
    {loading && <StatePanel kind="loading" title="Đang tải thông báo" body="Marketplace đang trả hoạt động của tài khoản." />}
    {!loading && error && <StatePanel kind="error" title="Không thể tải hoạt động" body={error}
      action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
    {!loading && !error && result && <>
      <div className="activity-intro"><h2>Thông báo Marketplace</h2>
        <strong>{unread} chưa đọc trên trang này</strong></div>
      {items.length === 0 ? <StatePanel kind="empty" title="Chưa có thông báo"
        body="Khi công việc, bàn giao hoặc thanh toán có cập nhật, thông báo từ Marketplace sẽ xuất hiện tại đây." /> :
        <div className="activity-list">{items.map(item => {
          const presentation = activityPresentation(item.type);
          const Icon = familyIcons[presentation.family];
          return <article key={item.id}
          className={'activity-row activity-tone-' + presentation.tone + (item.read ? '' : ' unread')}>
          <div className="activity-row-marker" aria-hidden="true"><div className="activity-event-plate">
            <span className="activity-plate-tape" /><Icon size={34} strokeWidth={1.8} />
            <span className="activity-plate-lines" />
          </div></div>
          <div className="activity-row-content"><div className="activity-row-meta">
            <span className="activity-event-type">{presentation.label}</span><time dateTime={item.createdAt ?? undefined}>{stamp(item.createdAt)}</time></div>
            <h3>{item.title}</h3><ActivityMessage key={item.message} message={item.message} />
            {/* amount has no currency/unit in Notification; preserve the server message without guessing. */}
            {item.jobId && <Link to={financialEvents.has(item.type) ? financePath(item.jobId) : '/work/' + encodeURIComponent(item.jobId) + (ratingEvents.has(item.type) ? '#contract-reviews' : '')}>
              {financialEvents.has(item.type) ? 'Xem bằng chứng tài chính' : ratingEvents.has(item.type) ? 'Xem đánh giá hợp đồng' : 'Xem công việc'} <ArrowUpRight size={21} aria-hidden="true" /></Link>}
          </div>
          <div className="activity-row-action"><span className={'activity-read-state' + (item.read ? ' is-read' : '')}>
            {item.read && <Check size={15} aria-hidden="true" />}{item.read ? 'Đã đọc' : 'Chưa đọc'}</span>
            {!item.read && <button className="activity-mark-read" type="button" disabled={!!pendingId}
              onClick={() => void markRead(item)}>{pendingId === item.id ? 'Đang lưu…' : 'Đánh dấu đã đọc'}</button>}</div>
        </article>; })}</div>}
      {actionError && <p className="form-error activity-action-error" role="alert">{actionError}</p>}
      <nav className="pagination" aria-label="Phân trang thông báo">
        <span>Trang {result.totalPages ? page + 1 : 0}/{result.totalPages}</span>
        <div><button className="button button-secondary" type="button" disabled={page === 0}
          onClick={() => setPage(value => value - 1)}>Trang trước</button>
          <button className="button button-secondary" type="button"
            disabled={page >= result.totalPages - 1} onClick={() => setPage(value => value + 1)}>Trang sau</button></div>
      </nav>
    </>}
  </section>;
}
