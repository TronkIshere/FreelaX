import { Transaction } from '@solana/web3.js';
import { api } from './api';

type WalletKey = { toBase58(): string };
export type InjectedSolanaWallet = {
  publicKey?: WalletKey | null;
  connect(): Promise<{ publicKey: WalletKey }>;
  signTransaction(transaction: Transaction): Promise<Transaction>;
  signMessage?(message: Uint8Array): Promise<{ signature: Uint8Array }>;
};

declare global {
  interface Window {
    phantom?: { solana?: InjectedSolanaWallet };
    solana?: InjectedSolanaWallet;
  }
}

function base64(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function localAutoWallet(address: string): InjectedSolanaWallet {
  const publicKey = { toBase58: () => address };
  return {
    publicKey,
    async connect() { return { publicKey }; },
    async signTransaction(transaction) {
      const unsigned = base64(transaction.serialize({ requireAllSignatures: false, verifySignatures: false }));
      const signed = await api.signAutoSolanaTransaction(unsigned);
      return Transaction.from(Uint8Array.from(atob(signed.transactionBase64), char => char.charCodeAt(0)));
    },
  };
}

export function injectedSolanaWallet(): InjectedSolanaWallet {
  const wallet = window.phantom?.solana ?? window.solana;
  if (!wallet?.connect || !wallet.signTransaction) {
    throw new Error('Không tìm thấy ví Solana hỗ trợ ký giao dịch trong trình duyệt.');
  }
  return wallet;
}

export async function connectSolanaWallet(): Promise<{ wallet: InjectedSolanaWallet; address: string }> {
  if (import.meta.env.VITE_SOLANA_CLUSTER === 'localnet') {
    const linked = await api.autoSolanaWallet();
    return { wallet: localAutoWallet(linked.walletAddress), address: linked.walletAddress };
  }
  const wallet = injectedSolanaWallet();
  let connection: { publicKey: WalletKey };
  try {
    connection = await wallet.connect();
  } catch (cause) {
    const code = cause && typeof cause === 'object' && 'code' in cause ? cause.code : null;
    const message = cause instanceof Error ? cause.message : '';
    if (code === 4001 || /reject|cancel|denied/i.test(message)) {
      throw new Error('Bạn đã từ chối kết nối trong ví. Hãy thử lại khi sẵn sàng.', { cause });
    }
    throw new Error('Chưa kết nối được ví. Hãy mở khóa ví và thử lại.', { cause });
  }
  const address = connection?.publicKey?.toBase58() || wallet.publicKey?.toBase58();
  if (!address) throw new Error('Ví chưa trả về địa chỉ Solana.');
  return { wallet, address };
}

export async function signEscrowTransaction(wallet: InjectedSolanaWallet, base64Transaction: string): Promise<string> {
  const bytes = Uint8Array.from(atob(base64Transaction), char => char.charCodeAt(0));
  const transaction = Transaction.from(bytes);
  const signed = await wallet.signTransaction(transaction);
  return base64(signed.serialize({ requireAllSignatures: false, verifySignatures: false }));
}

export function sameEscrowTransactionMessage(leftBase64: string, rightBase64: string): boolean {
  const decode = (value: string) => Transaction.from(Uint8Array.from(atob(value), char => char.charCodeAt(0)));
  const original = decode(leftBase64);
  const signed = decode(rightBase64);
  const a = original.serializeMessage();
  const b = signed.serializeMessage();
  return a.length === b.length && a.every((value, index) => value === b[index]);
}

export function hasEscrowSignerSignature(base64Transaction: string, address: string): boolean {
  const transaction = Transaction.from(Uint8Array.from(atob(base64Transaction), char => char.charCodeAt(0)));
  return transaction.signatures.some(entry => entry.publicKey.toBase58() === address && !!entry.signature)
    && transaction.verifySignatures(false);
}
