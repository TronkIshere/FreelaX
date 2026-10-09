import { useEffect, useState } from 'react';
import { connectSolanaWallet } from './escrowWallet';

export function WalletLinkPanel() {
  const [status, setStatus] = useState<'connecting' | 'connected' | 'error'>('connecting');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setStatus('connecting');
    void connectSolanaWallet().then(() => {
      if (active) setStatus('connected');
    }).catch(() => {
      if (active) setStatus('error');
    });
    return () => { active = false; };
  }, [attempt]);

  return <section className="wallet-link-panel" aria-label="Kết nối ví">
    <p role="status">{status === 'connected' ? 'Đã kết nối ví' : status === 'connecting' ? 'Đang kết nối ví…' : 'Chưa kết nối được ví.'}</p>
    {status === 'error' && <button className="text-button" type="button" onClick={() => setAttempt(value => value + 1)}>Thử lại</button>}
  </section>;
}
