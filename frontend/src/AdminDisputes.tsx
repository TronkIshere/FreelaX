import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ApiError, hasAuthority } from './api';
import { ActionGroup, EvidenceDisclosure, FactGrid, PageHeading, SectionHeading, StatePanel } from './components';
import { DisputeError, DisputeLink, DisputeRecord } from './ContractDispute';
import { disputeLabel, disputeLabels, fundingLabel, submissionLabel } from './status';
import { clearAttempt, localInstant, readAttempt, saveAttempt } from './workflowContracts';
import type { AdminDisputeDetail as CaseDetail, Dispute, DisputeDecision, DisputeStatus, SpringPage, User } from './types';

function AccessDenied() { return <StatePanel kind="error" title="Không có quyền quản trị" body="Workspace này yêu cầu quyền ROLE_ADMIN do Marketplace xác nhận." />; }
export function AdminDisputes({ user }: { user: User }) {
  return hasAuthority(user, 'ROLE_ADMIN') ? <DisputeQueue /> : <AccessDenied />;
}
function DisputeQueue() {
  const [status, setStatus] = useState<DisputeStatus>('OPEN'); const [page, setPage] = useState(0);
  const [result, setResult] = useState<SpringPage<Dispute> | null>(null);
  const [loading, setLoading] = useState(true); const [error, setError] = useState<unknown>(null); const [tick, setTick] = useState(0);
  useEffect(() => {
    let active = true; setLoading(true); setError(null); setResult(null);
    api.adminDisputes(status, page).then(value => { if (active) setResult(value); }, cause => { if (active) setError(cause); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [status, page, tick]);
  return <>
    <PageHeading eyebrow="Quản trị / Tranh chấp" title="Hồ sơ cần đối chiếu." description="Tiếp nhận hồ sơ, xem bằng chứng và đưa ra quyết định trong phạm vi MVP." />
    <label className="admin-filter">Trạng thái hồ sơ<select value={status} onChange={e => { setPage(0); setStatus(e.target.value as DisputeStatus); }}>
      {Object.entries(disputeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
    </select></label>
    {loading && <StatePanel kind="loading" title="Đang tải hàng đợi" body="Marketplace đang trả hồ sơ theo trạng thái đã chọn." />}
    <DisputeError error={error} />
    {!loading && !error && !result?.content.length && <StatePanel kind="empty" title="Không có hồ sơ" body="Không có hồ sơ ở trang và trạng thái này." />}
    {result && <section className="admin-queue" aria-label="Hàng đợi tranh chấp">
      {result.content.map(item => <article className="admin-case-row" key={item.disputeId}>
        <div><h2><Link to={'/admin/disputes/' + encodeURIComponent(item.disputeId)}>{item.reasonCode}</Link></h2><p>{item.description}</p></div>
        <div><strong>{disputeLabel(item.status)}</strong><p className="metadata">Mở {localInstant(item.openedAt)}</p><p className="metadata">{item.claimedBy ? 'Đã có Admin tiếp nhận' : 'Chưa tiếp nhận'}</p></div>
      </article>)}
      <ActionGroup label="Phân trang hồ sơ"><button className="button button-secondary" disabled={loading || result.number <= 0} onClick={() => setPage(result.number - 1)}>Trang trước</button>
        <span>Trang {result.number + 1}/{Math.max(1, result.totalPages)} · {result.totalElements} hồ sơ</span>
        <button className="button button-secondary" disabled={loading || result.number + 1 >= result.totalPages} onClick={() => setPage(result.number + 1)}>Trang sau</button>
      </ActionGroup>
    </section>}
    <button className="text-button" disabled={loading} onClick={() => setTick(t => t + 1)}>Tải lại hàng đợi</button>
  </>;
}
export function AdminDisputeDetail({ user }: { user: User }) {
  const { disputeId = '' } = useParams();
  return hasAuthority(user, 'ROLE_ADMIN') ? <AdminCase key={user.id + ':' + disputeId} id={disputeId} user={user} /> : <AccessDenied />;
}
type DecisionAttempt = { key: string; payload: DisputeDecision };
function AdminCase({ id, user }: { id: string; user: User }) {
  const [detail, setDetail] = useState<CaseDetail | null>(null); const [loading, setLoading] = useState(true);
  const [verified, setVerified] = useState(false); const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null); const [notice, setNotice] = useState('');
  const [outcome, setOutcome] = useState<DisputeDecision['outcome'] | null>(null); const [reason, setReason] = useState('');
  const [confirm, setConfirm] = useState(false); const [attempt, setAttempt] = useState<DecisionAttempt | null>(null);
  const [recoveryReady, setRecoveryReady] = useState(false);
  const lock = useRef(false); const alive = useRef(true); const saved = useRef<DecisionAttempt | null>(null);
  const scope = 'freelax:admin-decision:' + user.id + ':' + id;
  const read = useCallback(async () => {
    let value: CaseDetail;
    try { value = await api.adminDispute(id); }
    catch (cause) {
      if (alive.current) {
        setVerified(false);
        if (cause instanceof ApiError && [403, 404].includes(cause.status)) setDetail(null);
      }
      throw cause;
    }
    if (alive.current) {
      setDetail(value); setVerified(true);
      if (value.dispute.status !== 'UNDER_REVIEW' && saved.current) {
        clearAttempt(scope); saved.current = null; setAttempt(null); setOutcome(null); setConfirm(false);
      }
    }
    return value;
  }, [id, scope]);
  useEffect(() => {
    alive.current = true; setLoading(true);
    (async () => {
      const prior = readAttempt<DecisionAttempt>(scope);
      if (prior && (!prior.key || !prior.payload || !['RELEASE_TO_FREELANCER', 'REFUND_TO_CLIENT'].includes(prior.payload.outcome) || !prior.payload.reason?.trim() || prior.payload.reason.length > 2000)) throw new Error();
      saved.current = prior; setAttempt(prior);
      setRecoveryReady(true);
      if (prior) { setOutcome(prior.payload.outcome); setReason(prior.payload.reason); }
      await read();
    })().catch(cause => { if (alive.current) setError(cause); }).finally(() => { if (alive.current) setLoading(false); });
    return () => { alive.current = false; };
  }, [read, scope]);
  const dispute = detail?.dispute;
  const participant = detail?.contract.clientId === user.id || detail?.contract.freelancerId === user.id;
  const canClaim = recoveryReady && verified && !participant && dispute?.status === 'OPEN';
  const canResolve = recoveryReady && verified && !participant && dispute?.status === 'UNDER_REVIEW' && dispute.claimedBy === user.id;
  useEffect(() => {
    if (!dispute || !['DECISION_PENDING_RELEASE', 'DECISION_PENDING_REFUND'].includes(dispute.status)) return;
    const timer = window.setInterval(() => { if (!lock.current && document.visibilityState !== 'hidden') void read().catch(setError); }, 30000);
    return () => clearInterval(timer);
  }, [dispute?.status, read]);
  async function refresh() {
    if (lock.current) return; setBusy(true);
    try { await read(); setError(null); } catch (cause) { setVerified(false); setError(cause); } finally { if (alive.current) setBusy(false); }
  }
  async function claim() {
    if (lock.current || !canClaim) return;
    lock.current = true; setBusy(true); setError(null); setVerified(false);
    try { await api.claimDispute(id); await read(); if (alive.current) setNotice('Đã tiếp nhận hồ sơ. Bằng chứng participant đã khóa.'); }
    catch (cause) { if (alive.current) setError(cause); try { await read(); } catch { /* No blind claim retry. */ } }
    finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  async function resolve(event: React.FormEvent) {
    event.preventDefault(); if (lock.current || !canResolve || !confirm || !outcome) return;
    const payload = saved.current?.payload || { outcome, reason };
    if (!payload.reason.trim() || payload.reason.length > 2000) { setError('Lý do quyết định là bắt buộc, tối đa 2.000 ký tự.'); return; }
    lock.current = true; setBusy(true); setError(null); setVerified(false);
    try {
      if (saved.current) {
        const current = await read();
        if (current.dispute.status !== 'UNDER_REVIEW' || current.dispute.claimedBy !== user.id) return;
      }
      const intent = saved.current || { key: crypto.randomUUID(), payload: { ...payload, reason: payload.reason.trim() } };
      saveAttempt(scope, intent); saved.current = intent; setAttempt(intent);
      const returned = await api.resolveDispute(id, intent.key, intent.payload);
      if (alive.current) { setDetail(current => current ? { ...current, dispute: returned } : current); setNotice('Quyết định đã lưu. Kết quả tiền được xác nhận riêng.'); }
      clearAttempt(scope); saved.current = null; setAttempt(null); setOutcome(null); setConfirm(false); await read();
    } catch (cause) {
      if (alive.current) setError(cause);
      try { await read(); } catch { /* Keep exact key and payload; read before replay. */ }
    } finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  return <>
    <PageHeading eyebrow="Quản trị / Hồ sơ tranh chấp" title={detail?.contract.title || 'Đối chiếu hồ sơ.'} description="Quyết định release hoặc hoàn tiền toàn bộ. Marketplace thực thi và đối soát kết quả tài chính." />
    <Link className="text-link" to="/admin/disputes">← Hàng đợi tranh chấp</Link>
    {loading && <StatePanel kind="loading" title="Đang tải hồ sơ" body="Đang đọc hợp đồng, bằng chứng và audit được phép truy cập." />}
    <DisputeError error={error} />
    {detail && <div className="admin-case-document">
      <DisputeRecord dispute={detail.dispute} />
      <section><SectionHeading title="Hợp đồng tại thời điểm đối chiếu" /><p>{detail.contract.description}</p>
        <FactGrid facts={[{ label: 'Giá trị hợp đồng', value: String(detail.contract.amount) + ' ' + detail.contract.currency },
          { label: 'Hạn bàn giao', value: localInstant(detail.contract.deliveryDueAt) },
          { label: 'Lượt sửa đã dùng', value: detail.contract.revisionsUsed + '/' + detail.contract.maxRevisions },
          { label: 'Funding', value: detail.fundingStatus ? fundingLabel(detail.fundingStatus) : 'Chưa có bản ghi funding' }]} />
        <SectionHeading level={3} title="Sản phẩm bàn giao" />{detail.deliverables.map(item => <p key={item.id}>{item.title} · {item.description}{item.required && ' (bắt buộc)'}</p>)}
        <SectionHeading level={3} title="Tiêu chí nghiệm thu" />{detail.acceptanceCriteria.map(item => <p key={item.id}>{item.description}{item.required && ' (bắt buộc)'}</p>)}
        <EvidenceDisclosure summary="Danh tính participant / Hợp đồng"><p>Client: <code>{detail.contract.clientId}</code></p><p>Freelancer: <code>{detail.contract.freelancerId}</code></p><p>Contract: <code>{detail.contract.contractId}</code></p></EvidenceDisclosure>
      </section>
      <section className="submission-ledger"><SectionHeading title="Toàn bộ lịch sử bàn giao" />
        {!detail.submissions.length && <p>Chưa có bản bàn giao.</p>}
        {[...detail.submissions].sort((a, b) => b.version - a.version).map(item => <details className="ledger-row" key={item.id}><summary>Bản #{item.version} · {submissionLabel(item.status)} · {localInstant(item.submittedAt)}</summary>
          <div className="ledger-body"><p>{item.summary}</p>{item.reviewerFeedback && <p>Phản hồi: {item.reviewerFeedback}</p>}{item.evidence.map((e, index) => <div key={index}><p>{e.description}</p><DisputeLink url={e.url} /></div>)}
            {item.reviewedAt && <p className="metadata">Review: {localInstant(item.reviewedAt)}</p>}
          </div></details>)}
      </section>
      <section className="admin-decision"><SectionHeading title="Quyết định hồ sơ" />
        {participant && <p>Admin là participant của hợp đồng này không được tiếp nhận hoặc quyết định hồ sơ.</p>}
        {canClaim && <button className="button" disabled={busy} onClick={() => void claim()}>Tiếp nhận hồ sơ</button>}
        {dispute?.status === 'UNDER_REVIEW' && !canResolve && !participant && <p>Chỉ Admin đã tiếp nhận hồ sơ mới được quyết định.</p>}
        {canResolve && <>
          {!outcome && <ActionGroup><button className="button" disabled={busy} onClick={() => setOutcome('RELEASE_TO_FREELANCER')}>Release cho Freelancer</button>
            <button className="button button-secondary" disabled={busy} onClick={() => setOutcome('REFUND_TO_CLIENT')}>Hoàn tiền cho Client</button></ActionGroup>}
          {outcome && <form className="admin-resolution-form" onSubmit={resolve}>
            <h3>{outcome === 'RELEASE_TO_FREELANCER' ? 'Xác nhận release cho Freelancer' : 'Xác nhận hoàn tiền cho Client'}</h3>
            <p>Quyết định toàn bộ giá trị hợp đồng là không thể đảo ngược trong MVP. Không có chia phần hoặc mở lại. Quyết định đã lưu không đồng nghĩa tiền đã hoàn tất.</p>
            <label>Lý do quyết định<textarea required maxLength={2000} value={attempt?.payload.reason ?? reason} disabled={busy || !!attempt} onChange={e => setReason(e.target.value)} /></label>
            <label className="selection-label"><input type="checkbox" checked={confirm} disabled={busy} onChange={e => setConfirm(e.target.checked)} />Tôi xác nhận kết quả và lý do quyết định</label>
            {attempt && <p>Giữ nguyên key và nội dung quyết định trước. Đọc trạng thái máy chủ trước khi tiếp tục.</p>}
            <ActionGroup><button className="button" disabled={busy || !confirm}>{busy ? 'Đang ghi nhận…' : attempt ? 'Đối chiếu và tiếp tục quyết định' : 'Xác nhận quyết định'}</button>
              {!attempt && <button className="button button-secondary" type="button" disabled={busy} onClick={() => { setOutcome(null); setConfirm(false); }}>Quay lại</button>}
            </ActionGroup>
          </form>}
        </>}
        {notice && <p role="status">{notice}</p>}
      </section>
      <section className="admin-audit"><SectionHeading title="Audit hồ sơ" description="Request ID dưới đây là tham chiếu audit/idempotency; khác mã yêu cầu HTTP của lỗi API." />
        {detail.audit.map((item, i) => <details className="ledger-row" key={i}><summary>{({ OPENED: 'Mở hồ sơ', CLAIMED: 'Admin tiếp nhận', DECIDED: 'Admin quyết định', EVIDENCE_ADDED: 'Bổ sung bằng chứng', RESOLVED: 'Hoàn tất hồ sơ' } as Record<string, string>)[item.action] || 'Cập nhật hồ sơ'} · {localInstant(item.createdAt)}</summary>
          <div className="ledger-body"><p>{disputeLabel(item.beforeStatus || '')} → {disputeLabel(item.afterStatus)}</p>{item.reason && <p>{item.reason}</p>}<p className="metadata">Actor: <code>{item.actorId}</code></p>{item.requestId && <p className="metadata">Tham chiếu audit: <code>{item.requestId}</code></p>}</div>
        </details>)}
      </section>
      <Link className="text-link" to={'/work/' + encodeURIComponent(detail.dispute.jobId) + '#contract-dispute'}>Công việc liên quan →</Link>
    </div>}
    <button className="text-button" disabled={busy || loading} onClick={() => void refresh()}>Đối chiếu hồ sơ</button>
  </>;
}
