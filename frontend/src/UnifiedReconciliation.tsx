import { useEffect, useState } from 'react';
import { api } from './api';
import { PageHeading, SectionHeading } from './components';

type Cases = Awaited<ReturnType<typeof api.unifiedReconciliation>>;
const boundaryOptions = [
  ['USD_TO_CLIENT_USDC', 'USD → USDC Client'], ['CLIENT_USDC_TO_VAULT', 'Client ATA → vault'],
  ['VAULT_TO_RECIPIENT', 'Vault → ví người nhận'], ['WITHDRAWAL_TO_FIAT', 'Withdrawal → VND/phí hoặc USD hoàn'],
] as const;
const decisionLabels: Record<string, string> = {
  ACKNOWLEDGED: 'Đã ghi nhận', ESCALATED: 'Chuyển xử lý', CLEARED_BY_EVIDENCE: 'Đã khớp theo bằng chứng mới',
};

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
  return <form className="reconciliation-review" onSubmit={submit} aria-label={'Hủy hợp đồng quá hạn ký quỹ ' + contractId}>
    <p>USD đã nhận và USDC đã ở ví Client nhưng vault chưa được ký quỹ. Hủy hợp đồng để Client gửi USDC về treasury và nhận lại USD.</p>
    <label>Ghi chú<textarea value={note} maxLength={450} onChange={event => setNote(event.target.value)} /></label>
    <label><input type="checkbox" checked={confirm} onChange={event => setConfirm(event.target.checked)} /> Tôi đã kiểm tra hạn ký quỹ và bằng chứng on-ramp.</label>
    {error && <p role="alert" className="form-error">{error}</p>}
    <button className="text-button" type="submit" disabled={busy}>Hủy hợp đồng quá hạn</button>
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
    catch { setError('Chưa lưu được quyết định. Với "Đã khớp", bằng chứng hiện tại phải là MATCHED.'); }
    finally { setBusy(false); }
  }
  return <form className="reconciliation-review" onSubmit={submit} aria-label={'Ghi quyết định đối soát ' + paymentFlowId}>
    <label>Ranh giới<select value={boundary} onChange={event => setBoundary(event.target.value)}>
      {boundaryOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
    <label>Quyết định<select value={decision} onChange={event => setDecision(event.target.value)}>
      {Object.entries(decisionLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
    <label>Ghi chú<textarea value={note} maxLength={450} onChange={event => setNote(event.target.value)} /></label>
    {error && <p role="alert" className="form-error">{error}</p>}
    <p className="metadata">Quyết định chỉ được lưu vào audit; không thay đổi tiền hay ghi đè bằng chứng provider/chain.</p>
    <button className="text-button" type="submit" disabled={busy}>Lưu quyết định</button>
  </form>;
}

export function UnifiedReconciliation() {
  const [cases, setCases] = useState<Cases>([]);
  const [summary, setSummary] = useState<Awaited<ReturnType<typeof api.unifiedLedgerSummary>> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    void api.unifiedLedgerSummary().then(value => { if (active) setSummary(value); }).catch(() => { if (active) setSummary(null); });
    void api.unifiedReconciliation().then(value => {
      if (active) { setCases(value); setError(''); }
    }).catch(() => {
      if (active) setError('Chưa đọc được bốn ranh giới đối soát. Không xử lý lệnh tiền khi chưa có bằng chứng.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [tick]);
  return <main className="finance-detail-page">
    <PageHeading eyebrow="Quản trị / Thanh toán" title="Đối soát luồng USDC"
      description="Bốn ranh giới tiền của các flow mô phỏng gần đây. Mục có nhãn chặn cần xử lý trước bước tiếp theo; UNKNOWN khác cần kiểm tra thêm bằng chứng." />
    <button className="text-button" disabled={loading} onClick={() => setTick(value => value + 1)}>Đọc lại nguồn chứng cứ</button>
    {loading && <p role="status">Đang đối chiếu provider và Solana…</p>}
    {error && <p role="alert" className="form-error">{error}</p>}
    {summary && <section className="settlement-document" aria-label="Tổng hợp theo đồng tiền">
      <SectionHeading title="Tiền đang ở đâu" aside={`${summary.flows} flow · Mô phỏng`} />
      <p className="metadata">Mỗi đồng tiền tính riêng, không quy đổi. Chỉ tính bước đã có bằng chứng xác nhận; UNKNOWN được đếm riêng.</p>
      {summary.currencies.map(currency => <div key={currency.currency}>
        <h3>{currency.currency}{currency.unknownSteps ? ` · ${currency.unknownSteps} bước UNKNOWN` : ''}</h3>
        {currency.buckets.length ? <dl className="reference-list">{currency.buckets.map(bucket => <div key={bucket.code}>
          <dt>{bucket.label}</dt><dd><strong>{String(bucket.amount)} {currency.currency}</strong> · {bucket.flows} flow</dd>
        </div>)}</dl> : <p>Chưa có khoản nào.</p>}
      </div>)}
    </section>}
    {!loading && !error && cases.length === 0 && <p>Chưa có flow thống nhất để đối soát.</p>}
    {cases.map(item => <section className="settlement-document" key={item.paymentFlowId} aria-label={'Đối soát flow ' + item.paymentFlowId}>
      <SectionHeading title={'Flow ' + item.paymentFlowId} aside="Mô phỏng" />
      <p>Job <code>{item.jobId}</code> · Contract <code>{item.contractId}</code> · Milestone <code>{item.milestoneId}</code></p>
      <p>{item.grossUsd} USD · {item.escrowUsdc} USDC.</p>
      <dl className="reference-list">{([
        ['USD → USDC Client', item.usdToClientUsdc],
        ['Client ATA → vault', item.clientUsdcToVault],
        ['Vault → ví người nhận', item.vaultToRecipient],
        ['Withdrawal → VND/phí hoặc USD hoàn', item.withdrawalToFiat],
      ] as const).map(([label, boundary]) => <div key={label}>
        <dt>{label}</dt><dd><strong>{boundary.status}</strong> · {boundary.code} · {boundary.evidenceSource}
          {boundary.blocksNextAction ? ' · Chặn bước kế tiếp' : ''} · {new Date(boundary.observedAt).toLocaleString('vi-VN')}</dd>
      </div>)}</dl>
      {!!item.reviews?.length && <ul className="reference-list" aria-label="Lịch sử quyết định Admin">{item.reviews.map(review =>
        <li key={review.reviewedAt + review.boundary}>{decisionLabels[review.decision] ?? review.decision} · {review.boundary} · {review.note}
          {' · '}{new Date(review.reviewedAt).toLocaleString('vi-VN')}</li>)}</ul>}
      {item.usdToClientUsdc.status === 'MATCHED' && item.clientUsdcToVault.code === 'VAULT_PENDING'
        && <ExpiredFundingCancel contractId={item.contractId} onSaved={() => setTick(value => value + 1)} />}
      <ReviewForm paymentFlowId={item.paymentFlowId} onSaved={() => setTick(value => value + 1)} />
    </section>)}
  </main>;
}
