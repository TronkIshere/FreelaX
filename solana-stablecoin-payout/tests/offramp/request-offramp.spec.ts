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
  mintTo,
  TOKEN_PROGRAM_ID,
} from "@solana/spl-token";

import {
  getTestEnvironment,
  type TestEnvironment,
} from "../helpers/test-environment";
import { expectRejected } from "../helpers/invoice";
import {
  publishRateFixture,
  TEST_USDC_VND_E6,
} from "../helpers/rate";

describe("Request Off-ramp", () => {
  let environment: TestEnvironment;
  let treasuryAta: PublicKey;

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

  function deriveWithdrawalRecord(
    freelancer: PublicKey,
    withdrawalId: anchor.BN,
  ): [PublicKey, number] {
    return PublicKey.findProgramAddressSync(
      [
        Buffer.from("withdrawal"),
        freelancer.toBuffer(),
        withdrawalId.toArrayLike(Buffer, "le", 8),
      ],
      environment.program.programId,
    );
  }

  async function createFreelancer(tokenAmount: bigint) {
    const freelancer = Keypair.generate();
    const signature = await environment.provider.connection.requestAirdrop(
      freelancer.publicKey,
      LAMPORTS_PER_SOL,
    );
    await environment.provider.connection.confirmTransaction(
      signature,
      "confirmed",
    );

    const freelancerAta = await getOrCreateAssociatedTokenAccount(
      environment.provider.connection,
      environment.payer,
      environment.mockUsdc.mint,
      freelancer.publicKey,
    );
    if (tokenAmount > 0n) {
      await mintTo(
        environment.provider.connection,
        environment.payer,
        environment.mockUsdc.mint,
        freelancerAta.address,
        environment.mockUsdc.mintAuthority,
        tokenAmount,
      );
    }

    return { freelancer, freelancerAta: freelancerAta.address };
  }

  async function requestOfframp(
    freelancer: Keypair,
    freelancerAta: PublicKey,
    rateSnapshot: PublicKey,
    withdrawalId: anchor.BN,
    tokenAmount: anchor.BN,
    overrides: Partial<{
      acceptedMint: PublicKey;
      freelancerAta: PublicKey;
      treasuryAuthority: PublicKey;
      treasuryAta: PublicKey;
    }> = {},
  ): Promise<string> {
    const [withdrawalRecord] = deriveWithdrawalRecord(
      freelancer.publicKey,
      withdrawalId,
    );

    return environment.program.methods
      .requestOfframp(withdrawalId, tokenAmount)
      .accountsStrict({
        freelancer: freelancer.publicKey,
        config: environment.configPda,
        rateSnapshot,
        acceptedMint: overrides.acceptedMint ?? environment.mockUsdc.mint,
        freelancerAta: overrides.freelancerAta ?? freelancerAta,
        treasuryAuthority:
          overrides.treasuryAuthority ?? environment.treasuryAuthority.publicKey,
        treasuryAta: overrides.treasuryAta ?? treasuryAta,
        withdrawalRecord,
        tokenProgram: TOKEN_PROGRAM_ID,
        systemProgram: SystemProgram.programId,
      })
      .signers([freelancer])
      .rpc();
  }

  it("transfers tokens and creates a Pending WithdrawalRecord from a fresh snapshot", async () => {
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(100),
    );
    const { freelancer, freelancerAta } = await createFreelancer(20_000_000n);
    const withdrawalId = new anchor.BN(1);
    const tokenAmount = new anchor.BN(10_000_000);
    const [withdrawalRecord, bump] = deriveWithdrawalRecord(
      freelancer.publicKey,
      withdrawalId,
    );
    const freelancerBefore = await getAccount(
      environment.provider.connection,
      freelancerAta,
    );
    const treasuryBefore = await getAccount(
      environment.provider.connection,
      treasuryAta,
    );
    const supplyBefore = await getMint(
      environment.provider.connection,
      environment.mockUsdc.mint,
    );

    const signature = await requestOfframp(
      freelancer,
      freelancerAta,
      rateSnapshot,
      withdrawalId,
      tokenAmount,
    );

    const [record, freelancerAfter, treasuryAfter, supplyAfter] =
      await Promise.all([
        environment.program.account.withdrawalRecord.fetch(withdrawalRecord),
        getAccount(environment.provider.connection, freelancerAta),
        getAccount(environment.provider.connection, treasuryAta),
        getMint(environment.provider.connection, environment.mockUsdc.mint),
      ]);
    const expectedFiat = new anchor.BN(249_950);

    expect(record.withdrawalId.eq(withdrawalId)).to.equal(true);
    expect(record.freelancer.equals(freelancer.publicKey)).to.equal(true);
    expect(record.tokenAmount.eq(tokenAmount)).to.equal(true);
    expect(record.mint.equals(environment.mockUsdc.mint)).to.equal(true);
    expect(record.treasury.equals(treasuryAta)).to.equal(true);
    expect(record.rateSnapshot.equals(rateSnapshot)).to.equal(true);
    expect(record.fiatAmountVnd.eq(expectedFiat)).to.equal(true);
    expect(record.status).to.deep.equal({ pending: {} });
    expect(record.requestedAt.toNumber()).to.be.greaterThan(0);
    expect(record.completedAt).to.equal(null);
    expect(record.bump).to.equal(bump);
    expect(freelancerAfter.amount).to.equal(
      freelancerBefore.amount - 10_000_000n,
    );
    expect(treasuryAfter.amount).to.equal(treasuryBefore.amount + 10_000_000n);
    expect(supplyAfter.supply).to.equal(supplyBefore.supply);
    expect(TEST_USDC_VND_E6.eq(new anchor.BN(24_995_000_000))).to.equal(true);

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
    ).filter((event) => event.name.toLowerCase() === "offramprequested");

    expect(events).to.have.length(1);
    expect(
      (events[0].data as any).withdrawalRecord.equals(withdrawalRecord),
    ).to.equal(true);
    expect((events[0].data as any).fiatAmountVnd.eq(expectedFiat)).to.equal(true);
  });

  it("rejects a zero token amount without creating state", async () => {
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(101),
    );
    const { freelancer, freelancerAta } = await createFreelancer(10_000_000n);
    const withdrawalId = new anchor.BN(2);
    const [withdrawalRecord] = deriveWithdrawalRecord(
      freelancer.publicKey,
      withdrawalId,
    );

    await expectRejected(
      () =>
        requestOfframp(
          freelancer,
          freelancerAta,
          rateSnapshot,
          withdrawalId,
          new anchor.BN(0),
        ),
      "InvalidWithdrawalAmount",
    );
    expect(
      await environment.provider.connection.getAccountInfo(withdrawalRecord),
    ).to.equal(null);
  });

  it("rejects an expired RateSnapshot", async () => {
    const now = Math.floor(Date.now() / 1000);
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(102),
      {
        observedAt: new anchor.BN(now - 200),
        expiresAt: new anchor.BN(now - 100),
      },
    );
    const { freelancer, freelancerAta } = await createFreelancer(10_000_000n);

    await expectRejected(
      () =>
        requestOfframp(
          freelancer,
          freelancerAta,
          rateSnapshot,
          new anchor.BN(3),
          new anchor.BN(10_000_000),
        ),
      "RateSnapshotExpired",
    );
  });

  it("rejects the Freelancer public key without its signature", async () => {
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(110),
    );
    const { freelancer, freelancerAta } = await createFreelancer(10_000_000n);
    const withdrawalId = new anchor.BN(13);
    const [withdrawalRecord] = deriveWithdrawalRecord(
      freelancer.publicKey,
      withdrawalId,
    );

    await expectRejected(() =>
      environment.program.methods
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
        .rpc(),
    );
    expect(
      await environment.provider.connection.getAccountInfo(withdrawalRecord),
    ).to.equal(null);
  });

  it("rejects a Treasury Authority other than the one in Config", async () => {
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(111),
    );
    const { freelancer, freelancerAta } = await createFreelancer(10_000_000n);
    const fakeTreasury = Keypair.generate();

    await expectRejected(() =>
      requestOfframp(
        freelancer,
        freelancerAta,
        rateSnapshot,
        new anchor.BN(14),
        new anchor.BN(10_000_000),
        { treasuryAuthority: fakeTreasury.publicKey },
      ),
    );
  });

  it("rejects a request while Config is paused", async () => {
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
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(112),
    );
    const { freelancer, freelancerAta } = await createFreelancer(10_000_000n);
    const withdrawalId = new anchor.BN(15);
    const [withdrawalRecord] = deriveWithdrawalRecord(
      freelancer.publicKey,
      withdrawalId,
    );
    const freelancerBefore = await getAccount(
      environment.provider.connection,
      freelancerAta,
    );
    const treasuryBefore = await getAccount(
      environment.provider.connection,
      treasuryAta,
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
          requestOfframp(
            freelancer,
            freelancerAta,
            rateSnapshot,
            withdrawalId,
            new anchor.BN(10_000_000),
          ),
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

    expect(
      await environment.provider.connection.getAccountInfo(withdrawalRecord),
    ).to.equal(null);
    expect(
      (await getAccount(environment.provider.connection, freelancerAta)).amount,
    ).to.equal(freelancerBefore.amount);
    expect(
      (await getAccount(environment.provider.connection, treasuryAta)).amount,
    ).to.equal(treasuryBefore.amount);
  });

  it("rejects a snapshot older than the current configured maximum age", async () => {
    const {
      program,
      payer,
      configPda,
      mockUsdc,
      treasuryAuthority,
      rateAuthority,
      oracleAuthority,
      maxRateAgeSeconds,
    } = environment;
    const now = Math.floor(Date.now() / 1000);
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(103),
      {
        observedAt: new anchor.BN(now - 120),
        expiresAt: new anchor.BN(now + 60),
      },
    );
    const { freelancer, freelancerAta } = await createFreelancer(10_000_000n);

    await program.methods
      .updateConfig(
        treasuryAuthority.publicKey,
        rateAuthority.publicKey,
        oracleAuthority.publicKey,
        new anchor.BN(60),
        false,
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
          requestOfframp(
            freelancer,
            freelancerAta,
            rateSnapshot,
            new anchor.BN(4),
            new anchor.BN(10_000_000),
          ),
        "RateSnapshotTooOld",
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

  it("rejects a snapshot from a no-longer-configured Rate Authority", async () => {
    const {
      program,
      payer,
      configPda,
      mockUsdc,
      treasuryAuthority,
      rateAuthority,
      oracleAuthority,
      maxRateAgeSeconds,
    } = environment;
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(104),
    );
    const { freelancer, freelancerAta } = await createFreelancer(10_000_000n);
    const replacementRateAuthority = Keypair.generate();

    await program.methods
      .updateConfig(
        treasuryAuthority.publicKey,
        replacementRateAuthority.publicKey,
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

    try {
      await expectRejected(
        () =>
          requestOfframp(
            freelancer,
            freelancerAta,
            rateSnapshot,
            new anchor.BN(5),
            new anchor.BN(10_000_000),
          ),
        "InvalidRatePublisher",
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

  it("rejects wrong mint and token-account ownership", async () => {
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(105),
    );
    const first = await createFreelancer(20_000_000n);
    const second = await createFreelancer(0n);
    const wrongMint = await createMint(
      environment.provider.connection,
      environment.payer,
      environment.mockUsdc.mintAuthority.publicKey,
      null,
      6,
    );

    await expectRejected(
      () =>
        requestOfframp(
          first.freelancer,
          first.freelancerAta,
          rateSnapshot,
          new anchor.BN(6),
          new anchor.BN(10_000_000),
          { acceptedMint: wrongMint },
        ),
      "InvalidInvoiceMint",
    );
    await expectRejected(() =>
      requestOfframp(
        first.freelancer,
        first.freelancerAta,
        rateSnapshot,
        new anchor.BN(7),
        new anchor.BN(10_000_000),
        { freelancerAta: second.freelancerAta },
      ),
    );
    await expectRejected(() =>
      requestOfframp(
        first.freelancer,
        first.freelancerAta,
        rateSnapshot,
        new anchor.BN(8),
        new anchor.BN(10_000_000),
        { treasuryAta: first.freelancerAta },
      ),
    );
  });

  it("rolls back record creation when the Freelancer has insufficient tokens", async () => {
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(106),
    );
    const { freelancer, freelancerAta } = await createFreelancer(0n);
    const withdrawalId = new anchor.BN(9);
    const [withdrawalRecord] = deriveWithdrawalRecord(
      freelancer.publicKey,
      withdrawalId,
    );
    const treasuryBefore = await getAccount(
      environment.provider.connection,
      treasuryAta,
    );

    await expectRejected(() =>
      requestOfframp(
        freelancer,
        freelancerAta,
        rateSnapshot,
        withdrawalId,
        new anchor.BN(10_000_000),
      ),
    );

    expect(
      await environment.provider.connection.getAccountInfo(withdrawalRecord),
    ).to.equal(null);
    expect(
      (await getAccount(environment.provider.connection, treasuryAta)).amount,
    ).to.equal(treasuryBefore.amount);
  });

  it("rejects fiat conversion overflow and sub-one-VND results", async () => {
    const maximumU64 = new anchor.BN("18446744073709551615");
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(107),
      {
        usdcUsdE6: new anchor.BN(1_000_000),
        usdVndE6: maximumU64,
      },
    );
    const { freelancer, freelancerAta } = await createFreelancer(1n);

    await expectRejected(
      () =>
        requestOfframp(
          freelancer,
          freelancerAta,
          rateSnapshot,
          new anchor.BN(10),
          maximumU64,
        ),
      "FiatCalculationOverflow",
    );

    const normalRate = await publishRateFixture(
      environment,
      new anchor.BN(108),
    );
    await expectRejected(
      () =>
        requestOfframp(
          freelancer,
          freelancerAta,
          normalRate,
          new anchor.BN(11),
          new anchor.BN(1),
        ),
      "FiatAmountTooSmall",
    );
  });

  it("rejects a duplicate WithdrawalRecord PDA", async () => {
    const rateSnapshot = await publishRateFixture(
      environment,
      new anchor.BN(109),
    );
    const { freelancer, freelancerAta } = await createFreelancer(20_000_000n);
    const withdrawalId = new anchor.BN(12);

    await requestOfframp(
      freelancer,
      freelancerAta,
      rateSnapshot,
      withdrawalId,
      new anchor.BN(10_000_000),
    );
    await expectRejected(() =>
      requestOfframp(
        freelancer,
        freelancerAta,
        rateSnapshot,
        withdrawalId,
        new anchor.BN(10_000_000),
      ),
    );
  });
});
