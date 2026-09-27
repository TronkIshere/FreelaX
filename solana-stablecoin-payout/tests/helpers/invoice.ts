import * as anchor from "@anchor-lang/core";
import { Keypair, LAMPORTS_PER_SOL, PublicKey, SystemProgram } from "@solana/web3.js";

import type { TestEnvironment } from "./test-environment";

export interface InvoiceFixture {
  freelancer: Keypair;
  client: Keypair;
  invoiceId: anchor.BN;
  amount: anchor.BN;
  invoicePda: PublicKey;
}

export function deriveInvoicePda(
  programId: PublicKey,
  freelancer: PublicKey,
  invoiceId: anchor.BN,
): [PublicKey, number] {
  return PublicKey.findProgramAddressSync(
    [
      Buffer.from("invoice"),
      freelancer.toBuffer(),
      invoiceId.toArrayLike(Buffer, "le", 8),
    ],
    programId,
  );
}

export async function createInvoiceFixture(
  environment: TestEnvironment,
  invoiceId: anchor.BN,
  amount = new anchor.BN(10_000_000),
  client = environment.mockUsdc.client,
): Promise<InvoiceFixture> {
  const { provider, program, configPda, defaultRateSnapshot, defaultRateExpiresAt } = environment;
  const freelancer = Keypair.generate();
  const airdropSignature = await provider.connection.requestAirdrop(
    freelancer.publicKey,
    LAMPORTS_PER_SOL,
  );
  await provider.connection.confirmTransaction(airdropSignature, "confirmed");

  const [invoicePda] = deriveInvoicePda(
    program.programId,
    freelancer.publicKey,
    invoiceId,
  );

  await program.methods
    .createInvoice(invoiceId, client.publicKey, amount, defaultRateExpiresAt.subn(1))
    .accountsStrict({
      freelancer: freelancer.publicKey,
      config: configPda,
      rateSnapshot: defaultRateSnapshot,
      invoice: invoicePda,
      systemProgram: SystemProgram.programId,
    })
    .signers([freelancer])
    .rpc();

  return { freelancer, client, invoiceId, amount, invoicePda };
}

export async function expectRejected(
  action: () => Promise<unknown>,
  expectedMessage?: string,
): Promise<void> {
  let message = "";

  try {
    await action();
  } catch (error) {
    message = error instanceof Error ? error.message : String(error);
  }

  if (!message) {
    throw new Error("Expected transaction to be rejected");
  }
  if (expectedMessage && !message.includes(expectedMessage)) {
    throw new Error(`Expected error containing ${expectedMessage}, got: ${message}`);
  }
}
