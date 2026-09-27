import * as anchor from "@anchor-lang/core";
import { expect } from "chai";
import { Keypair } from "@solana/web3.js";
import { getOrCreateAssociatedTokenAccount, TOKEN_PROGRAM_ID } from "@solana/spl-token";

import {
  getTestEnvironment,
  type TestEnvironment,
} from "../helpers/test-environment";
import { createInvoiceFixture, expectRejected } from "../helpers/invoice";

describe("Close Invoice", () => {
  let environment: TestEnvironment;

  before(async () => {
    environment = await getTestEnvironment();
  });

  async function expectCloseReturnsRent(invoiceId: number): Promise<void> {
    const fixture = await createInvoiceFixture(
      environment,
      new anchor.BN(invoiceId),
    );

    await environment.program.methods
      .cancelInvoice()
      .accountsStrict({
        freelancer: fixture.freelancer.publicKey,
        config: environment.configPda,
        invoice: fixture.invoicePda,
      })
      .signers([fixture.freelancer])
      .rpc();

    const invoiceBefore = await environment.provider.connection.getAccountInfo(
      fixture.invoicePda,
    );
    const freelancerBefore = await environment.provider.connection.getBalance(
      fixture.freelancer.publicKey,
    );

    await environment.program.methods
      .closeInvoice()
      .accountsStrict({
        freelancer: fixture.freelancer.publicKey,
        invoice: fixture.invoicePda,
      })
      .signers([fixture.freelancer])
      .rpc();

    const invoiceAfter = await environment.provider.connection.getAccountInfo(
      fixture.invoicePda,
    );
    const freelancerAfter = await environment.provider.connection.getBalance(
      fixture.freelancer.publicKey,
    );

    expect(invoiceBefore).to.not.equal(null);
    expect(invoiceAfter).to.equal(null);
    expect(freelancerAfter - freelancerBefore).to.equal(
      invoiceBefore!.lamports,
    );
  }

  it("closes a Cancelled invoice and returns all rent to the Freelancer", async () => {
    await expectCloseReturnsRent(3_000);
  });

  it("allows the Freelancer to close a Paid invoice", async () => {
    const { provider, program, payer, configPda, mockUsdc } = environment;
    const fixture = await createInvoiceFixture(environment, new anchor.BN(3_001));
    const freelancerAta = await getOrCreateAssociatedTokenAccount(
      provider.connection,
      payer,
      mockUsdc.mint,
      fixture.freelancer.publicKey,
    );

    await program.methods
      .payInvoice()
      .accountsStrict({
        client: mockUsdc.client.publicKey,
        config: configPda,
        invoice: fixture.invoicePda,
        freelancer: fixture.freelancer.publicKey,
        acceptedMint: mockUsdc.mint,
        clientAta: mockUsdc.clientAta,
        freelancerAta: freelancerAta.address,
        tokenProgram: TOKEN_PROGRAM_ID,
      })
      .signers([mockUsdc.client])
      .rpc();

    await program.methods
      .closeInvoice()
      .accountsStrict({
        freelancer: fixture.freelancer.publicKey,
        invoice: fixture.invoicePda,
      })
      .signers([fixture.freelancer])
      .rpc();

    expect(
      await provider.connection.getAccountInfo(fixture.invoicePda),
    ).to.equal(null);
  });

  it("rejects closing a Pending invoice", async () => {
    const fixture = await createInvoiceFixture(environment, new anchor.BN(3_002));

    await expectRejected(
      () =>
        environment.program.methods
          .closeInvoice()
          .accountsStrict({
            freelancer: fixture.freelancer.publicKey,
            invoice: fixture.invoicePda,
          })
          .signers([fixture.freelancer])
          .rpc(),
      "InvoiceNotClosable",
    );

    expect(
      await environment.provider.connection.getAccountInfo(fixture.invoicePda),
    ).to.not.equal(null);
  });

  it("rejects closing by anyone other than the assigned Freelancer", async () => {
    const fixture = await createInvoiceFixture(environment, new anchor.BN(3_003));
    const stranger = Keypair.generate();

    await environment.program.methods
      .cancelInvoice()
      .accountsStrict({
        freelancer: fixture.freelancer.publicKey,
        config: environment.configPda,
        invoice: fixture.invoicePda,
      })
      .signers([fixture.freelancer])
      .rpc();

    await expectRejected(
      () =>
        environment.program.methods
          .closeInvoice()
          .accountsStrict({
            freelancer: stranger.publicKey,
            invoice: fixture.invoicePda,
          })
          .signers([stranger])
          .rpc(),
      "UnauthorizedFreelancer",
    );

    expect(
      await environment.provider.connection.getAccountInfo(fixture.invoicePda),
    ).to.not.equal(null);
  });
});
