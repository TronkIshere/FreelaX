import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError, hasAuthority } from './api';
import { ActionGroup, EvidenceDisclosure, FactGrid, SectionHeading } from './components';
import { DisputeError } from './ContractDispute';
import { ratingUpdated, validateReview } from './profileContracts';
import { localInstant } from './workflowContracts';
import type { ContractSettlement, Job, Review, ReviewInput, User } from './types';

export function reviewOpportunity(job: Job, user: User, settlement: ContractSettlement | null, rows: Review[], blocked = false) {
  return !blocked && (user.id === job.clientUserId || user.id === job.freelancerId) && !hasAuthority(user, 'ROLE_ADMIN') && job.status === 'COMPLETED' && job.contract?.status === 'COMPLETED' && job.contract.milestoneStatus === 'RELEASED'
    && settlement?.contractId === job.contract.id && settlement.jobId === job.id && settlement.milestoneId === job.contract.milestoneId && settlement.moneyStatus === 'SUCCEEDED'
    && rows.some(r => r.contractId === job.contract?.id && r.reviewerId === user.id && r.revieweeId === (user.id === job.clientUserId ? job.freelancerId : job.clientUserId) && !r.submitted);
}
export function ReviewRecord({ review, user, mode = 'public' }: { review: Review; user: User; mode?: 'public' | 'participant' | 'admin' }) {
  const own = review.reviewerId === user.id;
  const visible = review.submitted && (mode === 'admin' || !!review.publishedAt || (mode === 'participant' && own));
  return <article className="review-record"><SectionHeading level={3} title={own ? 'Đánh giá của bạn' : 'Đánh giá từ đối tác'} />
    {!visible ? <p>Nội dung chưa được công bố cho bạn.</p> : <>
      <FactGrid facts={[
        { label: 'Tổng thể', value: review.overall == null ? 'Chưa có điểm' : `${review.overall} / 5` },
        ...(review.dimensions ? [{ label: 'Giao tiếp', value: `${review.dimensions.communication} / 5` }, { label: 'Yêu cầu / Chất lượng', value: `${review.dimensions.requirementsOrQuality} / 5` }, { label: 'Đúng hạn', value: `${review.dimensions.timeliness} / 5` }] : []),
      ]} />
      {review.contentHidden && <p>Nội dung nhận xét đã ẩn; điểm số vẫn được giữ.</p>}
      {review.comment && (!review.contentHidden || mode === 'admin' || (mode === 'participant' && own)) && <p className="submission-summary">{review.comment}</p>}
      <p className="metadata">{review.publishedAt ? `Công bố: ${localInstant(review.publishedAt)}` : 'Đã gửi; chưa công bố công khai.'}</p>
      {review.submittedAt && <p className="metadata">Gửi: {localInstant(review.submittedAt)}</p>}
    </>}
    <ActionGroup><Link className="text-link" to={'/profiles/' + encodeURIComponent(review.reviewerId)}>Hồ sơ người đánh giá</Link><Link className="text-link" to={'/profiles/' + encodeURIComponent(review.revieweeId)}>Hồ sơ người nhận</Link></ActionGroup>
    <EvidenceDisclosure summary="Tham chiếu đánh giá"><p>Review: <code>{review.id}</code></p><p>Contract: <code>{review.contractId}</code></p></EvidenceDisclosure>
  </article>;
}
export function ReviewReport({ review, user, onRefresh }: { review: Review; user: User; onRefresh: () => Promise<void> }) {
  const [open, setOpen] = useState(false); const [reason, setReason] = useState(''); const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null); const [sent, setSent] = useState(false); const lock = useRef(false);
  if (!review.publishedAt || !review.submitted || review.revieweeId !== user.id) return null;
  async function report() {
    if (lock.current) return;
    if (!reason.trim() || reason.trim().length > 2000) { setError('Lý do báo cáo cần 1–2.000 ký tự.'); return; }
    lock.current = true; setBusy(true); setError(null);
    try { await api.reportReview(review.contractId, review.id, reason.trim()); setSent(true); setOpen(false); await onRefresh(); ratingUpdated([review.revieweeId]); }
    catch (e) { setError(e); await onRefresh().catch(() => undefined); }
    finally { lock.current = false; setBusy(false); }
  }
  return <section className="review-report"><DisputeError error={error} />{sent || review.reported ? <p role="status">Đã gửi báo cáo để xem xét. Báo cáo không tự ẩn nội dung.</p> : <>
    <button className="text-button" onClick={() => setOpen(!open)}>Báo cáo nhận xét</button>
    {open && <form onSubmit={e => { e.preventDefault(); void report(); }}><label>Lý do báo cáo<textarea maxLength={2000} value={reason} disabled={busy} onChange={e => setReason(e.target.value)} /></label><button className="button button-secondary" disabled={busy}>Gửi báo cáo để xem xét</button></form>}
  </>}</section>;
}
const emptyReview: ReviewInput = { overall: 0, dimensions: { communication: 0, requirementsOrQuality: 0, timeliness: 0 }, comment: '' };
export function ContractReviews({ job, user, settlement, blocked = false, onOpportunityChange }: { job: Job; user: User; settlement: ContractSettlement | null; blocked?: boolean; onOpportunityChange?: (available: boolean) => void }) {
  const id = job.contract!.id;
  const readScope = id + ':' + user.id;
  const [loadedScope, setLoadedScope] = useState<string | null>(null);
  const [rows, setRows] = useState<Review[] | null>(null); const [draft, setDraft] = useState<ReviewInput>(emptyReview);
  const [error, setError] = useState<unknown>(null); const [busy, setBusy] = useState(false); const [notice, setNotice] = useState('');
  const [uncertain, setUncertain] = useState(false); const [checked, setChecked] = useState(false); const [ineligible, setIneligible] = useState(false);
  const [intent, setIntent] = useState<ReviewInput | null>(null);
  const [ready, setReady] = useState(false);
  const [pollTick, setPollTick] = useState(0);
  const lock = useRef(false); const generation = useRef(0);
  const reading = useRef(false);
  const load = useCallback(async () => {
    const current = ++generation.current;
    reading.current = true;
    setReady(false);
    try { const reviews = await api.contractReviews(id); if (current === generation.current) { setRows(reviews); setLoadedScope(readScope); setChecked(true); setReady(true); return reviews; } }
    catch (e) { if (current === generation.current) { setError(e); setReady(false); setChecked(false); if (e instanceof ApiError && [403, 404].includes(e.status)) setRows(null); } }
    finally { if (current === generation.current) reading.current = false; }
    return null;
  }, [id, readScope]);
  useEffect(() => { setRows(null); setIneligible(false); void load(); return () => { generation.current++; }; }, [id, user.id]);
  const own = rows?.find(r => r.reviewerId === user.id);
  // Invitations are created after completion by the server scheduler. Re-read
  // while awaiting an invitation/publication; time and focus never grant eligibility.
  useEffect(() => {
    let active = true;
    const reconcile = async () => {
      if (document.visibilityState === 'hidden' || lock.current || reading.current) return;
      await load();
    };
    const onFocus = () => { void reconcile(); };
    window.addEventListener('focus', onFocus);
    const waiting = !own || (own.submitted && !own.publishedAt);
    const timer = waiting ? window.setTimeout(async () => {
      await reconcile(); if (active) setPollTick(value => value + 1);
    }, 30000) : undefined;
    return () => { active = false; window.removeEventListener('focus', onFocus); window.clearTimeout(timer); };
  }, [own, load, pollTick]);
  const eligible = ready && loadedScope === readScope && reviewOpportunity(job, user, settlement, rows || [], blocked) && !ineligible;
  // One fetch owner and one eligibility rule drive both the existing composer and its entry point.
  const opportunity = eligible && !own?.submitted;
  useEffect(() => {
    onOpportunityChange?.(opportunity);
    return () => onOpportunityChange?.(false);
  }, [opportunity, onOpportunityChange]);
  async function submit() {
    if (lock.current || !eligible || uncertain) return;
    const payload = intent || draft;
    const invalid = validateReview(payload); if (invalid) { setError(invalid); return; }
    setIntent(payload);
    lock.current = true; setBusy(true); setError(null); setNotice('');
    try {
      const saved = await api.submitReview(id, payload); setRows(previous => (previous || []).map(r => r.id === saved.id ? saved : r));
      setNotice('Đã gửi đánh giá. Nội dung công khai theo trạng thái công bố của máy chủ.'); await load(); ratingUpdated([saved.reviewerId, saved.revieweeId]);
    } catch (e) {
      setError(e); setUncertain(true); setChecked(false);
      if (e && typeof e === 'object' && 'businessCode' in e && e.businessCode === 'REVIEW_INELIGIBLE') { setIneligible(true); await Promise.allSettled([api.job(job.id), api.settlement(id), api.cancellation(id)]); }
      const updated = await load();
      if (updated?.some(r => r.reviewerId === user.id && r.submitted)) { setUncertain(false); setError(null); setNotice('Máy chủ đã có đánh giá của bạn; không gửi lại.'); ratingUpdated(updated.map(r => r.revieweeId)); }
    } finally { lock.current = false; setBusy(false); }
  }
  const rating = (label: string, value: number, change: (v: number) => void) => <label>{label}<select aria-label={label} value={value} disabled={busy || uncertain || !!intent} onChange={e => change(Number(e.target.value))}><option value={0}>Chọn điểm</option>{[1, 2, 3, 4, 5].map(n => <option key={n} value={n}>{n} / 5</option>)}</select></label>;
  return <section id="contract-reviews" className="contract-reviews"><SectionHeading title="Đánh giá hợp đồng" description="Đánh giá hai chiều; không thể sửa hoặc xóa sau khi gửi." />
    <DisputeError error={error} />{notice && <p role="status">{notice}</p>}
    {!rows ? <p role="status">Đang đọc đánh giá…</p> : !rows.length ? <p>Chưa có lời mời đánh giá từ máy chủ.</p> : rows.map(r => <div key={r.id}><ReviewRecord review={r} user={user} mode="participant" /><ReviewReport review={r} user={user} onRefresh={async () => { await load(); }} /></div>)}
    {opportunity && <form className="review-composer" onSubmit={e => { e.preventDefault(); void submit(); }}><SectionHeading title="Đánh giá đối tác" level={3} />
      {rating('Tổng thể', draft.overall, v => setDraft(d => ({ ...d, overall: v })))}
      {rating('Giao tiếp', draft.dimensions.communication, v => setDraft(d => ({ ...d, dimensions: { ...d.dimensions, communication: v } })))}
      {rating(user.userType === 'CLIENT' ? 'Chất lượng bàn giao' : 'Độ rõ ràng của yêu cầu', draft.dimensions.requirementsOrQuality, v => setDraft(d => ({ ...d, dimensions: { ...d.dimensions, requirementsOrQuality: v } })))}
      {rating('Đúng hạn', draft.dimensions.timeliness, v => setDraft(d => ({ ...d, dimensions: { ...d.dimensions, timeliness: v } })))}
      <label>Nhận xét (không bắt buộc)<textarea maxLength={2000} disabled={busy || uncertain || !!intent} value={draft.comment || ''} onChange={e => setDraft(d => ({ ...d, comment: e.target.value }))} /></label>
      <button className="button" disabled={busy || uncertain}>{busy ? 'Đang gửi…' : 'Gửi đánh giá'}</button>
    </form>}
    {ineligible && <p>Máy chủ chưa cho phép đánh giá hợp đồng này. Bản nháp được giữ; cần đối chiếu trạng thái hợp đồng.</p>}
    {uncertain && !own?.submitted && <div className="feedback-document"><p>Chưa xác nhận được kết quả. Đọc lại trước khi gửi đúng bản nháp này.</p><button className="button button-secondary" disabled={busy} onClick={() => void load()}>Đối chiếu đánh giá</button>{checked && !ineligible && <button className="button button-secondary" onClick={() => setUncertain(false)}>Thử lại bản đánh giá đã đối chiếu</button>}</div>}
    <button className="text-button" disabled={busy} onClick={() => void load()}>Đọc lại đánh giá</button>
  </section>;
}
