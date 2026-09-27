import * as anchor from "@anchor-lang/core";
import { PublicKey, SystemProgram } from "@solana/web3.js";

import type { TestEnvironment } from "./test-environment";

export const TEST_USDC_USD_E6 = new anchor.BN(999_800);
export const TEST_USD_VND_E6 = new anchor.BN(25_000_000_000);
export const TEST_USDC_VND_E6 = new anchor.BN(24_995_000_000);
export const TEST_SOURCE_HASH = Array.from({ length: 32 }, (_, index) => index);

export function deriveRateSnapshotPda(
  programId: PublicKey,
  rateId: anchor.BN,
): [PublicKey, number] {
  return PublicKey.findProgramAddressSync(
    [Buffer.from("rate"), rateId.toArrayLike(Buffer, "le", 8)],
    programId,
  );
}

export async function publishRateFixture(
  environment: TestEnvironment,
  rateId: anchor.BN,
  options: {
    usdcUsdE6?: anchor.BN;
    usdVndE6?: anchor.BN;
    observedAt?: anchor.BN;
    expiresAt?: anchor.BN;
  } = {},
): Promise<PublicKey> {
  const observedAt =
    options.observedAt ?? new anchor.BN(Math.floor(Date.now() / 1000));
  const expiresAt = options.expiresAt ?? observedAt.addn(120);
  const [rateSnapshot] = deriveRateSnapshotPda(
    environment.program.programId,
    rateId,
  );

  await environment.program.methods
    .publishRate(
      rateId,
      options.usdcUsdE6 ?? TEST_USDC_USD_E6,
      options.usdVndE6 ?? TEST_USD_VND_E6,
      observedAt,
      expiresAt,
      TEST_SOURCE_HASH,
    )
    .accountsStrict({
      rateAuthority: environment.rateAuthority.publicKey,
      config: environment.configPda,
      rateSnapshot,
      systemProgram: SystemProgram.programId,
    })
    .signers([environment.rateAuthority])
    .rpc();

  return rateSnapshot;
}
