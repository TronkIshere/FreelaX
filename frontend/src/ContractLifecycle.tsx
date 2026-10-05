import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from './api';
import { ActionGroup, EvidenceDisclosure, FactGrid, SectionHeading } from './components';
import { ContractCancellation, type CancellationState } from './ContractCancellation';
import { FundingPanel } from './Funding';
import { financialCopy, financialMoneyTone, settlementMoneyLabel, settlementNeedsRefresh, settlementStageLabel } from './financeStatus';
import { submissionLabel } from './status';
import { attemptScope, clearAttempt, httpsUrl, localInstant, readAttempt, saveAttempt, smallReview, validateSubmission } from './workflowContracts';
import type { ContractSettlement, ContractSubmission, ContractSummary, Job, Requirement, ReviewDecision, SubmissionPayload, User } from './types';

type SubmissionAttempt = { key: string; payload: SubmissionPayload; baselineVersion: number };
type DraftEvidence = Record<string, { selected: boolean; url: string; text: string }>;
const errorText = (cause: unknown) => cause instanceof ApiError && cause.code === 4026 ? 'Lần gửi trước có xung đột nội dung. Đối chiếu lại lịch sử; không đổi payload hoặc key.'
  : cause instanceof ApiError && cause.status === 409 ? 'Trạng thái đã thay đổi. Đang tải lại bản mới nhất trước khi tiếp tục.'
  : 'Chưa xác nhận được thao tác. Hãy đối chiếu trạng thái từ Marketplace trước khi thử lại.';

function ExternalEvidence({ url }: { url: string | null }) {
  const safe = url ? httpsUrl(url) : null;
  if (!safe) return url ? <p>Liên kết không có định dạng HTTPS an toàn.</p> : null;
  return <div className="deliverable-link"><span className="deliverable-address">{safe.hostname} · {safe.href}</span><a href={safe.href} target="_blank" rel="noopener noreferrer">Mở bằng chứng ↗</a></div>;
}
function EvidenceRecord({ submission, contract }: { submission: ContractSubmission; contract: ContractSummary }) {
  return <>
    {submission.reviewerFeedback && <section className="feedback-document" aria-label={'Phản hồi cho bản #' + submission.version}>
      <SectionHeading title="Phản hồi Client" level={3} /><p>{submission.reviewerFeedback}</p>
      {[...(submission.reviewDeliverableIds || []), ...(submission.reviewCriterionIds || [])].map(id => <p className="metadata" key={id}>Liên quan: {[...contract.deliverables, ...contract.acceptanceCriteria].find(r => r.id === id)?.title || [...contract.deliverables, ...contract.acceptanceCriteria].find(r => r.id === id)?.description || 'Yêu cầu trong hợp đồng'}</p>)}
    </section>}
    <p className="submission-summary">{submission.summary}</p>
    {(['deliverables', 'acceptanceEvidence'] as const).map(kind => submission[kind]?.length > 0 && <section className="evidence-record" key={kind}>
      <SectionHeading level={3} title={kind === 'deliverables' ? 'Sản phẩm bàn giao' : 'Bằng chứng nghiệm thu'} />
      {submission[kind].map(item => <div key={item.requirementId}>
        <strong>{(kind === 'deliverables' ? contract.deliverables : contract.acceptanceCriteria).find(r => r.id === item.requirementId)?.title || (kind === 'deliverables' ? contract.deliverables : contract.acceptanceCriteria).find(r => r.id === item.requirementId)?.description || 'Bằng chứng trong hợp đồng'}</strong>
        {item.description && <p>{item.description}</p>}<ExternalEvidence url={item.url} />
      </div>)}
    </section>)}
    <p className="metadata">Gửi {localInstant(submission.submittedAt)}{submission.submittedLate && ' · Nộp muộn'}{submission.reviewedAutomatically && ' · Máy chủ tự động duyệt'}</p>
  </>;
}

export function ReviewTiming({ submission, contract, now }: { submission: ContractSubmission; contract: ContractSummary; now: number }) {
  let small: boolean | null = null; try { small = smallReview(contract.amount); } catch { /* No fabricated policy when amount is invalid. */ }
  const due = submission.reviewDueAt ? new Date(submission.reviewDueAt).getTime() : NaN;
  const remaining = due - now;
  return <section className="review-timing" aria-label="Thời hạn review">
    {submission.reviewDueAt && <p>Hạn review: <time dateTime={submission.reviewDueAt}>{localInstant(submission.reviewDueAt)}</time></p>}
    {submission.reviewGraceDueAt && <p>Hạn gia hạn: <time dateTime={submission.reviewGraceDueAt}>{localInstant(submission.reviewGraceDueAt)}</time></p>}
    {submission.status === 'SUBMITTED' && <>
      <p className="metadata">Cửa sổ review hợp đồng: {contract.reviewWindowHours} giờ. {small === null ? '' : small ? 'Đủ điều kiện tự duyệt tại hạn review.' : 'Trên 500 USD: thêm 24 giờ gia hạn theo máy chủ.'}</p>
      {Number.isFinite(due) && <p role="status">{remaining > 0 ? 'Còn ' + Math.ceil(remaining / 60000) + ' phút đến hạn review' : 'Đang chờ máy chủ xử lý'}</p>}
    </>}
  </section>;
}

export function ContractLifecycle({ job, user, onJobUpdated, children, footer }: {
  job: Job; user: User; onJobUpdated: (job: Job) => void; children?: ReactNode; footer?: ReactNode;
}) {
  const contract = job.contract!;
  const client = user.userType === 'CLIENT' && job.clientUserId === user.id;
  const freelancer = user.userType === 'FREELANCER' && job.freelancerId === user.id;
  const scope = attemptScope('submit', user.id, contract.id, contract.milestoneId || 'missing');
  const [list, setList] = useState<ContractSubmission[]>([]);
  const [loading, setLoading] = useState(true);
  const [verified, setVerified] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [summary, setSummary] = useState('');
  const [deliverables, setDeliverables] = useState<DraftEvidence>({});
  const [acceptance, setAcceptance] = useState<DraftEvidence>({});
  const [pending, setPending] = useState<SubmissionAttempt | null>(null);
  const [decision, setDecision] = useState<ReviewDecision['decision'] | null>(null);
  const [feedback, setFeedback] = useState('');
  const [criterionIds, setCriterionIds] = useState<string[]>([]);
  const [deliverableIds, setDeliverableIds] = useState<string[]>([]);
  const [reason, setReason] = useState('QUALITY');
  const [description, setDescription] = useState('');
  const [decisionUncertain, setDecisionUncertain] = useState(false);
  const [now, setNow] = useState(Date.now());
  const [cancellation, setCancellation] = useState<CancellationState>({ record: null, ready: false, busy: false, uncertain: false });
  const [fundingBusy, setFundingBusy] = useState(false);
  const [settlement, setSettlement] = useState<ContractSettlement | null>(null);
  const [settlementLoading, setSettlementLoading] = useState(false);
  const [settlementError, setSettlementError] = useState('');
  const [settlementTick, setSettlementTick] = useState(0);
  const [settlementAttempt, setSettlementAttempt] = useState(0);
  const lock = useRef(false);
  const alive = useRef(true);
  const recovery = useRef<SubmissionAttempt | null>(null);
  const syncing = useRef<Promise<void> | null>(null);
  const latest = list[0];
  const disputed = contract.status === 'DISPUTED' || contract.milestoneStatus === 'DISPUTED' || latest?.status === 'DISPUTED';
  const releaseRelevant = contract.milestoneStatus === 'RELEASE_PENDING' || contract.milestoneStatus === 'RELEASED' || contract.status === 'COMPLETED' || latest?.status === 'APPROVED';
  const releaseConfirmed = settlement?.moneyStatus === 'SUCCEEDED';
  const releaseFailed = settlement?.moneyStatus === 'FAILED' && !settlement.retryable;
  const completed = contract.status === 'COMPLETED' || contract.milestoneStatus === 'RELEASED' || job.status === 'COMPLETED';
  const releasedPending = releaseRelevant && !releaseConfirmed && !completed;
  const refundPending = contract.milestoneStatus === 'REFUND_PENDING' || cancellation.record?.cancellationStatus === 'REFUND_PENDING';
  const cancelled = contract.status === 'CANCELLED' || job.status === 'CANCELLED' || cancellation.record?.cancellationStatus === 'CANCELLED';
  const workAllowed = cancellation.ready && !cancellation.busy && !cancellation.uncertain && !refundPending && !cancelled && !fundingBusy;
  const canSubmit = freelancer && workAllowed && verified && !disputed && !releaseRelevant && !loading &&
    ['ACTIVE', 'REVISION'].includes(contract.status) && ['FUNDED', 'IN_PROGRESS'].includes(contract.milestoneStatus || '') &&
    ['IN_PROGRESS', 'REVISION_REQUESTED'].includes(job.status) && (!latest || latest.status === 'REVISION_REQUESTED');
  const canReview = client && workAllowed && verified && !disputed && !releaseRelevant && !loading && !decisionUncertain && latest?.status === 'SUBMITTED' && contract.status === 'UNDER_REVIEW' && contract.milestoneStatus === 'SUBMITTED';
  const revisionAvailable = contract.revisionsUsed < contract.maxRevisions;

  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    if (!client && !freelancer) { setLoading(false); return; }
    let active = true;
    setLoading(true); setError('');
    (async () => {
      const saved = freelancer ? readAttempt<SubmissionAttempt>(scope) : null;
      if (saved && (!saved.key || !saved.payload || !Number.isInteger(saved.baselineVersion))) throw new Error();
      recovery.current = saved; setPending(saved);
      const returned = await api.contractSubmissions(contract.id);
      if (!active) return;
      const ordered = [...returned].sort((a, b) => b.version - a.version); setList(ordered); setVerified(true);
      if (saved && (ordered[0]?.version || 0) > saved.baselineVersion) { clearAttempt(scope); recovery.current = null; setPending(null); const fresh = await api.job(job.id); if (active) onJobUpdated(fresh); }
    })().catch(() => { if (active) setError('Không thể đối chiếu lịch sử hoặc lần gửi trước. Hãy tải lại.'); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [contract.id, scope, client, freelancer, job.id]);

  const synchronize = useCallback((): Promise<void> => {
    if (syncing.current) return syncing.current;
    const work = (async () => {
      const [fresh, returned] = await Promise.all([api.job(job.id), api.contractSubmissions(contract.id)]);
      if (!alive.current) return;
      const ordered = [...returned].sort((a, b) => b.version - a.version);
      setList(ordered); setVerified(true); onJobUpdated(fresh); setDecision(null); setDecisionUncertain(false);
      if (recovery.current && (ordered[0]?.version || 0) > recovery.current.baselineVersion) { clearAttempt(scope); recovery.current = null; setPending(null); }
    })().finally(() => { syncing.current = null; });
    syncing.current = work; return work;
  }, [contract.id, job.id, scope, onJobUpdated]);
  async function refresh() {
    if (lock.current) return;
    setBusy(true);
    try { await synchronize(); if (alive.current) setError(''); }
    catch { if (alive.current) setError('Không tải lại được trạng thái. Chưa gửi thao tác mới.'); }
    finally { if (alive.current) setBusy(false); }
  }
  useEffect(() => {
    if (latest?.status !== 'SUBMITTED' || disputed || releasedPending) return;
    let active = true; let inFlight = false; let lastFetch = 0;
    const reached = new Set<string>();
    async function refetch() {
      if (inFlight || lock.current || Date.now() - lastFetch < 3000) return;
      inFlight = true; lastFetch = Date.now();
      try { await synchronize(); } catch { if (active) setError('Chưa cập nhật được review từ máy chủ.'); }
      finally { inFlight = false; }
    }
    const clock = window.setInterval(() => {
      const time = Date.now(); setNow(time);
      for (const boundary of [latest.reviewDueAt, latest.reviewGraceDueAt]) {
        if (boundary && new Date(boundary).getTime() <= time && !reached.has(boundary)) { reached.add(boundary); void refetch(); }
      }
    }, 1000);
    const poll = window.setInterval(() => { if (document.visibilityState !== 'hidden' && latest.reviewDueAt && Date.now() >= new Date(latest.reviewDueAt).getTime()) void refetch(); }, 30000);
    const focus = () => { setNow(Date.now()); void refetch(); };
    const notification = (event: Event) => { if ((event as CustomEvent<{ jobId: string }>).detail?.jobId === job.id) void refetch(); };
    window.addEventListener('focus', focus); window.addEventListener('freelax:review-update', notification);
    return () => { active = false; clearInterval(clock); clearInterval(poll); window.removeEventListener('focus', focus); window.removeEventListener('freelax:review-update', notification); };
  }, [latest?.id, latest?.status, latest?.reviewDueAt, latest?.reviewGraceDueAt, disputed, releasedPending, synchronize, job.id]);

  useEffect(() => {
    if ((!client && !freelancer) || !releaseRelevant) return;
    let active = true;
    setSettlementLoading(true);
    api.settlement(contract.id).then(async value => {
      if (!active) return;
      setSettlement(value); setSettlementError('');
      if (value?.moneyStatus === 'SUCCEEDED' && !completed) await synchronize();
    }).catch(() => { if (active) setSettlementError('Chưa đối chiếu đầy đủ release/workflow; không suy ra kết quả từ funding hoặc checkout.'); })
      .finally(() => { if (active) setSettlementLoading(false); });
    return () => { active = false; };
  }, [client, freelancer, contract.id, contract.status, contract.milestoneStatus, releaseRelevant, completed, settlementAttempt, synchronize]);

  useEffect(() => {
    if ((!client && !freelancer) || !releaseRelevant || settlementLoading || (!settlementError && !settlementNeedsRefresh(settlement))) return;
    let active = true;
    const timer = window.setTimeout(async () => {
      if (document.visibilityState !== 'hidden' && !lock.current) {
        try {
          const value = await api.settlement(contract.id);
          if (!active) return;
          setSettlement(value); setSettlementError('');
          await synchronize();
        } catch { if (active) setSettlementError('Chưa cập nhật được release. Giữ bằng chứng đã xác nhận và tiếp tục đối soát.'); }
      }
      if (active) setSettlementTick(value => value + 1);
    }, 30000);
    return () => { active = false; window.clearTimeout(timer); };
  }, [client, freelancer, releaseRelevant, settlement, settlementError, settlementLoading, settlementTick, contract.id, synchronize]);

  function payload(): SubmissionPayload {
    return { summary: summary.trim(),
      deliverables: contract.deliverables.filter(r => deliverables[r.id]?.selected).map(r => ({ requirementId: r.id, url: deliverables[r.id].url.trim(), description: deliverables[r.id].text.trim() })),
      acceptanceEvidence: contract.acceptanceCriteria.filter(r => acceptance[r.id]?.selected).map(r => ({ criterionId: r.id, url: acceptance[r.id].url.trim(), note: acceptance[r.id].text.trim() })) };
  }
  async function submit(event: React.FormEvent) {
    event.preventDefault(); if (lock.current || !canSubmit) return;
    const value = recovery.current?.payload || payload();
    const invalid = validateSubmission(value, contract); if (invalid) { setError(invalid); return; }
    lock.current = true; setBusy(true); setError(''); setNotice('');
    try {
      if (recovery.current) {
        await synchronize();
        if (!recovery.current) { setNotice('Bản bàn giao đã được máy chủ ghi nhận.'); return; }
      }
      const saved = recovery.current || { key: crypto.randomUUID(), payload: value, baselineVersion: latest?.version || 0 };
      saveAttempt(scope, saved); recovery.current = saved; setPending(saved);
      const returned = await api.submitContract(contract.id, saved.key, saved.payload);
      if (!alive.current) return;
      clearAttempt(scope); recovery.current = null; setPending(null);
      setList(current => [returned, ...current.filter(item => item.id !== returned.id)].sort((a, b) => b.version - a.version));
      setSummary(''); setDeliverables({}); setAcceptance({}); setNotice('Đã ghi nhận bàn giao #' + returned.version + '.');
      await synchronize();
    } catch (cause) {
      if (alive.current) setError(errorText(cause));
      try { await synchronize(); } catch { /* Retain key and exact payload; no automatic repost. */ }
      if (cause instanceof ApiError && cause.status !== 0 && cause.status < 500 && cause.code !== 4026) {
        clearAttempt(scope); recovery.current = null; if (alive.current) setPending(null);
      }
    } finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  async function decide(event: React.FormEvent) {
    event.preventDefault(); if (lock.current || !canReview || !latest || !decision) return;
    let body: ReviewDecision;
    if (decision === 'REQUEST_REVISION') {
      if (!revisionAvailable || !feedback.trim() || feedback.length > 10000 || (!criterionIds.length && !deliverableIds.length)) { setError('Cần phản hồi và ít nhất một tiêu chí hoặc sản phẩm liên quan trong số lượt sửa còn lại.'); return; }
      body = { decision, feedback: feedback.trim(), criterionIds, deliverableIds };
    } else if (decision === 'OPEN_DISPUTE') {
      if (!reason.trim() || reason.length > 60 || !description.trim() || description.length > 10000) { setError('Cần mã lý do (tối đa 60 ký tự) và mô tả (tối đa 10.000 ký tự).'); return; }
      body = { decision, reasonCode: reason.trim(), description: description.trim() };
    } else body = { decision: 'APPROVE' };
    lock.current = true; setBusy(true); setError(''); setDecisionUncertain(true);
    try {
      const returned = await api.decideSubmission(contract.id, latest.id, body);
      if (alive.current) { setList(current => [returned, ...current.filter(s => s.id !== returned.id)]); setNotice('Đã ghi nhận quyết định từ máy chủ.'); }
      await synchronize();
    } catch (cause) { if (alive.current) setError(errorText(cause)); try { await synchronize(); } catch { /* Keep stale decisions disabled until state can be read. */ } }
    finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  function evidenceInputs(title: string, items: Requirement[], draft: DraftEvidence, setDraft: (value: DraftEvidence) => void) {
    return <fieldset><legend>{title}</legend>{items.map(item => {
      const value = draft[item.id] || { selected: false, url: '', text: '' };
      const update = (patch: Partial<typeof value>) => setDraft({ ...draft, [item.id]: { ...value, ...patch } });
      return <div className="evidence-input" key={item.id}><label className="selection-label"><input type="checkbox" checked={value.selected} disabled={busy || !!pending} onChange={e => update({ selected: e.target.checked })} />{item.title || item.description}</label>
        {value.selected && <><label>URL HTTPS<input type="url" maxLength={2048} value={value.url} disabled={busy || !!pending} onChange={e => update({ url: e.target.value })} /></label><label>{title === 'Sản phẩm bàn giao' ? 'Mô tả' : 'Ghi chú nghiệm thu'}<textarea maxLength={2000} value={value.text} disabled={busy || !!pending} onChange={e => update({ text: e.target.value })} /></label></>}
      </div>;
    })}</fieldset>;
  }
  if (!client && !freelancer) return null;
  const next = cancelled ? cancellation.record?.refundStatus === 'SUCCEEDED' ? 'Hợp đồng đã hủy; hoàn tiền đã xác nhận' : 'Hợp đồng đã hủy'
    : refundPending ? 'Đang đối soát hoàn tiền; chưa hủy cuối cùng'
    : cancellation.busy ? 'Đang ghi nhận thao tác hủy hợp đồng'
    : cancellation.uncertain ? 'Đang đối soát ý định hủy; chưa gửi thao tác khác'
    : disputed ? 'Đang chờ Admin xử lý'
    : releaseConfirmed ? settlement.simulation ? 'Đã xác nhận release mô phỏng' : 'Đã xác nhận release'
    : completed ? 'Phần việc đã hoàn tất theo Marketplace'
    : releaseFailed ? 'Phần việc đã duyệt; release cần xử lý lỗi'
    : releasedPending ? 'Đã duyệt; đang đối soát release'
    : !cancellation.ready ? 'Đang đối soát trạng thái hợp đồng'
    : job.status === 'AWAITING_PAYMENT' ? client ? 'Cần bạn funding hợp đồng' : 'Đang chờ Client hoàn tất funding'
    : canSubmit ? job.status === 'REVISION_REQUESTED' ? 'Bạn cần chỉnh sửa theo phản hồi' : 'Đến lượt bạn bàn giao công việc'
    : canReview ? 'Cần bạn duyệt bàn giao' : contract.status === 'REVISION' ? 'Đang chờ Freelancer gửi bản sửa'
    : contract.status === 'UNDER_REVIEW' ? 'Đang chờ Client phản hồi' : 'Đang chờ Freelancer bàn giao';
  return <div className={'lifecycle lifecycle-' + (disputed || refundPending ? 'revision' : releaseRelevant || cancelled ? 'complete' : contract.status === 'REVISION' ? 'revision' : contract.status === 'UNDER_REVIEW' ? 'review' : 'working') +
    (releasedPending ? ' lifecycle-money-' + (settlementError && !settlement ? 'error' : financialMoneyTone(settlement?.moneyStatus)) : '')}>
    <section className="ownership-band" aria-label="Lượt thực hiện"><h2>{next}</h2><ActionGroup>
      {canSubmit && <a className="text-link" href="#work-primary-action">{job.status === 'REVISION_REQUESTED' ? 'Soạn bản sửa ↓' : 'Soạn bàn giao ↓'}</a>}
      {latest && <a className="text-link" href="#latest-submission">{latest.reviewerFeedback && contract.status === 'REVISION' ? 'Xem phản hồi ↓' : 'Xem bản bàn giao ↓'}</a>}
      {releaseRelevant && <Link className="text-link" to={'/finance?jobId=' + encodeURIComponent(job.id)}>Xem trạng thái tài chính →</Link>}
    </ActionGroup></section>
    {children}
    {job.status === 'AWAITING_PAYMENT' && !cancelled && <FundingPanel job={job} user={user} onJobUpdated={onJobUpdated}
      blocked={!workAllowed || busy} operationLock={lock} onMutationChange={setFundingBusy} />}
    {latest && <section className="latest-submission" id="latest-submission" tabIndex={-1} aria-label="Bản bàn giao mới nhất"><SectionHeading title={'Bản bàn giao #' + latest.version} aside={submissionLabel(latest.status)} /><EvidenceRecord submission={latest} contract={contract} /><ReviewTiming submission={latest} contract={contract} now={now} /></section>}
    {loading && <p role="status">Đang tải lịch sử bàn giao…</p>}
    {disputed && <p role="status">Tranh chấp đang mở. Các thao tác bàn giao và review đã khóa; chờ Admin xử lý.</p>}
    {releaseRelevant && <section className="settlement-document" aria-label="Quyết toán hợp đồng">
      <SectionHeading title="Release hợp đồng" aside={settlement?.simulation ? 'Mô phỏng' : undefined} />
      {settlementLoading && <p role="status">Đang đọc trạng thái release…</p>}
      {!settlement && !settlementLoading && !settlementError && <p className="financial-status financial-status-pending" role="status">Chưa có bản ghi release để xác nhận. Đang đối soát với Marketplace.</p>}
      {settlement && <>
        <p className={'financial-status financial-status-' + financialMoneyTone(settlement.moneyStatus)} role="status">{settlementMoneyLabel(settlement.moneyStatus)}</p>
        {releaseConfirmed && <p>{settlement.simulation ? financialCopy.releaseSimulation : 'Marketplace đã xác nhận release. Chi trả ngân hàng cần bằng chứng riêng.'}</p>}
        {!releaseConfirmed && <p>{financialCopy.fundingVsRelease}</p>}
        <FactGrid facts={[{ label: 'Giá trị release', value: String(settlement.amount) + ' ' + settlement.currency },
          { label: 'Bằng chứng on-chain', value: settlementStageLabel(settlement.onChainStatus) },
          { label: 'Off-ramp', value: settlementStageLabel(settlement.offRampStatus) },
          { label: 'Tạo / Khôi phục chứng từ', value: settlementStageLabel(settlement.taxStatus) }]} />
        <p className="metadata">{financialCopy.taxVsCertificate}</p>
        {settlement.moneyStatus === 'FAILED' && <p role="alert">Release chưa thành công. Máy chủ xử lý trạng thái này; không có thao tác giải ngân thủ công trong UI.</p>}
        <EvidenceDisclosure summary="Tham chiếu release / Lỗi từng chặng"><dl className="reference-list">
          {([['releaseReference', 'Tham chiếu release'], ['onChainReference', 'Tham chiếu on-chain'], ['offRampReference', 'Tham chiếu chi trả VND'], ['taxReference', 'Tham chiếu chứng từ'], ['lastError', 'Lỗi release'], ['onChainError', 'Lỗi on-chain'], ['offRampError', 'Lỗi VND'], ['taxError', 'Lỗi thuế']] as const).map(([field, label]) => settlement[field] && <div key={field}><dt>{label}</dt><dd><code>{settlement[field]}</code></dd></div>)}
          <div><dt>Cập nhật từ Marketplace</dt><dd>{settlement.updatedAt}</dd></div>
        </dl></EvidenceDisclosure>
      </>}
      {settlementError && <p className="form-error" role="alert">{settlementError}</p>}
      <button className="text-button" disabled={settlementLoading || busy || cancellation.busy} onClick={() => setSettlementAttempt(value => value + 1)}>Đối chiếu release</button>
    </section>}
    <ContractCancellation key={contract.id + ':' + user.id} job={job} user={user} submissionCount={verified ? list.length : null}
      workflowBusy={busy || fundingBusy} operationLock={lock} onJobUpdated={onJobUpdated} onStateChange={setCancellation} />
    {canSubmit && <section className="work-composer" id="work-primary-action" tabIndex={-1}><SectionHeading title={job.status === 'REVISION_REQUESTED' ? 'Gửi bản sửa' : 'Gửi bàn giao'} />
      {pending && <p role="status">Giữ nguyên nội dung lần gửi trước để đối chiếu. Không tạo phiên bản mới khi kết quả chưa rõ.</p>}
      <form onSubmit={submit}><label>Tóm tắt bàn giao<textarea required maxLength={10000} value={pending?.payload.summary ?? summary} disabled={busy || !!pending} onChange={e => setSummary(e.target.value)} /></label>
        {!pending && <>{evidenceInputs('Sản phẩm bàn giao', contract.deliverables, deliverables, setDeliverables)}{evidenceInputs('Bằng chứng nghiệm thu', contract.acceptanceCriteria, acceptance, setAcceptance)}</>}
        <ActionGroup><button className="button" disabled={busy}>{busy ? 'Đang gửi…' : pending ? 'Đối chiếu và tiếp tục lần gửi trước' : job.status === 'REVISION_REQUESTED' ? 'Gửi bản sửa' : 'Gửi bàn giao'}</button></ActionGroup>
      </form></section>}
    {canReview && <section className="review-action" id="work-primary-action" tabIndex={-1}><SectionHeading title="Quyết định của bạn" />
      <p className="metadata">Chỉnh sửa đã dùng: {contract.revisionsUsed}/{contract.maxRevisions}</p>
      {!decision ? <ActionGroup><button className="button" disabled={busy} onClick={() => setDecision('APPROVE')}>Duyệt bàn giao</button><button className="button button-secondary" disabled={busy || !revisionAvailable} onClick={() => setDecision('REQUEST_REVISION')}>Yêu cầu chỉnh sửa</button><button className="text-button" disabled={busy} onClick={() => setDecision('OPEN_DISPUTE')}>Mở tranh chấp</button></ActionGroup>
        : <form className="decision-form" onSubmit={decide} aria-label="Xác nhận quyết định">
          {decision === 'APPROVE' && <p>Duyệt bản #{latest.version}? Phần việc được duyệt; tiền chuyển sang chờ xử lý, chưa giải ngân.</p>}
          {decision === 'REQUEST_REVISION' && <><label>Phản hồi chỉnh sửa<textarea required maxLength={10000} disabled={busy} value={feedback} onChange={e => setFeedback(e.target.value)} /></label>
            {([['Tiêu chí liên quan', contract.acceptanceCriteria, criterionIds, setCriterionIds], ['Sản phẩm liên quan', contract.deliverables, deliverableIds, setDeliverableIds]] as const).map(([title, items, ids, setIds]) => <fieldset key={title}><legend>{title}</legend>{items.map(item => <label className="selection-label" key={item.id}><input type="checkbox" disabled={busy} checked={ids.includes(item.id)} onChange={e => setIds(e.target.checked ? [...ids, item.id] : ids.filter(id => id !== item.id))} />{item.title || item.description}</label>)}</fieldset>)}</>}
          {decision === 'OPEN_DISPUTE' && <><p>Mở tranh chấp cho bản #{latest.version} và khóa review để chờ Admin.</p><label>Mã lý do<input required maxLength={60} value={reason} disabled={busy} onChange={e => setReason(e.target.value)} /></label><label>Mô tả tranh chấp<textarea required maxLength={10000} value={description} disabled={busy} onChange={e => setDescription(e.target.value)} /></label></>}
          <ActionGroup><button className="button" disabled={busy}>{busy ? 'Đang ghi nhận…' : decision === 'APPROVE' ? 'Xác nhận duyệt' : decision === 'REQUEST_REVISION' ? 'Gửi yêu cầu chỉnh sửa' : 'Xác nhận mở tranh chấp'}</button><button className="button button-secondary" type="button" disabled={busy} onClick={() => setDecision(null)}>Quay lại</button></ActionGroup>
        </form>}
    </section>}
    {notice && <p role="status" className="lifecycle-success">{notice}</p>}{error && <p role="alert" className="form-error">{error}</p>}
    <button className="text-button" disabled={busy || loading} onClick={() => void refresh()}>Đối chiếu workflow</button>
    <nav className="workflow-rail" aria-label="Các bước trình bày workflow"><span className="past">Assigned</span><span className={contract.status === 'ACTIVE' && !refundPending && !cancelled ? 'current' : 'past'} aria-current={contract.status === 'ACTIVE' && !refundPending && !cancelled ? 'step' : undefined}>Working</span><span className={contract.status === 'UNDER_REVIEW' && !releaseRelevant ? 'current' : 'future'} aria-current={contract.status === 'UNDER_REVIEW' && !releaseRelevant ? 'step' : undefined}>Review</span><span className={contract.status === 'REVISION' ? 'current' : 'future'} aria-current={contract.status === 'REVISION' ? 'step' : undefined}>Revision</span><span className={releaseRelevant ? 'current' : 'future'} aria-current={releaseRelevant ? 'step' : undefined}>Approved</span>{disputed && <span className="current" aria-current="step">Tranh chấp</span>}{refundPending && <span className="current" aria-current="step">Đối soát hoàn tiền</span>}{cancelled && <span className="current" aria-current="step">Đã hủy</span>}</nav>
    <section className="submission-ledger" aria-label="Lịch sử bàn giao"><SectionHeading title="Lịch sử bàn giao" aside={!loading ? list.length + ' phiên bản' : undefined} />
      {!loading && !list.length && <p>Chưa có bản bàn giao.</p>}
      {list.slice(1).map(item => <details className="ledger-row" key={item.id}><summary><strong>#{item.version}</strong><span>{submissionLabel(item.status)}</span><time>{localInstant(item.submittedAt)}</time></summary><div className="ledger-body"><EvidenceRecord submission={item} contract={contract} /></div></details>)}
    </section>
    {footer}
    {list.length > 0 && <EvidenceDisclosure summary="Tham chiếu bàn giao"><dl className="reference-list">{list.map(item => <div key={item.id}><dt>Bản #{item.version}</dt><dd><code>{item.id}</code>{item.disputeId && <p>Dispute: <code>{item.disputeId}</code></p>}</dd></div>)}</dl></EvidenceDisclosure>}
  </div>;
}
