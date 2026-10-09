import { useEffect, useState } from 'react';
import { api } from './api';
import { ActionGroup, SectionHeading } from './components';
import { connectSolanaWallet, hasEscrowSignerSignature,
  sameEscrowTransactionMessage, signEscrowTransaction } from './escrowWallet';
import type { EscrowFundingView, EscrowMutualRefund } from './types';

export function MutualRefundPanel({ contractId, escrow, client, freelancer, onRefresh, onBusy }: {
  contractId: string; escrow: EscrowFundingView; client: boolean; freelancer: boolean;
  onRefresh: () => Promise<void>; onBusy: (busy: boolean) => void;
}) {
  const [pending, setPending] = useState<EscrowMutualRefund | null>(null);
  const [agree, setAgree] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const active = ['Funded', 'Submitted', 'Revision'].includes(escrow.status);
  useEffect(() => {
    if (!freelancer || !active) return;
    let alive = true;
    const load = () => api.pendingMutualRefund(contractId)
      .then(value => { if (alive) setPending(value); })
      .catch(() => { if (alive) setError('Chưa đối soát được yêu cầu hoàn tiền.'); });
    void load();
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void load(); }, 10000);
    return () => { alive = false; window.clearInterval(timer); };
  }, [contractId, freelancer, active]);

  async function clientPropose() {
    if (busy || !agree) return;
    setBusy(true); onBusy(true); setError(''); setNotice('');
    try {
      const built = await api.buildMutualRefund(contractId);
      const connected = await connectSolanaWallet();
      if (connected.address !== built.clientWallet) throw new Error('Ví Client không khớp hợp đồng.');
      const signed = await signEscrowTransaction(connected.wallet, built.originalTransaction);
      if (!sameEscrowTransactionMessage(built.originalTransaction, signed)
          || !hasEscrowSignerSignature(signed, built.clientWallet))
        throw new Error('Chữ ký Client không hợp lệ cho giao dịch hoàn tiền.');
      await api.signMutualRefund(contractId, built.intentId, signed);
      setNotice('Client đã ký. Freelancer cần mở Job và ký cùng giao dịch trong thời hạn blockhash.');
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Chưa tạo được yêu cầu hoàn tiền.'); }
    finally { setBusy(false); onBusy(false); }
  }

  async function freelancerFinish() {
    if (busy || !agree || !pending?.partialTransaction) return;
    setBusy(true); onBusy(true); setError(''); setNotice('');
    try {
      if (!sameEscrowTransactionMessage(pending.originalTransaction, pending.partialTransaction)
          || !hasEscrowSignerSignature(pending.partialTransaction, pending.clientWallet))
        throw new Error('Giao dịch Client ký không khớp yêu cầu ban đầu.');
      const connected = await connectSolanaWallet();
      if (connected.address !== pending.freelancerWallet) throw new Error('Ví Freelancer không khớp hợp đồng.');
      const signed = await signEscrowTransaction(connected.wallet, pending.partialTransaction);
      if (!sameEscrowTransactionMessage(pending.originalTransaction, signed)
          || !hasEscrowSignerSignature(signed, pending.clientWallet)
          || !hasEscrowSignerSignature(signed, pending.freelancerWallet))
        throw new Error('Giao dịch cuối không có đủ hai chữ ký hợp lệ.');
      await api.finishMutualRefund(contractId, pending.intentId, signed);
      setNotice('Giao dịch hoàn tiền đã gửi. Chờ xác nhận on-chain.');
      await onRefresh();
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Chưa gửi được hoàn tiền.'); }
    finally { setBusy(false); onBusy(false); }
  }

  if (!active || (!client && !freelancer)) return null;
  return <section className="settlement-document" aria-label="Hoàn tiền escrow theo thỏa thuận">
    <SectionHeading title="Hoàn tiền theo thỏa thuận" aside="Hai chữ ký ví" />
    <p>Hoàn toàn bộ {escrow.amountBaseUnits ? (Number(escrow.amountBaseUnits) / 1_000_000).toFixed(6) : 'số token'} về ví Client <code>{escrow.clientWallet}</code>. Hai bên phải ký cùng một giao dịch trước khi blockhash hết hạn.</p>
    {escrow.status === 'Submitted' && <p>Nếu hạn review đã qua, lệnh giải ngân có thể được xác nhận trước giao dịch hoàn tiền này.</p>}
    <p>Mint: <code>{escrow.mint}</code> · Vault: <code>{escrow.vaultAddress}</code></p>
    <label><input type="checkbox" checked={agree} disabled={busy} onChange={event => setAgree(event.target.checked)} /> Tôi đồng ý hoàn toàn bộ token cho Client.</label>
    <ActionGroup>
      {client && <button className="button button-secondary" disabled={busy || !agree} onClick={() => void clientPropose()}>Ký đề nghị hoàn tiền</button>}
      {freelancer && <button className="button button-secondary" disabled={busy || !agree || !pending?.partialTransaction} onClick={() => void freelancerFinish()}>Ký và gửi hoàn tiền</button>}
    </ActionGroup>
    {freelancer && !pending && <p>Chưa có đề nghị đã ký từ Client.</p>}
    {notice && <p role="status">{notice}</p>}{error && <p role="alert" className="form-error">{error}</p>}
  </section>;
}
