import * as anchor from "@anchor-lang/core";
import { expect } from "chai";
import { randomBytes } from "crypto";
import { Keypair, LAMPORTS_PER_SOL, PublicKey, SystemProgram } from "@solana/web3.js";
import {
  ASSOCIATED_TOKEN_PROGRAM_ID, TOKEN_PROGRAM_ID, getAccount,
  getAssociatedTokenAddressSync, getOrCreateAssociatedTokenAccount,
} from "@solana/spl-token";
import { getTestEnvironment, type TestEnvironment } from "../helpers/test-environment";

const ZERO_HASH = Array(32).fill(0);
const HASH = Array(32).fill(7);

describe("Milestone escrow", () => {
  let env: TestEnvironment;
  before(async () => {
    env = await getTestEnvironment();
    for (const key of [env.mockUsdc.client.publicKey]) {
      const sig = await env.provider.connection.requestAirdrop(key, LAMPORTS_PER_SOL);
      await env.provider.connection.confirmTransaction(sig, "confirmed");
    }
  });

  async function createEscrow(amount: number, fundingExpiresAt?: number) {
    const freelancer = Keypair.generate();
    const sig = await env.provider.connection.requestAirdrop(freelancer.publicKey, LAMPORTS_PER_SOL);
    await env.provider.connection.confirmTransaction(sig, "confirmed");
    const id = [...randomBytes(16)];
    const [escrow] = PublicKey.findProgramAddressSync(
      [Buffer.from("milestone_escrow"), Buffer.from(id)], env.program.programId,
    );
    const vault = getAssociatedTokenAddressSync(env.mockUsdc.mint, escrow, true);
    const freelancerAta = (await getOrCreateAssociatedTokenAccount(
      env.provider.connection, env.payer, env.mockUsdc.mint, freelancer.publicKey,
    )).address;
    const due = Math.floor(Date.now() / 1000) + 3600;
    await env.program.methods.fundMilestoneEscrow(
      id, freelancer.publicKey, new anchor.BN(amount), new anchor.BN(fundingExpiresAt ?? due),
      new anchor.BN(due), 72, 2,
    ).accountsStrict({
      client: env.mockUsdc.client.publicKey,
      marketplaceAuthority: env.payer.publicKey,
      config: env.configPda,
      mint: env.mockUsdc.mint,
      escrow,
      vault,
      clientAta: env.mockUsdc.clientAta,
      tokenProgram: TOKEN_PROGRAM_ID,
      associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
      systemProgram: SystemProgram.programId,
    }).signers([env.mockUsdc.client]).rpc();
    return { freelancer, escrow, vault, freelancerAta, amount };
  }

  it("rejects funding after the locked funding deadline without moving tokens", async () => {
    const before = (await getAccount(env.provider.connection, env.mockUsdc.clientAta)).amount;
    let rejected = false;
    try {
      await createEscrow(5_000_000, Math.floor(Date.now() / 1000) - 60);
    } catch (error) { rejected = String(error).includes("InvalidEscrowTerms"); }
    expect(rejected).eq(true);
    expect((await getAccount(env.provider.connection, env.mockUsdc.clientAta)).amount).eq(before);
  });

  function settlementAccounts(x: Awaited<ReturnType<typeof createEscrow>>,
                              actor: PublicKey, recipient: PublicKey,
                              recipientAta: PublicKey) {
    return {
      actor, escrow: x.escrow, mint: env.mockUsdc.mint,
      vault: x.vault, recipient, recipientAta,
      tokenProgram: TOKEN_PROGRAM_ID,
      associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
      systemProgram: SystemProgram.programId,
    };
  }

  it("holds tokens, refuses early timeout, then releases once after Client approval", async () => {
    const x = await createEscrow(75_000_000);
    expect((await getAccount(env.provider.connection, x.vault)).amount).eq(75_000_000n);
    await env.program.methods.submitEscrowWork(HASH).accountsStrict({
      freelancer: x.freelancer.publicKey, marketplaceAuthority: env.payer.publicKey,
      escrow: x.escrow,
    }).signers([x.freelancer]).rpc();
    const submitted = await env.program.account.milestoneEscrow.fetch(x.escrow);
    expect(submitted.status).deep.eq({ submitted: {} });
    expect(submitted.reviewDueAt).not.eq(null);
    let rejected = false;
    try {
      await env.program.methods.settleMilestoneEscrow(true, ZERO_HASH)
        .accountsStrict(settlementAccounts(x, env.payer.publicKey,
          x.freelancer.publicKey, x.freelancerAta)).rpc();
    } catch (error) { rejected = String(error).includes("ReviewDeadlineNotPassed"); }
    expect(rejected).eq(true);
    const before = (await getAccount(env.provider.connection, x.freelancerAta)).amount;
    await env.program.methods.settleMilestoneEscrow(true, ZERO_HASH)
      .accountsStrict(settlementAccounts(x, env.mockUsdc.client.publicKey,
        x.freelancer.publicKey, x.freelancerAta))
      .signers([env.mockUsdc.client]).rpc();
    expect((await getAccount(env.provider.connection, x.vault)).amount).eq(0n);
    expect((await getAccount(env.provider.connection, x.freelancerAta)).amount).eq(before + 75_000_000n);
    expect((await env.program.account.milestoneEscrow.fetch(x.escrow)).status).deep.eq({ released: {} });
    let duplicateRejected = false;
    try {
      await env.program.methods.settleMilestoneEscrow(true, ZERO_HASH)
        .accountsStrict(settlementAccounts(x, env.mockUsdc.client.publicKey,
          x.freelancer.publicKey, x.freelancerAta))
        .signers([env.mockUsdc.client]).rpc();
    } catch (error) { duplicateRejected = String(error).includes("InvalidEscrowState"); }
    expect(duplicateRejected).eq(true);
  });

  it("requires the marketplace authority to co-sign a work submission", async () => {
    const x = await createEscrow(10_000_000);
    const outsider = Keypair.generate();
    let rejected = false;
    try {
      await env.program.methods.submitEscrowWork(HASH).accountsStrict({
        freelancer: x.freelancer.publicKey, marketplaceAuthority: outsider.publicKey,
        escrow: x.escrow,
      }).signers([x.freelancer, outsider]).rpc();
    } catch (error) { rejected = String(error).includes("UnauthorizedEscrowArbiter"); }
    expect(rejected).eq(true);
    expect((await env.program.account.milestoneEscrow.fetch(x.escrow)).status).deep.eq({ funded: {} });
  });

  it("uses one Client-approved delivery extension", async () => {
    const x = await createEscrow(8_000_000);
    const original = await env.program.account.milestoneEscrow.fetch(x.escrow);
    const newDue = original.originalDeliveryDueAt.toNumber() + 2 * 24 * 3600;
    await env.program.methods.requestEscrowExtension(new anchor.BN(newDue)).accountsStrict({
      freelancer: x.freelancer.publicKey, escrow: x.escrow,
    }).signers([x.freelancer]).rpc();
    await env.program.methods.approveEscrowExtension().accountsStrict({
      client: env.mockUsdc.client.publicKey, escrow: x.escrow,
    }).signers([env.mockUsdc.client]).rpc();
    const extended = await env.program.account.milestoneEscrow.fetch(x.escrow);
    expect(extended.deliveryDueAt.toNumber()).eq(newDue);
    expect(extended.extensionUsed).eq(true);
    let rejected = false;
    try {
      await env.program.methods.requestEscrowExtension(new anchor.BN(newDue + 3600)).accountsStrict({
        freelancer: x.freelancer.publicKey, escrow: x.escrow,
      }).signers([x.freelancer]).rpc();
    } catch (error) { rejected = String(error).includes("ExtensionAlreadyUsed"); }
    expect(rejected).eq(true);
  });

  it("commits revision feedback hash and starts a fresh review after resubmission", async () => {
    const x = await createEscrow(9_000_000);
    await env.program.methods.submitEscrowWork(HASH).accountsStrict({
      freelancer: x.freelancer.publicKey, marketplaceAuthority: env.payer.publicKey,
      escrow: x.escrow,
    }).signers([x.freelancer]).rpc();
    const first = await env.program.account.milestoneEscrow.fetch(x.escrow);
    const feedbackHash = Array(32).fill(11);
    await env.program.methods.requestEscrowRevision(feedbackHash).accountsStrict({
      client: env.mockUsdc.client.publicKey, marketplaceAuthority: env.payer.publicKey,
      escrow: x.escrow,
    }).signers([env.mockUsdc.client]).rpc();
    const revision = await env.program.account.milestoneEscrow.fetch(x.escrow);
    expect(revision.status).deep.eq({ revision: {} });
    expect(revision.reviewDueAt).eq(null);
    expect([...revision.revisionHash!]).deep.eq(feedbackHash);
    const updatedHash = Array(32).fill(12);
    await env.program.methods.submitEscrowWork(updatedHash).accountsStrict({
      freelancer: x.freelancer.publicKey, marketplaceAuthority: env.payer.publicKey,
      escrow: x.escrow,
    }).signers([x.freelancer]).rpc();
    const resubmitted = await env.program.account.milestoneEscrow.fetch(x.escrow);
    expect(resubmitted.status).deep.eq({ submitted: {} });
    expect(resubmitted.submissionCount).eq(2);
    expect(resubmitted.reviewDueAt!.toNumber()).at.least(first.reviewDueAt!.toNumber());
  });

  it("refunds only with both participant signatures", async () => {
    const x = await createEscrow(20_000_000);
    let rejected = false;
    try {
      await env.program.methods.refundMutualEscrow().accountsStrict({
          client: env.mockUsdc.client.publicKey, freelancer: x.freelancer.publicKey,
          escrow: x.escrow, mint: env.mockUsdc.mint, vault: x.vault,
          clientAta: env.mockUsdc.clientAta, tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([env.mockUsdc.client]).rpc();
    } catch (error) { rejected = true; }
    expect(rejected).eq(true);
    await env.program.methods.refundMutualEscrow().accountsStrict({
        client: env.mockUsdc.client.publicKey, freelancer: x.freelancer.publicKey,
        escrow: x.escrow, mint: env.mockUsdc.mint, vault: x.vault,
        clientAta: env.mockUsdc.clientAta, tokenProgram: TOKEN_PROGRAM_ID,
      })
      .signers([env.mockUsdc.client, x.freelancer]).rpc();
    expect((await getAccount(env.provider.connection, x.vault)).amount).eq(0n);
    expect((await env.program.account.milestoneEscrow.fetch(x.escrow)).status).deep.eq({ refunded: {} });
  });

  it("freezes a submitted escrow on dispute and lets only Admin resolve it", async () => {
    const x = await createEscrow(15_000_000);
    await env.program.methods.submitEscrowWork(HASH).accountsStrict({
      freelancer: x.freelancer.publicKey, marketplaceAuthority: env.payer.publicKey,
      escrow: x.escrow,
    }).signers([x.freelancer]).rpc();
    await env.program.methods.openEscrowDispute(HASH).accountsStrict({
      actor: env.mockUsdc.client.publicKey, escrow: x.escrow,
    }).signers([env.mockUsdc.client]).rpc();
    let rejected = false;
    try {
      await env.program.methods.settleMilestoneEscrow(true, HASH)
        .accountsStrict(settlementAccounts(x, env.mockUsdc.client.publicKey,
          x.freelancer.publicKey, x.freelancerAta))
        .signers([env.mockUsdc.client]).rpc();
    } catch (error) { rejected = String(error).includes("UnauthorizedEscrowArbiter"); }
    expect(rejected).eq(true);
    await env.program.methods.settleMilestoneEscrow(false, HASH)
      .accountsStrict(settlementAccounts(x, env.payer.publicKey,
        env.mockUsdc.client.publicKey, env.mockUsdc.clientAta)).rpc();
    expect((await env.program.account.milestoneEscrow.fetch(x.escrow)).status).deep.eq({ refunded: {} });
  });
});
