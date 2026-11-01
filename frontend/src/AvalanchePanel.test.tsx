// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from './api';
import { AvalancheEscrowPanel } from './AvalancheEscrowPanel';
import { STATE_TEXT, TRANSACTION_STATES, environmentBadge, explorerTxUrl, stateText } from './avalanchePresentation';
import type { AvalancheChainTransaction, AvalancheContractView } from './avalancheTypes';
import type { EvmWallet } from './evmWallet';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const HASH = '0x' + 'a'.repeat(64);
const user = { id: 'u', displayName: 'U', email: 'u@example.test', userType: 'CLIENT' as const };
const tx = (over: Partial<AvalancheChainTransaction> = {}): AvalancheChainTransaction => ({
  id: 1, contractId: 'c', milestoneId: 'm', networkId: 'AVALANCHE', environment: 'LOCAL_SIMULATION', chainId: 31337,
  escrowId: '0x1', action: 'fund', operationKey: 'k', txHash: HASH, status: 'PENDING', errorClass: null,
  businessApplied: false, createdAt: '', updatedAt: '', ...over,
});
const view = (over: Partial<AvalancheContractView> = {}): AvalancheContractView => ({
  networkId: 'AVALANCHE', avalancheEnabled: true,
  environment: { environment: 'LOCAL_SIMULATION', chainId: 31337, simulated: true, explorerBaseUrl: null, backendSignersAvailable: true },
  escrowId: '0x1', chain: null, transactions: [], notice: null, ...over,
});

describe('presentation', () => {
  it('describes each of the ten states and never calls an unfinished one final', () => {
    expect(TRANSACTION_STATES).toHaveLength(10);
    for (const s of TRANSACTION_STATES) expect(STATE_TEXT[s].label.length).toBeGreaterThan(0);
    expect(stateText('CONFIRMED').detail).toMatch(/chưa có thay đổi nghiệp vụ/);
    expect(stateText('UNKNOWN').detail).toMatch(/không gửi lại/);
    expect(stateText('SURPRISE').label).toBe('Chưa xác định');
  });
  it('labels simulated and Fuji environments distinctly without real-money wording', () => {
    expect(environmentBadge({ environment: 'LOCAL_SIMULATION', simulated: true })).toBe('Mô phỏng');
    expect(environmentBadge({ environment: 'AVALANCHE_FUJI', simulated: false })).toBe('Giao dịch testnet (Fuji)');
  });
  it('builds explorer links only for valid hashes over http(s)', () => {
    expect(explorerTxUrl('https://testnet.snowtrace.io/', HASH)).toBe('https://testnet.snowtrace.io/tx/' + HASH);
    expect(explorerTxUrl(null, HASH)).toBeNull();
    expect(explorerTxUrl('javascript:alert(1)', HASH)).toBeNull();
    expect(explorerTxUrl('https://x.test', '0x12')).toBeNull();
  });
});

describe('AvalancheEscrowPanel', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); });
  const mount = async (isClient = true, isFreelancer = false, wallet?: () => EvmWallet) => {
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient={isClient} isFreelancer={isFreelancer} walletFactory={wallet} />));
  };

  it('renders nothing when the gateway is disabled', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ avalancheEnabled: false, environment: null }));
    await mount();
    expect(host.innerHTML).toBe('');
  });

  it('shows the simulation banner and badge, with no mainnet or real-money claim', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ transactions: [tx({ status: 'FINALIZED' })] }));
    await mount();
    expect(host.querySelector('.avalanche-banner')?.textContent).toMatch(/mô phỏng/i);
    expect(host.textContent).toContain('Đã chốt');
    expect(host.textContent).not.toMatch(/mainnet|tiền thật.*thanh toán/i);
  });

  it('shows Fuji transactions as testnet with an explorer link', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({
      environment: { environment: 'AVALANCHE_FUJI', chainId: 43113, simulated: false, explorerBaseUrl: 'https://testnet.snowtrace.io', backendSignersAvailable: false },
      transactions: [tx({ environment: 'AVALANCHE_FUJI', chainId: 43113, status: 'CONFIRMED' })],
    }));
    await mount();
    expect(host.textContent).toContain('Giao dịch testnet (Fuji)');
    expect(host.querySelector('a')?.getAttribute('href')).toBe('https://testnet.snowtrace.io/tx/' + HASH);
    expect(host.textContent).not.toContain('tài khoản mô phỏng');
    expect(host.querySelector('li')?.textContent).toContain('chưa có thay đổi nghiệp vụ');
  });

  it('offers a simulated-account action only in the simulation', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view());
    await mount();
    expect(host.textContent).toContain('Nạp tiền ký quỹ bằng tài khoản mô phỏng');
  });

  it('funds with the wallet using only backend-prepared calldata and records the hash', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view());
    vi.spyOn(api, 'avalancheRefresh').mockResolvedValue(view());
    const prepared = { action: 'fund', escrowId: '0x1', transactions: [{ to: '0x' + '2'.repeat(40), data: '0xabcd', value: '0', chainId: 31337 }] };
    vi.spyOn(api, 'avalanchePrepare').mockResolvedValue(prepared);
    const record = vi.spyOn(api, 'avalancheRecordTransaction').mockResolvedValue(tx());
    const wallet = {
      connect: vi.fn(async () => ({ address: '0x' + '1'.repeat(40), chainId: 31337 })),
      sendPrepared: vi.fn(async () => HASH),
    } as unknown as EvmWallet;
    await mount(true, false, () => wallet);
    const button = [...host.querySelectorAll('button')].find(b => b.textContent === 'Nạp tiền ký quỹ bằng ví')!;
    await act(async () => { button.click(); });
    expect(wallet.connect).toHaveBeenCalledWith(31337);
    expect(wallet.sendPrepared).toHaveBeenCalledWith(prepared.transactions[0], '0x' + '1'.repeat(40), 31337);
    expect(record).toHaveBeenCalledWith('c', 'fund', HASH, expect.objectContaining({ idempotencyKey: expect.any(String) }));
  });

  it('surfaces a wallet refusal without recording anything', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view());
    vi.spyOn(api, 'avalanchePrepare').mockResolvedValue({ action: 'fund', escrowId: '0x1', transactions: [] });
    const record = vi.spyOn(api, 'avalancheRecordTransaction');
    const { WalletError } = await import('./evmWallet');
    const wallet = { connect: vi.fn(async () => { throw new WalletError('Wallet connection was declined.', 'USER_REJECTED'); }) } as unknown as EvmWallet;
    await mount(true, false, () => wallet);
    const button = [...host.querySelectorAll('button')].find(b => b.textContent === 'Nạp tiền ký quỹ bằng ví')!;
    await act(async () => { button.click(); });
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('declined');
    expect(record).not.toHaveBeenCalled();
  });

  it('lets only the client enable the rail', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ networkId: null }));
    await mount(false, true);
    expect(host.textContent).toContain('chưa dùng ký quỹ Avalanche');
    expect(host.querySelectorAll('button')).toHaveLength(0);
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient isFreelancer={false} />));
    expect(host.textContent).toContain('Dùng ký quỹ Avalanche');
  });
});
