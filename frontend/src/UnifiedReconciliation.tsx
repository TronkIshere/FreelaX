import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from './api';
import { EvidenceDisclosure, PageHeading, SectionHeading } from './components';

type Cases = Awaited<ReturnType<typeof api.unifiedReconciliation>>;
type Case = Cases[number];
type Boundary = Case['usdToClientUsdc'];
type Summary = Awaited<ReturnType<typeof api.unifiedLedgerSummary>>;
type Group = 'attention' | 'running' | 'done' | 'all';

const PAGE = 20;
const boundaryOptions = [
  ['USD_TO_CLIENT_USDC', 'Nộp USD → ví Client'], ['CLIENT_USDC_TO_VAULT', 'Ví Client → vault'],
  ['VAULT_TO_RECIPIENT', 'Vault → người nhận'], ['WITHDRAWAL_TO_FIAT', 'Rút → VND / hoàn USD'],
] as const;
const boundaryLabel = Object.fromEntries(boundaryOptions) as Record<string, string>;
const decisionLabels: Record<string, string> = {
  ACKNOWLEDGED: 'Đã ghi nhận', ESCALATED: 'Chuyển xử lý', CLEARED_BY_EVIDENCE: 'Đã khớp theo bằng chứng mới',
};
const statusLabels: Record<string, string> = { MATCHED: 'Khớp', PENDING: 'Đang chờ', MISMATCH: 'Lệch', UNKNOWN: 'Chưa rõ' };
const groupLabels: Record<Group, string> = { attention: 'Cần xử lý', running: 'Đang chạy', done: 'Đã khớp đủ', all: 'Tất cả' };

const statusLabel = (status: string) => statusLabels[status] ?? status;
const tone = (status: string) => status === 'MATCHED' ? 'matched' : status === 'PENDING' ? 'pending' : 'alert';
const short = (id: string) => id.slice(0, 8);
const amount = (value: unknown) => Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 6 });
const when = (value: string) => new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
const boundaries = (item: Case): [string, Boundary][] => [
  ['USD_TO_CLIENT_USDC', item.usdToClientUsdc], ['CLIENT_USDC_TO_VAULT', item.clientUsdcToVault],
  ['VAULT_TO_RECIPIENT', item.vaultToRecipient], ['WITHDRAWAL_TO_FIAT', item.withdrawalToFiat],
];
function groupOf(item: Case): Exclude<Group, 'all'> {
  const states = boundaries(item).map(([, boundary]) => boundary.status);
  if (states.some(status => status === 'MISMATCH' || status === 'UNKNOWN')) return 'attention';
  return states.every(status => status === 'MATCHED') ? 'done' : 'running';
}

function ExpiredFundingCancel({ contractId, onSaved }: { contractId: string; onSaved: () => void }) {
  const [note, setNote] = useState('');
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (busy || !confirm || note.trim().length < 10) { setError('Cần xác nhận và ghi chú ít nhất 10 ký tự.'); return; }
    setBusy(true); setError('');
    try { await api.cancelExpiredUnifiedFunding(contractId, note.trim()); onSaved(); }
    catch { setError('Server từ chối: chỉ hủy được khi đã quá hạn ký quỹ ít nhất 15 phút, USDC đã ở ví Client và chưa có escrow on-chain.'); }
    finally { setBusy(false); }
  }
  return <form className="recon-form" onSubmit={submit} aria-label={'Hủy hợp đồng quá hạn ký quỹ ' + contractId}>
    <h4>Hủy hợp đồng quá hạn ký quỹ</h4>
    <p className="metadata">Dùng khi USD đã nhận, USDC đã ở ví Client nhưng vault chưa được ký quỹ. Client sẽ gửi USDC về treasury và nhận lại USD.</p>
    <label className="recon-field recon-field-wide">Ghi chú<textarea value={note} maxLength={450} onChange={event => setNote(event.target.value)} /></label>
    <label className="recon-check"><input type="checkbox" checked={confirm} onChange={event => setConfirm(event.target.checked)} /> Tôi đã kiểm tra hạn ký quỹ và bằng chứng on-ramp.</label>
    {error && <p role="alert" className="form-error">{error}</p>}
    <button className="button button-secondary" type="submit" disabled={busy}>Hủy hợp đồng quá hạn</button>
  </form>;
}

function ReviewForm({ paymentFlowId, onSaved }: { paymentFlowId: string; onSaved: () => void }) {
  const [boundary, setBoundary] = useState<string>(boundaryOptions[0][0]);
  const [decision, setDecision] = useState('ACKNOWLEDGED');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (busy || note.trim().length < 10) { setError('Ghi chú cần ít nhất 10 ký tự.'); return; }
    setBusy(true); setError('');
    try { await api.reviewUnifiedReconciliation(paymentFlowId, boundary, decision, note.trim()); setNote(''); onSaved(); }
    catch { setError('Chưa lưu được quyết định. Với "Đã khớp", bằng chứng hiện tại phải là Khớp.'); }
    finally { setBusy(false); }
  }
  return <form className="recon-form" onSubmit={submit} aria-label={'Ghi quyết định đối soát ' + paymentFlowId}>
    <h4>Ghi quyết định</h4>
    <div className="recon-form-grid">
      <label className="recon-field">Chặng<select value={boundary} onChange={event => setBoundary(event.target.value)}>
        {boundaryOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="recon-field">Quyết định<select value={decision} onChange={event => setDecision(event.target.value)}>
        {Object.entries(decisionLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="recon-field recon-field-wide">Ghi chú<textarea value={note} maxLength={450} onChange={event => setNote(event.target.value)} /></label>
    </div>
    {error && <p role="alert" className="form-error">{error}</p>}
    <p className="metadata">Chỉ lưu vào audit; không chuyển tiền và không ghi đè bằng chứng của đối tác hay Solana.</p>
    <button className="button button-secondary" type="submit" disabled={busy}>Lưu quyết định</button>
  </form>;
}

function MoneySummary({ summary }: { summary: Summary }) {
  return <section className="recon-section" aria-label="Tổng hợp theo đồng tiền">
    <SectionHeading title="Tiền đang ở đâu" aside={`${summary.flows} flow · Mô phỏng`} />
    <p className="metadata">Mỗi loại tiền tính riêng, không quy đổi cộng chung. Chỉ tính bước đã có bằng chứng xác nhận.</p>
    <div className="recon-money">
      {summary.currencies.map(currency => <article className="recon-money-card" key={currency.currency}>
        <header><h3>{currency.currency}</h3>
          {currency.unknownSteps > 0 && <span className="recon-badge recon-alert">{currency.unknownSteps} bước chưa rõ</span>}</header>
        {currency.buckets.length ? <dl>{currency.buckets.map(bucket => <div key={bucket.code}>
          <dt>{bucket.label}</dt><dd><strong>{amount(bucket.amount)} {currency.currency}</strong><span>{bucket.flows} flow</span></dd>
        </div>)}</dl> : <p className="metadata">Chưa có khoản nào.</p>}
      </article>)}
    </div>
  </section>;
}

function FlowRow({ item, onSaved }: { item: Case; onSaved: () => void }) {
  const [open, setOpen] = useState(false);
  const group = groupOf(item);
  const detailId = 'recon-detail-' + item.paymentFlowId;
  const lastSeen = boundaries(item).map(([, boundary]) => boundary.observedAt).sort().at(-1);
  return <li className={'recon-row recon-row-' + group}>
    <article aria-label={'Đối soát flow ' + item.paymentFlowId}>
      <div className="recon-row-main">
        <div className="recon-row-id">
          <Link className="text-link" to={'/work/' + encodeURIComponent(item.jobId)}>Job {short(item.jobId)}</Link>
          <span className="metadata">Flow {short(item.paymentFlowId)}{lastSeen ? ' · ' + when(lastSeen) : ''}</span>
        </div>
        <strong className="recon-row-amount">{amount(item.grossUsd)} USD</strong>
        <ol className="recon-stages" aria-label="Bốn chặng tiền">
          {boundaries(item).map(([key, boundary]) => <li key={key} className={'recon-stage recon-' + tone(boundary.status)}
            title={boundary.code}><span>{boundaryLabel[key]}</span><strong>{statusLabel(boundary.status)}</strong></li>)}
        </ol>
        <div className="recon-row-actions">
          <span className={'recon-badge recon-' + (group === 'attention' ? 'alert' : group === 'done' ? 'matched' : 'pending')}>{groupLabels[group]}</span>
          <button className="text-button" type="button" aria-expanded={open} aria-controls={detailId}
            onClick={() => setOpen(value => !value)}>{open ? 'Thu gọn' : 'Chi tiết'}</button>
        </div>
      </div>
      {open && <div className="recon-detail" id={detailId}>
        <table className="recon-table">
          <thead><tr><th>Chặng</th><th>Trạng thái</th><th>Mã đối soát</th><th>Nguồn bằng chứng</th><th>Thời điểm</th></tr></thead>
          <tbody>{boundaries(item).map(([key, boundary]) => <tr key={key}>
            <td>{boundaryLabel[key]}</td>
            <td><span className={'recon-badge recon-' + tone(boundary.status)}>{statusLabel(boundary.status)}</span>
              {boundary.blocksNextAction && <span className="metadata"> · chặn bước tiếp</span>}</td>
            <td><code>{boundary.code}</code></td><td><code>{boundary.evidenceSource}</code></td><td>{when(boundary.observedAt)}</td>
          </tr>)}</tbody>
        </table>
        <EvidenceDisclosure summary="Mã tham chiếu">
          <dl className="reference-list">
            <div><dt>Flow</dt><dd><code>{item.paymentFlowId}</code></dd></div>
            <div><dt>Job</dt><dd><code>{item.jobId}</code></dd></div>
            <div><dt>Contract</dt><dd><code>{item.contractId}</code></dd></div>
            <div><dt>Milestone</dt><dd><code>{item.milestoneId}</code></dd></div>
            <div><dt>Số tiền</dt><dd>{amount(item.grossUsd)} USD · {amount(item.escrowUsdc)} USDC</dd></div>
          </dl>
        </EvidenceDisclosure>
        {!!item.reviews?.length && <section className="recon-history" aria-label="Lịch sử quyết định Admin">
          <h4>Lịch sử quyết định</h4>
          <ul>{item.reviews.map(review => <li key={review.reviewedAt + review.boundary}>
            <strong>{decisionLabels[review.decision] ?? review.decision}</strong> · {boundaryLabel[review.boundary] ?? review.boundary}
            <span className="metadata"> · {when(review.reviewedAt)}</span><p>{review.note}</p>
          </li>)}</ul>
        </section>}
        <div className="recon-forms">
          {item.usdToClientUsdc.status === 'MATCHED' && item.clientUsdcToVault.code === 'VAULT_PENDING'
            && <ExpiredFundingCancel contractId={item.contractId} onSaved={onSaved} />}
          <ReviewForm paymentFlowId={item.paymentFlowId} onSaved={onSaved} />
        </div>
      </div>}
    </article>
  </li>;
}

export function UnifiedReconciliation() {
  const [cases, setCases] = useState<Cases>([]);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tick, setTick] = useState(0);
  const [group, setGroup] = useState<Group | null>(null);
  const [query, setQuery] = useState('');
  const [limit, setLimit] = useState(PAGE);
  const [loadedAt, setLoadedAt] = useState<Date | null>(null);
  useEffect(() => {
    let active = true;
    setLoading(true);
    void api.unifiedLedgerSummary().then(value => { if (active) setSummary(value); }).catch(() => { if (active) setSummary(null); });
    void api.unifiedReconciliation().then(value => {
      if (active) { setCases(value); setError(''); setLoadedAt(new Date()); }
    }).catch(() => {
      if (active) setError('Chưa đọc được bốn chặng đối soát. Không xử lý lệnh tiền khi chưa có bằng chứng.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [tick]);

  const counts = useMemo(() => {
    const value = { attention: 0, running: 0, done: 0, all: cases.length };
    for (const item of cases) value[groupOf(item)] += 1;
    return value;
  }, [cases]);
  const selected: Group = group ?? (counts.attention ? 'attention' : 'all');
  const needle = query.trim().toLowerCase();
  const visible = cases.filter(item => (selected === 'all' || groupOf(item) === selected)
    && (!needle || [item.paymentFlowId, item.jobId, item.contractId, item.milestoneId].some(id => id.toLowerCase().includes(needle))));
  const refresh = () => setTick(value => value + 1);
  const choose = (next: Group) => { setGroup(next); setLimit(PAGE); };

  return <div className="finance-detail-page recon-page">
    <PageHeading eyebrow="Quản trị / Thanh toán" title="Đối soát luồng USDC"
      description="Theo dõi tiền của từng công việc qua bốn chặng. Chặng chưa khớp sẽ chặn bước tiền tiếp theo." />
    <div className="recon-toolbar">
      <span className="metadata" role="status">{loading ? 'Đang đối chiếu đối tác và Solana…'
        : loadedAt ? 'Cập nhật lúc ' + loadedAt.toLocaleTimeString('vi-VN') : ''}</span>
      <button className="button button-secondary" disabled={loading} onClick={refresh}>Đọc lại bằng chứng</button>
    </div>
    {error && <p role="alert" className="form-error">{error}</p>}

    <div className="recon-kpis" role="group" aria-label="Lọc theo tình trạng">
      {(['attention', 'running', 'done', 'all'] as Group[]).map(key => <button key={key} type="button"
        className={'recon-kpi recon-kpi-' + key + (selected === key ? ' is-active' : '')} aria-pressed={selected === key}
        onClick={() => choose(key)}><span>{groupLabels[key]}</span><strong>{counts[key]}</strong></button>)}
    </div>

    {summary && <MoneySummary summary={summary} />}

    <section className="recon-section" aria-label="Danh sách flow">
      <SectionHeading title={groupLabels[selected]} aside={`${visible.length} flow`} />
      <label className="recon-search">Tìm theo mã Job, flow, contract
        <input type="search" value={query} placeholder="Ví dụ: df84c4dd" onChange={event => { setQuery(event.target.value); setLimit(PAGE); }} /></label>
      {!loading && !error && visible.length === 0 && <p className="metadata">Không có flow nào trong nhóm này.</p>}
      <ul className="recon-list">
        {visible.slice(0, limit).map(item => <FlowRow key={item.paymentFlowId} item={item} onSaved={refresh} />)}
      </ul>
      {visible.length > limit && <button className="button button-secondary" type="button"
        onClick={() => setLimit(value => value + PAGE)}>Xem thêm {Math.min(PAGE, visible.length - limit)} flow</button>}
    </section>
  </div>;
}
