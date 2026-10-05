import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';
import { ActionGroup, EvidenceDisclosure, SectionHeading } from './components';
import { fundingLabel } from './status';
import { attemptScope, clearAttempt, contractAmount, fundingUnresolved, readAttempt, retryDelay, saveAttempt } from './workflowContracts';
import type { BankCode, ClientBankAccount, FundingResponse, Job, User } from './types';

const banks: BankCode[] = ['VIETCOMBANK', 'VIETINBANK', 'BIDV', 'AGRIBANK', 'TECHCOMBANK', 'MBBANK', 'ACB', 'VPBANK', 'SACOMBANK', 'TPBANK'];
type FundingAttempt = { key: string; amount: string; currency: string };

export function FundingPanel({ job, user, onJobUpdated }: { job: Job; user: User; onJobUpdated: (job: Job) => void }) {
  const contract = job.contract;
  const owner = user.userType === 'CLIENT' && job.clientUserId === user.id;
  const participant = owner || (user.userType === 'FREELANCER' && job.freelancerId === user.id);
  const [funding, setFunding] = useState<FundingResponse | null>(null);
  const [bank, setBank] = useState<ClientBankAccount | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [editingBank, setEditingBank] = useState(false);
  const [bankCode, setBankCode] = useState<BankCode>('VIETCOMBANK');
  const [number, setNumber] = useState('');
  const [holder, setHolder] = useState('');
  const [tick, setTick] = useState(0);
  const [pollTick, setPollTick] = useState(0);
  const lock = useRef(false);
  const alive = useRef(true);
  const attempt = useRef<FundingAttempt | null>(null);
  const initialized = useRef(false);
  const scope = contract?.milestoneId ? attemptScope('fund', user.id, contract.id, contract.milestoneId) : '';
  let amount = '';
  try { if (contract) amount = contractAmount(contract.amount); } catch { /* Invalid server amount disables mutation. */ }

  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    if (!participant || !contract?.milestoneId) { setLoading(false); return; }
    let active = true;
    initialized.current = false;
    async function load() {
      try {
        const previous = readAttempt<FundingAttempt>(scope);
        if (previous && (!previous.key || !previous.amount || !previous.currency)) throw new Error();
        attempt.current = previous;
        const [current, bankState] = await Promise.all([api.funding(contract!.id, contract!.milestoneId!), owner ? api.clientBank() : Promise.resolve(null)]);
        if (!active) return;
        initialized.current = true; setFunding(current); setBank(bankState); setError('');
        if (current?.fundingStatus === 'FAILED' || current?.fundingStatus === 'SUCCEEDED') { clearAttempt(scope); attempt.current = null; }
        if (current?.fundingStatus === 'SUCCEEDED' && job.status === 'AWAITING_PAYMENT') {
          const fresh = await api.job(job.id); if (active) onJobUpdated(fresh);
        }
      } catch { if (active) setError('Chưa đối chiếu được funding hoặc tài khoản ngân hàng. Hãy tải lại trước khi gửi.'); }
      finally { if (active) setLoading(false); }
    }
    void load();
    return () => { active = false; };
  // Reconciliation is scoped to the immutable participant/contract/milestone.
  }, [scope, participant, owner, tick]);

  useEffect(() => {
    if (!fundingUnresolved(funding?.fundingStatus) || !contract?.milestoneId) return;
    let active = true;
    const timer = window.setTimeout(async () => {
      try {
        const next = await api.funding(contract.id, contract.milestoneId!, funding!.fundingTransactionId);
        if (!active) return;
        setFunding(next); setError('');
        if (next?.fundingStatus === 'SUCCEEDED' || next?.fundingStatus === 'FAILED') { clearAttempt(scope); attempt.current = null; }
        if (next?.fundingStatus === 'SUCCEEDED') { const fresh = await api.job(job.id); if (active) onJobUpdated(fresh); }
      } catch { if (active) setError('Chưa cập nhật được funding; lần gửi vẫn đang được đối soát.'); }
      if (active) setPollTick(value => value + 1);
    }, retryDelay(funding?.retryAfterSeconds));
    return () => { active = false; window.clearTimeout(timer); };
  }, [funding, scope, pollTick, contract?.id, contract?.milestoneId, job.id]);

  async function saveBank(event: React.FormEvent) {
    event.preventDefault();
    if (lock.current || !owner) return;
    if (!/^[0-9]{6,34}$/.test(number) || holder.trim().length < 2 || holder.trim().length > 255) { setError('Kiểm tra số tài khoản (6–34 chữ số) và tên chủ tài khoản (2–255 ký tự).'); return; }
    lock.current = true; setBusy(true); setError('');
    try {
      const saved = await api.saveClientBank({ bankCode, bankAccountNumber: number, bankAccountHolderName: holder.trim() });
      if (alive.current) { setBank(saved); setNumber(''); setHolder(''); setEditingBank(false); }
    } catch { if (alive.current) setError('Không lưu được ngân hàng. Kiểm tra thông tin hoặc thử lại.'); }
    finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  async function fund() {
    if (lock.current || !owner || !contract?.milestoneId || !amount || !bank?.ready || !initialized.current || fundingUnresolved(funding?.fundingStatus) || !confirm || contract.status !== 'PENDING_FUNDING' || job.status !== 'AWAITING_PAYMENT') return;
    lock.current = true; setBusy(true); setError('');
    try {
      const saved = attempt.current ?? { key: crypto.randomUUID(), amount, currency: contract.currency };
      if (saved.amount !== amount || saved.currency !== contract.currency) throw new Error('amount conflict');
      saveAttempt(scope, saved); attempt.current = saved;
      const result = await api.fund(contract.id, contract.milestoneId, saved.key, saved.amount, saved.currency);
      if (!alive.current) return;
      setFunding(result); setConfirm(false);
      if (result.fundingStatus === 'SUCCEEDED' || result.fundingStatus === 'FAILED') { clearAttempt(scope); attempt.current = null; }
      if (result.fundingStatus === 'SUCCEEDED') { const fresh = await api.job(job.id); if (alive.current) onJobUpdated(fresh); }
    } catch (cause) {
      if (!alive.current) return;
      setConfirm(false);
      setError(cause instanceof ApiError && cause.code === 4021 ? 'Cần lưu tài khoản ngân hàng Client hợp lệ.'
        : cause instanceof ApiError && cause.status === 409 ? 'Trạng thái hoặc số tiền đã thay đổi. Đang đối chiếu lại; chưa gửi yêu cầu mới.'
        : 'Chưa xác nhận được kết quả. Giữ nguyên lần gửi và tải lại để đối soát.');
      if (cause instanceof ApiError && cause.code === 4021) { setBank(null); setEditingBank(true); }
      try {
        const latest = await api.funding(contract.id, contract.milestoneId);
        const fresh = await api.job(job.id);
        if (alive.current) {
          setFunding(latest); onJobUpdated(fresh);
          if (latest?.fundingStatus === 'SUCCEEDED' || latest?.fundingStatus === 'FAILED') { clearAttempt(scope); attempt.current = null; }
        }
      } catch { /* Keep the same attempt until authoritative reconciliation. */ }
    } finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  if (!participant || !contract) return null;
  const eligible = owner && job.status === 'AWAITING_PAYMENT' && contract.status === 'PENDING_FUNDING' && !!contract.milestoneId && !!amount;
  return <section className="funding-document" id="funding" tabIndex={-1} aria-label="Funding hợp đồng">
    <SectionHeading title={owner ? 'Funding hợp đồng' : 'Funding từ Client'} aside={funding?.simulation !== false ? 'Mô phỏng' : undefined} />
    <strong>{amount || 'Chưa xác minh số tiền'} {contract.currency}</strong>
    <p role="status">{loading ? 'Đang đối chiếu funding…' : funding ? fundingLabel(funding.fundingStatus) : 'Chưa có lần funding được ghi nhận'}</p>
    {funding?.fundingStatus === 'SUCCEEDED' && <p>Đã xác nhận funding. Đây chưa phải tiền đã chi trả cho Freelancer.</p>}
    {!owner && <p>Đang chờ Client và trạng thái xác nhận từ Marketplace.</p>}
    {owner && <>
      {bank?.ready && <p>Ngân hàng: {bank.bankCode} · {bank.maskedAccountNumber}</p>}
      {!bank?.ready && !loading && <p>Cần tài khoản ngân hàng Client trước khi funding.</p>}
      {eligible && !editingBank && <button className="text-button" disabled={busy || loading || fundingUnresolved(funding?.fundingStatus)} onClick={() => setEditingBank(true)}>{bank?.ready ? 'Cập nhật ngân hàng' : 'Thiết lập ngân hàng'}</button>}
      {eligible && editingBank && <form className="bank-form" onSubmit={saveBank}>
        <label>Ngân hàng<select value={bankCode} disabled={busy} onChange={e => setBankCode(e.target.value as BankCode)}>{banks.map(code => <option key={code}>{code}</option>)}</select></label>
        <label>Số tài khoản · 6–34 chữ số<input required pattern="[0-9]{6,34}" maxLength={34} inputMode="numeric" autoComplete="off" value={number} disabled={busy} onChange={e => setNumber(e.target.value)} /></label>
        <label>Tên chủ tài khoản<input required minLength={2} maxLength={255} autoComplete="off" value={holder} disabled={busy} onChange={e => setHolder(e.target.value)} /></label>
        <ActionGroup><button className="button" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu ngân hàng'}</button><button className="button button-secondary" type="button" disabled={busy} onClick={() => { setNumber(''); setHolder(''); setEditingBank(false); }}>Quay lại</button></ActionGroup>
      </form>}
      {eligible && !editingBank && !confirm && <ActionGroup><button className="button" disabled={busy || loading || !initialized.current || !bank?.ready || fundingUnresolved(funding?.fundingStatus) || funding?.fundingStatus === 'SUCCEEDED'} onClick={() => setConfirm(true)}>{funding?.fundingStatus === 'FAILED' ? 'Thử funding lại' : attempt.current ? 'Tiếp tục lần funding trước' : 'Funding mô phỏng'}</button></ActionGroup>}
      {eligible && confirm && <div className="approval-confirm" role="group" aria-label="Xác nhận funding">
        <strong>Xác nhận {amount} {contract.currency} · Mô phỏng</strong><p>Capture mô phỏng theo số tiền hợp đồng đã chốt. Không xác nhận chi trả cho Freelancer.</p>
        <ActionGroup><button className="button" disabled={busy} onClick={() => void fund()}>Xác nhận funding</button><button className="button button-secondary" disabled={busy} onClick={() => setConfirm(false)}>Quay lại</button></ActionGroup>
      </div>}
    </>}
    {error && <p role="alert" className="form-error">{error}</p>}
    <button className="text-button" disabled={busy || loading} onClick={() => { setLoading(true); setTick(value => value + 1); }}>Đối chiếu funding</button>
    {funding && <EvidenceDisclosure summary="Tham chiếu funding"><dl className="reference-list"><div><dt>Lần funding</dt><dd><code>{funding.fundingTransactionId}</code></dd></div><div><dt>Provider</dt><dd><code>{funding.providerReference || '—'}</code></dd></div></dl></EvidenceDisclosure>}
  </section>;
}
