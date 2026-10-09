import { useEffect, useRef, useState, type MutableRefObject } from 'react';
import { api, ApiError } from './api';
import { ActionGroup, SectionHeading } from './components';
import { connectSolanaWallet, signEscrowTransaction } from './escrowWallet';
import type { EscrowFundingBuild, EscrowFundingView, Job, User } from './types';

const liveStatuses = new Set(['Funded', 'Submitted', 'Revision', 'Disputed', 'Released']);

export function EscrowFundingPanel({ job, user, onJobUpdated, blocked = false,
  operationLock, onMutationChange, onChooseBack, expectedUsdc, onPaymentRefresh }: {
  job: Job; user: User; onJobUpdated: (job: Job) => void; blocked?: boolean;
  operationLock?: MutableRefObject<boolean>; onMutationChange?: (busy: boolean) => void;
  onChooseBack?: () => void; expectedUsdc?: string | number; onPaymentRefresh?: () => void;
}) {
  const contract = job.contract;
  const owner = user.userType === 'CLIENT' && job.clientUserId === user.id;
  const participant = owner || (user.userType === 'FREELANCER' && job.freelancerId === user.id);
  const [view, setView] = useState<EscrowFundingView | null>(null);
  const [build, setBuild] = useState<EscrowFundingBuild | null>(null);
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
          setError('Chưa cập nhật được trạng thái giữ tiền. Hãy thử lại.');
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

  async function prepare() {
    if (!owner || !contract?.milestoneId || lock.current || operationLock?.current || blocked) return;
    lock.current = true; setBusy(true); setError('');
    if (operationLock) operationLock.current = true;
    try {
      const connected = await connectSolanaWallet();
      if (contract.paymentRail === 'UNIFIED_USDC_PAYOUT') {
        const flow = await api.paymentFlow(contract.id, contract.milestoneId);
        if (flow.steps.find(step => step.kind === 'CLIENT_USDC')?.status !== 'CONFIRMED') {
          if (alive.current) onPaymentRefresh?.();
          return;
        }
      }
      const next = await api.prepareEscrowFunding(contract.id, contract.milestoneId, connected.address);
      if (alive.current) { setBuild(next); setConfirmedTerms(false); }
    } catch (cause) {
      if (alive.current) setError(cause instanceof ApiError
        ? 'Chưa chuẩn bị được khoản tiền. Hãy kiểm tra trạng thái công việc và thử lại.'
        : cause instanceof Error ? cause.message : 'Chưa chuẩn bị được khoản tiền.');
    } finally { lock.current = false; if (operationLock) operationLock.current = false; if (alive.current) setBusy(false); }
  }

  async function signAndFund() {
    if (!owner || !contract?.milestoneId || !build || !confirmedTerms
      || lock.current || operationLock?.current || blocked) return;
    lock.current = true; setBusy(true); setError('');
    if (operationLock) operationLock.current = true;
    try {
      const connected = await connectSolanaWallet();
      if (connected.address !== build.clientWallet) throw new Error('Ví của tài khoản chưa khớp với công việc này.');
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
  return <section className="funding-document" id="funding" tabIndex={-1} aria-label="Giữ tiền cho công việc">
    <SectionHeading title="Giữ tiền cho công việc" />
    <strong>{expectedUsdc == null ? `${contract.amount} ${contract.currency}` : `${expectedUsdc} USD`}</strong>
    <p role="status">{loading ? 'Đang cập nhật…' : view && liveStatuses.has(view.status) ? 'Đã giữ tiền. Người làm có thể bắt đầu.' : 'Đang chờ giữ tiền.'}</p>
    {view?.fundingExpiresAt && <p>Hạn ký quỹ: {new Date(Number(view.fundingExpiresAt) * 1000).toLocaleString('vi-VN')}.</p>}
    <p>Công việc bắt đầu khi khoản tiền này được xác nhận. Nếu hủy trước khi trả cho người làm, tiền sẽ được hoàn theo điều khoản.</p>
    {eligible && !build && !liveStatuses.has(view?.status ?? '') && <ActionGroup>
      <button className="button" disabled={blocked || busy || loading || view?.status === 'PENDING_CONFIRMATION'} onClick={() => void prepare()}>{busy ? 'Đang chuẩn bị…' : 'Chuẩn bị giữ tiền'}</button>
      {!view && onChooseBack && contract.paymentRail !== 'SOLANA_ESCROW' && contract.paymentRail !== 'UNIFIED_USDC_PAYOUT' && <button className="button button-secondary" disabled={busy} onClick={onChooseBack}>Quay lại thanh toán mô phỏng</button>}
    </ActionGroup>}
    {build && <div className="approval-confirm" role="group" aria-label="Xác nhận giữ tiền">
      <strong>Xác nhận giữ {expectedUsdc == null ? contract.amount : expectedUsdc} {expectedUsdc == null ? contract.currency : 'USD'} cho công việc</strong>
      <p>Người làm chỉ bắt đầu sau khi khoản tiền được xác nhận.</p>
      <label><input type="checkbox" checked={confirmedTerms} disabled={busy} onChange={event => setConfirmedTerms(event.target.checked)} /> Tôi đồng ý giữ khoản tiền này theo điều khoản công việc.</label>
      <ActionGroup><button className="button" disabled={busy || blocked || !confirmedTerms} onClick={() => void signAndFund()}>{busy ? 'Đang xác nhận…' : 'Xác nhận giữ tiền'}</button></ActionGroup>
    </div>}
    {!owner && <p>Đang chờ khách xác nhận khoản tiền. Người làm sẽ bắt đầu sau đó.</p>}
    {error && <p role="alert" className="form-error">{error}</p>}
    <button className="text-button" disabled={busy || loading} onClick={() => { setLoading(true); setTick(value => value + 1); }}>Làm mới trạng thái</button>
  </section>;
}
