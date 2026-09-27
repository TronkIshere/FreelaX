import * as anchor from "@anchor-lang/core";
import { Keypair, PublicKey, SystemProgram } from "@solana/web3.js";

import type { InvoicePayments } from "../../target/types/invoice_payments";
import {
  createMockUsdcFixture,
  type MockUsdcFixture,
} from "./mock-usdc";
import { publishRateFixture } from "./rate";


export interface TestEnvironment {
  provider: anchor.AnchorProvider;
  program: anchor.Program<InvoicePayments>;
  payer: Keypair;

  mockUsdc: MockUsdcFixture;

  configPda: PublicKey;
  configBump: number;
  programData: PublicKey;

  treasuryAuthority: Keypair;
  rateAuthority: Keypair;
  oracleAuthority: Keypair;
  maxRateAgeSeconds: anchor.BN;
  defaultRateSnapshot: PublicKey;
  defaultRateExpiresAt: anchor.BN;
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
  const [programData] = PublicKey.findProgramAddressSync(
    [program.programId.toBuffer()],
    new PublicKey("BPFLoaderUpgradeab1e11111111111111111111111"),
  );

  // Prove that an arbitrary first signer cannot seize the singleton Config.
  const unauthorizedInitializer = Keypair.generate();
  const airdropSignature = await provider.connection.requestAirdrop(
    unauthorizedInitializer.publicKey,
    anchor.web3.LAMPORTS_PER_SOL,
  );
  await provider.connection.confirmTransaction(airdropSignature, "confirmed");
  let unauthorizedInitializationRejected = false;
  try {
    await program.methods
      .initializeConfig(
        treasuryAuthority.publicKey,
        rateAuthority.publicKey,
        oracleAuthority.publicKey,
        maxRateAgeSeconds,
      )
      .accountsStrict({
        admin: unauthorizedInitializer.publicKey,
        config: configPda,
        acceptedMint: mockUsdc.mint,
        program: program.programId,
        programData,
        systemProgram: SystemProgram.programId,
      })
      .signers([unauthorizedInitializer])
      .rpc();
  } catch (error) {
    unauthorizedInitializationRejected = String(error).includes(
      "UnauthorizedInitializer",
    );
  }
  if (!unauthorizedInitializationRejected) {
    throw new Error("Config initialization was not restricted to upgrade authority");
  }

  await program.methods
    .initializeConfig(
      treasuryAuthority.publicKey,
      rateAuthority.publicKey,
      oracleAuthority.publicKey,
      maxRateAgeSeconds,
    )
    .accountsStrict({
      admin: payer.publicKey,
      config: configPda,
      acceptedMint: mockUsdc.mint,
      program: program.programId,
      programData,
      systemProgram: SystemProgram.programId,
    })
    .rpc();

  const environment: TestEnvironment = {
    provider,
    program,
    payer,
    mockUsdc,
    configPda,
    configBump,
    programData,
    treasuryAuthority,
    rateAuthority,
    oracleAuthority,
    maxRateAgeSeconds,
    defaultRateSnapshot: PublicKey.default,
    defaultRateExpiresAt: new anchor.BN(0),
  };
  const rateAuthorityAirdrop = await provider.connection.requestAirdrop(
    rateAuthority.publicKey,
    anchor.web3.LAMPORTS_PER_SOL,
  );
  await provider.connection.confirmTransaction(rateAuthorityAirdrop, "confirmed");
  const now = new anchor.BN(Math.floor(Date.now() / 1000));
  environment.defaultRateExpiresAt = now.addn(280);
  environment.defaultRateSnapshot = await publishRateFixture(
    environment,
    new anchor.BN(9_000_000),
    { observedAt: now, expiresAt: environment.defaultRateExpiresAt },
  );

  return environment;
}
