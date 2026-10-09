import { useEffect, useState } from 'react';
import { api } from './api';
import { connectSolanaWallet } from './escrowWallet';

export function WalletLinkPanel() {
  const [bound, setBound] = useState<string | null>(null);
  const [connected, setConnected] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [loaded, setLoaded] = useState(false);
  useEffect(() => {
    let active = true;
    api.boundSolanaWallet().then(value => { if (active) { setBound(value?.walletAddress || null); setLoaded(true); } })
      .catch(() => { if (active) setError('Chưa đọc được ví đã đăng ký.'); });
    return () => { active = false; };
  }, []);
  async function connectAndLink() {
    if (busy) return;
    setBusy(true); setError('');
    try {
      const { wallet, address } = await connectSolanaWallet();
      setConnected(address);
      if (bound && bound !== address) throw new Error('Ví kết nối khác ví đã đăng ký cho tài khoản.');
      if (!bound) {
        if (!wallet.signMessage) throw new Error('Ví này không hỗ trợ ký thông điệp xác minh.');
        const challenge = await api.solanaWalletChallenge(address);
        const result = await wallet.signMessage(new TextEncoder().encode(challenge.message));
        let bytes = '';
        for (const byte of result.signature) bytes += String.fromCharCode(byte);
        const verified = await api.verifySolanaWallet(challenge.challengeId, btoa(bytes));
        setBound(verified.walletAddress);
      }
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Không xác minh được ví.'); }
    finally { setBusy(false); }
  }
  return <section className="wallet-link-panel" aria-label="Ví Solana của tài khoản">
    <p>Mạng Solana: <code>{import.meta.env.VITE_SOLANA_CLUSTER || 'Chưa cấu hình nhãn mạng'}</code></p>
    <p>Ví Solana đã đăng ký: <code>{loaded ? bound || 'Chưa có' : 'Đang đối chiếu…'}</code></p>
    {connected && <p>Ví đang kết nối: <code>{connected}</code></p>}
    <button className="text-button" disabled={busy || !loaded} onClick={() => void connectAndLink()}>
      {busy ? 'Đang xác minh…' : bound ? 'Kết nối ví đã đăng ký' : 'Kết nối và xác minh ví'}</button>
    {error && <p role="alert" className="form-error">{error}</p>}
  </section>;
}
