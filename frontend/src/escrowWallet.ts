import { Transaction } from '@solana/web3.js';

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

export function injectedSolanaWallet(): InjectedSolanaWallet {
  const wallet = window.phantom?.solana ?? window.solana;
  if (!wallet?.connect || !wallet.signTransaction) {
    throw new Error('Không tìm thấy ví Solana hỗ trợ ký giao dịch trong trình duyệt.');
  }
  return wallet;
}

export async function connectSolanaWallet(): Promise<{ wallet: InjectedSolanaWallet; address: string }> {
  const wallet = injectedSolanaWallet();
  const connection = await wallet.connect();
  const address = connection.publicKey?.toBase58();
  if (!address) throw new Error('Ví chưa trả về địa chỉ Solana.');
  return { wallet, address };
}

export async function signEscrowTransaction(wallet: InjectedSolanaWallet, base64: string): Promise<string> {
  const bytes = Uint8Array.from(atob(base64), char => char.charCodeAt(0));
  const transaction = Transaction.from(bytes);
  const signed = await wallet.signTransaction(transaction);
  const serialized = signed.serialize({ requireAllSignatures: false, verifySignatures: false });
  let binary = '';
  for (const byte of serialized) binary += String.fromCharCode(byte);
  return btoa(binary);
}

export function sameEscrowTransactionMessage(leftBase64: string, rightBase64: string): boolean {
  const decode = (value: string) => Transaction.from(Uint8Array.from(atob(value), char => char.charCodeAt(0)));
  const original = decode(leftBase64);
  const signed = decode(rightBase64);
  const a = original.serializeMessage();
  const b = signed.serializeMessage();
  return a.length === b.length && a.every((value, index) => value === b[index]);
}

export function hasEscrowSignerSignature(base64: string, address: string): boolean {
  const transaction = Transaction.from(Uint8Array.from(atob(base64), char => char.charCodeAt(0)));
  return transaction.signatures.some(entry => entry.publicKey.toBase58() === address && !!entry.signature)
    && transaction.verifySignatures(false);
}
