import { describe, expect, it, vi } from 'vitest';
import { ALLOWED_EVM_CHAINS, EvmWallet, WalletError, assertAllowedChain, parseChainId, type Eip1193Provider } from './evmWallet';

const ADDRESS = '0x' + '1'.repeat(40);
const CONTRACT = '0x' + '2'.repeat(40);
const HASH = '0x' + 'a'.repeat(64);
const SIGNATURE = '0x' + 'b'.repeat(130);

function provider(handlers: Record<string, (params?: unknown) => unknown>) {
  const request = vi.fn(async ({ method, params }: { method: string; params?: unknown }) => {
    const handler = handlers[method];
    if (!handler) throw new Error('unexpected ' + method);
    return handler(params);
  });
  return { request } as unknown as Eip1193Provider & { request: ReturnType<typeof vi.fn> };
}

async function failure(promise: Promise<unknown>): Promise<WalletError> {
  try { await promise; } catch (error) { return error as WalletError; }
  throw new Error('expected rejection');
}

describe('chain allow-list', () => {
  it('permits only the local simulation and Fuji', () => {
    expect(Object.keys(ALLOWED_EVM_CHAINS).sort()).toEqual(['31337', '43113']);
    expect(() => assertAllowedChain(31337)).not.toThrow();
    expect(() => assertAllowedChain(43113)).not.toThrow();
  });
  it.each([43114, 1, 0, -1, 1.5, Number.NaN])('refuses chain %s', id => {
    expect(() => assertAllowedChain(id)).toThrowError(/not permitted/);
  });
  it('parses hex and decimal chain ids and rejects garbage', () => {
    expect(parseChainId('0xa869')).toBe(43113);
    expect(parseChainId('31337')).toBe(31337);
    expect(parseChainId(43113)).toBe(43113);
    expect(() => parseChainId('abc')).toThrow(WalletError);
  });
});

describe('connect', () => {
  it('connects on the expected chain', async () => {
    const p = provider({ eth_requestAccounts: () => [ADDRESS], eth_chainId: () => '0xa869' });
    expect(await new EvmWallet(p).connect(43113)).toEqual({ address: ADDRESS, chainId: 43113 });
  });
  it('refuses to be configured for Mainnet before contacting the wallet', async () => {
    const p = provider({});
    expect((await failure(new EvmWallet(p).connect(43114))).code).toBe('CHAIN_NOT_ALLOWED');
    expect(p.request).not.toHaveBeenCalled();
  });
  it('refuses a wallet that is on Mainnet and never asks it to send anything', async () => {
    const p = provider({ eth_requestAccounts: () => [ADDRESS], eth_chainId: () => '0xa86a' });
    expect((await failure(new EvmWallet(p).connect(43113))).code).toBe('CHAIN_NOT_ALLOWED');
  });
  it('switches from an allowed chain to the expected one', async () => {
    let chain = '0x7a69';
    const p = provider({
      eth_requestAccounts: () => [ADDRESS], eth_chainId: () => chain,
      wallet_switchEthereumChain: params => { chain = (params as { chainId: string }[])[0].chainId; return null; },
    });
    expect((await new EvmWallet(p).connect(43113)).chainId).toBe(43113);
  });
  it('reports a declined connection and a failed switch distinctly', async () => {
    const declined = provider({ eth_requestAccounts: () => { throw Object.assign(new Error('x'), { code: 4001 }); } });
    expect((await failure(new EvmWallet(declined).connect(31337))).code).toBe('USER_REJECTED');
    const stuck = provider({ eth_requestAccounts: () => [ADDRESS], eth_chainId: () => '0x7a69',
      wallet_switchEthereumChain: () => { throw new Error('unsupported'); } });
    expect((await failure(new EvmWallet(stuck).connect(43113))).code).toBe('WRONG_CHAIN');
  });
  it('rejects a missing or malformed account', async () => {
    const p = provider({ eth_requestAccounts: () => ['nope'] });
    expect((await failure(new EvmWallet(p).connect(31337))).code).toBe('NO_ACCOUNT');
  });
});

describe('sendPrepared', () => {
  const tx = { to: CONTRACT, data: '0xdeadbeef', value: '0', chainId: 43113 };
  it('sends exactly the prepared fields and returns the hash', async () => {
    const p = provider({ eth_chainId: () => '0xa869', eth_sendTransaction: () => HASH });
    expect(await new EvmWallet(p).sendPrepared(tx, ADDRESS, 43113)).toBe(HASH);
    expect(p.request).toHaveBeenLastCalledWith({ method: 'eth_sendTransaction',
      params: [{ from: ADDRESS, to: CONTRACT, data: '0xdeadbeef', value: '0x0' }] });
  });
  it('refuses a transaction for another chain or for Mainnet without calling the wallet', async () => {
    const p = provider({});
    const wallet = new EvmWallet(p);
    expect((await failure(wallet.sendPrepared({ ...tx, chainId: 43114 }, ADDRESS, 43113))).code).toBe('CHAIN_NOT_ALLOWED');
    expect((await failure(wallet.sendPrepared({ ...tx, chainId: 31337 }, ADDRESS, 43113))).code).toBe('CHAIN_MISMATCH');
    expect(p.request).not.toHaveBeenCalled();
  });
  it('refuses when the live wallet network drifted', async () => {
    const p = provider({ eth_chainId: () => '0x7a69' });
    expect((await failure(new EvmWallet(p).sendPrepared(tx, ADDRESS, 43113))).code).toBe('WRONG_CHAIN');
  });
  it('rejects malformed calldata and addresses', async () => {
    const p = provider({});
    const wallet = new EvmWallet(p);
    expect((await failure(wallet.sendPrepared({ ...tx, data: '0xzz' }, ADDRESS, 43113))).code).toBe('TX_INVALID');
    expect((await failure(wallet.sendPrepared({ ...tx, to: '0x12' }, ADDRESS, 43113))).code).toBe('TX_INVALID');
  });
  it('maps wallet rejection and bad hashes', async () => {
    const declined = provider({ eth_chainId: () => '0xa869', eth_sendTransaction: () => { throw Object.assign(new Error('User rejected'), { code: 4001 }); } });
    expect((await failure(new EvmWallet(declined).sendPrepared(tx, ADDRESS, 43113))).code).toBe('USER_REJECTED');
    const bad = provider({ eth_chainId: () => '0xa869', eth_sendTransaction: () => '0x1' });
    expect((await failure(new EvmWallet(bad).sendPrepared(tx, ADDRESS, 43113))).code).toBe('NO_HASH');
  });
  it('never exposes a private-key or raw-signing method', async () => {
    const p = provider({ eth_chainId: () => '0xa869', eth_sendTransaction: () => HASH });
    await new EvmWallet(p).sendPrepared(tx, ADDRESS, 43113);
    const methods = p.request.mock.calls.map(call => (call[0] as { method: string }).method);
    expect(methods.every((m: string) => ['eth_chainId', 'eth_sendTransaction'].includes(m))).toBe(true);
  });
});

describe('signTypedData', () => {
  const typed = (chainId: number, verifyingContract = CONTRACT) => JSON.stringify({ domain: { chainId, verifyingContract, name: 'FreelaXEscrow', version: '1' } });
  it('signs authorization data bound to the active chain', async () => {
    const p = provider({ eth_signTypedData_v4: () => SIGNATURE });
    expect(await new EvmWallet(p).signTypedData(typed(43113), ADDRESS, 43113)).toBe(SIGNATURE);
  });
  it('refuses typed data for Mainnet, another chain or a missing contract before the wallet is asked', async () => {
    const p = provider({});
    const wallet = new EvmWallet(p);
    expect((await failure(wallet.signTypedData(typed(43114), ADDRESS, 43113))).code).toBe('CHAIN_NOT_ALLOWED');
    expect((await failure(wallet.signTypedData(typed(31337), ADDRESS, 43113))).code).toBe('CHAIN_MISMATCH');
    expect((await failure(wallet.signTypedData(typed(43113, 'x'), ADDRESS, 43113))).code).toBe('TYPED_DATA_INVALID');
    expect((await failure(wallet.signTypedData('{', ADDRESS, 43113))).code).toBe('TYPED_DATA_INVALID');
    expect(p.request).not.toHaveBeenCalled();
  });
  it('maps declined and malformed signatures', async () => {
    const declined = provider({ eth_signTypedData_v4: () => { throw Object.assign(new Error('x'), { code: 4001 }); } });
    expect((await failure(new EvmWallet(declined).signTypedData(typed(43113), ADDRESS, 43113))).code).toBe('USER_REJECTED');
    const bad = provider({ eth_signTypedData_v4: () => '0x12' });
    expect((await failure(new EvmWallet(bad).signTypedData(typed(43113), ADDRESS, 43113))).code).toBe('BAD_SIGNATURE');
  });
});

it('reports a missing wallet without throwing a raw error', () => {
  const holder = globalThis as { ethereum?: unknown };
  const saved = holder.ethereum;
  delete holder.ethereum;
  try { expect(() => new EvmWallet()).toThrowError(/No EVM wallet/); } finally { holder.ethereum = saved; }
});
