import * as anchor from "@anchor-lang/core";
import { expect } from "chai";
import { Keypair } from "@solana/web3.js";
import { getOrCreateAssociatedTokenAccount, TOKEN_PROGRAM_ID } from "@solana/spl-token";

import {
  getTestEnvironment,
  type TestEnvironment,
} from "../helpers/test-environment";
import { createInvoiceFixture, expectRejected } from "../helpers/invoice";

describe("Cancel Invoice", () => {
  let environment: TestEnvironment;

  before(async () => {
    environment = await getTestEnvironment();
  });

  it("allows the assigned Freelancer to cancel a Pending invoice", async () => {
    const fixture = await createInvoiceFixture(environment, new anchor.BN(2_000));

    const signature = await environment.program.methods
      .cancelInvoice()
      .accountsStrict({
        freelancer: fixture.freelancer.publicKey,
        config: environment.configPda,
        invoice: fixture.invoicePda,
      })
      .signers([fixture.freelancer])
      .rpc();

    const invoice = await environment.program.account.invoice.fetch(
      fixture.invoicePda,
    );
    expect(invoice.status).to.deep.equal({ cancelled: {} });

    await environment.provider.connection.confirmTransaction(
      signature,
      "confirmed",
    );
    const transaction = await environment.provider.connection.getTransaction(
      signature,
      { commitment: "confirmed", maxSupportedTransactionVersion: 0 },
    );
    const parser = new anchor.EventParser(
      environment.program.programId,
      environment.program.coder,
    );
    const events = Array.from(
      parser.parseLogs(transaction!.meta!.logMessages!),
    ).filter((event) => event.name.toLowerCase() === "invoicecancelled");

    expect(events).to.have.length(1);
    expect((events[0].data as any).invoice.equals(fixture.invoicePda)).to.equal(
      true,
    );
  });

  it("rejects a signer other than the assigned Freelancer", async () => {
    const fixture = await createInvoiceFixture(environment, new anchor.BN(2_001));
    const stranger = Keypair.generate();

    await expectRejected(
      () =>
        environment.program.methods
          .cancelInvoice()
          .accountsStrict({
            freelancer: stranger.publicKey,
            config: environment.configPda,
            invoice: fixture.invoicePda,
          })
          .signers([stranger])
          .rpc(),
      "UnauthorizedFreelancer",
    );

    const invoice = await environment.program.account.invoice.fetch(
      fixture.invoicePda,
    );
    expect(invoice.status).to.deep.equal({ pending: {} });
  });

  it("rejects cancelling a Paid invoice", async () => {
    const { provider, program, payer, configPda, mockUsdc } = environment;
    const fixture = await createInvoiceFixture(environment, new anchor.BN(2_002));
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

    await expectRejected(
      () =>
        program.methods
          .cancelInvoice()
          .accountsStrict({
            freelancer: fixture.freelancer.publicKey,
            config: configPda,
            invoice: fixture.invoicePda,
          })
          .signers([fixture.freelancer])
          .rpc(),
      "InvoiceNotCancellable",
    );

    const invoice = await program.account.invoice.fetch(fixture.invoicePda);
    expect(invoice.status).to.deep.equal({ paid: {} });
  });

  it("rejects cancelling the same invoice twice", async () => {
    const fixture = await createInvoiceFixture(environment, new anchor.BN(2_003));

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
          .cancelInvoice()
          .accountsStrict({
            freelancer: fixture.freelancer.publicKey,
            config: environment.configPda,
            invoice: fixture.invoicePda,
          })
          .signers([fixture.freelancer])
          .rpc(),
      "InvoiceNotCancellable",
    );
  });

  it("rejects cancellation while Config is paused", async () => {
    const {
      program,
      payer,
      mockUsdc,
      configPda,
      treasuryAuthority,
      rateAuthority,
      oracleAuthority,
      maxRateAgeSeconds,
    } = environment;
    const fixture = await createInvoiceFixture(environment, new anchor.BN(2_004));

    await program.methods
      .updateConfig(
        treasuryAuthority.publicKey,
        rateAuthority.publicKey,
        oracleAuthority.publicKey,
        maxRateAgeSeconds,
        true,
      )
      .accountsStrict({
        admin: payer.publicKey,
        config: configPda,
        acceptedMint: mockUsdc.mint,
      })
      .rpc();

    try {
      await expectRejected(
        () =>
          program.methods
            .cancelInvoice()
            .accountsStrict({
              freelancer: fixture.freelancer.publicKey,
              config: configPda,
              invoice: fixture.invoicePda,
            })
            .signers([fixture.freelancer])
            .rpc(),
        "SystemPaused",
      );
    } finally {
      await program.methods
        .updateConfig(
          treasuryAuthority.publicKey,
          rateAuthority.publicKey,
          oracleAuthority.publicKey,
          maxRateAgeSeconds,
          false,
        )
        .accountsStrict({
          admin: payer.publicKey,
          config: configPda,
          acceptedMint: mockUsdc.mint,
        })
        .rpc();
    }

    const invoice = await program.account.invoice.fetch(fixture.invoicePda);
    expect(invoice.status).to.deep.equal({ pending: {} });
  });
});
