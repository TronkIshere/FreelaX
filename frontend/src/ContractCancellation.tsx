import { useCallback, useEffect, useRef, useState, type MutableRefObject } from 'react';
import { api, ApiError } from './api';
import { ActionGroup, EvidenceDisclosure, FactGrid, SectionHeading } from './components';
import { cancellationLabel, refundLabel } from './status';
import { financialCopy, financialMoneyTone } from './financeStatus';
import { attemptScope, clearAttempt, localInstant, readAttempt, saveAttempt } from './workflowContracts';
import type { CancellationDecision, CancellationRequest, ContractCancellationRecord, Job, User } from './types';

type Intent = { kind: 'request'; payload: CancellationRequest }
  | { kind: 'decision'; cancellationId: string; decision: CancellationDecision };
export type CancellationState = {
  record: ContractCancellationRecord | null; ready: boolean; busy: boolean; uncertain: boolean;
};
type Props = {
  job: Job; user: User; submissionCount: number | null; workflowBusy: boolean;
  operationLock: MutableRefObject<boolean>;
  onJobUpdated: (job: Job) => void; onStateChange: (state: CancellationState) => void;
};

function matchesIntent(row: ContractCancellationRecord | null, intent: Intent, userId: string): boolean {
  if (!row) return false;
  if (intent.kind === 'request') return row.requestedBy === userId && row.reasonCode === intent.payload.reasonCode && row.reason === intent.payload.description;
  return row.cancellationId === intent.cancellationId && row.decidedBy === userId &&
    (intent.decision === 'REJECT' ? row.cancellationStatus === 'REJECTED'
      : ['REFUND_PENDING', 'CANCELLED'].includes(row.cancellationStatus));
}

export function ContractCancellation({ job, user, submissionCount, workflowBusy, operationLock, onJobUpdated, onStateChange }: Props) {
  const contract = job.contract!;
  const client = user.userType === 'CLIENT' && job.clientUserId === user.id;
  const participant = client || (user.userType === 'FREELANCER' && job.freelancerId === user.id);
  const scope = 'freelax:cancellation:' + user.id + ':' + contract.id;
  const [row, setRow] = useState<ContractCancellationRecord | null>(null);
  const [ready, setReady] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [eligible, setEligible] = useState(false);
  const [acceptEligible, setAcceptEligible] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [form, setForm] = useState(false);
  const [reasonCode, setReasonCode] = useState('MUTUAL_CANCELLATION');
  const [description, setDescription] = useState('');
  const [confirmation, setConfirmation] = useState<CancellationDecision | null>(null);
  const [pollTick, setPollTick] = useState(0);
  const alive = useRef(true);
  const reading = useRef<Promise<{ row: ContractCancellationRecord | null; eligible: boolean; acceptEligible: boolean }> | null>(null);
  const intent = useRef<Intent | null>(null);
  const snapshot = useRef({ job, submissionCount });
  snapshot.current = { job, submissionCount };
  const updateJob = useRef(onJobUpdated); updateJob.current = onJobUpdated;

  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => { onStateChange({ record: row, ready, busy, uncertain }); }, [row, ready, busy, uncertain, onStateChange]);

  const read = useCallback((refreshJob = true, verifyHistory = false) => {
    if (reading.current) return reading.current;
    const work = (async () => {
      try {
        const [record, current] = await Promise.all([api.cancellation(contract.id),
          refreshJob ? api.job(job.id) : Promise.resolve(snapshot.current.job)]);
        const c = current.contract;
        const candidate = !!c && (c.status === 'PENDING_FUNDING' || c.status === 'ACTIVE') &&
          ['AWAITING_PAYMENT', 'IN_PROGRESS'].includes(current.status) &&
          ['PENDING_FUNDING', 'FUNDED', 'IN_PROGRESS'].includes(c.milestoneStatus || '');
        let canRequest = false;
        let canAccept = false;
        if (candidate && (!record || record.cancellationStatus === 'REQUESTED') && c?.milestoneId) {
          const [funding, settlement, historyCount] = await Promise.all([
            api.funding(c.id, c.milestoneId), api.settlement(c.id),
            verifyHistory ? api.contractSubmissions(c.id).then(list => list.length) : Promise.resolve(snapshot.current.submissionCount),
          ]);
          // A local unresolved funding attempt also forbids cancellation, even if latest GET is null.
          const localFunding = readAttempt<unknown>(attemptScope('fund', user.id, c.id, c.milestoneId));
          const preFunding = client && current.status === 'AWAITING_PAYMENT' && c.status === 'PENDING_FUNDING' &&
            c.milestoneStatus === 'PENDING_FUNDING' && funding === null && localFunding === null;
          const funded = current.status === 'IN_PROGRESS' && c.status === 'ACTIVE' &&
            ['FUNDED', 'IN_PROGRESS'].includes(c.milestoneStatus || '') && funding?.fundingStatus === 'SUCCEEDED';
          canAccept = historyCount === 0 && settlement === null && funded;
          canRequest = record === null && historyCount === 0 && settlement === null && (preFunding || funded);
        }
        if (alive.current) {
          setRow(record); setEligible(canRequest); setAcceptEligible(canAccept); setReady(true); setError('');
          if (refreshJob) updateJob.current(current);
          if (intent.current && matchesIntent(record, intent.current, user.id)) {
            clearAttempt(scope); intent.current = null; setUncertain(false); setForm(false); setConfirmation(null);
            setNotice('Đã đối chiếu kết quả từ Marketplace.');
          } else if (record && intent.current?.kind === 'request') {
            // The backend permits exactly one immutable proposal. A different stored record resolves the conflict.
            clearAttempt(scope); intent.current = null; setUncertain(false); setForm(false);
            setNotice('Marketplace đã ghi nhận một đề nghị khác. Đọc bản ghi này trước khi quyết định.');
          }
        }
        return { row: record, eligible: canRequest, acceptEligible: canAccept };
      } catch (cause) {
        if (alive.current) { setReady(false); setError('Chưa đối chiếu được hủy/hoàn tiền. Chưa gửi thao tác mới.'); }
        throw cause;
      } finally { if (alive.current) setLoading(false); }
    })().finally(() => { reading.current = null; });
    reading.current = work; return work;
  }, [contract.id, job.id, user.id, client, scope]);

  useEffect(() => {
    if (!participant) { setLoading(false); return; }
    try {
      const saved = readAttempt<Intent>(scope);
      if (saved && !((saved.kind === 'request' && typeof saved.payload?.reasonCode === 'string' && typeof saved.payload?.description === 'string') ||
        (saved.kind === 'decision' && typeof saved.cancellationId === 'string' && ['ACCEPT', 'REJECT'].includes(saved.decision)))) throw new Error();
      intent.current = saved; setUncertain(!!saved);
      if (saved?.kind === 'request') { setReasonCode(saved.payload.reasonCode); setDescription(saved.payload.description); }
      void read(!!saved).catch(() => {});
    } catch { setLoading(false); setReady(false); setUncertain(true); setError('Không đọc được lần hủy trước. Chưa gửi thao tác mới.'); }
  }, [participant, scope, read]);

  // Re-evaluate eligibility when verified submission history arrives; no new mutation is inferred.
  useEffect(() => {
    let active = true;
    if (participant && submissionCount !== null && !operationLock.current) {
      void (async () => {
        if (reading.current) await reading.current.catch(() => {});
        if (active && !operationLock.current) await read(false).catch(() => {});
      })();
    }
    return () => { active = false; };
  }, [participant, submissionCount, contract.status, contract.milestoneStatus, read, operationLock]);

  const final = row?.cancellationStatus === 'REJECTED' ||
    (row?.cancellationStatus === 'CANCELLED' && (row.refundStatus === null || row.refundStatus === 'SUCCEEDED'));
  const candidateState = ['PENDING_FUNDING', 'ACTIVE'].includes(contract.status);
  useEffect(() => {
    if (!participant || loading || (final && !uncertain) || (!row && !candidateState && !uncertain && job.status !== 'CANCELLED')) return;
    let active = true;
    const timer = window.setTimeout(async () => {
      if (document.visibilityState !== 'hidden' && !operationLock.current) await read().catch(() => {});
      if (active) setPollTick(value => value + 1);
    }, 30000);
    return () => { active = false; window.clearTimeout(timer); };
  }, [participant, loading, final, uncertain, row, candidateState, job.status, pollTick, read, operationLock]);

  async function reconcile() {
    if (operationLock.current) return;
    setLoading(true); await read().catch(() => {});
  }
  async function mutate(next: Intent) {
    if (operationLock.current || workflowBusy || !participant || !ready) return;
    operationLock.current = true; setBusy(true); setNotice(''); setError('');
    let sent = false;
    try {
      // Re-read current eligibility/allowedActions immediately before sending. Server rechecks atomically.
      if (reading.current) await reading.current;
      const current = await read(true, true);
      if (!alive.current) return;
      if (matchesIntent(current.row, next, user.id)) return;
      if (next.kind === 'request' ? !current.eligible || current.row !== null
        : !current.row || current.row.cancellationId !== next.cancellationId || current.row.requestedBy === user.id ||
          current.row.cancellationStatus !== 'REQUESTED' || !current.row.allowedActions.includes(next.decision) ||
          (next.decision === 'ACCEPT' && !current.acceptEligible)) {
        setError('Trạng thái không còn cho phép thao tác này. Kết quả mới nhất đã được tải.'); return;
      }
      if (intent.current && JSON.stringify(intent.current) !== JSON.stringify(next)) {
        setError('Giữ nguyên ý định trước để đối soát; không gửi quyết định khác.'); return;
      }
      saveAttempt(scope, next); intent.current = next; setUncertain(true); sent = true;
      const returned = next.kind === 'request' ? await api.requestCancellation(contract.id, next.payload)
        : await api.decideCancellation(contract.id, next.cancellationId, next.decision);
      if (!alive.current) return;
      setRow(returned); setEligible(false);
      await read();
    } catch (cause) {
      if (!alive.current) return;
      const ambiguous = sent && (!(cause instanceof ApiError) || cause.status === 0 || cause.status >= 500 || cause.status === 408);
      try { await read(); } catch { /* Persist exact intent; reconciliation is read-only. */ }
      if (!alive.current) return;
      if (!ambiguous && intent.current) { clearAttempt(scope); intent.current = null; setUncertain(false); }
      if (ambiguous && intent.current) setError('Kết quả chưa rõ. Đang đối soát từ Marketplace; không tạo đề nghị hoặc quyết định khác.');
      else if (cause instanceof ApiError && cause.status === 409) setError('Hợp đồng đã thay đổi hoặc thao tác xung đột. Kiểm tra trạng thái mới trước khi tiếp tục.');
      else if (!sent || intent.current) setError('Chưa thực hiện được thao tác. Giữ nội dung và đối chiếu lại.');
    } finally { operationLock.current = false; if (alive.current) setBusy(false); }
  }
  function request(event: React.FormEvent) {
    event.preventDefault();
    const payload = { reasonCode: reasonCode.trim(), description: description.trim() };
    if (!payload.reasonCode || reasonCode.length > 60 || !payload.description || description.length > 2000) {
      setError('Cần mã lý do (tối đa 60 ký tự) và mô tả (tối đa 2.000 ký tự).'); return;
    }
    void mutate({ kind: 'request', payload });
  }
  if (!participant) return null;
  if (ready && !row && !candidateState && !uncertain && !error && job.status !== 'CANCELLED') return null;
  const refundConfirmed = row?.cancellationStatus === 'CANCELLED' && row.refundStatus === 'SUCCEEDED';
  const ownRequest = row?.requestedBy === user.id;
  const canDecide = ready && !busy && !workflowBusy && !uncertain && row?.cancellationStatus === 'REQUESTED' && !ownRequest;
  const replay = intent.current;
  return <section className="cancellation-document" aria-label="Hủy hợp đồng / Hoàn tiền">
    <SectionHeading title={refundConfirmed ? 'Hợp đồng đã hủy / Hoàn tiền đã xác nhận' : 'Hủy hợp đồng / Hoàn tiền'}
      aside={row?.simulation ? 'Mô phỏng' : undefined} />
    {loading && <p role="status">Đang đối chiếu điều kiện hủy…</p>}
    {row && <>
      <p className={'financial-status financial-status-' + (row.refundStatus ? financialMoneyTone(row.refundStatus) : row.cancellationStatus === 'CANCELLED' || row.cancellationStatus === 'REJECTED' ? 'done' : 'pending')} role="status">{cancellationLabel(row.cancellationStatus)}</p>
      {row.cancellationStatus === 'REQUESTED' && <p>{ownRequest ? 'Bạn đã gửi đề nghị; chờ đối tác quyết định.' : 'Đối tác đề nghị hủy hợp đồng.'} Công việc vẫn tiếp tục; chưa có hoàn tiền.</p>}
      {row.cancellationStatus === 'REJECTED' && <p>Đề nghị hủy đã bị từ chối. Công việc tiếp tục, không có hoàn tiền.</p>}
      {row.cancellationStatus === 'REFUND_PENDING' && <p>Hai bên đã đồng ý; hợp đồng chưa được xác nhận hủy cuối cùng. {refundLabel(row.refundStatus)}.</p>}
      {refundConfirmed && <p>{row.simulation ? financialCopy.refundSimulation : 'Marketplace đã xác nhận hoàn tiền. Không suy ra chuyển khoản ngân hàng từ bản ghi này.'}</p>}
      {row.cancellationStatus === 'CANCELLED' && row.refundStatus === null && <p>Đã hủy trước funding. Không có hoàn tiền.</p>}
      <p className="submission-summary">{row.reason}</p>
      <FactGrid facts={[{ label: 'Giá trị hợp đồng', value: row.amount + ' ' + row.currency },
        { label: 'Gửi đề nghị', value: localInstant(row.requestedAt) },
        ...(row.decidedAt ? [{ label: 'Quyết định', value: localInstant(row.decidedAt) }] : [])]} />
      {canDecide && !confirmation && <ActionGroup>
        {acceptEligible && row.allowedActions.includes('ACCEPT') && <button className="button" onClick={() => setConfirmation('ACCEPT')}>Đồng ý hủy</button>}
        {row.allowedActions.includes('REJECT') && <button className="button button-secondary" onClick={() => setConfirmation('REJECT')}>Từ chối hủy</button>}
      </ActionGroup>}
      {confirmation && canDecide && row.allowedActions.includes(confirmation) && (confirmation === 'REJECT' || acceptEligible) && <div className="approval-confirm" role="group" aria-label="Xác nhận quyết định hủy">
        <p>{confirmation === 'ACCEPT' ? 'Đồng ý hủy và để máy chủ đối soát hoàn tiền. Đây chưa phải hoàn tiền đã xác nhận.' : 'Từ chối đề nghị hủy. Hợp đồng tiếp tục; không hoàn tiền.'}</p>
        <ActionGroup><button className={'button' + (confirmation === 'ACCEPT' ? ' button-caution' : '')} onClick={() => void mutate({ kind: 'decision', cancellationId: row.cancellationId, decision: confirmation })}>Xác nhận {confirmation === 'ACCEPT' ? 'đồng ý hủy' : 'từ chối hủy'}</button>
          <button className="button button-secondary" onClick={() => setConfirmation(null)}>Quay lại</button></ActionGroup>
      </div>}
      <EvidenceDisclosure summary="Tham chiếu hủy / Hoàn tiền"><dl className="reference-list">
        <div><dt>Đề nghị hủy</dt><dd><code>{row.cancellationId}</code></dd></div>
        <div><dt>Mã lý do</dt><dd>{row.reasonCode}</dd></div>
        {row.refundReference && <div><dt>Hoàn tiền</dt><dd><code>{row.refundReference}</code></dd></div>}
        {row.lastError && <div><dt>Lỗi đối soát</dt><dd>{row.lastError}</dd></div>}
      </dl></EvidenceDisclosure>
    </>}
    {ready && !row && !uncertain && <>
      {eligible && !form && <button className="text-button" disabled={busy || workflowBusy} onClick={() => setForm(true)}>{contract.status === 'PENDING_FUNDING' ? 'Hủy trước funding' : 'Đề nghị hủy hợp đồng'}</button>}
      {form && eligible && <form className="cancellation-form" onSubmit={request}>
        <p>{contract.status === 'PENDING_FUNDING' ? 'Chỉ hủy khi chưa có bất kỳ lần funding nào. Không có hoàn tiền.' : 'Gửi đề nghị cho đối tác. Công việc vẫn tiếp tục khi đề nghị chờ quyết định; nếu hai bên đồng ý, máy chủ đối soát hoàn tiền.'}</p>
        <label>Mã lý do<input required maxLength={60} value={reasonCode} disabled={busy || workflowBusy} onChange={e => setReasonCode(e.target.value)} /></label>
        <label>Lý do hủy<textarea required maxLength={2000} value={description} disabled={busy || workflowBusy} onChange={e => setDescription(e.target.value)} /></label>
        <ActionGroup><button className={'button button-secondary' + (contract.status === 'PENDING_FUNDING' ? ' button-caution' : '')} disabled={busy || workflowBusy}>Xác nhận {contract.status === 'PENDING_FUNDING' ? 'hủy trước funding' : 'gửi đề nghị'}</button>
          <button className="text-button" type="button" disabled={busy || workflowBusy} onClick={() => setForm(false)}>Quay lại</button></ActionGroup>
      </form>}
      {!eligible && <p className="metadata">Chưa đủ điều kiện hủy theo trạng thái, lịch sử funding hoặc bàn giao hiện có.</p>}
    </>}
    {busy && <p role="status">Đang ghi nhận; chưa gửi thao tác khác.</p>}
    {uncertain && <p role="status">Chưa xác nhận kết quả lần thao tác trước. Đọc lại trạng thái trước khi tiếp tục.</p>}
    {uncertain && ready && replay && !busy && <button className="text-button" disabled={workflowBusy}
      onClick={() => void mutate(replay)}>Đối chiếu và tiếp tục ý định trước</button>}
    {notice && <p role="status">{notice}</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    <button className="text-button" disabled={busy || workflowBusy || loading} onClick={() => void reconcile()}>Đối chiếu hủy / Hoàn tiền</button>
  </section>;
}
