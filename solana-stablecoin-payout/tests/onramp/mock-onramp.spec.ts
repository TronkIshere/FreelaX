import * as anchor from "@anchor-lang/core";
import { expect } from "chai";
import {
  ASSOCIATED_TOKEN_PROGRAM_ID,
  getAccount,
  getAssociatedTokenAddressSync,
  TOKEN_PROGRAM_ID,
} from "@solana/spl-token";
import {
  Keypair,
  LAMPORTS_PER_SOL,
  PublicKey,
  SystemProgram,
} from "@solana/web3.js";

import { expectRejected } from "../helpers/invoice";
import {
  getTestEnvironment,
  type TestEnvironment,
} from "../helpers/test-environment";

describe("Mock On-ramp", () => {
  let environment: TestEnvironment;

  before(async () => {
    environment = await getTestEnvironment();
  });

  function deriveReceipt(
    client: PublicKey,
    purchaseId: anchor.BN,
  ): [PublicKey, number] {
    return PublicKey.findProgramAddressSync(
      [
        Buffer.from("mock_onramp"),
        client.toBuffer(),
        purchaseId.toArrayLike(Buffer, "le", 8),
      ],
      environment.program.programId,
    );
  }

  async function executeMockOnramp(
    client: PublicKey,
    purchaseId: anchor.BN,
    usdAmountE6: anchor.BN,
    authority = environment.mockOnrampAuthority,
  ): Promise<string> {
    const [receipt] = deriveReceipt(client, purchaseId);
    const clientAta = getAssociatedTokenAddressSync(
      environment.mockUsdc.mint,
      client,
    );

    return environment.program.methods
      .mockOnramp(purchaseId, usdAmountE6)
      .accountsStrict({
        onrampAuthority: authority.publicKey,
        config: environment.configPda,
        acceptedMint: environment.mockUsdc.mint,
        mockOnrampTreasuryAuthority:
          environment.mockOnrampTreasuryAuthority,
        mockOnrampTreasuryAta: environment.mockOnrampTreasuryAta,
        client,
        clientAta,
        mockOnrampReceipt: receipt,
        associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
        tokenProgram: TOKEN_PROGRAM_ID,
        systemProgram: SystemProgram.programId,
      })
      .signers([authority])
      .rpc();
  }

  it("transfers Mock USDC from the program treasury to a new Client ATA", async () => {
    const client = Keypair.generate();
    const purchaseId = new anchor.BN(1);
    const usdAmountE6 = new anchor.BN(100_000_000);
    const [receipt, receiptBump] = deriveReceipt(client.publicKey, purchaseId);
    const clientAta = getAssociatedTokenAddressSync(
      environment.mockUsdc.mint,
      client.publicKey,
    );
    const treasuryBefore = await getAccount(
      environment.provider.connection,
      environment.mockOnrampTreasuryAta,
    );

    const signature = await executeMockOnramp(
      client.publicKey,
      purchaseId,
      usdAmountE6,
    );

    const [treasuryAfter, clientAfter, receiptData] = await Promise.all([
      getAccount(
        environment.provider.connection,
        environment.mockOnrampTreasuryAta,
      ),
      getAccount(environment.provider.connection, clientAta),
      environment.program.account.mockOnrampReceipt.fetch(receipt),
    ]);
    expect(treasuryAfter.amount).to.equal(
      treasuryBefore.amount - 100_000_000n,
    );
    expect(clientAfter.amount).to.equal(100_000_000n);
    expect(receiptData.purchaseId.eq(purchaseId)).to.equal(true);
    expect(receiptData.client.equals(client.publicKey)).to.equal(true);
    expect(receiptData.clientAta.equals(clientAta)).to.equal(true);
    expect(receiptData.usdAmountE6.eq(usdAmountE6)).to.equal(true);
    expect(receiptData.tokenAmount.eq(usdAmountE6)).to.equal(true);
    expect(
      receiptData.authority.equals(environment.mockOnrampAuthority.publicKey),
    ).to.equal(true);
    expect(receiptData.bump).to.equal(receiptBump);

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
    ).filter((event) => event.name.toLowerCase() === "mockonrampcompleted");
    expect(events).to.have.length(1);
  });

  it("rejects a duplicate purchase without transferring twice", async () => {
    const client = Keypair.generate();
    const purchaseId = new anchor.BN(2);
    const amount = new anchor.BN(50_000_000);
    await executeMockOnramp(client.publicKey, purchaseId, amount);
    const clientAta = getAssociatedTokenAddressSync(
      environment.mockUsdc.mint,
      client.publicKey,
    );
    const clientBefore = await getAccount(
      environment.provider.connection,
      clientAta,
    );
    const treasuryBefore = await getAccount(
      environment.provider.connection,
      environment.mockOnrampTreasuryAta,
    );

    await expectRejected(() =>
      executeMockOnramp(client.publicKey, purchaseId, amount),
    );

    expect(
      (await getAccount(environment.provider.connection, clientAta)).amount,
    ).to.equal(clientBefore.amount);
    expect(
      (
        await getAccount(
          environment.provider.connection,
          environment.mockOnrampTreasuryAta,
        )
      ).amount,
    ).to.equal(treasuryBefore.amount);
  });

  it("rejects an authority other than the configured Mock On-ramp Authority", async () => {
    const client = Keypair.generate();
    const fakeAuthority = Keypair.generate();
    const airdropSignature =
      await environment.provider.connection.requestAirdrop(
        fakeAuthority.publicKey,
        LAMPORTS_PER_SOL,
      );
    await environment.provider.connection.confirmTransaction(
      airdropSignature,
      "confirmed",
    );
    const purchaseId = new anchor.BN(3);
    const [receipt] = deriveReceipt(client.publicKey, purchaseId);

    await expectRejected(
      () =>
        executeMockOnramp(
          client.publicKey,
          purchaseId,
          new anchor.BN(10_000_000),
          fakeAuthority,
        ),
      "UnauthorizedMockOnrampAuthority",
    );
    expect(
      await environment.provider.connection.getAccountInfo(receipt),
    ).to.equal(null);
  });

  it("rejects zero and above-limit purchase amounts", async () => {
    const zeroClient = Keypair.generate();
    const largeClient = Keypair.generate();

    await expectRejected(
      () =>
        executeMockOnramp(
          zeroClient.publicKey,
          new anchor.BN(4),
          new anchor.BN(0),
        ),
      "InvalidMockOnrampAmount",
    );
    await expectRejected(
      () =>
        executeMockOnramp(
          largeClient.publicKey,
          new anchor.BN(5),
          environment.maxMockOnrampAmount.addn(1),
        ),
      "MockOnrampAmountTooLarge",
    );
  });

  it("rejects purchases while Mock On-ramp is disabled", async () => {
    const client = Keypair.generate();
    const purchaseId = new anchor.BN(6);

    await environment.program.methods
      .configureMockOnramp(
        environment.mockOnrampAuthority.publicKey,
        environment.maxMockOnrampAmount,
        false,
      )
      .accountsStrict({
        admin: environment.payer.publicKey,
        config: environment.configPda,
      })
      .rpc();
    try {
      await expectRejected(
        () =>
          executeMockOnramp(
            client.publicKey,
            purchaseId,
            new anchor.BN(10_000_000),
          ),
        "MockOnrampDisabled",
      );
    } finally {
      await environment.program.methods
        .configureMockOnramp(
          environment.mockOnrampAuthority.publicKey,
          environment.maxMockOnrampAmount,
          true,
        )
        .accountsStrict({
          admin: environment.payer.publicKey,
          config: environment.configPda,
        })
        .rpc();
    }
  });

  it("restricts Mock On-ramp configuration to Admin and valid settings", async () => {
    const fakeAdmin = Keypair.generate();
    await expectRejected(
      () =>
        environment.program.methods
          .configureMockOnramp(
            environment.mockOnrampAuthority.publicKey,
            environment.maxMockOnrampAmount,
            true,
          )
          .accountsStrict({
            admin: fakeAdmin.publicKey,
            config: environment.configPda,
          })
          .signers([fakeAdmin])
          .rpc(),
      "UnauthorizedAdmin",
    );
    await expectRejected(
      () =>
        environment.program.methods
          .configureMockOnramp(
            PublicKey.default,
            environment.maxMockOnrampAmount,
            true,
          )
          .accountsStrict({
            admin: environment.payer.publicKey,
            config: environment.configPda,
          })
          .rpc(),
      "InvalidMockOnrampAuthority",
    );
    await expectRejected(
      () =>
        environment.program.methods
          .configureMockOnramp(
            environment.mockOnrampAuthority.publicKey,
            new anchor.BN(0),
            true,
          )
          .accountsStrict({
            admin: environment.payer.publicKey,
            config: environment.configPda,
          })
          .rpc(),
      "InvalidMaxMockOnrampAmount",
    );
  });

  it("rolls back the receipt and Client ATA when treasury funds are insufficient", async () => {
    const client = Keypair.generate();
    const purchaseId = new anchor.BN(7);
    const [receipt] = deriveReceipt(client.publicKey, purchaseId);
    const clientAta = getAssociatedTokenAddressSync(
      environment.mockUsdc.mint,
      client.publicKey,
    );
    const treasuryBefore = await getAccount(
      environment.provider.connection,
      environment.mockOnrampTreasuryAta,
    );

    await expectRejected(() =>
      executeMockOnramp(
        client.publicKey,
        purchaseId,
        new anchor.BN(15_000_000_000),
      ),
    );
    expect(
      await environment.provider.connection.getAccountInfo(receipt),
    ).to.equal(null);
    expect(
      await environment.provider.connection.getAccountInfo(clientAta),
    ).to.equal(null);
    expect(
      (
        await getAccount(
          environment.provider.connection,
          environment.mockOnrampTreasuryAta,
        )
      ).amount,
    ).to.equal(treasuryBefore.amount);
  });
});
