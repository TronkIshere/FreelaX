import { useEffect, useRef, useState, type MutableRefObject } from 'react';
import { api, ApiError } from './api';
import { ActionGroup, EvidenceDisclosure, SectionHeading } from './components';
import { fundingLabel } from './status';
import { attemptScope, clearAttempt, contractAmount, fundingUnresolved, readAttempt, retryDelay, saveAttempt } from './workflowContracts';
import type { BankCode, ClientBankAccount, FundingResponse, Job, PaymentFlowTimeline, User } from './types';
import { EscrowFundingPanel } from './EscrowFunding';
import { WalletLinkPanel } from './WalletLink';
import { PartnerFundingPanel } from './PartnerFunding';

const banks: BankCode[] = ['VIETCOMBANK', 'VIETINBANK', 'BIDV', 'AGRIBANK', 'TECHCOMBANK', 'MBBANK', 'ACB', 'VPBANK', 'SACOMBANK', 'TPBANK'];
type FundingAttempt = { key: string; amount: string; currency: string };

export function FundingPanel(props: {
  job: Job; user: User; onJobUpdated: (job: Job) => void; blocked?: boolean;
  operationLock?: MutableRefObject<boolean>; onMutationChange?: (busy: boolean) => void;
}) {
  const [chooseEscrow, setChooseEscrow] = useState(false);
  const [choosePartner, setChoosePartner] = useState(false);
  if (props.job.contract?.paymentRail === 'UNIFIED_USDC_PAYOUT') {
    return <UnifiedFundingPanel {...props} />;
  }
  if (props.job.contract?.paymentRail === 'PARTNER_ESCROW_MOCK' || choosePartner) {
    return <PartnerFundingPanel {...props} onChooseBack={() => setChoosePartner(false)} />;
  }
  if (props.job.contract?.paymentRail === 'SOLANA_ESCROW' || chooseEscrow) {
    return <><WalletLinkPanel /><EscrowFundingPanel {...props} onChooseBack={() => setChooseEscrow(false)} /></>;
  }
  return <><WalletLinkPanel /><SimulatedFundingPanel {...props} onChooseEscrow={() => setChooseEscrow(true)} onChoosePartner={() => setChoosePartner(true)} /></>;
}

function UnifiedFundingPanel({ job, user, onJobUpdated, blocked = false, operationLock, onMutationChange }: {
  job: Job; user: User; onJobUpdated: (job: Job) => void; blocked?: boolean;
  operationLock?: MutableRefObject<boolean>; onMutationChange?: (busy: boolean) => void;
}) {
  const contract = job.contract;
  const owner = user.userType === 'CLIENT' && job.clientUserId === user.id;
  const [flow, setFlow] = useState<PaymentFlowTimeline | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [tick, setTick] = useState(0);
  const [bank, setBank] = useState<ClientBankAccount | null>(null);
  const scope = contract?.milestoneId ? attemptScope('unified-usd-order', user.id, contract.id, contract.milestoneId) : '';
  useEffect(() => {
    if (!owner) return;
    let active = true;
    // The payer bank is snapshotted on the USD order and reused for any USD refund.
    void api.clientBank().then(value => { if (active) setBank(value); }).catch(() => { if (active) setBank(null); });
    return () => { active = false; };
  }, [owner, tick]);
  useEffect(() => { onMutationChange?.(busy); }, [busy, onMutationChange]);
  useEffect(() => {
    if (!contract?.milestoneId) { setLoading(false); return; }
    let active = true;
    setLoading(true);
    void api.paymentFlow(contract.id, contract.milestoneId).then(result => {
      if (active) { setFlow(result); setError(''); }
    }).catch(() => {
      if (active) setError('Chưa đọc được timeline thanh toán. Hãy đối chiếu lại trước khi thao tác.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [contract?.id, contract?.milestoneId, tick]);
  useEffect(() => {
    const orderStatus = flow?.steps.find(step => step.kind === 'USD_ORDER')?.status;
    const usdcStatus = flow?.steps.find(step => step.kind === 'CLIENT_USDC')?.status;
    if (!['PENDING', 'PROCESSING', 'UNKNOWN'].includes(orderStatus || '')
        && !['PENDING', 'PROCESSING', 'UNKNOWN'].includes(usdcStatus || '')) return;
    const timer = window.setTimeout(() => setTick(value => value + 1), 5000);
    return () => window.clearTimeout(timer);
  }, [flow, tick]);
  async function openOrder() {
    if (!contract?.milestoneId || !owner || blocked || busy || operationLock?.current) return;
    setBusy(true); setError(''); if (operationLock) operationLock.current = true;
    try {
      const saved = readAttempt<{ key: string }>(scope) ?? { key: crypto.randomUUID() };
      saveAttempt(scope, saved);
      const next = await api.openUnifiedUsdOrder(contract.id, contract.milestoneId, saved.key);
      setFlow(next);
      if (next.termsStatus === 'LOCKED') clearAttempt(scope);
    } catch (cause) {
      setError(cause instanceof ApiError && cause.code === 4021
        ? 'Cần tài khoản ngân hàng Client và ví Solana đã liên kết trước khi tạo USD order.'
        : 'Chưa xác nhận được USD order. Giữ nguyên lần gửi và đối chiếu trước khi thử lại.');
      setTick(value => value + 1);
    } finally { setBusy(false); if (operationLock) operationLock.current = false; }
  }
  async function submitOrder() {
    if (!contract?.milestoneId || !owner || blocked || busy || operationLock?.current) return;
    setBusy(true); setError(''); if (operationLock) operationLock.current = true;
    try { setFlow(await api.submitUnifiedUsdOrder(contract.id, contract.milestoneId)); setConfirm(false); }
    catch { setError('Chưa xác nhận được lệnh nộp USD. Hãy đối chiếu cùng order trước khi gửi lại.'); setTick(value => value + 1); }
    finally { setBusy(false); if (operationLock) operationLock.current = false; }
  }
  if (!contract) return null;
  const usdOrder = flow?.steps.find(step => step.kind === 'USD_ORDER');
  const clientUsdc = flow?.steps.find(step => step.kind === 'CLIENT_USDC');
  // On-ramp delivers USDC to the bound Client wallet and the vault names the Freelancer wallet,
  // so both parties link wallets before any money moves.
  return <><WalletLinkPanel />
    <section className="funding-document" id="funding" tabIndex={-1} aria-label="Luồng thanh toán thống nhất">
    <SectionHeading title="Thanh toán của Job" aside="Mô phỏng" />
    {loading && <p role="status">Đang đọc timeline thanh toán…</p>}
    {error && <p role="alert" className="form-error">{error}</p>}
    {flow && <>
      <p>Mã luồng: <code>{flow.paymentFlowId}</code></p>
      <p>Giá Job: {flow.grossUsd} USD · USDC dự kiến vào escrow: {flow.escrowUsdc} · Phí Freelancer chịu: {flow.platformFeeUsd} USD.</p>
      {flow.payerBankCode && <p>Tài khoản Client nộp USD / nhận hoàn: {flow.payerBankCode} · {flow.payerBankMaskedAccount}.</p>}
      {flow.termsStatus === 'DRAFT' && <p role="status">Điều khoản và quote đang chờ khóa. Chưa tạo lệnh USD và chưa thể bắt đầu công việc.</p>}
      {flow.termsStatus === 'LOCKED' && <p>Quote: {flow.quoteSource || 'Chưa có nguồn'} · Hết hạn: {flow.quoteExpiresAt || 'Chưa xác nhận'} · Mint: {flow.mint || 'Chưa xác nhận'}.</p>}
      <p>Hủy trước release: USDC về ví Client trước, sau đó đối tác hoàn USD theo điều khoản. Phí FreelaX bằng 0 khi hoàn.</p>
      <p>Công việc: {flow.jobStatus} · Hợp đồng: {flow.contractStatus}. Mỗi bước tiền cần xác nhận riêng.</p>
      <ul>{flow.steps.map(step => <li key={step.kind}>{step.kind}: {step.status}{step.amount != null && step.currency ? ` · ${step.amount} ${step.currency}` : ''}{step.reference ? ` · ${step.reference}` : ''}</li>)}</ul>
      {owner && flow.termsStatus === 'DRAFT' && !bank?.ready && <ClientBankForm blocked={blocked} onSaved={setBank} />}
      {owner && flow.termsStatus === 'DRAFT' && <p className="metadata">Liên kết ví Solana của bạn trước khi tạo USD order: USDC từ on-ramp chỉ được chuyển vào ví đã xác minh.</p>}
      {owner && flow.termsStatus === 'DRAFT' && <button className="button" disabled={busy || blocked || loading || !bank?.ready} onClick={() => void openOrder()}>Tạo USD order mô phỏng</button>}
      {owner && usdOrder?.status === 'AWAITING_CLIENT' && !confirm && <button className="button" disabled={busy || blocked || loading} onClick={() => setConfirm(true)}>Xem và xác nhận nộp USD</button>}
      {owner && usdOrder?.status === 'AWAITING_CLIENT' && confirm && <div className="approval-confirm" role="group" aria-label="Xác nhận USD order">
        <p>Client nộp {flow.grossUsd} USD theo quote đã hiển thị; mock chỉ ghi đã nhận sau sao kê đối tác. USDC chưa vào escrow ở bước này.</p>
        <ActionGroup><button className="button" disabled={busy || blocked} onClick={() => void submitOrder()}>Xác nhận nộp USD mô phỏng</button><button className="button button-secondary" disabled={busy} onClick={() => setConfirm(false)}>Quay lại</button></ActionGroup>
      </div>}
      {usdOrder?.status === 'PENDING' && <p role="status">Đối tác mock đang xác nhận USD; chưa mở công việc.</p>}
      {clientUsdc?.status === 'CONFIRMED' && <p role="status">USDC đã vào ví Client theo receipt on-ramp. Client cần ký chuyển vào escrow; công việc chỉ mở sau khi vault được xác minh.</p>}
    </>}
    <button className="text-button" disabled={loading} onClick={() => setTick(value => value + 1)}>Đối chiếu timeline</button>
  </section>
    {clientUsdc?.status === 'CONFIRMED' && <><EscrowFundingPanel job={job} user={user} onJobUpdated={onJobUpdated} blocked={blocked} operationLock={operationLock} onMutationChange={onMutationChange} expectedUsdc={flow!.escrowUsdc} /></>}
  </>;
}

/** Client payer account; required before a unified USD order because USD refunds return to it. */
function ClientBankForm({ blocked, onSaved }: { blocked: boolean; onSaved: (bank: ClientBankAccount) => void }) {
  const [bankCode, setBankCode] = useState<BankCode>('VIETCOMBANK');
  const [number, setNumber] = useState('');
  const [holder, setHolder] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (busy || blocked) return;
    if (!/^[0-9]{6,34}$/.test(number) || holder.trim().length < 2 || holder.trim().length > 255) { setError('Kiểm tra số tài khoản (6–34 chữ số) và tên chủ tài khoản (2–255 ký tự).'); return; }
    setBusy(true); setError('');
    try { onSaved(await api.saveClientBank({ bankCode, bankAccountNumber: number, bankAccountHolderName: holder.trim() })); }
    catch { setError('Không lưu được ngân hàng. Kiểm tra thông tin hoặc thử lại.'); }
    finally { setBusy(false); }
  }
  return <form className="bank-form" onSubmit={save} aria-label="Tài khoản ngân hàng Client">
    <p>Cần tài khoản ngân hàng Client trước khi tạo USD order; khoản hoàn USD (nếu có) trả về tài khoản này.</p>
    <label>Ngân hàng<select value={bankCode} disabled={busy} onChange={e => setBankCode(e.target.value as BankCode)}>{banks.map(code => <option key={code}>{code}</option>)}</select></label>
    <label>Số tài khoản · 6–34 chữ số<input required pattern="[0-9]{6,34}" maxLength={34} inputMode="numeric" autoComplete="off" value={number} disabled={busy} onChange={e => setNumber(e.target.value)} /></label>
    <label>Tên chủ tài khoản<input required minLength={2} maxLength={255} autoComplete="off" value={holder} disabled={busy} onChange={e => setHolder(e.target.value)} /></label>
    {error && <p role="alert" className="form-error">{error}</p>}
    <ActionGroup><button className="button" disabled={blocked || busy}>{busy ? 'Đang lưu…' : 'Lưu ngân hàng'}</button></ActionGroup>
  </form>;
}

function SimulatedFundingPanel({ job, user, onJobUpdated, blocked = false, operationLock, onMutationChange, onChooseEscrow, onChoosePartner }: {
  job: Job; user: User; onJobUpdated: (job: Job) => void; blocked?: boolean;
  operationLock?: MutableRefObject<boolean>; onMutationChange?: (busy: boolean) => void;
  onChooseEscrow: () => void;
  onChoosePartner: () => void;
}) {
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
  useEffect(() => { onMutationChange?.(busy); }, [busy, onMutationChange]);
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
    if (lock.current || blocked || operationLock?.current || !owner) return;
    if (!/^[0-9]{6,34}$/.test(number) || holder.trim().length < 2 || holder.trim().length > 255) { setError('Kiểm tra số tài khoản (6–34 chữ số) và tên chủ tài khoản (2–255 ký tự).'); return; }
    lock.current = true; setBusy(true); setError('');
    try {
      const saved = await api.saveClientBank({ bankCode, bankAccountNumber: number, bankAccountHolderName: holder.trim() });
      if (alive.current) { setBank(saved); setNumber(''); setHolder(''); setEditingBank(false); }
    } catch { if (alive.current) setError('Không lưu được ngân hàng. Kiểm tra thông tin hoặc thử lại.'); }
    finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  async function fund() {
    if (lock.current || blocked || operationLock?.current || !owner || !contract?.milestoneId || !amount || !bank?.ready || !initialized.current || fundingUnresolved(funding?.fundingStatus) || !confirm || contract.status !== 'PENDING_FUNDING' || job.status !== 'AWAITING_PAYMENT') return;
    lock.current = true; setBusy(true); setError('');
    if (operationLock) operationLock.current = true;
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
    } finally { lock.current = false; if (operationLock) operationLock.current = false; if (alive.current) setBusy(false); }
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
      {eligible && !editingBank && <button className="text-button" disabled={blocked || busy || loading || fundingUnresolved(funding?.fundingStatus)} onClick={() => setEditingBank(true)}>{bank?.ready ? 'Cập nhật ngân hàng' : 'Thiết lập ngân hàng'}</button>}
      {eligible && editingBank && <form className="bank-form" onSubmit={saveBank}>
        <label>Ngân hàng<select value={bankCode} disabled={busy} onChange={e => setBankCode(e.target.value as BankCode)}>{banks.map(code => <option key={code}>{code}</option>)}</select></label>
        <label>Số tài khoản · 6–34 chữ số<input required pattern="[0-9]{6,34}" maxLength={34} inputMode="numeric" autoComplete="off" value={number} disabled={busy} onChange={e => setNumber(e.target.value)} /></label>
        <label>Tên chủ tài khoản<input required minLength={2} maxLength={255} autoComplete="off" value={holder} disabled={busy} onChange={e => setHolder(e.target.value)} /></label>
        <ActionGroup><button className="button" disabled={blocked || busy}>{busy ? 'Đang lưu…' : 'Lưu ngân hàng'}</button><button className="button button-secondary" type="button" disabled={busy} onClick={() => { setNumber(''); setHolder(''); setEditingBank(false); }}>Quay lại</button></ActionGroup>
      </form>}
      {eligible && !editingBank && !confirm && <ActionGroup><button className="button" disabled={blocked || busy || loading || !initialized.current || !bank?.ready || fundingUnresolved(funding?.fundingStatus) || funding?.fundingStatus === 'SUCCEEDED'} onClick={() => setConfirm(true)}>{funding?.fundingStatus === 'FAILED' ? 'Thử funding lại' : attempt.current ? 'Tiếp tục lần funding trước' : 'Funding mô phỏng'}</button></ActionGroup>}
      {eligible && !loading && !funding && !attempt.current && <button className="text-button" disabled={blocked || busy} onClick={onChooseEscrow}>Chọn ký quỹ Solana</button>}
      {eligible && !loading && !funding && !attempt.current && <button className="text-button" disabled={blocked || busy} onClick={onChoosePartner}>Chọn ký quỹ đối tác mock</button>}
      {eligible && confirm && <div className="approval-confirm" role="group" aria-label="Xác nhận funding">
        <strong>Xác nhận {amount} {contract.currency} · Mô phỏng</strong><p>Capture mô phỏng theo số tiền hợp đồng đã chốt. Không xác nhận chi trả cho Freelancer.</p>
        <ActionGroup><button className="button" disabled={blocked || busy} onClick={() => void fund()}>Xác nhận funding</button><button className="button button-secondary" disabled={busy} onClick={() => setConfirm(false)}>Quay lại</button></ActionGroup>
      </div>}
    </>}
    {error && <p role="alert" className="form-error">{error}</p>}
    <button className="text-button" disabled={busy || loading} onClick={() => { setLoading(true); setTick(value => value + 1); }}>Đối chiếu funding</button>
    {funding && <EvidenceDisclosure summary="Tham chiếu funding"><dl className="reference-list"><div><dt>Lần funding</dt><dd><code>{funding.fundingTransactionId}</code></dd></div><div><dt>Provider</dt><dd><code>{funding.providerReference || '—'}</code></dd></div></dl></EvidenceDisclosure>}
  </section>;
}
