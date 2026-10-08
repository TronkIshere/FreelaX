import { randomUUID } from "crypto";
import { readFileSync } from "fs";
import bs58 from "bs58";
import { Keypair, LAMPORTS_PER_SOL, PublicKey, Transaction } from "@solana/web3.js";
import { getAccount, getAssociatedTokenAddressSync, getOrCreateAssociatedTokenAccount, mintTo } from "@solana/spl-token";
import { getTestEnvironment } from "../tests/helpers/test-environment";

const envFile = Object.fromEntries(readFileSync("../.env", "utf8")
  .split(/\r?\n/).filter(line => line.includes("=") && !line.startsWith("#"))
  .map(line => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]));
const signers = (envFile.SOLANA_LOCAL_PRIVATE_KEYS || "").split(";").filter(Boolean)
  .map(value => Keypair.fromSecretKey(bs58.decode(value)));
function signer(address: string): Keypair {
  const found = signers.find(value => value.publicKey.toBase58() === address);
  if (!found) throw new Error(`No local signer for ${address}`);
  return found;
}
const clientWallet = signer(envFile.SOLANA_CUSTODIAL_CLIENT_PUBLIC_KEY);
const freelancerWallet = signer(envFile.DEMO_FREELANCER_SOLANA_PUBLIC_KEY);
const marketplace = process.env.MARKETPLACE_URL ?? "http://127.0.0.1:9191/api/v1";

async function request(method: string, path: string, body?: object, token?: string,
                       headers: Record<string, string> = {}): Promise<any> {
  const response = await fetch(marketplace + path, {
    method,
    headers: { "Content-Type": "application/json", ...headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const json: any = await response.json();
  if (!response.ok || json.code !== 200) {
    throw new Error(`${method} ${path}: HTTP ${response.status} ${JSON.stringify(json)}`);
  }
  return json.data;
}

function signTransaction(base64: string, wallet: Keypair, requireAll = true): string {
  const transaction = Transaction.from(Buffer.from(base64, "base64"));
  transaction.partialSign(wallet);
  return transaction.serialize({ requireAllSignatures: requireAll }).toString("base64");
}

async function waitFor(read: () => Promise<any>, predicate: (value: any) => boolean,
                       label: string, tries = 40): Promise<any> {
  let last: any;
  for (let attempt = 0; attempt < tries; attempt++) {
    last = await read();
    if (predicate(last)) return last;
    await new Promise(resolve => setTimeout(resolve, 750));
  }
  throw new Error(`${label} did not reconcile; last state: ${JSON.stringify(last)}`);
}

async function main() {
  const env = await getTestEnvironment();
  for (const address of [envFile.SOLANA_SYSTEM_FEE_PAYER,
    clientWallet.publicKey.toBase58(), freelancerWallet.publicKey.toBase58()]) {
    const signature = await env.provider.connection.requestAirdrop(
      new PublicKey(address), 3 * LAMPORTS_PER_SOL);
    await env.provider.connection.confirmTransaction(signature, "confirmed");
  }
  const clientAta = (await getOrCreateAssociatedTokenAccount(env.provider.connection,
    env.payer, env.mockUsdc.mint, clientWallet.publicKey)).address;
  await mintTo(env.provider.connection, env.payer, env.mockUsdc.mint, clientAta,
    env.mockUsdc.mintAuthority, 200_000_000n);

  const client = await request("POST", "/auth/sign-in", {
    email: "nguyenhuutrong11133@gmail.com", password: envFile.DEMO_CLIENT_PASSWORD,
  });
  const freelancer = await request("POST", "/auth/sign-in", {
    email: "freelancer.seed@example.com", password: envFile.DEMO_FREELANCER_PASSWORD,
  });
  const clientToken = client.accessToken;
  const freelancerToken = freelancer.accessToken;
  if (!clientToken || !freelancerToken) throw new Error("Demo sign-in did not return access tokens");
  const linkedClient = await request("GET", "/solana/wallet-link", undefined, clientToken);
  const linkedFreelancer = await request("GET", "/solana/wallet-link", undefined, freelancerToken);
  if (linkedClient?.walletAddress !== clientWallet.publicKey.toBase58()
      || linkedFreelancer?.walletAddress !== freelancerWallet.publicKey.toBase58()) {
    throw new Error("Demo users are not linked to the configured local wallets");
  }

  async function assignedJob(amount: number) {
    const job = await request("POST", "/marketplace/jobs", {
      title: `Local escrow E2E ${randomUUID().slice(0, 8)}`,
      description: "Automated local validator escrow proof",
      category: "WEB_FRONTEND", skills: ["TypeScript"], budgetUsd: amount,
      deliveryDueAt: new Date(Date.now() + 25 * 3600_000).toISOString(),
      reviewWindowHours: 72, maxRevisions: 2,
      deliverables: [{ title: "E2E artifact", description: "One verifiable artifact", required: true }],
      acceptanceCriteria: [{ description: "Artifact URL opens", required: true }],
    }, clientToken);
    await request("POST", `/marketplace/jobs/${job.id}/apply`, undefined, freelancerToken);
    const assigned = await request("POST", `/marketplace/jobs/${job.id}/assignments`,
      { freelancerId: freelancer.userId }, clientToken);
    if (!assigned.contract?.id || !assigned.contract?.milestoneId) {
      throw new Error("Assignment did not create a WorkContract and Milestone");
    }
    return assigned;
  }

  async function fund(job: any) {
    const contract = job.contract;
    const path = `/contracts/${contract.id}/milestones/${contract.milestoneId}/escrow`;
    const build = await request("POST", path + "/fund/build",
      { walletAddress: clientWallet.publicKey.toBase58() }, clientToken);
    const submitted = await request("POST", path + "/fund/submit", {
      buildSessionId: build.buildSessionId,
      transactionBase64: signTransaction(build.transactionBase64, clientWallet),
    }, clientToken);
    const escrow = await waitFor(() => request("GET", path, undefined, clientToken),
      value => value.status === "Funded", "on-chain funding");
    const active = await waitFor(() => request("GET", `/marketplace/jobs/${job.id}`,
      undefined, clientToken), value => value.contract?.status === "ACTIVE",
      "contract activation");
    if (escrow.vaultBalanceBaseUnits !== String(job.budgetUsd * 1_000_000)
        || active.contract.milestoneStatus !== "FUNDED") {
      throw new Error("Funding did not activate work with the exact vault balance");
    }
    return { path, escrow, signature: submitted.fundSignature, contract: active.contract };
  }

  const releaseJob = await assignedJob(75);
  const releaseFunding = await fund(releaseJob);
  const contract = releaseFunding.contract;
  const payload = {
    summary: "E2E deliverable submitted",
    deliverables: contract.deliverables.map((item: any) => ({
      requirementId: item.id, url: "https://example.com/e2e-artifact", description: "E2E proof",
    })),
    acceptanceEvidence: contract.acceptanceCriteria.map((item: any) => ({
      criterionId: item.id, url: "https://example.com/e2e-artifact", note: "Criterion met",
    })),
  };
  const actionPath = `/contracts/${contract.id}/escrow/actions`;
  const submissionBuild = await request("POST", actionPath + "/submit/build",
    { submission: payload }, freelancerToken);
  const submittedAction = await request("POST", actionPath + `/${submissionBuild.intentId}/submit`, {
    transactionBase64: signTransaction(submissionBuild.transactionBase64, freelancerWallet),
  }, freelancerToken);
  await waitFor(() => request("GET", releaseFunding.path, undefined, clientToken),
    value => value.status === "Submitted", "on-chain submission");
  const submission = await request("POST", `/contracts/${contract.id}/submissions`, payload,
    freelancerToken, { "Idempotency-Key": randomUUID() });
  const releaseBuild = await request("POST", actionPath + "/release/build",
    { review: { decision: "APPROVE" } }, clientToken);
  const releasedAction = await request("POST", actionPath + `/${releaseBuild.intentId}/submit`, {
    transactionBase64: signTransaction(releaseBuild.transactionBase64, clientWallet),
  }, clientToken);
  await waitFor(() => request("GET", releaseFunding.path, undefined, clientToken),
    value => value.status === "Released", "on-chain release");
  await request("POST", `/contracts/${contract.id}/submissions/${submission.id}/decisions`,
    { decision: "APPROVE" }, clientToken);
  const completed = await waitFor(() => request("GET", `/marketplace/jobs/${releaseJob.id}`,
    undefined, clientToken), value => value.status === "COMPLETED", "Job completion");
  const releaseVault = await request("GET", releaseFunding.path, undefined, clientToken);
  const freelancerAta = getAssociatedTokenAddressSync(env.mockUsdc.mint, freelancerWallet.publicKey);
  const freelancerReceived = (await getAccount(env.provider.connection, freelancerAta)).amount;
  if (completed.contract.status !== "COMPLETED" || releaseVault.vaultBalanceBaseUnits !== "0"
      || freelancerReceived !== 75_000_000n) {
    throw new Error("Marketplace release and chain balances disagree");
  }

  const refundJob = await assignedJob(20);
  const refundFunding = await fund(refundJob);
  const beforeRefund = (await getAccount(env.provider.connection, clientAta)).amount;
  const refundActionPath = `/contracts/${refundFunding.contract.id}/escrow/actions`;
  const refundBuild = await request("POST", refundActionPath + "/mutual-refund/build", {}, clientToken);
  const clientSigned = signTransaction(refundBuild.originalTransaction, clientWallet, false);
  await request("POST", refundActionPath + `/mutual-refund/${refundBuild.intentId}/client-sign`,
    { transactionBase64: clientSigned }, clientToken);
  const pending = await request("GET", refundActionPath + "/mutual-refund/pending",
    undefined, freelancerToken);
  if (pending.intentId !== refundBuild.intentId) throw new Error("Freelancer cannot see pending refund");
  const refundResult = await request("POST", refundActionPath + `/mutual-refund/${pending.intentId}/finish`,
    { transactionBase64: signTransaction(pending.partialTransaction, freelancerWallet) }, freelancerToken);
  const refunded = await waitFor(() => request("GET", refundFunding.path, undefined, clientToken),
    value => value.status === "Refunded", "on-chain mutual refund");
  const cancelled = await waitFor(() => request("GET", `/marketplace/jobs/${refundJob.id}`,
    undefined, clientToken), value => value.status === "CANCELLED", "Job refund reconciliation", 60);
  const afterRefund = (await getAccount(env.provider.connection, clientAta)).amount;
  if (cancelled.contract.status !== "CANCELLED" || refunded.vaultBalanceBaseUnits !== "0"
      || afterRefund !== beforeRefund + 20_000_000n) {
    throw new Error("Marketplace refund and chain balances disagree");
  }

  console.log(JSON.stringify({
    release: { jobId: releaseJob.id, contractId: contract.id,
      milestoneId: contract.milestoneId, fundingSignature: releaseFunding.signature,
      submissionSignature: submittedAction.signature, releaseSignature: releasedAction.signature,
      jobStatus: completed.status, vaultAfter: releaseVault.vaultBalanceBaseUnits,
      freelancerReceivedBaseUnits: String(freelancerReceived) },
    refund: { jobId: refundJob.id, contractId: refundFunding.contract.id,
      milestoneId: refundFunding.contract.milestoneId, fundingSignature: refundFunding.signature,
      refundSignature: refundResult.signature, jobStatus: cancelled.status,
      vaultAfter: refunded.vaultBalanceBaseUnits,
      clientBalanceRestored: afterRefund === beforeRefund + 20_000_000n },
  }, null, 2));
}

main().catch(error => { console.error(error); process.exitCode = 1; });
