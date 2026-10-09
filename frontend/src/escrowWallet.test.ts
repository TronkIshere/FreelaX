import { Keypair, SystemProgram, Transaction } from '@solana/web3.js';
import { afterEach, expect, it, vi } from 'vitest';
import { api } from './api';
import { connectSolanaWallet, hasEscrowSignerSignature, signEscrowTransaction } from './escrowWallet';

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllEnvs(); });

it('connects a local account and signs its prepared transaction without a browser wallet', async () => {
  vi.stubEnv('VITE_SOLANA_CLUSTER', 'localnet');
  const signer = Keypair.generate();
  vi.spyOn(api, 'autoSolanaWallet').mockResolvedValue({ walletAddress: signer.publicKey.toBase58() });
  vi.spyOn(api, 'signAutoSolanaTransaction').mockImplementation(async value => {
    const transaction = Transaction.from(Uint8Array.from(atob(value), char => char.charCodeAt(0)));
    transaction.partialSign(signer);
    return { transactionBase64: btoa(String.fromCharCode(...transaction.serialize({
      requireAllSignatures: false, verifySignatures: false,
    }))) };
  });
  const { wallet, address } = await connectSolanaWallet();
  expect(address).toBe(signer.publicKey.toBase58());
  const transaction = new Transaction({ feePayer: signer.publicKey,
    recentBlockhash: '11111111111111111111111111111111' }).add(
      SystemProgram.transfer({ fromPubkey: signer.publicKey, toPubkey: Keypair.generate().publicKey, lamports: 1 }),
    );
  const unsigned = btoa(String.fromCharCode(...transaction.serialize({
    requireAllSignatures: false, verifySignatures: false,
  })));
  const signed = await signEscrowTransaction(wallet, unsigned);
  expect(hasEscrowSignerSignature(signed, address)).toBe(true);
  expect(api.signAutoSolanaTransaction).toHaveBeenCalledOnce();
});
