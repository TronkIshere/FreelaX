// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from './api';
import { AvalancheEscrowPanel } from './AvalancheEscrowPanel';
import { STATE_TEXT, TRANSACTION_STATES, chainInstant, contractChainLabel, environmentBadge, explorerTxUrl, stateText, tokenLabel, usdcFromBaseUnits } from './avalanchePresentation';
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

describe('chain-state display helpers', () => {
  it('formats USDC base units exactly, without floating point', () => {
    expect(usdcFromBaseUnits('100000000')).toBe('100');
    expect(usdcFromBaseUnits('1500000')).toBe('1.5');
    expect(usdcFromBaseUnits('1')).toBe('0.000001');
    expect(usdcFromBaseUnits('0')).toBe('0');
    expect(usdcFromBaseUnits('123456789012345678901')).toBe('123456789012345.678901');
    expect(usdcFromBaseUnits('-1')).toBeNull();
    expect(usdcFromBaseUnits('1e6')).toBeNull();
    expect(usdcFromBaseUnits(null)).toBeNull();
  });
  it('labels the token per environment and shows unset deadlines as a dash', () => {
    expect(tokenLabel(true)).toBe('USDC mô phỏng');
    expect(tokenLabel(false)).toBe('Testnet USDC');
    expect(chainInstant(0)).toBe('—');
  });
});

describe('contractChainLabel (A7)', () => {
  it('labels Avalanche only from the network id, Solana rails as legacy, and mock rails not at all', () => {
    expect(contractChainLabel({ paymentNetworkId: 'avalanche-local', paymentRail: 'UNIFIED_USDC_PAYOUT' })).toBe('Ký quỹ Avalanche');
    expect(contractChainLabel({ paymentNetworkId: null, paymentRail: 'SOLANA_ESCROW' })).toBe('Solana (tham chiếu cũ)');
    expect(contractChainLabel({ paymentRail: 'UNIFIED_USDC_PAYOUT' })).toBe('Solana (tham chiếu cũ)');
    expect(contractChainLabel({ paymentRail: 'PARTNER_ESCROW_MOCK' })).toBeNull();
    expect(contractChainLabel({ paymentRail: 'SIMULATED' })).toBeNull();
    expect(contractChainLabel(null)).toBeNull();
  });
});

describe('AvalancheEscrowPanel', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => {
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    vi.spyOn(api, 'evmWalletCurrent').mockResolvedValue({ walletAddress: '0x' + '1'.repeat(40) });
  });
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
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('từ chối');
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

  const button = (text: string) => [...host.querySelectorAll('button')].find(b => b.textContent === text);
  const fakeWallet = () => ({
    connect: vi.fn(async () => ({ address: '0x' + '1'.repeat(40), chainId: 31337 })),
    sendPrepared: vi.fn(async () => HASH),
  }) as unknown as EvmWallet;

  it('anchors the recorded submission with the freelancer wallet (A1 submit)', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view());
    vi.spyOn(api, 'avalancheRefresh').mockResolvedValue(view());
    const prepare = vi.spyOn(api, 'avalanchePrepare').mockResolvedValue({ action: 'submit', escrowId: '0x1',
      transactions: [{ to: '0x' + '2'.repeat(40), data: '0xab', value: '0', chainId: 31337 }] });
    const record = vi.spyOn(api, 'avalancheRecordTransaction').mockResolvedValue(tx({ action: 'submit' }));
    const wallet = fakeWallet();
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient={false} isFreelancer
      submissions={[{ id: 's2', version: 2, status: 'SUBMITTED' }]} walletFactory={() => wallet} />));
    await act(async () => { button('Ghi nhận bàn giao bằng ví')!.click(); });
    expect(prepare).toHaveBeenCalledWith('c', 'submit', expect.objectContaining({ submissionId: 's2', idempotencyKey: expect.any(String) }));
    expect(record).toHaveBeenCalledWith('c', 'submit', HASH, expect.objectContaining({ submissionId: 's2' }));
  });

  it('requires a date before an extension request and sends it as epoch seconds', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view());
    vi.spyOn(api, 'avalancheRefresh').mockResolvedValue(view());
    const execute = vi.spyOn(api, 'avalancheExecute').mockResolvedValue(tx({ action: 'extension-request' }));
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient={false} isFreelancer />));
    const simulated = () => button('Xin gia hạn bằng tài khoản mô phỏng')!;
    expect(simulated().disabled).toBe(true);
    const input = host.querySelector<HTMLInputElement>('[data-action="extension-request"] input[type="datetime-local"]')!;
    await act(async () => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
      setter.call(input, '2026-11-10T12:00');
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    expect(simulated().disabled).toBe(false);
    await act(async () => { simulated().click(); });
    expect(execute).toHaveBeenCalledWith('c', 'extension-request', expect.objectContaining({
      newDeliveryDueAt: Math.floor(new Date('2026-11-10T12:00').getTime() / 1000) }));
  });

  it('lets a non-participant arbiter settle a recorded decision (A1 resolve)', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view());
    vi.spyOn(api, 'avalancheRefresh').mockResolvedValue(view());
    const execute = vi.spyOn(api, 'avalancheExecute').mockResolvedValue(tx({ action: 'resolve' }));
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient={false} isFreelancer={false} isArbiter
      dispute={{ disputeId: 'd', status: 'DECISION_PENDING_RELEASE', openedBy: 'x' }} />));
    expect([...host.querySelectorAll('[data-action]')].map(e => e.getAttribute('data-action'))).toEqual(['resolve']);
    await act(async () => { button('Thực thi quyết định tranh chấp bằng tài khoản mô phỏng')!.click(); });
    expect(execute).toHaveBeenCalledWith('c', 'resolve', expect.objectContaining({ disputeId: 'd' }));
  });

  it('shows amount and revisions read from the chain snapshot with the environment token label', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ chain: { status: 'Funded', client: '0x1', freelancer: '0x2',
      amountBaseUnits: '250000000', maxRevisions: 2, revisionsUsed: 1, deliveryDueAt: 0, reviewDueAt: 0 } }));
    await mount();
    const facts = host.querySelector('.avalanche-chain-facts')!.textContent!;
    expect(facts).toContain('250 USDC mô phỏng');
    expect(facts).toContain('1/2');
  });

  it('P3-C4: reports a stuck escrow plainly, names the blocked paths and offers no workaround', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ issuerAlerts: [
      { code: 'FREELANCER_BLACKLISTED', address: '0x' + 'f'.repeat(40), blocks: ['release', 'resolve:release'] },
      { code: 'TOKEN_PAUSED', address: null, blocks: ['release', 'resolve', 'mutual-refund'] }] }));
    await mount();
    const box = host.querySelector('[data-testid="issuer-alerts"]')!;
    expect(box.getAttribute('role')).toBe('alert');
    expect(box.textContent).toContain('Freelancer');
    expect(box.textContent).toContain('Giải ngân');
    expect(box.textContent).toContain('Không có rút tiền đặc quyền');
    expect(box.querySelectorAll('button')).toHaveLength(0);
  });

  it('shows no stuck-escrow box when nothing blocks the escrow', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ issuerAlerts: [] }));
    await mount();
    expect(host.querySelector('[data-testid="issuer-alerts"]')).toBeNull();
  });

  it('renders nothing for the arbiter on a contract that is not on the Avalanche rail', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ networkId: null }));
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient={false} isFreelancer={false} isArbiter />));
    expect(host.innerHTML).toBe('');
  });

  it('offers mutual refund by wallet and, in the simulation only, by simulated accounts', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view());
    await mount();
    expect(button('Hoàn tiền thỏa thuận bằng ví')).toBeDefined();
    expect(button('Hoàn tiền thỏa thuận bằng tài khoản mô phỏng')).toBeDefined();
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({
      environment: { environment: 'AVALANCHE_FUJI', chainId: 43113, simulated: false, explorerBaseUrl: null, backendSignersAvailable: false } }));
    act(() => root.unmount()); root = createRoot(host);
    await mount();
    expect(button('Hoàn tiền thỏa thuận bằng ví')).toBeDefined();
    expect(host.textContent).not.toContain('tài khoản mô phỏng');
  });

  const typed = JSON.stringify({ domain: { chainId: 31337, verifyingContract: '0x' + '3'.repeat(40) }, primaryType: 'MutualRefund' });
  const SIG = '0x' + '5'.repeat(130);

  it('F13: the first signer stores a signature over the prepared deadline and sends nothing', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view());
    vi.spyOn(api, 'avalancheRefresh').mockResolvedValue(view());
    const prepare = vi.spyOn(api, 'avalanchePrepare').mockResolvedValue({ action: 'mutual-refund', escrowId: '0x1', transactions: [],
      typedData: typed, typedDataDigest: '0x' + 'd'.repeat(64), authorizationDeadline: 1_900_000_000 });
    const store = vi.spyOn(api, 'avalancheMutualRefundSignature').mockResolvedValue(view({
      mutualRefund: { authorizationDeadline: 1_900_000_000, typedDataDigest: '0x' + 'd'.repeat(64), clientSigned: true, freelancerSigned: false } }));
    const wallet = { ...fakeWallet(), signTypedData: vi.fn(async () => SIG) } as unknown as EvmWallet;
    await mount(true, false, () => wallet);
    await act(async () => { button('Hoàn tiền thỏa thuận bằng ví')!.click(); });
    expect(prepare).toHaveBeenCalledTimes(1);
    expect(wallet.signTypedData).toHaveBeenCalledWith(typed, '0x' + '1'.repeat(40), 31337);
    expect(store).toHaveBeenCalledWith('c', { authorizationDeadline: 1_900_000_000, typedDataDigest: '0x' + 'd'.repeat(64), signature: SIG });
    expect(wallet.sendPrepared).not.toHaveBeenCalled();
  });

  it('F13: the second signer reuses the pending deadline, then sends the refund prepared with both signatures', async () => {
    const pending = { authorizationDeadline: 1_900_000_000, typedDataDigest: '0x' + 'd'.repeat(64), clientSigned: true, freelancerSigned: false };
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ mutualRefund: pending }));
    vi.spyOn(api, 'avalancheRefresh').mockResolvedValue(view());
    const prepare = vi.spyOn(api, 'avalanchePrepare')
      .mockResolvedValueOnce({ action: 'mutual-refund', escrowId: '0x1', transactions: [], typedData: typed,
        typedDataDigest: pending.typedDataDigest, authorizationDeadline: pending.authorizationDeadline })
      .mockResolvedValueOnce({ action: 'mutual-refund', escrowId: '0x1',
        transactions: [{ to: '0x' + '2'.repeat(40), data: '0xab', value: '0', chainId: 31337 }] });
    vi.spyOn(api, 'avalancheMutualRefundSignature').mockResolvedValue(view({ mutualRefund: { ...pending, freelancerSigned: true } }));
    const record = vi.spyOn(api, 'avalancheRecordTransaction').mockResolvedValue(tx({ action: 'mutual-refund' }));
    const wallet = { ...fakeWallet(), signTypedData: vi.fn(async () => SIG) } as unknown as EvmWallet;
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient={false} isFreelancer walletFactory={() => wallet} />));
    expect(host.querySelector('[data-testid="mutual-refund-state"]')?.textContent).toContain('Client đã ký');
    await act(async () => { button('Hoàn tiền thỏa thuận bằng ví')!.click(); });
    expect(prepare.mock.calls[0][2]).toEqual({ authorizationDeadline: 1_900_000_000 });
    expect(wallet.sendPrepared).toHaveBeenCalledTimes(1);
    expect(record).toHaveBeenCalledWith('c', 'mutual-refund', HASH, expect.objectContaining({ idempotencyKey: expect.any(String) }));
  });

  const disputeOpen = { disputeId: 'd1', status: 'UNDER_REVIEW' as const, openedBy: 'other' };
  const disputedChain = { status: 'DISPUTED', client: '0x' + 'c'.repeat(40), freelancer: '0x' + 'f'.repeat(40), amountBaseUnits: '5000000',
    maxRevisions: 2, revisionsUsed: 0, deliveryDueAt: 0, reviewDueAt: 0 };

  it('R12: settlement is offered only while the escrow is Disputed on chain, for both outcomes', async () => {
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ chain: { ...disputedChain, status: 'FUNDED' } }));
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient isFreelancer={false} dispute={disputeOpen} />));
    expect(host.textContent).not.toContain('Tất toán tranh chấp');
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ chain: disputedChain }));
    act(() => root.unmount()); root = createRoot(host);
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient isFreelancer={false} dispute={disputeOpen} />));
    expect(button('Tất toán tranh chấp theo thỏa thuận (giải ngân cho Freelancer) bằng ví')).toBeDefined();
    expect(button('Tất toán tranh chấp theo thỏa thuận (hoàn cho Client) bằng ví')).toBeDefined();
    expect(button('Tất toán tranh chấp theo thỏa thuận (hoàn cho Client) bằng tài khoản mô phỏng')).toBeDefined();
  });

  it('R12: the first signer stores a signature for the chosen outcome and sends nothing; the second sends the settlement', async () => {
    const digest = '0x' + 'e'.repeat(64);
    const settleTyped = JSON.stringify({ domain: { chainId: 31337 }, primaryType: 'MutualSettlement' });
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ chain: disputedChain }));
    vi.spyOn(api, 'avalancheRefresh').mockResolvedValue(view({ chain: disputedChain }));
    const prepare = vi.spyOn(api, 'avalanchePrepare').mockResolvedValue({ action: 'mutual-settle', escrowId: '0x1', transactions: [],
      typedData: settleTyped, typedDataDigest: digest, authorizationDeadline: 1_900_000_000 });
    const pending = { releaseToFreelancer: false, authorizationDeadline: 1_900_000_000, typedDataDigest: digest, clientSigned: true, freelancerSigned: false };
    const store = vi.spyOn(api, 'avalancheMutualSettlementSignature').mockResolvedValue(view({ chain: disputedChain, mutualSettlements: [pending] }));
    const wallet = { ...fakeWallet(), signTypedData: vi.fn(async () => SIG) } as unknown as EvmWallet;
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient isFreelancer={false} dispute={disputeOpen} walletFactory={() => wallet} />));
    await act(async () => { button('Tất toán tranh chấp theo thỏa thuận (hoàn cho Client) bằng ví')!.click(); });
    expect(prepare.mock.calls[0][1]).toBe('mutual-settle');
    expect(prepare.mock.calls[0][2]).toEqual({ disputeId: 'd1', releaseToFreelancer: false });
    expect(store).toHaveBeenCalledWith('c', { disputeId: 'd1', releaseToFreelancer: false, authorizationDeadline: 1_900_000_000,
      typedDataDigest: digest, signature: SIG });
    expect(wallet.sendPrepared).not.toHaveBeenCalled();

    // the freelancer completes the same proposal (same outcome and deadline) and sends the settlement
    act(() => root.unmount()); root = createRoot(host);
    prepare.mockReset();
    prepare.mockResolvedValueOnce({ action: 'mutual-settle', escrowId: '0x1', transactions: [], typedData: settleTyped,
      typedDataDigest: digest, authorizationDeadline: 1_900_000_000 })
      .mockResolvedValueOnce({ action: 'mutual-settle', escrowId: '0x1', transactions: [{ to: '0x' + '2'.repeat(40), data: '0xab', value: '0', chainId: 31337 }] });
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view({ chain: disputedChain, mutualSettlements: [pending] }));
    store.mockResolvedValue(view({ chain: disputedChain, mutualSettlements: [{ ...pending, freelancerSigned: true }] }));
    const record = vi.spyOn(api, 'avalancheRecordTransaction').mockResolvedValue(tx({ action: 'mutual-settle' }));
    await act(async () => root.render(<AvalancheEscrowPanel contractId="c" user={user} isClient={false} isFreelancer dispute={disputeOpen} walletFactory={() => wallet} />));
    expect(host.querySelector('[data-testid="mutual-settlement-state"]')?.textContent).toContain('Client đã ký');
    await act(async () => { button('Tất toán tranh chấp theo thỏa thuận (hoàn cho Client) bằng ví')!.click(); });
    expect(prepare.mock.calls[0][2]).toEqual({ disputeId: 'd1', releaseToFreelancer: false, authorizationDeadline: 1_900_000_000 });
    expect(wallet.sendPrepared).toHaveBeenCalledTimes(1);
    expect(record).toHaveBeenCalledWith('c', 'mutual-settle', HASH, expect.objectContaining({ disputeId: 'd1', releaseToFreelancer: false }));
  });

  it('links an EVM wallet with a personal_sign challenge when none is linked', async () => {
    vi.spyOn(api, 'evmWalletCurrent').mockResolvedValue(null);
    vi.spyOn(api, 'avalancheStatus').mockResolvedValue(view());
    vi.spyOn(api, 'avalancheRefresh').mockResolvedValue(view());
    const message = 'FreelaX EVM wallet binding\nNonce: n';
    vi.spyOn(api, 'evmWalletChallenge').mockResolvedValue({ challengeId: 'ch', walletAddress: '0x' + '1'.repeat(40), message, expiresAt: '' });
    const verify = vi.spyOn(api, 'evmWalletVerify').mockResolvedValue({ walletAddress: '0x' + '1'.repeat(40) });
    const wallet = { ...fakeWallet(), personalSign: vi.fn(async () => SIG) } as unknown as EvmWallet;
    await mount(true, false, () => wallet);
    await act(async () => { button('Liên kết ví EVM')!.click(); });
    expect(wallet.personalSign).toHaveBeenCalledWith(message, '0x' + '1'.repeat(40));
    expect(verify).toHaveBeenCalledWith('ch', SIG);
    expect(host.textContent).toContain('Ví EVM đã liên kết');
  });
});
