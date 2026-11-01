import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';
import { ActionGroup, SectionHeading } from './components';
import type { AvalancheContractView } from './avalancheTypes';
import { actionText, environmentBadge, environmentBanner, explorerTxUrl, shortHash, stateText } from './avalanchePresentation';
import { EvmWallet, WalletError } from './evmWallet';
import type { User } from './types';

export const avalancheUiEnabled = () => import.meta.env.VITE_AVALANCHE_ENABLED === 'true';

type Props = { contractId: string; user: User; isClient: boolean; isFreelancer: boolean; onChanged?: () => void; walletFactory?: () => EvmWallet };

const SIMPLE_ACTIONS: { action: string; client: boolean; freelancer: boolean }[] = [
  { action: 'fund', client: true, freelancer: false },
  { action: 'extension-approve', client: true, freelancer: false },
  { action: 'release', client: true, freelancer: true },
];

function message(error: unknown): string {
  if (error instanceof WalletError) return error.message;
  if (error instanceof ApiError) return error.message;
  return 'Không thể hoàn tất thao tác. Hãy thử lại.';
}

export function AvalancheEscrowPanel({ contractId, isClient, isFreelancer, onChanged, walletFactory }: Props) {
  const [view, setView] = useState<AvalancheContractView | null>(null);
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

  const withWallet = (action: string) => run(async () => {
    const env = view?.environment;
    if (!env) throw new WalletError('Chưa biết môi trường mạng.', 'NO_ENV');
    const input = { idempotencyKey: crypto.randomUUID() };
    const prepared = await api.avalanchePrepare(contractId, action, input);
    const wallet = (walletFactory ?? (() => new EvmWallet()))();
    const { address } = await wallet.connect(env.chainId);
    let last: string | null = null;
    for (const tx of prepared.transactions) last = await wallet.sendPrepared(tx, address, env.chainId);
    if (last) await api.avalancheRecordTransaction(contractId, action, last, input);
  });

  const withSimulatedAccount = (action: string) => run(async () => {
    await api.avalancheExecute(contractId, action, { idempotencyKey: crypto.randomUUID() });
  });

  if (!view || !view.avalancheEnabled) {
    return error ? <p role="alert">{error}</p> : null;
  }
  const env = view.environment;
  const onAvalanche = view.networkId !== null;
  const allowed = SIMPLE_ACTIONS.filter(a => (a.client && isClient) || (a.freelancer && isFreelancer));
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
    {onAvalanche && env && <ActionGroup label="Thao tác ký quỹ">
      {allowed.map(a => <span key={a.action}>
        <button className="button" disabled={busy} onClick={() => void withWallet(a.action)}>{actionText(a.action)} bằng ví</button>
        {env.backendSignersAvailable && env.simulated && <button className="button button-secondary" disabled={busy}
          onClick={() => void withSimulatedAccount(a.action)}>{actionText(a.action)} bằng tài khoản mô phỏng</button>}
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
