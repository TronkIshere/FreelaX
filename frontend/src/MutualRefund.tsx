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
      setNotice('Khách hàng đã xác nhận. Đang chờ người làm xác nhận hoàn tiền.');
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
      setNotice('Đã gửi yêu cầu hoàn tiền. Đang chờ xác nhận.');
      await onRefresh();
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Chưa gửi được hoàn tiền.'); }
    finally { setBusy(false); onBusy(false); }
  }

  if (!active || (!client && !freelancer)) return null;
  return <section className="settlement-document" aria-label="Hoàn tiền theo thỏa thuận">
    <SectionHeading title="Hoàn tiền theo thỏa thuận" />
    <p>Hoàn toàn bộ số tiền của công việc về cho khách hàng. Hai bên cần xác nhận yêu cầu này.</p>
    {escrow.status === 'Submitted' && <p>Nếu đã hết hạn duyệt, khoản thanh toán có thể hoàn tất trước yêu cầu hoàn tiền.</p>}
    <label><input type="checkbox" checked={agree} disabled={busy} onChange={event => setAgree(event.target.checked)} /> Tôi đồng ý hoàn toàn bộ số tiền cho khách hàng.</label>
    <ActionGroup>
      {client && <button className="button button-secondary" disabled={busy || !agree} onClick={() => void clientPropose()}>Xác nhận hoàn tiền</button>}
      {freelancer && <button className="button button-secondary" disabled={busy || !agree || !pending?.partialTransaction} onClick={() => void freelancerFinish()}>Đồng ý hoàn tiền</button>}
    </ActionGroup>
    {freelancer && !pending && <p>Đang chờ khách hàng gửi yêu cầu hoàn tiền.</p>}
    {notice && <p role="status">{notice}</p>}{error && <p role="alert" className="form-error">{error}</p>}
  </section>;
}
