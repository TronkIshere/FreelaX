import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';
import { ActionGroup, SectionHeading } from './components';
import { availableAvalancheActions, deliveryDateSeconds, type AvalancheActionOffer } from './avalancheActions';
import type { AvalancheActionInput, AvalancheContractView } from './avalancheTypes';
import { actionText, chainInstant, environmentBadge, environmentBanner, explorerTxUrl, issuerAlertText, shortHash, stateText, tokenLabel, usdcFromBaseUnits } from './avalanchePresentation';
import { EvmWallet, WalletError } from './evmWallet';
import type { ContractSubmission, Dispute, User } from './types';

export const avalancheUiEnabled = () => import.meta.env.VITE_AVALANCHE_ENABLED === 'true';

type Props = {
  contractId: string; user: User; isClient: boolean; isFreelancer: boolean;
  /** Administrator who is not a participant: may settle a recorded dispute decision (arbiter role). */
  isArbiter?: boolean;
  submissions?: readonly Pick<ContractSubmission, 'id' | 'version' | 'status'>[];
  dispute?: Pick<Dispute, 'disputeId' | 'status' | 'openedBy'> | null;
  onChanged?: () => void; walletFactory?: () => EvmWallet;
};

function message(error: unknown): string {
  if (error instanceof WalletError) return error.message;
  if (error instanceof ApiError) return error.message;
  return 'Không thể hoàn tất thao tác. Hãy thử lại.';
}

export function AvalancheEscrowPanel({ contractId, user, isClient, isFreelancer, isArbiter = false, submissions = [], dispute = null, onChanged, walletFactory }: Props) {
  const [view, setView] = useState<AvalancheContractView | null>(null);
  const [extensionDate, setExtensionDate] = useState('');
  const [evmWallet, setEvmWallet] = useState<string | null | undefined>(undefined);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);

  const load = useCallback(async (refresh = false) => {
    try {
      const next = refresh ? await api.avalancheRefresh(contractId) : await api.avalancheStatus(contractId);
      if (alive.current) { setView(next); setError(''); }
    } catch (cause) {
      if (alive.current) setError(message(cause));
    }
  }, [contractId]);

  useEffect(() => { void load(); }, [load]);
  const party = isClient || isFreelancer;
  useEffect(() => {
    if (!party || !view?.avalancheEnabled || view.networkId === null) return;
    api.evmWalletCurrent().then(w => { if (alive.current) setEvmWallet(w?.walletAddress ?? null); }, () => { if (alive.current) setEvmWallet(null); });
  }, [party, view?.avalancheEnabled, view?.networkId]);
  useEffect(() => {
    const open = view?.transactions.some(t => !['FINALIZED', 'REVERTED', 'FAILED', 'REPLACED'].includes(t.status));
    if (!open) return;
    const id = window.setInterval(() => void load(true), 5000);
    return () => window.clearInterval(id);
  }, [view, load]);

  async function run(work: () => Promise<void>) {
    if (lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try { await work(); await load(true); onChanged?.(); } catch (cause) { if (alive.current) setError(message(cause)); }
    finally { lock.current = false; if (alive.current) setBusy(false); }
  }

  const enable = () => run(async () => { setView(await api.avalancheEnable(contractId)); });

  function inputFor(offer: AvalancheActionOffer): AvalancheActionInput {
    const input: AvalancheActionInput = { ...offer.input, idempotencyKey: crypto.randomUUID() };
    if (offer.needsDate) {
      const seconds = deliveryDateSeconds(extensionDate);
      if (seconds === null) throw new WalletError('Hãy chọn hạn bàn giao mới.', 'NO_DATE');
      input.newDeliveryDueAt = seconds;
    }
    return input;
  }

  const linkWallet = () => run(async () => {
    const env = view?.environment;
    if (!env) throw new WalletError('Chưa biết môi trường mạng.', 'NO_ENV');
    const wallet = (walletFactory ?? (() => new EvmWallet()))();
    const { address } = await wallet.connect(env.chainId);
    const challenge = await api.evmWalletChallenge(address);
    const signature = await wallet.personalSign(challenge.message, address);
    const bound = await api.evmWalletVerify(challenge.challengeId, signature);
    setEvmWallet(bound.walletAddress);
  });

  /** F13: each party signs the SAME typed data (same deadline); the party who completes the pair sends the refund. */
  async function mutualRefundWithWallet(wallet: EvmWallet, address: string, chainId: number) {
    const pending = view?.mutualRefund ?? null;
    let state = pending;
    const mine = isClient ? pending?.clientSigned : pending?.freelancerSigned;
    if (!mine) {
      const prepared = await api.avalanchePrepare(contractId, 'mutual-refund',
        pending ? { authorizationDeadline: pending.authorizationDeadline } : {});
      if (!prepared.typedData || !prepared.typedDataDigest || prepared.authorizationDeadline == null) {
        throw new WalletError('Yêu cầu ký không hợp lệ.', 'TYPED_DATA_INVALID');
      }
      const signature = await wallet.signTypedData(prepared.typedData, address, chainId);
      const next = await api.avalancheMutualRefundSignature(contractId,
        { authorizationDeadline: prepared.authorizationDeadline, typedDataDigest: prepared.typedDataDigest, signature });
      if (alive.current) setView(next);
      state = next.mutualRefund ?? null;
    }
    if (!(state?.clientSigned && state?.freelancerSigned)) return; // waiting for the other party
    const input = { idempotencyKey: crypto.randomUUID() };
    const final = await api.avalanchePrepare(contractId, 'mutual-refund', input);
    let last: string | null = null;
    for (const tx of final.transactions) last = await wallet.sendPrepared(tx, address, chainId);
    if (last) await api.avalancheRecordTransaction(contractId, 'mutual-refund', last, input);
  }

  const withWallet = (offer: AvalancheActionOffer) => run(async () => {
    const env = view?.environment;
    if (!env) throw new WalletError('Chưa biết môi trường mạng.', 'NO_ENV');
    if (offer.action === 'mutual-refund') {
      const wallet = (walletFactory ?? (() => new EvmWallet()))();
      const { address } = await wallet.connect(env.chainId);
      await mutualRefundWithWallet(wallet, address, env.chainId);
      return;
    }
    const action = offer.action;
    const input = inputFor(offer);
    const prepared = await api.avalanchePrepare(contractId, action, input);
    const wallet = (walletFactory ?? (() => new EvmWallet()))();
    const { address } = await wallet.connect(env.chainId);
    let last: string | null = null;
    for (const tx of prepared.transactions) last = await wallet.sendPrepared(tx, address, env.chainId);
    if (last) await api.avalancheRecordTransaction(contractId, action, last, input);
  });

  const withSimulatedAccount = (offer: AvalancheActionOffer) => run(async () => {
    await api.avalancheExecute(contractId, offer.action, inputFor(offer));
  });

  if (!view || !view.avalancheEnabled || (isArbiter && view.networkId === null)) {
    return error ? <p role="alert">{error}</p> : null;
  }
  const env = view.environment;
  const onAvalanche = view.networkId !== null;
  const simulatedAccounts = !!env && env.backendSignersAvailable && env.simulated;
  const allowed = availableAvalancheActions({ userId: user.id, isClient, isFreelancer, isArbiter, submissions, dispute })
    .filter(a => a.wallet || simulatedAccounts);
  return <section className="review-action avalanche-panel" aria-label="Ký quỹ Avalanche">
    <SectionHeading title="Ký quỹ Avalanche" />
    {env && <p className={'avalanche-banner' + (env.simulated ? ' simulated' : ' testnet')} role="note">
      <strong>{environmentBadge(env)}</strong> — {environmentBanner(env)}
    </p>}
    {error && <p role="alert">{error}</p>}
    {view.notice && <p role="status">{view.notice}</p>}
    {!onAvalanche && isClient && <ActionGroup>
      <button className="button" disabled={busy} onClick={() => void enable()}>Dùng ký quỹ Avalanche cho hợp đồng này</button>
    </ActionGroup>}
    {!onAvalanche && !isClient && <p>Hợp đồng này chưa dùng ký quỹ Avalanche.</p>}
    {onAvalanche && !!view.issuerAlerts?.length && <div className="avalanche-stuck" role="alert" data-testid="issuer-alerts">
      <strong>Ký quỹ đang bị kẹt (mô phỏng/testnet)</strong>
      <ul>{view.issuerAlerts.map(a => <li key={a.code + (a.address ?? '')}>{issuerAlertText(a.code, a.blocks)}</li>)}</ul>
      <small>Không có rút tiền đặc quyền, đường vòng hay chuyển hướng tiền. Ký quỹ giữ nguyên cho đến khi nhà phát hành gỡ biện pháp kiểm soát; khi đó thao tác bình thường sẽ thành công.</small>
    </div>}
    {onAvalanche && view.chain && <p>Trạng thái trên chuỗi: <strong>{view.chain.status}</strong></p>}
    {onAvalanche && view.chain && <dl className="avalanche-chain-facts" aria-label="Dữ liệu ký quỹ đọc từ chuỗi">
      <div><dt>Số tiền ký quỹ</dt><dd>{usdcFromBaseUnits(view.chain.amountBaseUnits) ?? '—'} {tokenLabel(env?.simulated ?? true)}</dd></div>
      <div><dt>Lượt chỉnh sửa</dt><dd>{view.chain.revisionsUsed}/{view.chain.maxRevisions}</dd></div>
      <div><dt>Hạn bàn giao</dt><dd>{chainInstant(view.chain.deliveryDueAt)}</dd></div>
      <div><dt>Hạn review</dt><dd>{chainInstant(view.chain.reviewDueAt)}</dd></div>
    </dl>}
    {onAvalanche && party && evmWallet === null && env && <ActionGroup label="Ví EVM">
      <span>Bạn chưa liên kết ví EVM cho ký quỹ Avalanche.</span>
      <button className="button" disabled={busy} onClick={() => void linkWallet()}>Liên kết ví EVM</button>
    </ActionGroup>}
    {onAvalanche && party && evmWallet && <p className="metadata">Ví EVM đã liên kết: <code>{shortHash(evmWallet)}</code></p>}
    {onAvalanche && view.mutualRefund && <p role="status" data-testid="mutual-refund-state">
      Hoàn tiền thỏa thuận: Client {view.mutualRefund.clientSigned ? 'đã ký' : 'chưa ký'}, Freelancer {view.mutualRefund.freelancerSigned ? 'đã ký' : 'chưa ký'};
      chữ ký hết hạn lúc {chainInstant(view.mutualRefund.authorizationDeadline)}.
    </p>}
    {onAvalanche && env && <ActionGroup label="Thao tác ký quỹ">
      {allowed.map(a => <span key={a.action} data-action={a.action}>
        {a.needsDate && <label>Hạn bàn giao mới<input type="datetime-local" value={extensionDate} disabled={busy}
          onChange={event => setExtensionDate(event.target.value)} /></label>}
        {a.wallet && <button className="button" disabled={busy || (a.needsDate && !extensionDate)} onClick={() => void withWallet(a)}>{actionText(a.action)} bằng ví</button>}
        {simulatedAccounts && <button className="button button-secondary" disabled={busy || (a.needsDate && !extensionDate)}
          onClick={() => void withSimulatedAccount(a)}>{actionText(a.action)} bằng tài khoản mô phỏng</button>}
        {!a.wallet && a.walletUnavailableReason && <small> {a.walletUnavailableReason}</small>}
      </span>)}
    </ActionGroup>}
    {onAvalanche && <ul className="avalanche-transactions" aria-label="Giao dịch ký quỹ">
      {view.transactions.map(t => {
        const s = stateText(t.status);
        const link = env ? explorerTxUrl(env.explorerBaseUrl, t.txHash) : null;
        return <li key={t.id} data-state={t.status}>
          <span>{actionText(t.action)}</span>{' '}
          <span className={'badge tone-' + s.tone} title={s.detail}>{s.label}</span>{' '}
          {env && <span className="badge">{environmentBadge({ environment: env.environment, simulated: t.chainId === 31337 })}</span>}{' '}
          {t.txHash && (link ? <a href={link} target="_blank" rel="noreferrer noopener">{shortHash(t.txHash)}</a> : <code>{shortHash(t.txHash)}</code>)}
          <small> {s.detail}</small>
          {t.errorClass && <small> Mã lỗi: {t.errorClass}</small>}
        </li>;
      })}
    </ul>}
    {onAvalanche && <ActionGroup><button className="button button-secondary" disabled={busy} onClick={() => void run(async () => undefined)}>Làm mới trạng thái</button></ActionGroup>}
  </section>;
}
