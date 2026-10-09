// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, expect, it, vi } from 'vitest';
import { api } from './api';
import { WalletLinkPanel } from './WalletLink';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllEnvs(); });

it('connects automatically and shows only the success message', async () => {
  vi.stubEnv('VITE_SOLANA_CLUSTER', 'localnet');
  vi.spyOn(api, 'autoSolanaWallet').mockResolvedValue({ walletAddress: 'D4GygZ5dmnMb8QJhgKSqgmDReQCbfoz39pKEv3W7zzNS' });
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  try {
    await act(async () => root.render(<WalletLinkPanel />));
    expect(api.autoSolanaWallet).toHaveBeenCalledOnce();
    expect(host.textContent).toBe('Đã kết nối ví');
    expect(host.textContent).not.toContain('D4Gyg');
    expect(host.textContent).not.toContain('localnet');
  } finally {
    act(() => root.unmount());
    host.remove();
  }
});
