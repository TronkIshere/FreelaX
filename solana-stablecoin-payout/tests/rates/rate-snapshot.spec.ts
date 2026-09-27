import * as anchor from "@anchor-lang/core";
import { expect } from "chai";
import {
  Keypair,
  LAMPORTS_PER_SOL,
  PublicKey,
  SystemProgram,
} from "@solana/web3.js";

import {
  getTestEnvironment,
  type TestEnvironment,
} from "../helpers/test-environment";

const RATE_SEED = Buffer.from("rate");
const USDC_USD_E6 = new anchor.BN(999_800);
const USD_VND_E6 = new anchor.BN(25_000_000_000);
const USDC_VND_E6 = new anchor.BN(24_995_000_000);
const SOURCE_HASH = Array.from({ length: 32 }, (_, index) => index);

describe("RateSnapshot", () => {
  let environment: TestEnvironment;

  before(async () => {
    environment = await getTestEnvironment();

    const signature = await environment.provider.connection.requestAirdrop(
      environment.rateAuthority.publicKey,
      LAMPORTS_PER_SOL
    );
    await environment.provider.connection.confirmTransaction(
      signature,
      "confirmed"
    );
  });

  function deriveRateSnapshot(rateId: anchor.BN): [PublicKey, number] {
    return PublicKey.findProgramAddressSync(
      [RATE_SEED, rateId.toArrayLike(Buffer, "le", 8)],
      environment.program.programId
    );
  }

  async function publishRate(
    rateId: anchor.BN,
    options: {
      authority?: Keypair;
      usdcUsdE6?: anchor.BN;
      usdVndE6?: anchor.BN;
      observedAt?: anchor.BN;
      expiresAt?: anchor.BN;
    } = {}
  ): Promise<string> {
    const authority = options.authority ?? environment.rateAuthority;
    const observedAt =
      options.observedAt ?? new anchor.BN(Math.floor(Date.now() / 1000));
    const expiresAt = options.expiresAt ?? observedAt.addn(120);
    const [rateSnapshot] = deriveRateSnapshot(rateId);

    return environment.program.methods
      .publishRate(
        rateId,
        options.usdcUsdE6 ?? USDC_USD_E6,
        options.usdVndE6 ?? USD_VND_E6,
        observedAt,
        expiresAt,
        SOURCE_HASH
      )
      .accountsStrict({
        rateAuthority: authority.publicKey,
        config: environment.configPda,
        rateSnapshot,
        systemProgram: SystemProgram.programId,
      })
      .signers([authority])
      .rpc();
  }

  async function expectRejected(action: () => Promise<unknown>): Promise<void> {
    let rejected = false;

    try {
      await action();
    } catch {
      rejected = true;
    }

    expect(rejected).to.equal(true);
  }

  it("lets the configured Rate Authority publish an immutable snapshot", async () => {
    const rateId = new anchor.BN(1);
    const observedAt = new anchor.BN(Math.floor(Date.now() / 1000));
    const expiresAt = observedAt.addn(120);
    const [rateSnapshotPda, rateSnapshotBump] = deriveRateSnapshot(rateId);

    const signature = await publishRate(rateId, {
      observedAt,
      expiresAt,
    });

    const snapshot = await environment.program.account.rateSnapshot.fetch(
      rateSnapshotPda
    );

    expect(snapshot.rateId.eq(rateId)).to.equal(true);
    expect(snapshot.usdcUsdE6.eq(USDC_USD_E6)).to.equal(true);
    expect(snapshot.usdVndE6.eq(USD_VND_E6)).to.equal(true);
    expect(snapshot.usdcVndE6.eq(USDC_VND_E6)).to.equal(true);
    expect(snapshot.observedAt.eq(observedAt)).to.equal(true);
    expect(snapshot.expiresAt.eq(expiresAt)).to.equal(true);
    expect(snapshot.sourceHash).to.deep.equal(SOURCE_HASH);
    expect(
      snapshot.publisher.equals(environment.rateAuthority.publicKey)
    ).to.equal(true);
    expect(snapshot.bump).to.equal(rateSnapshotBump);

    await environment.provider.connection.confirmTransaction(
      signature,
      "confirmed"
    );
    const transaction = await environment.provider.connection.getTransaction(
      signature,
      {
        commitment: "confirmed",
        maxSupportedTransactionVersion: 0,
      }
    );
    expect(transaction).to.not.equal(null);

    const parser = new anchor.EventParser(
      environment.program.programId,
      environment.program.coder
    );
    const events = Array.from(
      parser.parseLogs(transaction!.meta!.logMessages!)
    ).filter((event) => event.name.toLowerCase() === "ratepublished");

    expect(events).to.have.length(1);
    const published = events[0].data as any;

    expect(published.rateSnapshot.equals(rateSnapshotPda)).to.equal(true);
    expect(published.rateId.eq(rateId)).to.equal(true);
    expect(published.usdcUsdE6.eq(USDC_USD_E6)).to.equal(true);
    expect(published.usdVndE6.eq(USD_VND_E6)).to.equal(true);
    expect(published.usdcVndE6.eq(USDC_VND_E6)).to.equal(true);
    expect(published.observedAt.eq(observedAt)).to.equal(true);
    expect(published.expiresAt.eq(expiresAt)).to.equal(true);
    expect(published.sourceHash).to.deep.equal(SOURCE_HASH);
  });

  it("rejects a signer that is not the configured Rate Authority", async () => {
    await expectRejected(() =>
      publishRate(new anchor.BN(2), { authority: Keypair.generate() })
    );
  });

  it("rejects the correct Rate Authority public key without its signature", async () => {
    const rateId = new anchor.BN(3);
    const observedAt = new anchor.BN(Math.floor(Date.now() / 1000));
    const [rateSnapshot] = deriveRateSnapshot(rateId);

    await expectRejected(() =>
      environment.program.methods
        .publishRate(
          rateId,
          USDC_USD_E6,
          USD_VND_E6,
          observedAt,
          observedAt.addn(120),
          SOURCE_HASH
        )
        .accountsStrict({
          rateAuthority: environment.rateAuthority.publicKey,
          config: environment.configPda,
          rateSnapshot,
          systemProgram: SystemProgram.programId,
        })
        .rpc()
    );
  });

  it("rejects either component rate when it is zero", async () => {
    await expectRejected(() =>
      publishRate(new anchor.BN(4), { usdcUsdE6: new anchor.BN(0) })
    );
    await expectRejected(() =>
      publishRate(new anchor.BN(5), { usdVndE6: new anchor.BN(0) })
    );
  });

  it("rejects expiration at or before observation", async () => {
    const observedAt = new anchor.BN(Math.floor(Date.now() / 1000));

    await expectRejected(() =>
      publishRate(new anchor.BN(6), {
        observedAt,
        expiresAt: observedAt,
      })
    );
  });

  it("rejects an observation unreasonably far in the future", async () => {
    const observedAt = new anchor.BN(Math.floor(Date.now() / 1000) + 3_600);

    await expectRejected(() =>
      publishRate(new anchor.BN(7), {
        observedAt,
        expiresAt: observedAt.addn(120),
      })
    );
  });

  it("rejects a snapshot lifetime above the configured maximum", async () => {
    const observedAt = new anchor.BN(Math.floor(Date.now() / 1000));

    await expectRejected(() =>
      publishRate(new anchor.BN(9), {
        observedAt,
        expiresAt: observedAt.addn(301),
      })
    );
  });

  it("rejects publication while the system is paused", async () => {
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

    await program.methods
      .updateConfig(
        treasuryAuthority.publicKey,
        rateAuthority.publicKey,
        oracleAuthority.publicKey,
        maxRateAgeSeconds,
        true
      )
      .accountsStrict({
        admin: payer.publicKey,
        config: configPda,
        acceptedMint: mockUsdc.mint,
      })
      .rpc();

    try {
      await expectRejected(() => publishRate(new anchor.BN(10)));
    } finally {
      await program.methods
        .updateConfig(
          treasuryAuthority.publicKey,
          rateAuthority.publicKey,
          oracleAuthority.publicKey,
          maxRateAgeSeconds,
          false
        )
        .accountsStrict({
          admin: payer.publicKey,
          config: configPda,
          acceptedMint: mockUsdc.mint,
        })
        .rpc();
    }
  });

  it("rejects a duplicate Rate ID", async () => {
    await expectRejected(() => publishRate(new anchor.BN(1)));
  });

  it("rejects a combined rate that cannot fit in u64", async () => {
    const maximumU64 = new anchor.BN("18446744073709551615");

    await expectRejected(() =>
      publishRate(new anchor.BN(8), {
        usdcUsdE6: maximumU64,
        usdVndE6: maximumU64,
      })
    );
  });
});
