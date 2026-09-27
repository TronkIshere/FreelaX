import * as anchor from "@anchor-lang/core";
import { expect } from "chai";
import {
  Keypair,
  LAMPORTS_PER_SOL,
  PublicKey,
  SystemProgram,
} from "@solana/web3.js";
import {
  getAccount,
  getOrCreateAssociatedTokenAccount,
  mintTo,
  TOKEN_PROGRAM_ID,
} from "@solana/spl-token";

import {
  getTestEnvironment,
  type TestEnvironment,
} from "../helpers/test-environment";
import { expectRejected } from "../helpers/invoice";
import { publishRateFixture } from "../helpers/rate";

describe("Record Off-ramp", () => {
  let environment: TestEnvironment;
  let treasuryAta: PublicKey;
  let sequence = 0;

  before(async () => {
    environment = await getTestEnvironment();

    const airdropSignature = await environment.provider.connection.requestAirdrop(
      environment.rateAuthority.publicKey,
      LAMPORTS_PER_SOL,
    );
    await environment.provider.connection.confirmTransaction(
      airdropSignature,
      "confirmed",
    );

    treasuryAta = (
      await getOrCreateAssociatedTokenAccount(
        environment.provider.connection,
        environment.payer,
        environment.mockUsdc.mint,
        environment.treasuryAuthority.publicKey,
      )
    ).address;
  });

  async function createPendingWithdrawal() {
    sequence += 1;
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(200 + sequence),
    );
    const freelancer = Keypair.generate();
    const airdropSignature = await environment.provider.connection.requestAirdrop(
      freelancer.publicKey,
      LAMPORTS_PER_SOL,
    );
    await environment.provider.connection.confirmTransaction(
      airdropSignature,
      "confirmed",
    );
    const freelancerAta = (
      await getOrCreateAssociatedTokenAccount(
        environment.provider.connection,
        environment.payer,
        environment.mockUsdc.mint,
        freelancer.publicKey,
      )
    ).address;
    await mintTo(
      environment.provider.connection,
      environment.payer,
      environment.mockUsdc.mint,
      freelancerAta,
      environment.mockUsdc.mintAuthority,
      10_000_000n,
    );

    const withdrawalId = new anchor.BN(sequence);
    const [withdrawalRecord] = PublicKey.findProgramAddressSync(
      [
        Buffer.from("withdrawal"),
        freelancer.publicKey.toBuffer(),
        withdrawalId.toArrayLike(Buffer, "le", 8),
      ],
      environment.program.programId,
    );

    await environment.program.methods
      .requestOfframp(withdrawalId, new anchor.BN(10_000_000))
      .accountsStrict({
        freelancer: freelancer.publicKey,
        config: environment.configPda,
        rateSnapshot,
        acceptedMint: environment.mockUsdc.mint,
        freelancerAta,
        treasuryAuthority: environment.treasuryAuthority.publicKey,
        treasuryAta,
        withdrawalRecord,
        tokenProgram: TOKEN_PROGRAM_ID,
        systemProgram: SystemProgram.programId,
      })
      .signers([freelancer])
      .rpc();

    return { freelancerAta, withdrawalId, withdrawalRecord };
  }

  it("lets the configured Settlement Oracle complete a Pending withdrawal", async () => {
    const fixture = await createPendingWithdrawal();
    const freelancerBefore = await getAccount(
      environment.provider.connection,
      fixture.freelancerAta,
    );
    const treasuryBefore = await getAccount(
      environment.provider.connection,
      treasuryAta,
    );

    const signature = await environment.program.methods
      .recordOfframp()
      .accountsStrict({
        oracleAuthority: environment.oracleAuthority.publicKey,
        config: environment.configPda,
        withdrawalRecord: fixture.withdrawalRecord,
      })
      .signers([environment.oracleAuthority])
      .rpc();

    const record = await environment.program.account.withdrawalRecord.fetch(
      fixture.withdrawalRecord,
    );
    expect(record.status).to.deep.equal({ completed: {} });
    expect(record.completedAt).to.not.equal(null);
    expect(record.completedAt!.toNumber()).to.be.greaterThan(0);
    expect(
      (await getAccount(environment.provider.connection, fixture.freelancerAta))
        .amount,
    ).to.equal(freelancerBefore.amount);
    expect(
      (await getAccount(environment.provider.connection, treasuryAta)).amount,
    ).to.equal(treasuryBefore.amount);

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
    ).filter((event) => event.name.toLowerCase() === "offrampcompleted");
    expect(events).to.have.length(1);
    expect(
      (events[0].data as any).withdrawalRecord.equals(fixture.withdrawalRecord),
    ).to.equal(true);
  });

  it("rejects a signer other than the configured Settlement Oracle", async () => {
    const fixture = await createPendingWithdrawal();
    const fakeOracle = Keypair.generate();

    await expectRejected(
      () =>
        environment.program.methods
          .recordOfframp()
          .accountsStrict({
            oracleAuthority: fakeOracle.publicKey,
            config: environment.configPda,
            withdrawalRecord: fixture.withdrawalRecord,
          })
          .signers([fakeOracle])
          .rpc(),
      "UnauthorizedOracle",
    );
    const record = await environment.program.account.withdrawalRecord.fetch(
      fixture.withdrawalRecord,
    );
    expect(record.status).to.deep.equal({ pending: {} });
  });

  it("rejects the correct Oracle public key without its signature", async () => {
    const fixture = await createPendingWithdrawal();

    await expectRejected(() =>
      environment.program.methods
        .recordOfframp()
        .accountsStrict({
          oracleAuthority: environment.oracleAuthority.publicKey,
          config: environment.configPda,
          withdrawalRecord: fixture.withdrawalRecord,
        })
        .rpc(),
    );
    const record = await environment.program.account.withdrawalRecord.fetch(
      fixture.withdrawalRecord,
    );
    expect(record.status).to.deep.equal({ pending: {} });
  });

  it("rejects completing the same withdrawal twice", async () => {
    const fixture = await createPendingWithdrawal();

    await environment.program.methods
      .recordOfframp()
      .accountsStrict({
        oracleAuthority: environment.oracleAuthority.publicKey,
        config: environment.configPda,
        withdrawalRecord: fixture.withdrawalRecord,
      })
      .signers([environment.oracleAuthority])
      .rpc();
    const firstCompletion = await environment.program.account.withdrawalRecord.fetch(
      fixture.withdrawalRecord,
    );

    await expectRejected(
      () =>
        environment.program.methods
          .recordOfframp()
          .accountsStrict({
            oracleAuthority: environment.oracleAuthority.publicKey,
            config: environment.configPda,
            withdrawalRecord: fixture.withdrawalRecord,
          })
          .signers([environment.oracleAuthority])
          .rpc(),
      "WithdrawalNotPending",
    );
    const secondCompletion = await environment.program.account.withdrawalRecord.fetch(
      fixture.withdrawalRecord,
    );
    expect(secondCompletion.status).to.deep.equal({ completed: {} });
    expect(secondCompletion.completedAt!.eq(firstCompletion.completedAt!)).to.equal(
      true,
    );
  });

  it("rejects completion while Config is paused", async () => {
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
    const fixture = await createPendingWithdrawal();

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
            .recordOfframp()
            .accountsStrict({
              oracleAuthority: oracleAuthority.publicKey,
              config: configPda,
              withdrawalRecord: fixture.withdrawalRecord,
            })
            .signers([oracleAuthority])
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

    const record = await program.account.withdrawalRecord.fetch(
      fixture.withdrawalRecord,
    );
    expect(record.status).to.deep.equal({ pending: {} });
    expect(record.completedAt).to.equal(null);
  });
});
