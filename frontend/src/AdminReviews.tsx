import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ApiError, hasAuthority } from './api';
import { ActionGroup, EvidenceDisclosure, PageHeading, SectionHeading, StatePanel } from './components';
import { DisputeError } from './ContractDispute';
import { ReviewRecord } from './ContractReviews';
import { ratingUpdated } from './profileContracts';
import { localInstant } from './workflowContracts';
import type { AdminReviewDetail, ModerationAction, Review, User } from './types';

export function AdminReviews({ user }: { user: User }) {
  if (!hasAuthority(user, 'ROLE_ADMIN')) return <StatePanel kind="error" title="Không có quyền quản trị" body="Khu vực này yêu cầu quyền Admin từ phiên đã xác minh." />;
  return <ReportedQueue user={user} />;
}
function ReportedQueue({ user }: { user: User }) {
  const [rows, setRows] = useState<Review[] | null>(null); const [size, setSize] = useState(20); const [error, setError] = useState<unknown>(null); const generation = useRef(0);
  async function load() { const current = ++generation.current; try { const list = await api.reportedReviews(size); if (current === generation.current) { setRows(list); setError(null); } } catch (e) { if (current === generation.current) { setError(e); if (e instanceof ApiError && [403, 404].includes(e.status)) setRows(null); } } }
  useEffect(() => { void load(); return () => { generation.current++; }; }, [size]);
  return <><PageHeading eyebrow="Quản trị / Đánh giá" title="Nhận xét được báo cáo." description="Xem nội dung, ngữ cảnh và lịch sử trước khi quyết định kiểm duyệt." />
    <DisputeError error={error} /><ActionGroup><label>Số hồ sơ<select value={size} onChange={e => setSize(Number(e.target.value))}>{[20, 50, 100].map(n => <option key={n}>{n}</option>)}</select></label><button className="button button-secondary" onClick={() => void load()}>Đọc lại hàng đợi</button></ActionGroup>
    {!rows ? <p role="status">Đang đọc hàng đợi…</p> : !rows.length ? <p>Chưa có báo cáo cần kiểm duyệt.</p> : rows.map(r => <section key={r.id} className="review-record"><ReviewRecord review={r} user={user} mode="admin" /><Link className="button button-secondary" to={'/admin/reviews/' + encodeURIComponent(r.id)}>Xem hồ sơ kiểm duyệt</Link></section>)}
  </>;
}
export function AdminReview({ user }: { user: User }) {
  const { reviewId = '' } = useParams();
  if (!hasAuthority(user, 'ROLE_ADMIN')) return <StatePanel kind="error" title="Không có quyền quản trị" body="Khu vực này yêu cầu quyền Admin từ phiên đã xác minh." />;
  return <ModerationDetail user={user} key={reviewId + ':' + user.id} />;
}
function ModerationDetail({ user }: { user: User }) {
  const { reviewId = '' } = useParams(); const [detail, setDetail] = useState<AdminReviewDetail | null>(null);
  const [error, setError] = useState<unknown>(null); const [reason, setReason] = useState(''); const [action, setAction] = useState<ModerationAction>('HIDE_CONTENT');
  const [confirm, setConfirm] = useState(false); const [busy, setBusy] = useState(false); const [notice, setNotice] = useState(''); const [uncertain, setUncertain] = useState(false);
  const lock = useRef(false); const generation = useRef(0);
  async function load() {
    const current = ++generation.current;
    try { const d = await api.adminReview(reviewId); if (current === generation.current) { setDetail(d); return d; } } catch (e) { if (current === generation.current) { setError(e); if (e instanceof ApiError && [403, 404].includes(e.status)) setDetail(null); } } return null;
  }
  useEffect(() => { setDetail(null); void load(); return () => { generation.current++; }; }, [reviewId]);
  const invalidated = detail?.audit.some(a => a.action === 'INVALIDATE' || a.afterState === 'INVALIDATED');
  const participant = detail && [detail.review.reviewerId, detail.review.revieweeId].includes(user.id);
  async function moderate() {
    if (lock.current || !detail || participant || invalidated || uncertain || !confirm) return;
    if (!reason.trim() || reason.trim().length > 2000) { setError('Lý do kiểm duyệt cần 1–2.000 ký tự.'); return; }
    lock.current = true; setBusy(true); setError(null); setNotice('');
    try {
      await api.moderateReview(reviewId, action, reason.trim()); setConfirm(false);
      const fresh = await load(); if (!fresh) { setUncertain(true); return; }
      await Promise.all([api.profile(fresh.review.revieweeId), api.publicReviews(fresh.review.revieweeId, 0)]);
      ratingUpdated([fresh.review.revieweeId]);
      setNotice(action === 'HIDE_CONTENT' ? 'Máy chủ đã ẩn nhận xét; điểm số vẫn được giữ.' : 'Máy chủ đã loại đánh giá khỏi danh sách công khai và điểm tổng hợp.');
    } catch (e) { setError(e); setUncertain(true); await load(); }
    finally { lock.current = false; setBusy(false); }
  }
  return <><PageHeading eyebrow="Quản trị / Đánh giá" title="Hồ sơ kiểm duyệt." description="Ẩn nhận xét giữ điểm số; loại đánh giá thay đổi dữ liệu uy tín trên máy chủ." /><Link className="text-link" to="/admin/reviews">Trở về hàng đợi</Link>
    <DisputeError error={error} />{notice && <p role="status">{notice}</p>}
    {!detail ? <p role="status">Đang đọc hồ sơ…</p> : <section className="profile-document"><ReviewRecord review={detail.review} user={user} mode="admin" />
      <SectionHeading title="Lịch sử kiểm duyệt" level={3} />{!detail.audit.length && <p>Chưa có quyết định kiểm duyệt.</p>}
      {detail.audit.map((a, i) => <article className="ledger-body" key={i}><p>{a.action === 'INVALIDATE' ? 'Loại đánh giá' : a.action === 'HIDE_CONTENT' ? 'Ẩn nhận xét' : 'Cập nhật kiểm duyệt'} · {localInstant(a.createdAt)}</p><p>{a.reason}</p><EvidenceDisclosure summary="Nguồn quyết định"><p>Admin: <code>{a.actorId}</code></p><p>Trước / Sau: {a.beforeState} / {a.afterState}</p><p>Mã yêu cầu: <code>{a.requestId}</code></p></EvidenceDisclosure></article>)}
      {participant ? <p>Bạn là một bên của hợp đồng này; không thể kiểm duyệt.</p> : invalidated ? <p>Đánh giá đã bị loại. Không có thao tác khôi phục.</p> : <form className="review-composer" onSubmit={e => { e.preventDefault(); if (!reason.trim() || reason.trim().length > 2000) setError('Lý do kiểm duyệt cần 1–2.000 ký tự.'); else setConfirm(true); }}>
        <fieldset disabled={busy || uncertain || confirm}><legend>Quyết định kiểm duyệt</legend><label>Thao tác<select value={action} onChange={e => setAction(e.target.value as ModerationAction)}><option value="HIDE_CONTENT" disabled={detail.review.contentHidden}>Ẩn nội dung nhận xét — giữ điểm</option><option value="INVALIDATE">Loại đánh giá — bỏ khỏi uy tín</option></select></label>
          <label>Lý do kiểm duyệt<textarea maxLength={2000} value={reason} onChange={e => setReason(e.target.value)} /></label></fieldset>
        {!confirm && <button className="button button-secondary" disabled={busy || uncertain || (action === 'HIDE_CONTENT' && detail.review.contentHidden)}>Xem lại quyết định</button>}
        {confirm && <section className="feedback-document"><h3>{action === 'HIDE_CONTENT' ? 'Xác nhận ẩn nhận xét?' : 'Xác nhận loại đánh giá?'}</h3><p>{action === 'HIDE_CONTENT' ? 'Nội dung công khai sẽ ẩn, điểm số vẫn được giữ.' : 'Đánh giá sẽ không còn công khai hoặc đóng góp vào điểm tổng hợp.'} Không có thao tác hoàn tác.</p><p>{reason}</p><ActionGroup><button type="button" className="button" disabled={busy || uncertain} onClick={() => void moderate()}>Xác nhận kiểm duyệt</button><button type="button" className="button button-secondary" disabled={busy} onClick={() => setConfirm(false)}>Quay lại</button></ActionGroup></section>}
      </form>}
      {uncertain && <p>Chưa xác nhận đầy đủ kết quả; không tự gửi lại quyết định. Đọc lại hồ sơ để đối chiếu.</p>}
    </section>}
    <button className="button button-secondary" disabled={busy} onClick={() => { void load().then(async fresh => { if (fresh) { await api.profile(fresh.review.revieweeId); ratingUpdated([fresh.review.revieweeId]); } }).catch(setError); }}>Đọc lại hồ sơ kiểm duyệt</button>
  </>;
}
