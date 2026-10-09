import assert from "assert/strict";
import { randomUUID } from "crypto";
import { readFileSync, writeFileSync } from "fs";
import { execSync } from "child_process";
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

// Outage drill on the unified rail (local only). Payment Backend is stopped while the Client
// submits the USD order and while the fiat exit is due; the Gateway RPC link is cut while the
// withdrawal is submitted and while Admin reconciles. Every step must recover on the same
// references with no second token transfer or fiat statement.
const sh = (command: string) => execSync(command, { cwd: "..", stdio: "pipe" }).toString();
const payment = (action: "stop" | "start") => sh(action === "stop"
  ? "docker compose stop payment-backend"
  : "docker compose start payment-backend && for i in $(seq 1 60); do curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:9190/actuator/health | grep -qE '200|401' && break; sleep 2; done");
// The Gateway reaches RPC through local-rpc-proxy (SOLANA_RPC_HTTP_URL=...:9133); cutting the
// proxy is an RPC outage for Marketplace/Gateway while the validator keeps producing blocks.
const rpc = (action: "stop" | "start") => sh(`solana-stablecoin-payout/scripts/local-rpc-proxy.sh ${action}`);
const pause = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

async function main() {
  rpc("start");
  payment("start");
  await until("payment backend ready", () => provider("/internal/unified-mock/usd-orders/" + randomUUID())
    .then(() => true, error => String(error).includes("HTTP 404")), ready => ready, 60);
  const { connection } = anchor.AnchorProvider.env();
  const client = await api("POST", "/auth/sign-in", { email: "nguyenhuutrong11133@gmail.com", password: env.DEMO_CLIENT_PASSWORD });
  const freelancer = await api("POST", "/auth/sign-in", { email: "freelancer.seed@example.com", password: env.DEMO_FREELANCER_PASSWORD });
  const admin = await api("POST", "/auth/sign-in", { email: "admin.e2e@example.test", password: env.DEMO_ADMIN_PASSWORD });
  const clientToken = client.accessToken, freelancerToken = freelancer.accessToken;
  const config = (await (await fetch("http://127.0.0.1:9193/api/v1/solana/config", {
    headers: { "X-Internal-Api-Key": env.SOLANA_INTERNAL_API_KEY } })).json() as any).data;
  const treasuryAta = getAssociatedTokenAddressSync(new PublicKey(config.acceptedMint), new PublicKey(config.treasuryAuthority));
  const job = await api("POST", "/marketplace/jobs", { title: `Unified outage ${randomUUID().slice(0, 8)}`,
    description: "Unified outage drill", category: "WEB_FRONTEND", skills: ["TypeScript"], budgetUsd: 5,
    deliveryDueAt: new Date(Date.now() + 25 * 3600_000).toISOString(), reviewWindowHours: 72, maxRevisions: 0,
    deliverables: [{ title: "Artifact", description: "Artifact", required: true }],
    acceptanceCriteria: [{ description: "Opens", required: true }] }, clientToken);
  assert.equal(job.localPaymentTerms?.rail, "UNIFIED_USDC_PAYOUT");
  await api("POST", `/marketplace/jobs/${job.id}/apply`, { acceptedTermsFingerprint: job.localPaymentTerms.fingerprint }, freelancerToken);
  const assigned = await api("POST", `/marketplace/jobs/${job.id}/assignments`,
    { freelancerId: freelancer.userId, acceptedTermsFingerprint: job.localPaymentTerms.fingerprint }, clientToken);
  const contractId = assigned.contract.id, milestoneId = assigned.contract.milestoneId;
  const flowPath = `/contracts/${contractId}/milestones/${milestoneId}/payment-flow`;
  const escrowPath = `/contracts/${contractId}/milestones/${milestoneId}/escrow`;
  const log: any = { jobId: job.id };

  // 1. Payment Backend down while the Client submits the USD order.
  await api("POST", flowPath + "/usd-order", undefined, clientToken, { "Idempotency-Key": randomUUID() });
  payment("stop");
  const during = await api("POST", flowPath + "/usd-order/submit", undefined, clientToken);
  log.usdOrderDuringOutage = stage(during, "USD_ORDER").status;
  assert.equal(log.usdOrderDuringOutage, "UNKNOWN");
  await pause(12_000);
  assert.notEqual(stage(await api("GET", flowPath, undefined, clientToken), "USD_RECEIVED").status, "CONFIRMED");
  payment("start");
  await until("order re-read", () => api("GET", flowPath, undefined, clientToken),
    flow => ["AWAITING_CLIENT", "PENDING", "CONFIRMED"].includes(stage(flow, "USD_ORDER").status), 90);
  const order = stage(await api("GET", flowPath, undefined, clientToken), "USD_ORDER").status;
  if (order === "AWAITING_CLIENT") await api("POST", flowPath + "/usd-order/submit", undefined, clientToken);
  const usd = await until("USD and on-ramp after recovery", () => api("GET", flowPath, undefined, clientToken),
    flow => stage(flow, "CLIENT_USDC").status === "CONFIRMED", 480);
  const usdStatement = await provider(`/internal/unified-mock/usd-orders/${usd.paymentFlowId}/statement`);
  log.usdStatementRows = usdStatement.entries.filter((e: any) => e.kind === "USD_RECEIVED").length;
  assert.equal(log.usdStatementRows, 1);

  // 2. Fund, deliver and approve normally.
  const build = await api("POST", escrowPath + "/fund/build", { walletAddress: clientWallet.publicKey.toBase58() }, clientToken);
  await api("POST", escrowPath + "/fund/submit", { buildSessionId: build.buildSessionId,
    transactionBase64: sign(build.transactionBase64, clientWallet) }, clientToken);
  await until("work activated", () => api("GET", `/marketplace/jobs/${job.id}`, undefined, clientToken),
    value => value.status === "IN_PROGRESS", 90);
  const active = await api("GET", `/marketplace/jobs/${job.id}`, undefined, clientToken);
  const payload = { summary: "Outage drill delivery",
    deliverables: active.contract.deliverables.map((i: any) => ({ requirementId: i.id, url: "https://example.com/o", description: "E2E" })),
    acceptanceEvidence: active.contract.acceptanceCriteria.map((i: any) => ({ criterionId: i.id, url: "https://example.com/o", note: "E2E" })) };
  const actions = `/contracts/${contractId}/escrow/actions`;
  const submit = await api("POST", actions + "/submit/build", { submission: payload }, freelancerToken);
  await api("POST", actions + `/${submit.intentId}/submit`, { transactionBase64: sign(submit.transactionBase64, freelancerWallet) }, freelancerToken);
  await until("submitted", () => api("GET", escrowPath, undefined, clientToken), value => value.status === "Submitted");
  await until("mirrored", () => api("GET", `/contracts/${contractId}/submissions`, undefined, clientToken), list => list.length > 0);
  const release = await api("POST", actions + "/release/build", { review: { decision: "APPROVE" } }, clientToken);
  await api("POST", actions + `/${release.intentId}/submit`, { transactionBase64: sign(release.transactionBase64, clientWallet) }, clientToken);
  const released = await until("released", () => api("GET", flowPath, undefined, clientToken),
    flow => stage(flow, "USDC_RELEASE").status === "CONFIRMED", 120);
  const flowId = released.paymentFlowId;
  log.paymentFlowId = flowId;

  // 3. RPC down: Admin sees UNKNOWN and the withdrawal cannot be prepared.
  rpc("stop");
  const recon = (await api("GET", "/admin/payment-flows/reconciliation", undefined, admin.accessToken))
    .find((c: any) => c.paymentFlowId === flowId);
  log.boundaryDuringRpcOutage = recon.vaultToRecipient.status + "/" + recon.vaultToRecipient.code;
  assert.equal(recon.vaultToRecipient.status, "UNKNOWN");
  await assert.rejects(api("POST", flowPath + "/withdrawal/prepare", undefined, freelancerToken));
  rpc("start");

  // 4. RPC down at withdrawal submission: the signed transaction is retried as-is.
  const withdrawal = await retryPending(() => api("POST", flowPath + "/withdrawal/prepare", undefined, freelancerToken));
  const signedWithdrawal = sign(withdrawal.transactionBase64, freelancerWallet);
  const treasuryBefore = (await getAccount(connection, treasuryAta)).amount;
  rpc("stop");
  await assert.rejects(api("POST", flowPath + "/withdrawal/submit", { buildSessionId: withdrawal.buildSessionId,
    transactionBase64: signedWithdrawal }, freelancerToken));
  log.withdrawalDuringRpcOutage = stage(await api("GET", flowPath, undefined, freelancerToken), "WITHDRAWAL").status;
  assert.notEqual(log.withdrawalDuringRpcOutage, "CONFIRMED");
  rpc("start");

  // 5. Payment Backend down when the fiat exit is due. The outage outlived the 120 s build
  // session, so the original signature is refused (never sent twice) and the Freelancer
  // prepares again: same withdrawal ID, same locked quote while it is still valid.
  const resubmit = await api("POST", flowPath + "/withdrawal/submit", { buildSessionId: withdrawal.buildSessionId,
    transactionBase64: signedWithdrawal }, freelancerToken).then(() => "accepted", error => String(error));
  log.resubmitAfterRpcOutage = resubmit.includes("BUILD_SESSION_EXPIRED") ? "BUILD_SESSION_EXPIRED" : resubmit.slice(0, 120);
  payment("stop");
  if (resubmit !== "accepted") {
    const again = await retryPending(() => api("POST", flowPath + "/withdrawal/prepare", undefined, freelancerToken));
    assert.equal(again.withdrawalId, withdrawal.withdrawalId);
    log.quoteKeptAfterRebuild = again.quoteExpiresAt === withdrawal.quoteExpiresAt;
    await api("POST", flowPath + "/withdrawal/submit", { buildSessionId: again.buildSessionId,
      transactionBase64: sign(again.transactionBase64, freelancerWallet) }, freelancerToken).catch(() => null);
  }
  await until("withdrawal confirmed", () => api("GET", flowPath, undefined, freelancerToken),
    flow => stage(flow, "WITHDRAWAL").status === "CONFIRMED", 90);
  await pause(12_000);
  const stalled = await api("GET", flowPath, undefined, freelancerToken);
  log.vndDuringPaymentOutage = stage(stalled, "VND_PAYOUT").status;
  assert.notEqual(log.vndDuringPaymentOutage, "CONFIRMED");
  payment("start");
  const paid = await until("VND after recovery", () => api("GET", flowPath, undefined, freelancerToken),
    flow => stage(flow, "VND_PAYOUT").status === "CONFIRMED" && stage(flow, "PLATFORM_FEE").status === "CONFIRMED", 120);
  const exit = await provider(`/internal/unified-mock/fiat-exits/${flowId}/statement`);
  log.fiatStatementRows = exit.entries.map((e: any) => e.kind).sort();
  assert.deepEqual(log.fiatStatementRows, ["PLATFORM_FEE", "VND_PAYOUT"]);
  log.treasuryDeltaBaseUnits = String((await getAccount(connection, treasuryAta)).amount - treasuryBefore);
  assert.equal(log.treasuryDeltaBaseUnits, "5000000");
  log.vnd = stage(paid, "VND_PAYOUT").amount;
  log.feeUsdc = stage(paid, "PLATFORM_FEE").amount;
  console.log(JSON.stringify({ result: "PASS", ...log }, null, 2));
}

describe("Unified outage drill", function () {
  this.timeout(900_000);
  it("recovers from Payment Backend and RPC outages without duplicate money", main);
});
