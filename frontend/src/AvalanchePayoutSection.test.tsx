// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from './api';
import { AvalancheEscrowPanel } from './AvalancheEscrowPanel';
import { AvalanchePayoutSection, PAYOUT_STATUS_TEXT, UNSUPPORTED_REASON_TEXT } from './AvalanchePayoutSection';
import type { AvalancheContractView, AvalanchePayoutView } from './avalancheTypes';
import type { EvmWallet } from './evmWallet';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const HASH = '0x' + 'a'.repeat(64);
const FROM = '0x' + '1'.repeat(40);
const TOKEN = '0x' + 'b'.repeat(40);
const prepared = { chainId: 31337, to: TOKEN, data: '0xa9059cbb' + '0'.repeat(128), value: '0' };
const payout = (over: Partial<AvalanchePayoutView> = {}): AvalanchePayoutView => ({
  id: 'p1', contractId: 'c', networkId: 'avalanche-local', environment: 'LOCAL_SIMULATION', chainId: 31337, tokenAddress: TOKEN,
  amountBaseUnits: '5000000', fromAddress: FROM, depositAddress: '0x' + 'd'.repeat(40), partnerId: 'local-mock', partnerSimulated: true,
  partnerReportedFee: null, partnerReportedRate: null, status: 'AWAITING_USER_TRANSFER', transferTxHash: null, transferChainState: null,
  transferMatch: null, onChainTransferConfirmed: false, transferConfirmedAt: null, fiatPayoutConfirmed: false, fiatReference: null,
  fiatConfirmedAt: null, failureReason: null, taxTreatment: 'NOT_DEFINED', notice: 'Demonstration only', preparedTransfer: prepared, ...over,
});

let host: HTMLDivElement;
let root: Root;
beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); });

const button = (text: string) => [...host.querySelectorAll('button')].find(b => b.textContent === text);
const mount = (walletFactory?: () => EvmWallet) => act(async () => root.render(
  <AvalanchePayoutSection contractId="c" expectedChainId={31337} simulated walletFactory={walletFactory} />));

describe('Avalanche payout section (non-custodial, Phase 3)', () => {
  it('shows an explicit unsupported-rail result and creates nothing', async () => {
    vi.spyOn(api, 'avalanchePayout').mockResolvedValue(null);
    const request = vi.spyOn(api, 'avalanchePayoutRequest').mockResolvedValue({ outcome: 'UNSUPPORTED_RAIL', reason: 'PARTNER_NOT_CONFIGURED', payout: null });
    await mount();
    await act(async () => { button('Yêu cầu chi trả')!.click(); });
    expect(request).toHaveBeenCalledWith('c');
    expect(host.querySelector('[data-testid="payout-unsupported"]')?.textContent).toContain(UNSUPPORTED_REASON_TEXT.PARTNER_NOT_CONFIGURED);
    expect(button('Chuyển USDC mô phỏng bằng ví')).toBeUndefined();
  });

  it('sends exactly the backend-built transfer from the released wallet and records its hash', async () => {
    vi.spyOn(api, 'avalanchePayout').mockResolvedValue(payout());
    const record = vi.spyOn(api, 'avalanchePayoutTransfer').mockResolvedValue(payout({ status: 'TRANSFER_SUBMITTED', transferTxHash: HASH, preparedTransfer: null }));
    const wallet = { connect: vi.fn(async () => ({ address: FROM.toUpperCase().replace('0X', '0x'), chainId: 31337 })), sendPrepared: vi.fn(async () => HASH) };
    await mount(() => wallet as unknown as EvmWallet);
    expect(host.textContent).toContain(PAYOUT_STATUS_TEXT.AWAITING_USER_TRANSFER);
    expect(host.textContent).toContain('5 USDC mô phỏng');
    expect(host.textContent).toContain('Đối tác mô phỏng');
    await act(async () => { button('Chuyển USDC mô phỏng bằng ví')!.click(); });
    expect(wallet.sendPrepared).toHaveBeenCalledWith({ chainId: 31337, to: TOKEN, data: prepared.data, value: '0' }, expect.any(String), 31337);
    expect(record).toHaveBeenCalledWith('p1', HASH);
    expect(host.textContent).toContain(PAYOUT_STATUS_TEXT.TRANSFER_SUBMITTED);
  });

  it('refuses to send from a wallet other than the one the escrow released to', async () => {
    vi.spyOn(api, 'avalanchePayout').mockResolvedValue(payout());
    const record = vi.spyOn(api, 'avalanchePayoutTransfer');
    const wallet = { connect: vi.fn(async () => ({ address: '0x' + '9'.repeat(40), chainId: 31337 })), sendPrepared: vi.fn(async () => HASH) };
    await mount(() => wallet as unknown as EvmWallet);
    await act(async () => { button('Chuyển USDC mô phỏng bằng ví')!.click(); });
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('đúng ví');
    expect(wallet.sendPrepared).not.toHaveBeenCalled();
    expect(record).not.toHaveBeenCalled();
  });

  it('refuses a prepared transfer for another chain', async () => {
    vi.spyOn(api, 'avalanchePayout').mockResolvedValue(payout({ preparedTransfer: { ...prepared, chainId: 43113 } }));
    const wallet = { connect: vi.fn(), sendPrepared: vi.fn() };
    await mount(() => wallet as unknown as EvmWallet);
    await act(async () => { button('Chuyển USDC mô phỏng bằng ví')!.click(); });
    expect(host.querySelector('[role="alert"]')).not.toBeNull();
    expect(wallet.connect).not.toHaveBeenCalled();
  });

  it('shows the on-chain and fiat confirmations separately', async () => {
    vi.spyOn(api, 'avalanchePayout').mockResolvedValue(payout({ status: 'FIAT_PAYOUT_PENDING', onChainTransferConfirmed: true,
      transferTxHash: HASH, preparedTransfer: null }));
    const refresh = vi.spyOn(api, 'avalanchePayoutRefresh').mockResolvedValue(payout({ status: 'FIAT_PAYOUT_CONFIRMED',
      onChainTransferConfirmed: true, fiatPayoutConfirmed: true, transferTxHash: HASH, preparedTransfer: null }));
    await mount();
    expect(host.querySelector('[data-testid="onchain-confirmed"]')?.textContent).toBe('Đã xác nhận');
    expect(host.querySelector('[data-testid="fiat-confirmed"]')?.textContent).toBe('Chưa');
    expect(host.textContent).toContain('không có nghĩa là đã chi trả');
    await act(async () => { button('Làm mới chi trả')!.click(); });
    expect(refresh).toHaveBeenCalledWith('p1');
    expect(host.querySelector('[data-testid="fiat-confirmed"]')?.textContent).toBe('Đã xác nhận');
    expect(button('Làm mới chi trả')).toBeUndefined();
  });
});

describe('panel placement', () => {
  const view = (status: string): AvalancheContractView => ({
    networkId: 'avalanche-local', avalancheEnabled: true,
    environment: { environment: 'LOCAL_SIMULATION', chainId: 31337, simulated: true, explorerBaseUrl: null, backendSignersAvailable: false },
    escrowId: '0x1', chain: { status } as AvalancheContractView['chain'], transactions: [], notice: null,
  });
  const user = { id: 'u', displayName: 'U', email: 'u@example.test', userType: 'FREELANCER' as const };

  it('shows the simulated Solana-rail fee and taxable income, and the old wording when none exists', async () => {
    vi.spyOn(api, 'avalanchePayout').mockResolvedValue(payout({ taxTreatment: 'SOLANA_RAIL_RULES_SIMULATED',
      platformFeeUsd: 0.15, taxUsdToVndRate: 25432.46, taxRateSource: 'LIVE_OPEN_ER_API', taxableAmountVnd: 127162 }));
    await mount();
    expect(host.querySelector('[data-testid="platform-fee"]')?.textContent).toBe('0.15 USD');
    expect(host.querySelector('[data-testid="taxable-vnd"]')?.textContent).toContain('VND');
    expect(host.textContent).toContain('không trừ vào số tiền ví của bạn chuyển');
    act(() => root.unmount()); root = createRoot(host);
    vi.spyOn(api, 'avalanchePayout').mockResolvedValue(payout());
    await mount();
    expect(host.querySelector('[data-testid="platform-fee"]')).toBeNull();
    expect(host.textContent).toContain('Thuế cho chi trả Avalanche chưa được xác định');
  });

  it('appears only for the freelancer of a released escrow', async () => {
    vi.spyOn(api, 'evmWalletCurrent').mockResolvedValue({ walletAddress: FROM });
    vi.spyOn(api, 'avalanchePayout').mockResolvedValue(null);
    const status = vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view('RELEASED'));
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient={false} isFreelancer />));
    expect(host.querySelector('[data-testid="avalanche-payout"]')).not.toBeNull();
    act(() => root.unmount());
    root = createRoot(host);
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient isFreelancer={false} />));
    expect(host.querySelector('[data-testid="avalanche-payout"]')).toBeNull();
    act(() => root.unmount());
    root = createRoot(host);
    status.mockResolvedValue(view('SUBMITTED'));
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient={false} isFreelancer />));
    expect(host.querySelector('[data-testid="avalanche-payout"]')).toBeNull();
  });
});
