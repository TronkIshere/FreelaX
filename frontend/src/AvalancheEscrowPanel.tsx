import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';
import { ActionGroup, SectionHeading } from './components';
import { availableAvalancheActions, deliveryDateSeconds, type AvalancheActionOffer } from './avalancheActions';
import type { AvalancheActionInput, AvalancheContractView } from './avalancheTypes';
import { actionText, chainInstant, environmentBadge, environmentBanner, explorerTxUrl, shortHash, stateText, tokenLabel, usdcFromBaseUnits } from './avalanchePresentation';
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

  const withWallet = (offer: AvalancheActionOffer) => run(async () => {
    const env = view?.environment;
    if (!env) throw new WalletError('Chưa biết môi trường mạng.', 'NO_ENV');
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
    {onAvalanche && view.chain && <p>Trạng thái trên chuỗi: <strong>{view.chain.status}</strong></p>}
    {onAvalanche && view.chain && <dl className="avalanche-chain-facts" aria-label="Dữ liệu ký quỹ đọc từ chuỗi">
      <div><dt>Số tiền ký quỹ</dt><dd>{usdcFromBaseUnits(view.chain.amountBaseUnits) ?? '—'} {tokenLabel(env?.simulated ?? true)}</dd></div>
      <div><dt>Lượt chỉnh sửa</dt><dd>{view.chain.revisionsUsed}/{view.chain.maxRevisions}</dd></div>
      <div><dt>Hạn bàn giao</dt><dd>{chainInstant(view.chain.deliveryDueAt)}</dd></div>
      <div><dt>Hạn review</dt><dd>{chainInstant(view.chain.reviewDueAt)}</dd></div>
    </dl>}
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
