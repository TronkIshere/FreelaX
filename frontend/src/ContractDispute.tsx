import { useEffect, useRef, useState, type MutableRefObject } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from './api';
import { ActionGroup, EvidenceDisclosure, FactGrid, SectionHeading } from './components';
import { finalDispute, validateDispute, validateDisputeEvidence } from './disputeContracts';
import { disputeLabel, refundLabel } from './status';
import { clearAttempt, httpsUrl, localInstant, readAttempt, saveAttempt } from './workflowContracts';
import type { Dispute, DisputeEvidenceInput, OpenDisputeInput, User } from './types';

export function DisputeError({ error }: { error: unknown }) {
  if (!error) return null;
  const cause = error instanceof ApiError ? error : null;
  return <div className="form-error" role="alert"><p>{typeof error === 'string' ? error : cause?.status === 403 ? 'Không có quyền thực hiện thao tác này.' : cause?.status === 404 ? 'Không tìm thấy hồ sơ được phép truy cập.' : 'Chưa xác nhận được thao tác. Đối chiếu trạng thái máy chủ trước khi thử lại.'}</p>
    {cause && (cause.requestId || cause.businessCode) && <EvidenceDisclosure summary="Thông tin hỗ trợ">
      {cause.requestId && <p>Mã yêu cầu HTTP: <code>{cause.requestId}</code></p>}
      {cause.businessCode && <p>Mã lỗi: <code>{cause.businessCode}</code></p>}
    </EvidenceDisclosure>}
  </div>;
}
export function DisputeLink({ url }: { url: string | null | undefined }) {
  const safe = url ? httpsUrl(url) : null;
  return safe?.hostname ? <div className="deliverable-link"><span className="deliverable-address">{safe.hostname} · {safe.href}</span><a href={safe.href} target="_blank" rel="noopener noreferrer">Mở bằng chứng ↗</a></div> : url ? <p>Liên kết không có định dạng HTTPS an toàn.</p> : null;
}
export function DisputeRecord({ dispute }: { dispute: Dispute }) {
  return <>
    <SectionHeading title={disputeLabel(dispute.status)} />
    <p className="metadata">Lý do: {dispute.reasonCode}</p><p className="submission-summary">{dispute.description}</p>
    <FactGrid facts={[
      { label: 'Mở hồ sơ', value: localInstant(dispute.openedAt) },
      ...(dispute.negotiationUntil ? [{ label: 'Hạn tự thương lượng', value: localInstant(dispute.negotiationUntil) }] : []),
      ...(dispute.moderationDueAt ? [{ label: 'Hạn điều phối quyết định', value: localInstant(dispute.moderationDueAt) }] : []),
      { label: 'Admin tiếp nhận', value: dispute.claimedAt ? localInstant(dispute.claimedAt) : 'Chưa tiếp nhận' },
      ...(dispute.decisionAt ? [{ label: 'Quyết định', value: localInstant(dispute.decisionAt) }] : []),
      ...(dispute.resolvedAt ? [{ label: 'Hoàn tất hồ sơ', value: localInstant(dispute.resolvedAt) }] : []),
    ]} />
    {dispute.resolutionReason && <section className="feedback-document"><SectionHeading title="Lý do quyết định" level={3} /><p>{dispute.resolutionReason}</p></section>}
    {dispute.status === 'OPEN' && <p>{dispute.negotiationUntil ? 'Tiền đang đóng băng; hai bên có thể tự thương lượng trước hạn. Nếu không thống nhất, Admin tiếp nhận sau hạn.' : 'Chờ Admin tiếp nhận.'} Hai bên có thể bổ sung bằng chứng.</p>}
    {dispute.negotiationOutcome && dispute.status === 'OPEN' && <p>Đề xuất: {dispute.negotiationOutcome === 'REFUND_TO_CLIENT' ? 'Hoàn đủ USD cho Client' : 'Giải ngân cho Freelancer (phí 3%)'} · {dispute.negotiationReason}</p>}
    {dispute.status === 'UNDER_REVIEW' && <p>Admin đã tiếp nhận; bằng chứng đã khóa.</p>}
    {dispute.status === 'DECISION_PENDING_RELEASE' && <p>Quyết định release đã lưu. Chưa xác nhận release thành công; các chặng tài chính được đối soát riêng.</p>}
    {dispute.status === 'RESOLVED_RELEASE' && <p>Hồ sơ đã giải quyết. Xem bản ghi settlement để đối chiếu release; không suy ra chi trả ngân hàng từ quyết định Admin.</p>}
    {['DECISION_PENDING_REFUND', 'RESOLVED_REFUND'].includes(dispute.status) && <>
      <p>{dispute.status === 'DECISION_PENDING_REFUND' ? 'Quyết định hoàn tiền đã lưu. Chưa xác nhận hoàn tiền hoàn tất.' : 'Hồ sơ hoàn tiền đã giải quyết. Đối chiếu trạng thái hợp đồng và bản ghi hoàn tiền từ máy chủ.'}</p>
      <p>{refundLabel(dispute.refundStatus)}</p>
    </>}
    <section className="dispute-evidence"><SectionHeading title="Bằng chứng hồ sơ" level={3} />
      {!dispute.evidence?.length && <p>Chưa có bằng chứng bổ sung.</p>}
      {(dispute.evidence || []).map((item, index) => <article className="ledger-body" key={item.id}>
        <h4>Bằng chứng {index + 1} · {item.kind === 'TEXT' ? 'Nội dung' : 'Liên kết'}</h4>
        {item.text && <p>{item.text}</p>}<DisputeLink url={item.url} /><p className="metadata">{localInstant(item.createdAt)}</p>
        <EvidenceDisclosure summary="Nguồn / Tham chiếu"><p>Người gửi: <code>{item.actorId}</code></p><p>ID: <code>{item.id}</code></p>{item.sha256 && <p>SHA-256: <code>{item.sha256}</code></p>}</EvidenceDisclosure>
      </article>)}
    </section>
    <EvidenceDisclosure summary="Tham chiếu hồ sơ"><p>Dispute: <code>{dispute.disputeId}</code></p><p>Contract: <code>{dispute.contractId}</code></p>
      {dispute.claimedBy && <p>Admin tiếp nhận: <code>{dispute.claimedBy}</code></p>}
      {dispute.resolvedBy && <p>Admin quyết định: <code>{dispute.resolvedBy}</code></p>}
      {dispute.refundReference && <p>Tham chiếu hoàn tiền: <code>{dispute.refundReference}</code></p>}
    </EvidenceDisclosure>
  </>;
}
type AppendAttempt = { key: string; payload: DisputeEvidenceInput[]; baselineIds: string[] };
export function ContractDispute({ contractId, user, dispute, ready, eligible, blocked, operationLock, onRefresh, onBusy, onOpenOnChain }: {
  contractId: string; user: User; dispute: Dispute | null; ready: boolean; eligible: boolean; blocked: boolean;
  operationLock: MutableRefObject<boolean>; onRefresh: () => Promise<void>; onBusy: (value: boolean) => void;
  onOpenOnChain?: (input: OpenDisputeInput) => Promise<void>;
}) {
  const [opening, setOpening] = useState(false);
  const [reasonCode, setReason] = useState('QUALITY'); const [description, setDescription] = useState('');
  const [kind, setKind] = useState<'TEXT' | 'LINK'>('TEXT'); const [text, setText] = useState('');
  const [url, setUrl] = useState(''); const [sha256, setHash] = useState('');
  const [items, setItems] = useState<DisputeEvidenceInput[]>([]);
  const [attempt, setAttempt] = useState<AppendAttempt | null>(null);
  const [openIntent, setOpenIntent] = useState<OpenDisputeInput | null>(null);
  const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>(null); const [notice, setNotice] = useState('');
  const [storageReady, setStorageReady] = useState(false);
  const [negotiationOutcome, setNegotiationOutcome] = useState<'RELEASE_TO_FREELANCER' | 'REFUND_TO_CLIENT'>('REFUND_TO_CLIENT');
  const [negotiationReason, setNegotiationReason] = useState('');
  const alive = useRef(true); const scope = 'freelax:dispute-evidence:' + user.id + ':' + contractId;
  useEffect(() => {
    alive.current = true;
    try {
      const saved = readAttempt<AppendAttempt>(scope);
      if (saved && (typeof saved.key !== 'string' || !saved.key || !Array.isArray(saved.payload) || !Array.isArray(saved.baselineIds) || validateDisputeEvidence(saved.payload))) throw new Error();
      setAttempt(saved); setStorageReady(true);
    } catch { setError('Không đọc được lần gửi trước. Chưa gửi bằng chứng mới.'); }
    return () => { alive.current = false; };
  }, [scope]);
  const frozen = !!attempt || !!openIntent;
  const enabled = ready && storageReady && !blocked && !busy;
  function draft(): DisputeEvidenceInput {
    return { kind, ...(text ? { text } : {}), ...(kind === 'LINK' ? { url } : {}), ...(sha256 ? { sha256 } : {}) };
  }
  function addEvidence() {
    if (!enabled || frozen) return;
    const value = draft(); const invalid = validateDisputeEvidence([...items, value]);
    if (invalid) { setError(invalid); return; } setItems([...items, value]); setText(''); setUrl(''); setHash(''); setError(null);
  }
  async function mutate(event: React.FormEvent) {
    event.preventDefault(); if (!enabled || operationLock.current) return;
    const append = !!dispute;
    const payload = attempt?.payload || items;
    const input = openIntent || { reasonCode, description, evidence: items };
    const invalid = append ? !payload.length ? 'Cần ít nhất một bằng chứng.' : validateDisputeEvidence(payload) : validateDispute(input);
    if (invalid) { setError(invalid); return; }
    if (append && dispute.status !== 'OPEN' || !append && !eligible) return;
    operationLock.current = true; setBusy(true); onBusy(true); setError(null); setNotice('');
    try {
      // Every replay first reads authority. No automatic mutation retry on a transport failure.
      if (attempt || openIntent) {
        const fresh = await api.dispute(contractId); await onRefresh();
        if (append && (!fresh || fresh.status !== 'OPEN') || !append && fresh) {
          if (append) { clearAttempt(scope); setAttempt(null); }
          setOpenIntent(null); setNotice('Đã đối chiếu hồ sơ. Kiểm tra bằng chứng trước khi tạo ý định mới.'); return;
        }
      }
      if (append) {
        const saved = attempt || { key: crypto.randomUUID(), payload, baselineIds: dispute.evidence.map(e => e.id) };
        saveAttempt(scope, saved); setAttempt(saved);
        await api.appendDisputeEvidence(contractId, dispute.disputeId, saved.key, saved.payload);
        clearAttempt(scope); setAttempt(null);
      } else {
        setOpenIntent(input);
        if (onOpenOnChain) await onOpenOnChain(input);
        await api.openDispute(contractId, input); setOpenIntent(null); setOpening(false);
      }
      if (!alive.current) return;
      setItems([]); setDescription(''); setNotice(append ? 'Đã ghi nhận bằng chứng.' : 'Đã mở hồ sơ tranh chấp.'); await onRefresh();
    } catch (cause) {
      if (alive.current) setError(cause);
      try { await onRefresh(); } catch { /* Keep exact intent; do not repost. */ }
      if (cause instanceof ApiError && cause.status === 400) {
        if (append) { clearAttempt(scope); setAttempt(null); }
        setOpenIntent(null);
      }
    } finally { operationLock.current = false; if (alive.current) { setBusy(false); onBusy(false); } }
  }
  async function negotiate() {
    if (!dispute || !enabled || operationLock.current || dispute.status !== 'OPEN'
      || !dispute.negotiationUntil || Date.now() >= new Date(dispute.negotiationUntil).getTime()) return;
    const outcome = dispute.negotiationOutcome || negotiationOutcome;
    const reason = dispute.negotiationReason || negotiationReason.trim();
    if (!reason || reason.length > 2000 || dispute.negotiationProposedBy === user.id) return;
    operationLock.current = true; setBusy(true); onBusy(true); setError(null);
    try {
      const fresh = await api.dispute(contractId);
      if (!fresh || fresh.status !== 'OPEN' || fresh.negotiationProposedBy !== dispute.negotiationProposedBy) return;
      await api.negotiateDispute(contractId, dispute.disputeId, outcome, reason);
      if (alive.current) { setNotice(dispute.negotiationProposedBy ? 'Hai bên đã đồng ý; đang đối soát tiền.' : 'Đã gửi đề xuất dàn xếp.'); await onRefresh(); }
    } catch (cause) { if (alive.current) setError(cause); await onRefresh().catch(() => {}); }
    finally { operationLock.current = false; if (alive.current) { setBusy(false); onBusy(false); } }
  }
  const formVisible = (!dispute && opening && eligible) || dispute?.status === 'OPEN';
  return <section className="dispute-document" id="contract-dispute" aria-label="Hồ sơ tranh chấp">
    {dispute ? <DisputeRecord dispute={dispute} /> : <SectionHeading title="Tranh chấp hợp đồng" description={ready ? 'Chưa có hồ sơ tranh chấp.' : 'Đang đối chiếu hồ sơ từ Marketplace.'} />}
    {dispute?.status === 'OPEN' && dispute.negotiationUntil && Date.now() < new Date(dispute.negotiationUntil).getTime() && <section aria-label="Tự thương lượng">
      <SectionHeading title="Tự thương lượng" level={3} />
      {!dispute.negotiationProposedBy && <>
        <label>Kết quả đề xuất<select value={negotiationOutcome} onChange={e => setNegotiationOutcome(e.target.value as typeof negotiationOutcome)}><option value="REFUND_TO_CLIENT">Hoàn đủ USD cho Client</option><option value="RELEASE_TO_FREELANCER">Giải ngân cho Freelancer, trừ phí 3%</option></select></label>
        <label>Lý do thỏa thuận<textarea value={negotiationReason} maxLength={2000} onChange={e => setNegotiationReason(e.target.value)} /></label>
      </>}
      {dispute.negotiationProposedBy === user.id ? <p>Đang chờ bên còn lại đồng ý đúng nội dung đề xuất.</p>
        : <button className="button" type="button" disabled={!enabled || (!dispute.negotiationProposedBy && !negotiationReason.trim())} onClick={() => void negotiate()}>{dispute.negotiationProposedBy ? 'Đồng ý đề xuất' : 'Gửi đề xuất'}</button>}
    </section>}
    {!dispute && eligible && !opening && <button className="button button-secondary" disabled={!enabled} onClick={() => setOpening(true)}>Mở hồ sơ tranh chấp</button>}
    {formVisible && <form className="dispute-form" onSubmit={mutate}>
      {!dispute && <><p>Xác nhận mở tranh chấp sẽ khóa bàn giao và review để Admin xem xét.</p>
        <label>Mã lý do<input required maxLength={60} disabled={!enabled || frozen} value={reasonCode} onChange={e => setReason(e.target.value)} /></label>
        <label>Mô tả tranh chấp<textarea required maxLength={2000} disabled={!enabled || frozen} value={description} onChange={e => setDescription(e.target.value)} /></label></>}
      <fieldset disabled={!enabled || frozen}><legend>{dispute ? 'Bổ sung bằng chứng' : 'Bằng chứng ban đầu (tùy chọn)'}</legend>
        <label>Loại bằng chứng<select value={kind} onChange={e => setKind(e.target.value as typeof kind)}><option value="TEXT">Nội dung</option><option value="LINK">Liên kết HTTPS</option></select></label>
        <label>Nội dung bằng chứng<textarea maxLength={2000} value={text} onChange={e => setText(e.target.value)} /></label>
        {kind === 'LINK' && <label>URL HTTPS<input type="url" maxLength={2048} value={url} onChange={e => setUrl(e.target.value)} /></label>}
        <label>SHA-256 (tùy chọn)<input maxLength={64} value={sha256} onChange={e => setHash(e.target.value)} /></label>
        <button className="text-button" type="button" onClick={addEvidence}>Thêm vào lần gửi</button>
      </fieldset>
      <p className="metadata">{(attempt?.payload || items).length}/10 bằng chứng trong lần gửi. Bằng chứng đã gửi không thể sửa hoặc xóa.</p>
      {(attempt?.payload || items).map((item, i) => <p className="evidence-draft" key={i}>{i + 1}. {item.text || item.url}</p>)}
      {frozen && <p>Giữ nguyên ý định trước để đối chiếu. Chưa tạo lần gửi mới khi kết quả chưa rõ.</p>}
      <ActionGroup><button className="button" disabled={!enabled}>{busy ? 'Đang ghi nhận…' : frozen ? 'Đối chiếu và tiếp tục' : dispute ? 'Gửi bằng chứng' : 'Xác nhận mở hồ sơ'}</button>
        {!dispute && !frozen && <button className="button button-secondary" type="button" disabled={busy} onClick={() => setOpening(false)}>Quay lại</button>}
      </ActionGroup>
    </form>}
    {dispute && (dispute.status.includes('RELEASE')) && <Link className="text-link" to={'/finance?jobId=' + encodeURIComponent(dispute.jobId)}>Xem bằng chứng tài chính →</Link>}
    {finalDispute(dispute) && <p className="metadata">Hồ sơ đã kết thúc; trạng thái bàn giao cũ được giữ trong lịch sử.</p>}
    {notice && <p role="status">{notice}</p>}<DisputeError error={error} />
    <button className="text-button" disabled={busy || blocked} onClick={() => void onRefresh().catch(setError)}>Đối chiếu tranh chấp</button>
  </section>;
}
