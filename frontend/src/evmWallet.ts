export type EvmEnvironment = 'LOCAL_SIMULATION' | 'AVALANCHE_FUJI';

export const ALLOWED_EVM_CHAINS: Readonly<Record<number, { environment: EvmEnvironment; label: string; simulated: boolean }>> = {
  31337: { environment: 'LOCAL_SIMULATION', label: 'Local simulation (Anvil)', simulated: true },
  43113: { environment: 'AVALANCHE_FUJI', label: 'Avalanche Fuji testnet', simulated: false },
};

export type Eip1193Provider = {
  request(args: { method: string; params?: unknown[] | Record<string, unknown> }): Promise<unknown>;
};

export type PreparedEvmTransaction = {
  chainFormat?: string; purpose?: string; to: string; data: string; value?: string; chainId: number;
};

export type PreparedEvmAction = {
  action: string; escrowId: string; transactions: PreparedEvmTransaction[];
  typedData?: string | null; typedDataDigest?: string | null;
  /** Deadline the typed data covers (mutual refund: both parties must sign the same one, register F13). */
  authorizationDeadline?: number | null;
};

export interface EscrowWalletPort {
  readonly family: 'solana' | 'evm';
  connect(expectedChainId: number): Promise<{ address: string; chainId: number }>;
}

export class WalletError extends Error {
  constructor(message: string, public readonly code: string) {
    super(message);
    this.name = 'WalletError';
  }
}

const HEX_ADDRESS = /^0x[0-9a-fA-F]{40}$/;
const HEX_DATA = /^0x([0-9a-fA-F]{2})*$/;

export function assertAllowedChain(chainId: number): void {
  if (!Number.isSafeInteger(chainId) || !(chainId in ALLOWED_EVM_CHAINS)) {
    throw new WalletError('Chain ' + chainId + ' is not permitted. Only the local simulation and the Avalanche Fuji testnet are supported.', 'CHAIN_NOT_ALLOWED');
  }
}

export function parseChainId(value: unknown): number {
  if (typeof value === 'number' && Number.isSafeInteger(value)) return value;
  if (typeof value === 'string' && /^0x[0-9a-fA-F]+$/.test(value)) return Number.parseInt(value, 16);
  if (typeof value === 'string' && /^[0-9]+$/.test(value)) return Number.parseInt(value, 10);
  throw new WalletError('The wallet returned an unreadable chain id.', 'CHAIN_UNREADABLE');
}

function toHexQuantity(value: string | undefined): string {
  if (!value || value === '0') return '0x0';
  if (/^0x[0-9a-fA-F]+$/.test(value)) return value;
  if (/^[0-9]+$/.test(value)) return '0x' + BigInt(value).toString(16);
  throw new WalletError('Invalid transaction value.', 'TX_INVALID');
}

function rejected(cause: unknown): boolean {
  const code = cause && typeof cause === 'object' && 'code' in cause ? (cause as { code: unknown }).code : null;
  const message = cause instanceof Error ? cause.message : '';
  return code === 4001 || /reject|cancel|denied/i.test(message);
}

export function injectedEvmProvider(): Eip1193Provider {
  const provider = (globalThis as { ethereum?: Eip1193Provider }).ethereum;
  if (!provider || typeof provider.request !== 'function') {
    throw new WalletError('No EVM wallet was found in this browser.', 'NO_WALLET');
  }
  return provider;
}

export class EvmWallet implements EscrowWalletPort {
  readonly family = 'evm' as const;

  constructor(private readonly provider: Eip1193Provider = injectedEvmProvider()) {}

  async connect(expectedChainId: number): Promise<{ address: string; chainId: number }> {
    assertAllowedChain(expectedChainId);
    let accounts: unknown;
    try {
      accounts = await this.provider.request({ method: 'eth_requestAccounts' });
    } catch (cause) {
      if (rejected(cause)) throw new WalletError('Wallet connection was declined.', 'USER_REJECTED');
      throw new WalletError('The wallet could not be connected.', 'CONNECT_FAILED');
    }
    const address = Array.isArray(accounts) && typeof accounts[0] === 'string' ? accounts[0] : '';
    if (!HEX_ADDRESS.test(address)) throw new WalletError('The wallet returned no valid address.', 'NO_ACCOUNT');
    let chainId = parseChainId(await this.provider.request({ method: 'eth_chainId' }));
    assertAllowedChain(chainId);
    if (chainId !== expectedChainId) {
      try {
        await this.provider.request({ method: 'wallet_switchEthereumChain', params: [{ chainId: '0x' + expectedChainId.toString(16) }] });
      } catch (cause) {
        if (rejected(cause)) throw new WalletError('Switching network was declined.', 'USER_REJECTED');
        throw new WalletError('The wallet is on the wrong network and cannot switch automatically.', 'WRONG_CHAIN');
      }
      chainId = parseChainId(await this.provider.request({ method: 'eth_chainId' }));
      if (chainId !== expectedChainId) throw new WalletError('The wallet is still on a different network.', 'WRONG_CHAIN');
    }
    return { address, chainId };
  }

  async sendPrepared(tx: PreparedEvmTransaction, from: string, expectedChainId: number): Promise<string> {
    assertAllowedChain(expectedChainId);
    assertAllowedChain(tx.chainId);
    if (tx.chainId !== expectedChainId) throw new WalletError('The prepared transaction targets a different chain than the active environment.', 'CHAIN_MISMATCH');
    if (!HEX_ADDRESS.test(tx.to) || !HEX_DATA.test(tx.data) || !HEX_ADDRESS.test(from)) {
      throw new WalletError('The prepared transaction is malformed.', 'TX_INVALID');
    }
    const live = parseChainId(await this.provider.request({ method: 'eth_chainId' }));
    if (live !== expectedChainId) throw new WalletError('The wallet network changed. Reconnect before signing.', 'WRONG_CHAIN');
    try {
      const hash = await this.provider.request({
        method: 'eth_sendTransaction',
        params: [{ from, to: tx.to, data: tx.data, value: toHexQuantity(tx.value) }],
      });
      if (typeof hash !== 'string' || !/^0x[0-9a-fA-F]{64}$/.test(hash)) throw new WalletError('The wallet returned no transaction hash.', 'NO_HASH');
      return hash;
    } catch (cause) {
      if (cause instanceof WalletError) throw cause;
      if (rejected(cause)) throw new WalletError('The transaction was declined in the wallet.', 'USER_REJECTED');
      throw new WalletError('The wallet could not send the transaction.', 'SEND_FAILED');
    }
  }

  async signTypedData(typedDataJson: string, from: string, expectedChainId: number): Promise<string> {
    assertAllowedChain(expectedChainId);
    let parsed: { domain?: { chainId?: unknown; verifyingContract?: unknown } };
    try {
      parsed = JSON.parse(typedDataJson);
    } catch {
      throw new WalletError('The authorization request is malformed.', 'TYPED_DATA_INVALID');
    }
    const domainChain = parseChainId(parsed.domain?.chainId);
    assertAllowedChain(domainChain);
    if (domainChain !== expectedChainId) throw new WalletError('The authorization targets a different chain than the active environment.', 'CHAIN_MISMATCH');
    if (typeof parsed.domain?.verifyingContract !== 'string' || !HEX_ADDRESS.test(parsed.domain.verifyingContract)) {
      throw new WalletError('The authorization has no valid contract.', 'TYPED_DATA_INVALID');
    }
    try {
      const signature = await this.provider.request({ method: 'eth_signTypedData_v4', params: [from, typedDataJson] });
      if (typeof signature !== 'string' || !/^0x[0-9a-fA-F]{130}$/.test(signature)) throw new WalletError('The wallet returned an invalid signature.', 'BAD_SIGNATURE');
      return signature;
    } catch (cause) {
      if (cause instanceof WalletError) throw cause;
      if (rejected(cause)) throw new WalletError('The signature was declined in the wallet.', 'USER_REJECTED');
      throw new WalletError('The wallet could not sign the authorization.', 'SIGN_FAILED');
    }
  }

  /**
   * EIP-191 personal_sign of a FreelaX wallet-binding challenge (register A8). Only messages that start with the binding
   * header are signed, so this method cannot be used to sign arbitrary text. The marketplace and gateway verify the result.
   */
  async personalSign(message: string, from: string): Promise<string> {
    if (!message.startsWith('FreelaX EVM wallet binding\n') || !HEX_ADDRESS.test(from)) {
      throw new WalletError('The binding request is malformed.', 'MESSAGE_INVALID');
    }
    const hexMessage = '0x' + Array.from(new TextEncoder().encode(message), b => b.toString(16).padStart(2, '0')).join('');
    try {
      const signature = await this.provider.request({ method: 'personal_sign', params: [hexMessage, from] });
      if (typeof signature !== 'string' || !/^0x[0-9a-fA-F]{130}$/.test(signature)) throw new WalletError('The wallet returned an invalid signature.', 'BAD_SIGNATURE');
      return signature;
    } catch (cause) {
      if (cause instanceof WalletError) throw cause;
      if (rejected(cause)) throw new WalletError('The signature was declined in the wallet.', 'USER_REJECTED');
      throw new WalletError('The wallet could not sign the binding message.', 'SIGN_FAILED');
    }
  }
}
