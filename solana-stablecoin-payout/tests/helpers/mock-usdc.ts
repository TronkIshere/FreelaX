import type { AnchorProvider } from "@anchor-lang/core";
import { Keypair, PublicKey } from "@solana/web3.js";
import {
  createMint,
  getOrCreateAssociatedTokenAccount,
  mintTo,
} from "@solana/spl-token";

export const MOCK_USDC_DECIMALS = 6;
export const INITIAL_CLIENT_USDC = 1_000n;
export const BASE_UNITS_PER_USDC = 10n ** 6n;

/** Các account được tạo để những test khác tái sử dụng. */
export interface MockUsdcFixture {
  mintAuthority: Keypair;
  client: Keypair;
  mint: PublicKey;
  clientAta: PublicKey;
  mintAmount: bigint;
}

/**
 * Dựng môi trường Mock USDC trên localnet:
 * 1. Tạo Mint có 6 decimals.
 * 2. Tạo ATA cho client.
 * 3. Mint 1,000 Mock USDC vào ATA đó.
 *
 * File helper chỉ chuẩn bị dữ liệu; các câu expect nằm trong file spec.
 */
export async function createMockUsdcFixture(
  provider: AnchorProvider,
  payer: Keypair,
): Promise<MockUsdcFixture> {
  const mintAuthority = Keypair.generate();
  const client = Keypair.generate();

  const mint = await createMint(
    provider.connection,
    payer,
    mintAuthority.publicKey,
    null,
    MOCK_USDC_DECIMALS,
  );

  const clientAta = await getOrCreateAssociatedTokenAccount(
    provider.connection,
    payer,
    mint,
    client.publicKey,
  );

  const mintAmount = INITIAL_CLIENT_USDC * BASE_UNITS_PER_USDC;

  await mintTo(
    provider.connection,
    payer,
    mint,
    clientAta.address,
    mintAuthority,
    mintAmount,
  );

  return {
    mintAuthority,
    client,
    mint,
    clientAta: clientAta.address,
    mintAmount,
  };
}
