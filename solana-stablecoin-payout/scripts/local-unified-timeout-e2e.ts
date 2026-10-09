import assert from "assert/strict";
import { randomUUID } from "crypto";
import { readFileSync, writeFileSync } from "fs";
import * as anchor from "@anchor-lang/core";
import bs58 from "bs58";
import { Keypair, PublicKey, Transaction } from "@solana/web3.js";
import { getAccount, getAssociatedTokenAddressSync } from "@solana/spl-token";

const env = Object.fromEntries(readFileSync("../.env", "utf8")
  .split(/\r?\n/).filter(line => line.includes("=") && !line.startsWith("#"))
  .map(line => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]));
const signers = (env.SOLANA_LOCAL_PRIVATE_KEYS || "").split(";").filter(Boolean)
  .map(value => Keypair.fromSecretKey(bs58.decode(value)));
const signer = (address: string) => {
  const key = signers.find(item => item.publicKey.toBase58() === address);
  if (!key) throw new Error(`Missing local signer for ${address}`);
  return key;
};
const clientWallet = signer(env.SOLANA_CUSTODIAL_CLIENT_PUBLIC_KEY);
const freelancerWallet = signer(env.DEMO_FREELANCER_SOLANA_PUBLIC_KEY);
const marketplace = "http://127.0.0.1:9191/api/v1";

async function call(base: string, method: string, path: string, body?: object,
                    token?: string, headers: Record<string, string> = {}): Promise<any> {
  const response = await fetch(base + path, { method,
    headers: { "Content-Type": "application/json", ...headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body) });
  const json: any = await response.json();
  if (!response.ok || json.code !== 200)
    throw new Error(`${method} ${path}: HTTP ${response.status} ${JSON.stringify(json)}`);
  return json.data;
}
const api = (method: string, path: string, body?: object, token?: string,
             headers?: Record<string, string>) => call(marketplace, method, path, body, token, headers);
const provider = (path: string) => call("http://127.0.0.1:9190", "GET", path, undefined,
  undefined, { "X-Internal-Api-Key": env.PAYMENT_INTERNAL_API_KEY });
function sign(base64: string, wallet: Keypair, requireAllSignatures = true): string {
  const transaction = Transaction.from(Buffer.from(base64, "base64"));
  transaction.partialSign(wallet);
  return transaction.serialize({ requireAllSignatures }).toString("base64");
}
async function until<T>(label: string, read: () => Promise<T>, ready: (value: T) => boolean,
                        attempts = 60): Promise<T> {
  let last: T | undefined;
  for (let i = 0; i < attempts; i++) {
    try {
      last = await read();
      if (ready(last)) return last;
    } catch (error) {
      if (!String(error).includes('"code":"FUNDING_AMOUNT_CHANGED"')) throw error;
    }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error(`${label} timed out: ${JSON.stringify(last)}`);
}
async function retryPending<T>(read: () => Promise<T>): Promise<T> {
  for (let i = 0; i < 20; i++) {
    try { return await read(); }
    catch (error) {
      if (!String(error).includes('"code":"FUNDING_IN_PROGRESS"')) throw error;
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
  }
  throw new Error("Pending action did not become ready");
}
const stage = (timeline: any, kind: string) => timeline.steps.find((step: any) => step.kind === kind);

type Row = { kind: string; amount: number; jobId: string; contractId: string; milestoneId: string; rail: string };

// Review timeout on the unified rail. Run "setup" on the normal ledger, copy the ledger and
// restart the copy with --warp-slot past the 72h review deadline, start Marketplace with
// ESCROW_E2E_CLOCK_OFFSET_SECONDS (dev profile only), then run "finish". The Marketplace
// scheduler — not the test — must submit the permissionless release.
const statePath = "target/unified-timeout-e2e.json";

async function login() {
  const client = await api("POST", "/auth/sign-in", {
    email: "nguyenhuutrong11133@gmail.com", password: env.DEMO_CLIENT_PASSWORD });
  const freelancer = await api("POST", "/auth/sign-in", {
    email: "freelancer.seed@example.com", password: env.DEMO_FREELANCER_PASSWORD });
  return { client, freelancer, clientToken: client.accessToken, freelancerToken: freelancer.accessToken };
}

async function setup() {
  const { freelancer, clientToken, freelancerToken } = await login();
  const job = await api("POST", "/marketplace/jobs", {
    title: `Unified timeout ${randomUUID().slice(0, 8)}`, description: "Unified review-timeout E2E",
    category: "WEB_FRONTEND", skills: ["TypeScript"], budgetUsd: 6,
    deliveryDueAt: new Date(Date.now() + 25 * 3600_000).toISOString(), reviewWindowHours: 72, maxRevisions: 0,
    deliverables: [{ title: "Artifact", description: "Artifact", required: true }],
    acceptanceCriteria: [{ description: "Artifact URL opens", required: true }],
  }, clientToken);
  assert.equal(job.localPaymentTerms?.rail, "UNIFIED_USDC_PAYOUT", "cutover flag must be on");
  await api("POST", `/marketplace/jobs/${job.id}/apply`,
    { acceptedTermsFingerprint: job.localPaymentTerms.fingerprint }, freelancerToken);
  const assigned = await api("POST", `/marketplace/jobs/${job.id}/assignments`,
    { freelancerId: freelancer.userId, acceptedTermsFingerprint: job.localPaymentTerms.fingerprint }, clientToken);
  const row: Row = { kind: "timeout", amount: 6, jobId: job.id, contractId: assigned.contract.id,
    milestoneId: assigned.contract.milestoneId, rail: assigned.contract.paymentRail };
  async function fund(row: Row) {
    assert.equal(row.rail, "UNIFIED_USDC_PAYOUT");
    const flowPath = `/contracts/${row.contractId}/milestones/${row.milestoneId}/payment-flow`;
    const escrowPath = `/contracts/${row.contractId}/milestones/${row.milestoneId}/escrow`;
    const initial = await api("GET", flowPath, undefined, clientToken);
    if (stage(initial, "ESCROW").status === "CONFIRMED")
      return { flowPath, escrowPath, flowId: initial.paymentFlowId,
        fundingSignature: "already-funded" };
    if (initial.termsStatus === "DRAFT") {
      await api("POST", flowPath + "/usd-order", undefined, clientToken,
        { "Idempotency-Key": randomUUID() });
      await api("POST", flowPath + "/usd-order/submit", undefined, clientToken);
    }
    const usd = await until("USD statement and on-ramp", () => api("GET", flowPath, undefined, clientToken),
      value => stage(value, "USD_RECEIVED").status === "CONFIRMED"
        && stage(value, "CLIENT_USDC").status === "CONFIRMED");
    assert.equal(usd.paymentFlowId, initial.paymentFlowId);
    const statement = await provider(`/internal/unified-mock/usd-orders/${usd.paymentFlowId}/statement`);
    assert(statement.entries.some((entry: any) => entry.kind === "USD_RECEIVED"
      && Number(entry.amount) === row.amount));
    const build = await api("POST", escrowPath + "/fund/build",
      { walletAddress: clientWallet.publicKey.toBase58() }, clientToken);
    const submitted = await api("POST", escrowPath + "/fund/submit", {
      buildSessionId: build.buildSessionId,
      transactionBase64: sign(build.transactionBase64, clientWallet) }, clientToken);
    const escrow = await until("funded vault", () => api("GET", escrowPath, undefined, clientToken),
      value => value.status === "Funded");
    assert.equal(escrow.vaultBalanceBaseUnits, String(row.amount * 1_000_000));
    await until("work activated", () => api("GET", `/marketplace/jobs/${row.jobId}`, undefined, clientToken),
      value => value.status === "IN_PROGRESS");
    const after = await api("GET", flowPath, undefined, clientToken);
    assert.equal(stage(after, "ESCROW").status, "CONFIRMED");
    return { flowPath, escrowPath, flowId: usd.paymentFlowId, fundingSignature: submitted.fundSignature };
  }


  const funding = await fund(row);
  const active = await api("GET", `/marketplace/jobs/${row.jobId}`, undefined, clientToken);
  const payload = { summary: "Delivered; Client stays silent",
    deliverables: active.contract.deliverables.map((item: any) => ({
      requirementId: item.id, url: "https://example.com/timeout", description: "E2E" })),
    acceptanceEvidence: active.contract.acceptanceCriteria.map((item: any) => ({
      criterionId: item.id, url: "https://example.com/timeout", note: "E2E" })) };
  const actions = `/contracts/${row.contractId}/escrow/actions`;
  const submit = await api("POST", actions + "/submit/build", { submission: payload }, freelancerToken);
  const submitted = await api("POST", actions + `/${submit.intentId}/submit`,
    { transactionBase64: sign(submit.transactionBase64, freelancerWallet) }, freelancerToken);
  const escrow = await until("submission", () => api("GET", funding.escrowPath, undefined, clientToken),
    value => value.status === "Submitted");
  await until("submission mirrored", () => api("GET", `/contracts/${row.contractId}/submissions`,
    undefined, clientToken), list => list.length > 0);
  const { connection } = anchor.AnchorProvider.env();
  const mint = new PublicKey(escrow.mint);
  const freelancerAta = getAssociatedTokenAddressSync(mint, freelancerWallet.publicKey);
  const state = { ...row, flowPath: funding.flowPath, escrowPath: funding.escrowPath,
    reviewDueAt: escrow.reviewDueAt, submissionSignature: submitted.signature,
    submissionSlot: await connection.getSlot("confirmed"),
    freelancerBefore: String((await getAccount(connection, freelancerAta)).amount), mint: escrow.mint };
  writeFileSync(statePath, JSON.stringify(state, null, 2));
  console.log(JSON.stringify(state, null, 2));
}

async function finish() {
  const state = JSON.parse(readFileSync(statePath, "utf8"));
  const { clientToken } = await login();
  const { connection } = anchor.AnchorProvider.env();
  const slot = await connection.getSlot("confirmed");
  const chainTime = await connection.getBlockTime(slot);
  assert.ok(chainTime && chainTime > Number(state.reviewDueAt), `chain clock ${chainTime} not past ${state.reviewDueAt}`);
  // Early read: the test never sends the release itself.
  const flow = await until("scheduler timeout release", () => api("GET", state.flowPath, undefined, clientToken),
    value => stage(value, "USDC_RELEASE").status === "CONFIRMED" && stage(value, "WORK_ACCEPTED").status === "CONFIRMED", 240);
  const escrow = await api("GET", state.escrowPath, undefined, clientToken);
  const job = await api("GET", `/marketplace/jobs/${state.jobId}`, undefined, clientToken);
  const submissions = await api("GET", `/contracts/${state.contractId}/submissions`, undefined, clientToken);
  const freelancerAta = getAssociatedTokenAddressSync(new PublicKey(state.mint), freelancerWallet.publicKey);
  const after = (await getAccount(connection, freelancerAta)).amount;
  assert.equal(escrow.status, "Released");
  assert.equal(escrow.vaultBalanceBaseUnits, "0");
  assert.equal(job.status, "COMPLETED");
  assert.equal(after - BigInt(state.freelancerBefore), BigInt(state.amount * 1_000_000));
  // Chain proof that this was the timeout path: settled at/after the on-chain review deadline.
  const onchain = (await (await fetch(`http://127.0.0.1:9193/api/v1/solana/escrows/${state.milestoneId}`, {
    headers: { "X-Internal-Api-Key": env.SOLANA_INTERNAL_API_KEY } })).json() as any).data;
  assert.ok(Number(onchain.settledAt) >= Number(onchain.reviewDueAt), "release happened before review deadline");
  assert.equal(submissions[0].status, "APPROVED");
  assert.notEqual(stage(flow, "WITHDRAWAL").status, "CONFIRMED");
  console.log(JSON.stringify({ result: "PASS", jobId: state.jobId, paymentFlowId: flow.paymentFlowId,
    chainTime, reviewDueAt: state.reviewDueAt, releaseSignature: escrow.releaseSignature,
    freelancerReceivedBaseUnits: String(after - BigInt(state.freelancerBefore)),
    chainSettledAt: onchain.settledAt, chainReviewDueAt: onchain.reviewDueAt,
    usdcRelease: stage(flow, "USDC_RELEASE").status, workAccepted: stage(flow, "WORK_ACCEPTED").status }, null, 2));
}

describe("Unified review timeout", function () {
  this.timeout(600_000);
  it(`phase ${process.env.UNIFIED_TIMEOUT_PHASE || "setup"}`, () =>
    process.env.UNIFIED_TIMEOUT_PHASE === "finish" ? finish() : setup());
});
