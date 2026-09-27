import * as anchor from "@anchor-lang/core";
import { Keypair, PublicKey } from "@solana/web3.js";

import type { InvoicePayments } from "../../target/types/invoice_payments";
import {
  createMockUsdcFixture,
  type MockUsdcFixture,
} from "./mock-usdc";


export interface TestEnvironment {
  provider: anchor.AnchorProvider;
  program: anchor.Program<InvoicePayments>;
  payer: Keypair;

  mockUsdc: MockUsdcFixture;

  configPda: PublicKey;
  configBump: number;

  treasuryAuthority: Keypair;
  rateAuthority: Keypair;
  oracleAuthority: Keypair;
  maxRateAgeSeconds: anchor.BN;
}


let environmentPromise: Promise<TestEnvironment> | undefined;

export function getTestEnvironment(): Promise<TestEnvironment> {
  if (!environmentPromise) {
    environmentPromise = createTestEnvironment();
  }

  return environmentPromise;
}

/**
 * Tạo dữ liệu nền trên local validator:
 *
 * 1. Lấy provider, program và payer.
 * 2. Tạo Mock USDC.
 * 3. Tính Config PDA.
 * 4. Khởi tạo Config PDA.
 * 5. Trả các account cho những file test khác sử dụng.
 */
async function createTestEnvironment(): Promise<TestEnvironment> {
  const provider = anchor.AnchorProvider.env();
  anchor.setProvider(provider);

  const program = anchor.workspace
    .invoicePayments as anchor.Program<InvoicePayments>;

  const payer = (provider.wallet as any).payer as Keypair;

  const mockUsdc = await createMockUsdcFixture(provider, payer);

  const [configPda, configBump] = PublicKey.findProgramAddressSync(
    [Buffer.from("config")],
    program.programId,
  );

  const treasuryAuthority = Keypair.generate();
  const rateAuthority = Keypair.generate();
  const oracleAuthority = Keypair.generate();
  const maxRateAgeSeconds = new anchor.BN(300);

  await program.methods
    .initializeConfig(
      treasuryAuthority.publicKey,
      rateAuthority.publicKey,
      oracleAuthority.publicKey,
      maxRateAgeSeconds,
    )
    .accounts({
      admin: payer.publicKey,
      acceptedMint: mockUsdc.mint,
    })
    .rpc();

  return {
    provider,
    program,
    payer,
    mockUsdc,
    configPda,
    configBump,
    treasuryAuthority,
    rateAuthority,
    oracleAuthority,
    maxRateAgeSeconds,
  };
}
