import * as anchor from "@anchor-lang/core";
import { expect } from "chai";
import {
  Keypair,
  LAMPORTS_PER_SOL,
  PublicKey,
  SystemProgram,
} from "@solana/web3.js";
import {
  createMint,
  getAccount,
  getMint,
  getOrCreateAssociatedTokenAccount,
  TOKEN_PROGRAM_ID,
} from "@solana/spl-token";

import {
  getTestEnvironment,
  type TestEnvironment,
} from "../helpers/test-environment";

describe("Pay Invoice", () => {
  let environment: TestEnvironment;

  before(async () => {
    environment = await getTestEnvironment();
  });

  async function createPendingInvoice(
    invoiceId: anchor.BN,
    amount: anchor.BN,
    client = environment.mockUsdc.client,
    expiresAt = environment.defaultRateExpiresAt.subn(1),
  ) {
    const { provider, program, payer, configPda, mockUsdc } = environment;
    const freelancer = Keypair.generate();

    const airdropSignature = await provider.connection.requestAirdrop(
      freelancer.publicKey,
      LAMPORTS_PER_SOL,
    );
    await provider.connection.confirmTransaction(
      airdropSignature,
      "confirmed",
    );

    const freelancerAta = await getOrCreateAssociatedTokenAccount(
      provider.connection,
      payer,
      mockUsdc.mint,
      freelancer.publicKey,
    );

    const [invoicePda] = PublicKey.findProgramAddressSync(
      [
        Buffer.from("invoice"),
        freelancer.publicKey.toBuffer(),
        invoiceId.toArrayLike(Buffer, "le", 8),
      ],
      program.programId,
    );

    await program.methods
      .createInvoice(
        invoiceId,
        client.publicKey,
        amount,
        expiresAt,
      )
      .accountsStrict({
        freelancer: freelancer.publicKey,
        config: configPda,
        rateSnapshot: environment.defaultRateSnapshot,
        invoice: invoicePda,
        systemProgram: SystemProgram.programId,
      })
      .signers([freelancer])
      .rpc();

    return {
      client,
      freelancer,
      freelancerAta: freelancerAta.address,
      invoicePda,
    };
  }

  async function expectRejected(
    action: () => Promise<unknown>,
    expectedMessage?: string,
  ): Promise<void> {
    let errorMessage = "";

    try {
      await action();
    } catch (error) {
      errorMessage = error instanceof Error ? error.message : String(error);
    }

    expect(errorMessage).to.not.equal("");
    if (expectedMessage) {
      expect(errorMessage).to.include(expectedMessage);
    }
  }

  async function expectPaymentStateUnchanged(
    invoicePda: PublicKey,
    clientAta: PublicKey,
    freelancerAta: PublicKey,
    clientBalance: bigint,
    freelancerBalance: bigint,
  ): Promise<void> {
    const { provider, program } = environment;
    const [invoice, clientAccount, freelancerAccount] = await Promise.all([
      program.account.invoice.fetch(invoicePda),
      getAccount(provider.connection, clientAta),
      getAccount(provider.connection, freelancerAta),
    ]);

    expect(invoice.status).to.deep.equal({ pending: {} });
    expect(invoice.paidAt).to.equal(null);
    expect(clientAccount.amount).to.equal(clientBalance);
    expect(freelancerAccount.amount).to.equal(freelancerBalance);
  }

  it("transfers Mock USDC and marks the invoice as paid", async () => {
    const {
      provider,
      program,
      payer,
      configPda,
      mockUsdc,
    } = environment;

    // -------------------------------------------------------
    // PHẦN 1: TẠO FREELANCER
    // -------------------------------------------------------

    const freelancer = Keypair.generate();

    const airdropSignature =
      await provider.connection.requestAirdrop(
        freelancer.publicKey,
        LAMPORTS_PER_SOL,
      );

    await provider.connection.confirmTransaction(
      airdropSignature,
      "confirmed",
    );

    // Tạo ATA Mock USDC cho Freelancer.
    const freelancerAta =
      await getOrCreateAssociatedTokenAccount(
        provider.connection,
        payer,
        mockUsdc.mint,
        freelancer.publicKey,
      );

    // -------------------------------------------------------
    // PHẦN 2: TẠO INVOICE PENDING
    // -------------------------------------------------------

    const invoiceId = new anchor.BN(1_000);

    // 25 Mock USDC = 25,000,000 base units.
    const invoiceAmount = new anchor.BN(25_000_000);

    const [invoicePda] =
      PublicKey.findProgramAddressSync(
        [
          Buffer.from("invoice"),
          freelancer.publicKey.toBuffer(),
          invoiceId.toArrayLike(Buffer, "le", 8),
        ],
        program.programId,
      );

    await program.methods
      .createInvoice(
        invoiceId,
        mockUsdc.client.publicKey,
        invoiceAmount,
        environment.defaultRateExpiresAt.subn(1),
      )
      .accountsStrict({
        freelancer: freelancer.publicKey,
        config: configPda,
        rateSnapshot: environment.defaultRateSnapshot,
        invoice: invoicePda,
        systemProgram: SystemProgram.programId,
      })
      .signers([freelancer])
      .rpc();

    // -------------------------------------------------------
    // PHẦN 3: ĐỌC SỐ DƯ TRƯỚC THANH TOÁN
    // -------------------------------------------------------

    const clientBefore = await getAccount(
      provider.connection,
      mockUsdc.clientAta,
    );

    const freelancerBefore = await getAccount(
      provider.connection,
      freelancerAta.address,
    );

    const mintBefore = await getMint(
      provider.connection,
      mockUsdc.mint,
    );

    // -------------------------------------------------------
    // PHẦN 4: CLIENT THANH TOÁN
    // -------------------------------------------------------

    await program.methods
      .payInvoice()
      .accountsStrict({
        client: mockUsdc.client.publicKey,
        config: configPda,
        invoice: invoicePda,
        freelancer: freelancer.publicKey,
        acceptedMint: mockUsdc.mint,
        clientAta: mockUsdc.clientAta,
        freelancerAta: freelancerAta.address,
        tokenProgram: TOKEN_PROGRAM_ID,
      })
      // Client ký để Token Program được phép trừ Client ATA.
      .signers([mockUsdc.client])
      .rpc();

    // -------------------------------------------------------
    // PHẦN 5: ĐỌC SỐ DƯ SAU THANH TOÁN
    // -------------------------------------------------------

    const clientAfter = await getAccount(
      provider.connection,
      mockUsdc.clientAta,
    );

    const freelancerAfter = await getAccount(
      provider.connection,
      freelancerAta.address,
    );

    const mintAfter = await getMint(
      provider.connection,
      mockUsdc.mint,
    );

    const invoiceAfter =
      await program.account.invoice.fetch(invoicePda);

    const amount = BigInt(invoiceAmount.toString());

    // Client bị trừ đúng 25 Mock USDC.
    expect(clientAfter.amount).to.equal(
      clientBefore.amount - amount,
    );

    // Freelancer nhận đúng 25 Mock USDC.
    expect(freelancerAfter.amount).to.equal(
      freelancerBefore.amount + amount,
    );

    // Transfer không mint hoặc burn nên tổng supply không đổi.
    expect(mintAfter.supply).to.equal(mintBefore.supply);

    // Invoice chuyển từ Pending sang Paid.
    expect(invoiceAfter.status).to.deep.equal({
      paid: {},
    });

    // Thời điểm thanh toán đã được ghi.
    expect(invoiceAfter.paidAt).to.not.equal(null);
    expect(
      invoiceAfter.paidAt!.toNumber(),
    ).to.be.greaterThan(0);

    console.log(
      "Client balance before:",
      clientBefore.amount.toString(),
    );
    console.log(
      "Client balance after:",
      clientAfter.amount.toString(),
    );
    console.log(
      "Freelancer balance before:",
      freelancerBefore.amount.toString(),
    );
    console.log(
      "Freelancer balance after:",
      freelancerAfter.amount.toString(),
    );
    console.log("Invoice status: Paid");
  });
  it("rejects paying the same invoice twice", async () => {
  const {
    provider,
    program,
    payer,
    configPda,
    mockUsdc,
  } = environment;

  // 1. Tạo Freelancer riêng cho test.
  const freelancer = Keypair.generate();

  const airdropSignature =
    await provider.connection.requestAirdrop(
      freelancer.publicKey,
      LAMPORTS_PER_SOL,
    );

  await provider.connection.confirmTransaction(
    airdropSignature,
    "confirmed",
  );

  const freelancerAta =
    await getOrCreateAssociatedTokenAccount(
      provider.connection,
      payer,
      mockUsdc.mint,
      freelancer.publicKey,
    );

  // 2. Tạo Invoice Pending trị giá 10 Mock USDC.
  const invoiceId = new anchor.BN(1_001);
  const invoiceAmount = new anchor.BN(10_000_000);

  const [invoicePda] =
    PublicKey.findProgramAddressSync(
      [
        Buffer.from("invoice"),
        freelancer.publicKey.toBuffer(),
        invoiceId.toArrayLike(Buffer, "le", 8),
      ],
      program.programId,
    );

  await program.methods
    .createInvoice(
      invoiceId,
      mockUsdc.client.publicKey,
      invoiceAmount,
      environment.defaultRateExpiresAt.subn(1),
    )
    .accountsStrict({
      freelancer: freelancer.publicKey,
      config: configPda,
      rateSnapshot: environment.defaultRateSnapshot,
      invoice: invoicePda,
      systemProgram: SystemProgram.programId,
    })
    .signers([freelancer])
    .rpc();

  // 3. Thanh toán lần đầu — phải thành công.
  await program.methods
    .payInvoice()
    .accountsStrict({
      client: mockUsdc.client.publicKey,
      config: configPda,
      invoice: invoicePda,
      freelancer: freelancer.publicKey,
      acceptedMint: mockUsdc.mint,
      clientAta: mockUsdc.clientAta,
      freelancerAta: freelancerAta.address,
      tokenProgram: TOKEN_PROGRAM_ID,
    })
    .signers([mockUsdc.client])
    .rpc();

  const invoiceAfterFirstPayment =
    await program.account.invoice.fetch(invoicePda);

  expect(invoiceAfterFirstPayment.status).to.deep.equal({
    paid: {},
  });

  // Ghi lại số dư sau lần thanh toán đầu tiên.
  const clientBeforeSecondPayment = await getAccount(
    provider.connection,
    mockUsdc.clientAta,
  );

  const freelancerBeforeSecondPayment = await getAccount(
    provider.connection,
    freelancerAta.address,
  );

  // 4. Cố thanh toán lần thứ hai.
  let secondPaymentFailed = false;

  try {
    await program.methods
      .payInvoice()
      .accountsStrict({
        client: mockUsdc.client.publicKey,
        config: configPda,
        invoice: invoicePda,
        freelancer: freelancer.publicKey,
        acceptedMint: mockUsdc.mint,
        clientAta: mockUsdc.clientAta,
        freelancerAta: freelancerAta.address,
        tokenProgram: TOKEN_PROGRAM_ID,
      })
      .signers([mockUsdc.client])
      .rpc();
  } catch (error) {
    secondPaymentFailed = true;

    console.log(
      "Second payment was correctly rejected",
    );

    if (error instanceof Error) {
      console.log(
        "Expected error:",
        error.message.split("\n")[0],
      );
    }
  }

  expect(secondPaymentFailed).to.equal(true);

  // 5. Kiểm tra lần thanh toán thất bại không trừ thêm token.
  const clientAfterSecondPayment = await getAccount(
    provider.connection,
    mockUsdc.clientAta,
  );

  const freelancerAfterSecondPayment = await getAccount(
    provider.connection,
    freelancerAta.address,
  );

  expect(clientAfterSecondPayment.amount).to.equal(
    clientBeforeSecondPayment.amount,
  );

  expect(freelancerAfterSecondPayment.amount).to.equal(
    freelancerBeforeSecondPayment.amount,
  );

  const invoiceAfterSecondPayment =
    await program.account.invoice.fetch(invoicePda);

  expect(invoiceAfterSecondPayment.status).to.deep.equal({
    paid: {},
  });

  expect(invoiceAfterSecondPayment.paidAt).to.not.equal(null);
});

  it("rejects payment after the locked Invoice expiry", async () => {
    const now = Math.floor(Date.now() / 1000);
    const fixture = await createPendingInvoice(
      new anchor.BN(1_031),
      new anchor.BN(10_000_000),
      environment.mockUsdc.client,
      new anchor.BN(now + 2),
    );
    await new Promise((resolve) => setTimeout(resolve, 3_000));

    await expectRejected(
      () =>
        environment.program.methods
          .payInvoice()
          .accountsStrict({
            client: environment.mockUsdc.client.publicKey,
            config: environment.configPda,
            invoice: fixture.invoicePda,
            freelancer: fixture.freelancer.publicKey,
            acceptedMint: environment.mockUsdc.mint,
            clientAta: environment.mockUsdc.clientAta,
            freelancerAta: fixture.freelancerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([environment.mockUsdc.client])
          .rpc(),
      "InvoiceExpired",
    );
    const invoice = await environment.program.account.invoice.fetch(
      fixture.invoicePda,
    );
    expect(invoice.status).to.deep.equal({ pending: {} });
  });

  it("rejects a client signer that is not assigned to the invoice", async () => {
    const { provider, program, payer, configPda, mockUsdc } = environment;
    const fixture = await createPendingInvoice(
      new anchor.BN(1_002),
      new anchor.BN(10_000_000),
    );
    const fakeClient = Keypair.generate();
    const fakeClientAta = await getOrCreateAssociatedTokenAccount(
      provider.connection,
      payer,
      mockUsdc.mint,
      fakeClient.publicKey,
    );
    const clientBefore = await getAccount(
      provider.connection,
      mockUsdc.clientAta,
    );
    const freelancerBefore = await getAccount(
      provider.connection,
      fixture.freelancerAta,
    );

    await expectRejected(
      () =>
        program.methods
          .payInvoice()
          .accountsStrict({
            client: fakeClient.publicKey,
            config: configPda,
            invoice: fixture.invoicePda,
            freelancer: fixture.freelancer.publicKey,
            acceptedMint: mockUsdc.mint,
            clientAta: fakeClientAta.address,
            freelancerAta: fixture.freelancerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([fakeClient])
          .rpc(),
      "UnauthorizedClient",
    );

    await expectPaymentStateUnchanged(
      fixture.invoicePda,
      mockUsdc.clientAta,
      fixture.freelancerAta,
      clientBefore.amount,
      freelancerBefore.amount,
    );
  });

  it("rejects a mint other than Config.accepted_mint", async () => {
    const { provider, program, payer, configPda, mockUsdc } = environment;
    const fixture = await createPendingInvoice(
      new anchor.BN(1_003),
      new anchor.BN(10_000_000),
    );
    const wrongMint = await createMint(
      provider.connection,
      payer,
      mockUsdc.mintAuthority.publicKey,
      null,
      6,
    );
    const clientBefore = await getAccount(
      provider.connection,
      mockUsdc.clientAta,
    );
    const freelancerBefore = await getAccount(
      provider.connection,
      fixture.freelancerAta,
    );

    await expectRejected(
      () =>
        program.methods
          .payInvoice()
          .accountsStrict({
            client: mockUsdc.client.publicKey,
            config: configPda,
            invoice: fixture.invoicePda,
            freelancer: fixture.freelancer.publicKey,
            acceptedMint: wrongMint,
            clientAta: mockUsdc.clientAta,
            freelancerAta: fixture.freelancerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([mockUsdc.client])
          .rpc(),
      "InvalidInvoiceMint",
    );

    await expectPaymentStateUnchanged(
      fixture.invoicePda,
      mockUsdc.clientAta,
      fixture.freelancerAta,
      clientBefore.amount,
      freelancerBefore.amount,
    );
  });

  it("pays with the Invoice-locked mint after Config rotates to a new mint", async () => {
    const {
      provider,
      program,
      payer,
      configPda,
      mockUsdc,
      treasuryAuthority,
      rateAuthority,
      oracleAuthority,
      maxRateAgeSeconds,
    } = environment;
    const fixture = await createPendingInvoice(
      new anchor.BN(1_030),
      new anchor.BN(10_000_000),
    );
    const rotatedMint = await createMint(
      provider.connection,
      payer,
      mockUsdc.mintAuthority.publicKey,
      null,
      6,
    );

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
        acceptedMint: rotatedMint,
      })
      .rpc();

    try {
      await program.methods
        .payInvoice()
        .accountsStrict({
          client: mockUsdc.client.publicKey,
          config: configPda,
          invoice: fixture.invoicePda,
          freelancer: fixture.freelancer.publicKey,
          acceptedMint: mockUsdc.mint,
          clientAta: mockUsdc.clientAta,
          freelancerAta: fixture.freelancerAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([mockUsdc.client])
        .rpc();
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
    expect(invoice.status).to.deep.equal({ paid: {} });
    expect(invoice.mint.equals(mockUsdc.mint)).to.equal(true);
  });

  it("rejects a Client ATA that is not owned by the Client", async () => {
    const { provider, program, configPda, mockUsdc } = environment;
    const fixture = await createPendingInvoice(
      new anchor.BN(1_004),
      new anchor.BN(10_000_000),
    );
    const clientBefore = await getAccount(
      provider.connection,
      mockUsdc.clientAta,
    );
    const freelancerBefore = await getAccount(
      provider.connection,
      fixture.freelancerAta,
    );

    await expectRejected(() =>
      program.methods
        .payInvoice()
        .accountsStrict({
          client: mockUsdc.client.publicKey,
          config: configPda,
          invoice: fixture.invoicePda,
          freelancer: fixture.freelancer.publicKey,
          acceptedMint: mockUsdc.mint,
          clientAta: fixture.freelancerAta,
          freelancerAta: fixture.freelancerAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([mockUsdc.client])
        .rpc(),
    );

    await expectPaymentStateUnchanged(
      fixture.invoicePda,
      mockUsdc.clientAta,
      fixture.freelancerAta,
      clientBefore.amount,
      freelancerBefore.amount,
    );
  });

  it("rejects a Freelancer ATA that is not owned by the Freelancer", async () => {
    const { provider, program, configPda, mockUsdc } = environment;
    const fixture = await createPendingInvoice(
      new anchor.BN(1_005),
      new anchor.BN(10_000_000),
    );
    const clientBefore = await getAccount(
      provider.connection,
      mockUsdc.clientAta,
    );
    const freelancerBefore = await getAccount(
      provider.connection,
      fixture.freelancerAta,
    );

    await expectRejected(() =>
      program.methods
        .payInvoice()
        .accountsStrict({
          client: mockUsdc.client.publicKey,
          config: configPda,
          invoice: fixture.invoicePda,
          freelancer: fixture.freelancer.publicKey,
          acceptedMint: mockUsdc.mint,
          clientAta: mockUsdc.clientAta,
          freelancerAta: mockUsdc.clientAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([mockUsdc.client])
        .rpc(),
    );

    await expectPaymentStateUnchanged(
      fixture.invoicePda,
      mockUsdc.clientAta,
      fixture.freelancerAta,
      clientBefore.amount,
      freelancerBefore.amount,
    );
  });

  it("rejects payment when the Client has insufficient tokens", async () => {
    const { provider, program, payer, configPda, mockUsdc } = environment;
    const emptyClient = Keypair.generate();
    const emptyClientAta = await getOrCreateAssociatedTokenAccount(
      provider.connection,
      payer,
      mockUsdc.mint,
      emptyClient.publicKey,
    );
    const fixture = await createPendingInvoice(
      new anchor.BN(1_006),
      new anchor.BN(10_000_000),
      emptyClient,
    );
    const freelancerBefore = await getAccount(
      provider.connection,
      fixture.freelancerAta,
    );

    await expectRejected(() =>
      program.methods
        .payInvoice()
        .accountsStrict({
          client: emptyClient.publicKey,
          config: configPda,
          invoice: fixture.invoicePda,
          freelancer: fixture.freelancer.publicKey,
          acceptedMint: mockUsdc.mint,
          clientAta: emptyClientAta.address,
          freelancerAta: fixture.freelancerAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([emptyClient])
        .rpc(),
    );

    await expectPaymentStateUnchanged(
      fixture.invoicePda,
      emptyClientAta.address,
      fixture.freelancerAta,
      0n,
      freelancerBefore.amount,
    );
  });

  it("rejects payment while Config is paused", async () => {
    const {
      provider,
      program,
      payer,
      configPda,
      mockUsdc,
      treasuryAuthority,
      rateAuthority,
      oracleAuthority,
      maxRateAgeSeconds,
    } = environment;
    const fixture = await createPendingInvoice(
      new anchor.BN(1_007),
      new anchor.BN(10_000_000),
    );
    const clientBefore = await getAccount(
      provider.connection,
      mockUsdc.clientAta,
    );
    const freelancerBefore = await getAccount(
      provider.connection,
      fixture.freelancerAta,
    );

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
            .payInvoice()
            .accountsStrict({
              client: mockUsdc.client.publicKey,
              config: configPda,
              invoice: fixture.invoicePda,
              freelancer: fixture.freelancer.publicKey,
              acceptedMint: mockUsdc.mint,
              clientAta: mockUsdc.clientAta,
              freelancerAta: fixture.freelancerAta,
              tokenProgram: TOKEN_PROGRAM_ID,
            })
            .signers([mockUsdc.client])
            .rpc(),
        "SystemPaused",
      );

      await expectPaymentStateUnchanged(
        fixture.invoicePda,
        mockUsdc.clientAta,
        fixture.freelancerAta,
        clientBefore.amount,
        freelancerBefore.amount,
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
  });
});
