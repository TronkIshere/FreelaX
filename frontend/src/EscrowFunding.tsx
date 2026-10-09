import { useEffect, useRef, useState, type MutableRefObject } from 'react';
import { api, ApiError } from './api';
import { ActionGroup, EvidenceDisclosure, SectionHeading } from './components';
import { connectSolanaWallet, signEscrowTransaction } from './escrowWallet';
import type { EscrowFundingBuild, EscrowFundingView, Job, User } from './types';

const liveStatuses = new Set(['Funded', 'Submitted', 'Revision', 'Disputed', 'Released']);

export function EscrowFundingPanel({ job, user, onJobUpdated, blocked = false,
  operationLock, onMutationChange, onChooseBack, expectedUsdc }: {
  job: Job; user: User; onJobUpdated: (job: Job) => void; blocked?: boolean;
  operationLock?: MutableRefObject<boolean>; onMutationChange?: (busy: boolean) => void;
  onChooseBack?: () => void; expectedUsdc?: string | number;
}) {
  const contract = job.contract;
  const owner = user.userType === 'CLIENT' && job.clientUserId === user.id;
  const participant = owner || (user.userType === 'FREELANCER' && job.freelancerId === user.id);
  const [view, setView] = useState<EscrowFundingView | null>(null);
  const [build, setBuild] = useState<EscrowFundingBuild | null>(null);
  const [address, setAddress] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [tick, setTick] = useState(0);
  const [confirmedTerms, setConfirmedTerms] = useState(false);
  const lock = useRef(false);
  const alive = useRef(true);

  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => { onMutationChange?.(busy); }, [busy, onMutationChange]);
  useEffect(() => {
    if (!participant || !contract?.milestoneId) { setLoading(false); return; }
    let active = true;
    async function load() {
      try {
        const next = await api.escrowFunding(contract!.id, contract!.milestoneId!);
        if (!active) return;
        setView(next); setError('');
        if (liveStatuses.has(next.status) && job.status === 'AWAITING_PAYMENT') {
          const fresh = await api.job(job.id);
          if (active) onJobUpdated(fresh);
        }
      } catch (cause) {
        if (active && !(cause instanceof ApiError && cause.status === 404 && contract!.paymentRail !== 'SOLANA_ESCROW')) {
          setError('Chưa đối soát được trạng thái escrow trên Solana.');
        }
      } finally { if (active) setLoading(false); }
    }
    void load();
    return () => { active = false; };
  }, [contract?.id, contract?.milestoneId, contract?.paymentRail, participant, job.id, job.status, tick]);

  useEffect(() => {
    if (!view || !['AWAITING_SIGNATURE', 'PENDING_CONFIRMATION', 'UNCONFIRMED'].includes(view.status)) return;
    const timer = window.setTimeout(() => setTick(value => value + 1), 5000);
    return () => window.clearTimeout(timer);
  }, [view, tick]);

  async function connect() {
    try {
      const connected = await connectSolanaWallet();
      if (alive.current) { setAddress(connected.address); setError(''); }
    } catch (cause) { if (alive.current) setError(cause instanceof Error ? cause.message : 'Không kết nối được ví.'); }
  }

  async function prepare() {
    if (!owner || !contract?.milestoneId || lock.current || operationLock?.current || blocked) return;
    lock.current = true; setBusy(true); setError('');
    if (operationLock) operationLock.current = true;
    try {
      const connected = await connectSolanaWallet();
      const next = await api.prepareEscrowFunding(contract.id, contract.milestoneId, connected.address);
      if (alive.current) { setAddress(connected.address); setBuild(next); setConfirmedTerms(false); }
    } catch (cause) {
      if (alive.current) setError(cause instanceof ApiError
        ? 'Không chuẩn bị được escrow. Kiểm tra ví đã đăng ký, hạn funding và trạng thái Job.'
        : cause instanceof Error ? cause.message : 'Không chuẩn bị được giao dịch.');
    } finally { lock.current = false; if (operationLock) operationLock.current = false; if (alive.current) setBusy(false); }
  }

  async function signAndFund() {
    if (!owner || !contract?.milestoneId || !build || !confirmedTerms
      || lock.current || operationLock?.current || blocked) return;
    lock.current = true; setBusy(true); setError('');
    if (operationLock) operationLock.current = true;
    try {
      const connected = await connectSolanaWallet();
      if (connected.address !== build.clientWallet) throw new Error('Ví đang kết nối không khớp ví Client của escrow.');
      const signed = await signEscrowTransaction(connected.wallet, build.transactionBase64);
      const next = await api.submitEscrowFunding(contract.id, contract.milestoneId,
        build.buildSessionId, signed);
      if (alive.current) { setView(next); setBuild(null); setTick(value => value + 1); }
      if (liveStatuses.has(next.status)) {
        const fresh = await api.job(job.id);
        if (alive.current) onJobUpdated(fresh);
      }
    } catch (cause) {
      if (alive.current) {
        setError(cause instanceof Error ? cause.message : 'Chưa xác minh được giao dịch; hãy đối soát trước khi gửi lại.');
        setTick(value => value + 1);
      }
    } finally { lock.current = false; if (operationLock) operationLock.current = false; if (alive.current) setBusy(false); }
  }

  if (!participant || !contract?.milestoneId) return null;
  const eligible = owner && contract.status === 'PENDING_FUNDING' && job.status === 'AWAITING_PAYMENT';
  return <section className="funding-document" id="funding" tabIndex={-1} aria-label="Ký quỹ Solana">
    <SectionHeading title={expectedUsdc == null ? 'Ký quỹ Solana' : 'Ký quỹ USDC'} aside="On-chain" />
    <strong>{expectedUsdc == null ? `${contract.amount} ${contract.currency}` : `${expectedUsdc} USDC`}</strong>
    <p role="status">{loading ? 'Đang đối soát escrow…' : view ? `Trạng thái on-chain: ${view.status} · ${view.settlementStatus}` : 'Chưa có token được xác nhận trong vault.'}</p>
    {view?.fundingExpiresAt && <p>Hạn ký quỹ: {new Date(Number(view.fundingExpiresAt) * 1000).toLocaleString('vi-VN')}.</p>}
    <p>Tiền chỉ được xem là ký quỹ sau khi backend xác minh escrow trên Solana. Khi Client im lặng hết hạn review, lệnh giải ngân có thể được gọi mà không cần chữ ký Client.</p>
    {address && <p>Ví đang kết nối: <code>{address}</code></p>}
    {!address && <button className="text-button" disabled={busy} onClick={() => void connect()}>Kết nối ví Solana</button>}
    {eligible && !build && !liveStatuses.has(view?.status ?? '') && <ActionGroup>
      <button className="button" disabled={blocked || busy || loading || view?.status === 'PENDING_CONFIRMATION'} onClick={() => void prepare()}>{busy ? 'Đang chuẩn bị…' : 'Chuẩn bị giao dịch ký quỹ'}</button>
      {!view && onChooseBack && contract.paymentRail !== 'SOLANA_ESCROW' && contract.paymentRail !== 'UNIFIED_USDC_PAYOUT' && <button className="button button-secondary" disabled={busy} onClick={onChooseBack}>Quay lại thanh toán mô phỏng</button>}
    </ActionGroup>}
    {build && <div className="approval-confirm" role="group" aria-label="Xác nhận ký quỹ Solana">
      <strong>Xác nhận token sẽ chuyển vào vault escrow</strong>
      <p>Mint: <code>{build.mint}</code></p>
      <p>Số token (base units): <code>{build.amountBaseUnits}</code></p>
      <p>Vault/escrow: <code>{build.escrowAddress}</code></p>
      <p>Ví Client: <code>{build.clientWallet}</code></p>
      <p>Ví Freelancer: <code>{build.freelancerWallet}</code></p>
      <label><input type="checkbox" checked={confirmedTerms} disabled={busy} onChange={event => setConfirmedTerms(event.target.checked)} /> Tôi đã kiểm tra mint, số tiền và hai ví.</label>
      <ActionGroup><button className="button" disabled={busy || blocked || !confirmedTerms} onClick={() => void signAndFund()}>{busy ? 'Đang ký…' : 'Ký và gửi giao dịch'}</button></ActionGroup>
    </div>}
    {!owner && <p>Client cần ký giao dịch funding. Freelancer chỉ bắt đầu sau khi vault được xác nhận đủ tiền.</p>}
    {error && <p role="alert" className="form-error">{error}</p>}
    <button className="text-button" disabled={busy || loading} onClick={() => { setLoading(true); setTick(value => value + 1); }}>Đối soát escrow</button>
    {view && <EvidenceDisclosure summary="Bằng chứng escrow"><dl className="reference-list">
      <div><dt>Escrow PDA</dt><dd><code>{view.escrowAddress}</code></dd></div>
      <div><dt>Vault</dt><dd><code>{view.vaultAddress || 'Chưa xác minh'}</code></dd></div>
      <div><dt>Số dư vault (base units)</dt><dd><code>{view.vaultBalanceBaseUnits || 'Chưa xác minh'}</code></dd></div>
      <div><dt>Funding signature</dt><dd><code>{view.fundSignature || 'Đang chờ'}</code></dd></div>
    </dl></EvidenceDisclosure>}
  </section>;
}
