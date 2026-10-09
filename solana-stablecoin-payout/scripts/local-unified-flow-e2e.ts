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
const jobsFile = process.env.UNIFIED_E2E_JOBS_FILE || "target/unified-e2e-jobs.json";

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

async function main() {
  if (process.argv.includes("--prepare")) {
    const rpc = async (method: string, params: unknown[] = []) => {
      const response = await fetch("http://127.0.0.1:9123", { method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ jsonrpc: "2.0", id: 1, method, params }) });
      return (await response.json() as any).result;
    };
    const slot = await rpc("getSlot");
    const blockTime = await rpc("getBlockTime", [slot]);
    assert(blockTime && Math.abs(blockTime - Math.floor(Date.now() / 1000)) < 120,
      "Local validator clock differs from host; use a fresh ledger");
    const gateway = "http://127.0.0.1:9193/api/v1/solana/config";
    const headers = { "Content-Type": "application/json",
      "X-Internal-Api-Key": env.SOLANA_INTERNAL_API_KEY };
    const readConfig = async () => {
      const response = await fetch(gateway, { headers });
      assert.equal(response.status, 200);
      const json = await response.json() as any;
      assert(json.exists && json.data, "Bootstrap Mock USDC before preparing Jobs");
      return json.data;
    };
    let config = await readConfig();
    assert(signers.some(key => key.publicKey.toBase58() === config.admin),
      "Gateway has no local Config admin signer");
    const authority = env.SOLANA_ONRAMP_AUTHORITY_PUBLIC_KEY;
    assert(signers.some(key => key.publicKey.toBase58() === authority),
      "Gateway has no local on-ramp/rate signer");
    const sendConfig = async (path: string, body: object) => {
      const response = await fetch(gateway + path, { method: "PUT", headers,
        body: JSON.stringify({ ...body, mode: "send", commitment: "confirmed", skipPreflight: false }) });
      assert.equal(response.status, 200, await response.text());
    };
    if (config.mockOnrampAuthority !== authority) {
      await sendConfig("/mock-onramp", { admin: config.admin, authority,
        maxAmount: config.maxMockOnrampAmount, enabled: true });
      config = await until("mock on-ramp authority", readConfig,
        value => value.mockOnrampAuthority === authority);
    }
    if (config.rateAuthority !== authority) {
      await sendConfig("", { admin: config.admin, acceptedMint: config.acceptedMint,
        treasuryAuthority: config.treasuryAuthority, rateAuthority: authority,
        oracleAuthority: config.oracleAuthority,
        maxRateAgeSeconds: config.maxRateAgeSeconds, paused: false });
      await until("rate authority", readConfig, value => value.rateAuthority === authority);
    }
    const client = await api("POST", "/auth/sign-in", {
      email: "nguyenhuutrong11133@gmail.com", password: env.DEMO_CLIENT_PASSWORD });
    const freelancer = await api("POST", "/auth/sign-in", {
      email: "freelancer.seed@example.com", password: env.DEMO_FREELANCER_PASSWORD });
    const rows = [];
    for (const [kind, amount] of [["release", 15], ["refund", 10]] as const) {
      const job = await api("POST", "/marketplace/jobs", {
        title: `Unified local ${kind} ${randomUUID().slice(0, 8)}`,
        description: "Local unified payment flow E2E proof", category: "WEB_FRONTEND",
        skills: ["TypeScript"], budgetUsd: amount,
        deliveryDueAt: new Date(Date.now() + 25 * 3600_000).toISOString(),
        reviewWindowHours: 72, maxRevisions: 2,
        deliverables: [{ title: "Artifact", description: "Verifiable artifact", required: true }],
        acceptanceCriteria: [{ description: "Artifact URL opens", required: true }],
      }, client.accessToken);
      assert.ok(job.localPaymentTerms?.fingerprint, "unified terms preview missing");
      await api("POST", `/marketplace/jobs/${job.id}/apply`,
        { acceptedTermsFingerprint: job.localPaymentTerms.fingerprint }, freelancer.accessToken);
      const assigned = await api("POST", `/marketplace/jobs/${job.id}/assignments`,
        { freelancerId: freelancer.userId,
          acceptedTermsFingerprint: job.localPaymentTerms.fingerprint }, client.accessToken);
      assert.equal(assigned.contract.paymentRail, "UNIFIED_USDC_PAYOUT");
      rows.push({ kind, amount, jobId: job.id, contractId: assigned.contract.id,
        milestoneId: assigned.contract.milestoneId, rail: assigned.contract.paymentRail });
    }
    writeFileSync(jobsFile, JSON.stringify(rows, null, 2));
    console.log(JSON.stringify({ mode: "prepared", jobsFile, rows }, null, 2));
    return;
  }
  const jobs: Array<{ kind: string; amount: number; jobId: string;
    contractId: string; milestoneId: string; rail: string }> =
    JSON.parse(readFileSync(jobsFile, "utf8"));
  const { connection } = anchor.AnchorProvider.env();
  const configResponse = await fetch("http://127.0.0.1:9193/api/v1/solana/config", {
    headers: { "X-Internal-Api-Key": env.SOLANA_INTERNAL_API_KEY } });
  assert.equal(configResponse.status, 200);
  const config = await configResponse.json() as any;
  const mint = new PublicKey(config.data.acceptedMint);
  const clientAta = getAssociatedTokenAddressSync(mint, clientWallet.publicKey);
  const freelancerAta = getAssociatedTokenAddressSync(mint, freelancerWallet.publicKey);
  const treasuryAta = getAssociatedTokenAddressSync(mint,
    new PublicKey(config.data.treasuryAuthority));
  const balance = async (address: PublicKey) => await connection.getAccountInfo(address)
    ? (await getAccount(connection, address)).amount : 0n;
  const client = await api("POST", "/auth/sign-in", {
    email: "nguyenhuutrong11133@gmail.com", password: env.DEMO_CLIENT_PASSWORD });
  const freelancer = await api("POST", "/auth/sign-in", {
    email: "freelancer.seed@example.com", password: env.DEMO_FREELANCER_PASSWORD });
  const clientToken = client.accessToken;
  const freelancerToken = freelancer.accessToken;
  assert.equal((await api("GET", "/solana/wallet-link", undefined, clientToken)).walletAddress,
    clientWallet.publicKey.toBase58());
  assert.equal((await api("GET", "/solana/wallet-link", undefined, freelancerToken)).walletAddress,
    freelancerWallet.publicKey.toBase58());
  const bank = await api("GET", "/payment-methods/bank-account", undefined, clientToken);
  if (!bank.ready) await api("PUT", "/payment-methods/bank-account", {
    bankCode: "VIETCOMBANK", bankAccountNumber: "123456789012",
    bankAccountHolderName: "NGUYEN HUU TRONG" }, clientToken);

  async function fund(row: typeof jobs[number]) {
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

  const release = jobs.find(row => row.kind === "release")!;
  const releaseFunding = await fund(release);
  const active = await api("GET", `/marketplace/jobs/${release.jobId}`, undefined, clientToken);
  const payload = { summary: "Unified E2E deliverable",
    deliverables: active.contract.deliverables.map((item: any) => ({
      requirementId: item.id, url: "https://example.com/unified-proof", description: "E2E proof" })),
    acceptanceEvidence: active.contract.acceptanceCriteria.map((item: any) => ({
      criterionId: item.id, url: "https://example.com/unified-proof", note: "Verified" })) };
  const actions = `/contracts/${release.contractId}/escrow/actions`;
  let releaseSignature = "already-released";
  const releaseBefore = await api("GET", releaseFunding.flowPath, undefined, clientToken);
  if (stage(releaseBefore, "USDC_RELEASE").status !== "CONFIRMED") {
    const freelancerBefore = await connection.getAccountInfo(freelancerAta)
      ? (await getAccount(connection, freelancerAta)).amount : 0n;
    const existingSubmissions = await api("GET", `/contracts/${release.contractId}/submissions`,
      undefined, freelancerToken);
    if (existingSubmissions.length === 0) {
      const submissionBuild = await api("POST", actions + "/submit/build",
        { submission: payload }, freelancerToken);
      await api("POST", actions + `/${submissionBuild.intentId}/submit`, {
        transactionBase64: sign(submissionBuild.transactionBase64, freelancerWallet) }, freelancerToken);
      await until("submission mirrored from chain", () => api("GET",
        `/contracts/${release.contractId}/submissions`, undefined, freelancerToken),
        rows => rows.length > 0);
    }
    const releaseBuild = await api("POST", actions + "/release/build",
      { review: { decision: "APPROVE" } }, clientToken);
    const releaseTx = await api("POST", actions + `/${releaseBuild.intentId}/submit`, {
      transactionBase64: sign(releaseBuild.transactionBase64, clientWallet) }, clientToken);
    releaseSignature = releaseTx.signature;
    await until("released vault", () => api("GET", releaseFunding.escrowPath, undefined, clientToken),
      value => value.status === "Released");
    await until("release timeline", () => api("GET", releaseFunding.flowPath, undefined, clientToken),
      value => stage(value, "USDC_RELEASE").status === "CONFIRMED");
    assert.equal((await getAccount(connection, freelancerAta)).amount,
      freelancerBefore + BigInt(release.amount * 1_000_000));
  }
  const withdrawalPath = releaseFunding.flowPath + "/withdrawal";
  const beforePayout = await api("GET", releaseFunding.flowPath, undefined, freelancerToken);
  const releaseTreasuryBefore = await balance(treasuryAta);
  if (stage(beforePayout, "WITHDRAWAL").status !== "CONFIRMED") {
    const withdrawal = await retryPending(() => api("POST", withdrawalPath + "/prepare",
      undefined, freelancerToken));
    await api("POST", withdrawalPath + "/submit", {
      buildSessionId: withdrawal.buildSessionId,
      transactionBase64: sign(withdrawal.transactionBase64, freelancerWallet) }, freelancerToken);
    await until("release withdrawal", () => api("GET", releaseFunding.flowPath,
      undefined, freelancerToken), value => stage(value, "WITHDRAWAL").status === "CONFIRMED");
    assert.equal(await balance(treasuryAta),
      releaseTreasuryBefore + BigInt(release.amount * 1_000_000));
  }
  const paid = await until("VND and fee statements", () => api("GET", releaseFunding.flowPath,
    undefined, freelancerToken), value => stage(value, "VND_PAYOUT").status === "CONFIRMED"
      && stage(value, "PLATFORM_FEE").status === "CONFIRMED");
  assert.equal(Number(stage(paid, "VND_PAYOUT").amount), 363750);
  assert.equal(Number(stage(paid, "PLATFORM_FEE").amount), 0.45);
  const payoutStatement = await provider(`/internal/unified-mock/fiat-exits/${releaseFunding.flowId}/statement`);
  assert.deepEqual(payoutStatement.entries.map((entry: any) => entry.kind).sort(),
    ["PLATFORM_FEE", "VND_PAYOUT"]);
  assert.equal((await api("GET", `/marketplace/jobs/${release.jobId}`, undefined,
    clientToken)).status, "COMPLETED");
  assert.equal((await api("GET", releaseFunding.escrowPath, undefined,
    clientToken)).vaultBalanceBaseUnits, "0");

  const refund = jobs.find(row => row.kind === "refund")!;
  const refundFunding = await fund(refund);
  const refundBefore = await api("GET", refundFunding.flowPath, undefined, clientToken);
  if (stage(refundBefore, "USDC_REFUND").status !== "CONFIRMED") {
    const chainBefore = await until("chain refund state", () => api("GET",
      refundFunding.escrowPath, undefined, clientToken),
      value => value.status === "Funded" || value.status === "Refunded");
    if (chainBefore.status !== "Refunded") {
      const beforeRefund = (await getAccount(connection, clientAta)).amount;
      const refundActions = `/contracts/${refund.contractId}/escrow/actions`;
      const refundBuild = await api("POST", refundActions + "/mutual-refund/build", {}, clientToken);
      await api("POST", refundActions + `/mutual-refund/${refundBuild.intentId}/client-sign`, {
        transactionBase64: sign(refundBuild.originalTransaction, clientWallet, false) }, clientToken);
      const pending = await api("GET", refundActions + "/mutual-refund/pending", undefined, freelancerToken);
      await api("POST", refundActions + `/mutual-refund/${pending.intentId}/finish`, {
        transactionBase64: sign(pending.partialTransaction, freelancerWallet) }, freelancerToken);
      await until("refunded vault", () => api("GET", refundFunding.escrowPath, undefined, clientToken),
        value => value.status === "Refunded");
      assert.equal((await getAccount(connection, clientAta)).amount,
        beforeRefund + BigInt(refund.amount * 1_000_000));
    }
    await until("refund timeline", () => api("GET", refundFunding.flowPath, undefined, clientToken),
      value => stage(value, "USDC_REFUND").status === "CONFIRMED");
  }
  const refundTreasuryBefore = await balance(treasuryAta);
  const beforeRefundExit = await api("GET", refundFunding.flowPath, undefined, clientToken);
  if (stage(beforeRefundExit, "WITHDRAWAL").status !== "CONFIRMED") {
    const refundWithdrawal = await retryPending(() => api("POST",
      refundFunding.flowPath + "/withdrawal/prepare", undefined, clientToken));
    await api("POST", refundFunding.flowPath + "/withdrawal/submit", {
      buildSessionId: refundWithdrawal.buildSessionId,
      transactionBase64: sign(refundWithdrawal.transactionBase64, clientWallet) }, clientToken);
    await until("refund withdrawal", () => api("GET", refundFunding.flowPath,
      undefined, clientToken), value => stage(value, "WITHDRAWAL").status === "CONFIRMED");
    assert.equal(await balance(treasuryAta),
      refundTreasuryBefore + BigInt(refund.amount * 1_000_000));
  }
  const refunded = await until("USD refund statement", () => api("GET", refundFunding.flowPath,
    undefined, clientToken), value => stage(value, "USD_REFUND").status === "CONFIRMED");
  assert.equal(Number(stage(refunded, "USD_REFUND").amount), refund.amount);
  const refundStatement = await provider(`/internal/unified-mock/fiat-exits/${refundFunding.flowId}/statement`);
  assert.deepEqual(refundStatement.entries.map((entry: any) => entry.kind), ["USD_REFUND"]);
  assert.equal((await api("GET", `/marketplace/jobs/${refund.jobId}`, undefined,
    clientToken)).status, "CANCELLED");
  assert.equal((await api("GET", refundFunding.escrowPath, undefined,
    clientToken)).vaultBalanceBaseUnits, "0");
  const admin = await api("POST", "/auth/sign-in", {
    email: "admin.e2e@example.test", password: env.DEMO_ADMIN_PASSWORD });
  const cases = await until("four matched boundaries", () => api("GET",
    "/admin/payment-flows/reconciliation", undefined, admin.accessToken),
    rows => [releaseFunding.flowId, refundFunding.flowId].every(id => {
      const row = rows.find((item: any) => item.paymentFlowId === id);
      return row && ["usdToClientUsdc", "clientUsdcToVault", "vaultToRecipient", "withdrawalToFiat"]
        .every(key => row[key].status === "MATCHED");
    }));
  const reconciliation = Object.fromEntries([releaseFunding.flowId, refundFunding.flowId].map(id => {
    const row = cases.find((item: any) => item.paymentFlowId === id);
    return [id, ["usdToClientUsdc", "clientUsdcToVault", "vaultToRecipient", "withdrawalToFiat"]
      .map(key => row[key].code)];
  }));
  console.log(JSON.stringify({ result: "PASS", rail: "UNIFIED_USDC_PAYOUT", reconciliation,
    release: { ...release, paymentFlowId: releaseFunding.flowId,
      fundingSignature: releaseFunding.fundingSignature, releaseSignature,
      withdrawalReference: stage(paid, "WITHDRAWAL").reference,
      payoutVnd: stage(paid, "VND_PAYOUT").amount, feeUsdc: stage(paid, "PLATFORM_FEE").amount },
    refund: { ...refund, paymentFlowId: refundFunding.flowId,
      fundingSignature: refundFunding.fundingSignature,
      withdrawalReference: stage(refunded, "WITHDRAWAL").reference,
      refundedUsd: stage(refunded, "USD_REFUND").amount } }, null, 2));
}

describe("Unified USD → USDC → escrow → fiat local flow", function () {
  this.timeout(240_000);
  it("releases and refunds two independent Jobs with provider statements", main);
});
