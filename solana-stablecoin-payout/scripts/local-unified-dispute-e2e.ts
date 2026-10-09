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

// Admin dispute on the unified rail, through the fiat exit: one Job resolved to the Freelancer
// (USDC release → withdrawal → VND + fee), one to the Client (USDC refund → treasury → USD).
async function main() {
  const { connection } = anchor.AnchorProvider.env();
  const client = await api("POST", "/auth/sign-in", {
    email: "nguyenhuutrong11133@gmail.com", password: env.DEMO_CLIENT_PASSWORD });
  const freelancer = await api("POST", "/auth/sign-in", {
    email: "freelancer.seed@example.com", password: env.DEMO_FREELANCER_PASSWORD });
  const admin = await api("POST", "/auth/sign-in", {
    email: "admin.e2e@example.test", password: env.DEMO_ADMIN_PASSWORD });
  const clientToken = client.accessToken, freelancerToken = freelancer.accessToken;
  const configResponse = await fetch("http://127.0.0.1:9193/api/v1/solana/config", {
    headers: { "X-Internal-Api-Key": env.SOLANA_INTERNAL_API_KEY } });
  const config = (await configResponse.json() as any).data;
  const mint = new PublicKey(config.acceptedMint);
  const treasuryAta = getAssociatedTokenAddressSync(mint, new PublicKey(config.treasuryAuthority));
  const balance = async (address: PublicKey) => await connection.getAccountInfo(address)
    ? (await getAccount(connection, address)).amount : 0n;

  const rows: Row[] = [];
  for (const [kind, amount] of [["dispute-release", 9], ["dispute-refund", 7]] as const) {
    const job = await api("POST", "/marketplace/jobs", {
      title: `Unified ${kind} ${randomUUID().slice(0, 8)}`, description: "Unified Admin dispute E2E",
      category: "WEB_FRONTEND", skills: ["TypeScript"], budgetUsd: amount,
      deliveryDueAt: new Date(Date.now() + 25 * 3600_000).toISOString(),
      reviewWindowHours: 72, maxRevisions: 0,
      deliverables: [{ title: "Artifact", description: "Disputed artifact", required: true }],
      acceptanceCriteria: [{ description: "Artifact URL opens", required: true }],
    }, clientToken);
    assert.equal(job.localPaymentTerms?.rail, "UNIFIED_USDC_PAYOUT", "cutover flag must be on");
    await api("POST", `/marketplace/jobs/${job.id}/apply`,
      { acceptedTermsFingerprint: job.localPaymentTerms.fingerprint }, freelancerToken);
    const assigned = await api("POST", `/marketplace/jobs/${job.id}/assignments`,
      { freelancerId: freelancer.userId, acceptedTermsFingerprint: job.localPaymentTerms.fingerprint }, clientToken);
    rows.push({ kind, amount, jobId: job.id, contractId: assigned.contract.id,
      milestoneId: assigned.contract.milestoneId, rail: assigned.contract.paymentRail });
  }

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


  const results: any = {};
  for (const row of rows) {
    const funding = await fund(row);
    const active = await api("GET", `/marketplace/jobs/${row.jobId}`, undefined, clientToken);
    const payload = { summary: "Disputed delivery",
      deliverables: active.contract.deliverables.map((item: any) => ({
        requirementId: item.id, url: "https://example.com/disputed", description: "E2E" })),
      acceptanceEvidence: active.contract.acceptanceCriteria.map((item: any) => ({
        criterionId: item.id, url: "https://example.com/disputed", note: "E2E" })) };
    const actions = `/contracts/${row.contractId}/escrow/actions`;
    const submit = await api("POST", actions + "/submit/build", { submission: payload }, freelancerToken);
    await api("POST", actions + `/${submit.intentId}/submit`,
      { transactionBase64: sign(submit.transactionBase64, freelancerWallet) }, freelancerToken);
    await until("submission", () => api("GET", funding.escrowPath, undefined, clientToken),
      value => value.status === "Submitted");
    await until("submission mirrored", () => api("GET", `/contracts/${row.contractId}/submissions`,
      undefined, clientToken), list => list.length > 0);
    const reason = { reasonCode: "E2E_UNIFIED_DISPUTE", description: "Client contests the artifact", evidence: [] };
    const open = await api("POST", actions + "/open-dispute/build",
      { reasonCode: reason.reasonCode, description: reason.description }, clientToken);
    await api("POST", actions + `/${open.intentId}/submit`,
      { transactionBase64: sign(open.transactionBase64, clientWallet) }, clientToken);
    await until("on-chain dispute", () => api("GET", funding.escrowPath, undefined, clientToken),
      value => value.status === "Disputed");
    let dispute = await until("dispute record", () => api("GET", `/contracts/${row.contractId}/disputes`,
      undefined, clientToken), value => !!value, 30).catch(() => null);
    if (!dispute) dispute = await api("POST", `/contracts/${row.contractId}/disputes`, reason, clientToken);
    // A dispute freezes release: no withdrawal can be prepared while the vault is disputed.
    await assert.rejects(api("POST", funding.flowPath + "/withdrawal/prepare", undefined, freelancerToken));
    await api("POST", `/admin/disputes/${dispute.disputeId}/claim`, undefined, admin.accessToken);
    const release = row.kind === "dispute-release";
    await api("POST", `/admin/disputes/${dispute.disputeId}/resolve`,
      { outcome: release ? "RELEASE_TO_FREELANCER" : "REFUND_TO_CLIENT", reason: "E2E unified dispute decision" },
      admin.accessToken, { "Idempotency-Key": randomUUID() });
    await until("terminal on flow", () => api("GET", funding.flowPath, undefined, clientToken),
      flow => stage(flow, release ? "USDC_RELEASE" : "USDC_REFUND").status === "CONFIRMED", 180);
    const owner = release ? freelancerToken : clientToken;
    const wallet = release ? freelancerWallet : clientWallet;
    const treasuryBefore = await balance(treasuryAta);
    const withdrawal = await retryPending(() => api("POST", funding.flowPath + "/withdrawal/prepare", undefined, owner));
    await api("POST", funding.flowPath + "/withdrawal/submit", { buildSessionId: withdrawal.buildSessionId,
      transactionBase64: sign(withdrawal.transactionBase64, wallet) }, owner);
    const done = await until("fiat exit", () => api("GET", funding.flowPath, undefined, owner),
      flow => release ? stage(flow, "VND_PAYOUT").status === "CONFIRMED" && stage(flow, "PLATFORM_FEE").status === "CONFIRMED"
        : stage(flow, "USD_REFUND").status === "CONFIRMED", 120);
    assert.equal(await balance(treasuryAta), treasuryBefore + BigInt(row.amount * 1_000_000));
    const job = await api("GET", `/marketplace/jobs/${row.jobId}`, undefined, clientToken);
    assert.equal(job.status, release ? "COMPLETED" : "CANCELLED");
    const resolved = await api("GET", `/admin/disputes/${dispute.disputeId}`, undefined, admin.accessToken);
    results[row.kind] = { jobId: row.jobId, paymentFlowId: done.paymentFlowId, disputeId: dispute.disputeId,
      disputeStatus: resolved.dispute.status, jobStatus: job.status,
      fiat: release ? { vnd: stage(done, "VND_PAYOUT").amount, feeUsdc: stage(done, "PLATFORM_FEE").amount }
        : { usdRefund: stage(done, "USD_REFUND").amount, vnd: stage(done, "VND_PAYOUT").status } };
  }
  const cases = await until("reconciliation", () => api("GET", "/admin/payment-flows/reconciliation",
    undefined, admin.accessToken), list => Object.values(results).every((r: any) => {
      const item = list.find((c: any) => c.paymentFlowId === r.paymentFlowId);
      return item && ["usdToClientUsdc", "clientUsdcToVault", "vaultToRecipient", "withdrawalToFiat"]
        .every(key => item[key].status === "MATCHED");
    }));
  assert.ok(cases);
  console.log(JSON.stringify({ result: "PASS", ...results }, null, 2));
}

describe("Unified Admin dispute through fiat exit", function () {
  this.timeout(600_000);
  it("resolves one dispute to the Freelancer and one to the Client", main);
});
