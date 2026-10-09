import { randomUUID } from "crypto";
import { readFileSync, writeFileSync } from "fs";
import bs58 from "bs58";
import { Connection, Keypair, PublicKey, Transaction } from "@solana/web3.js";
import { getAccount, getAssociatedTokenAddressSync } from "@solana/spl-token";

const values = Object.fromEntries(readFileSync("../.env", "utf8").split(/\r?\n/)
  .filter(line => line.includes("=") && !line.startsWith("#"))
  .map(line => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]));
const keys = values.SOLANA_LOCAL_PRIVATE_KEYS.split(";").filter(Boolean)
  .map(value => Keypair.fromSecretKey(bs58.decode(value)));
const wallet = (address: string) => {
  const key = keys.find(value => value.publicKey.toBase58() === address);
  if (!key) throw new Error(`Missing wallet signer ${address}`);
  return key;
};
const clientWallet = wallet(values.SOLANA_CUSTODIAL_CLIENT_PUBLIC_KEY);
const freelancerWallet = wallet(values.DEMO_FREELANCER_SOLANA_PUBLIC_KEY);
const rpc = new Connection(process.env.ANCHOR_PROVIDER_URL ?? "http://172.23.24.69:9123", "confirmed");
const market = "http://127.0.0.1:9191/api/v1";
const statePath = "/tmp/freelax-timeout-escrow-e2e.json";

async function api(method: string, path: string, token?: string, body?: object,
                   extra: Record<string, string> = {}): Promise<any> {
  const response = await fetch(market + path, {
    method, headers: { "Content-Type": "application/json", ...extra,
      ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const result: any = await response.json();
  if (!response.ok || result.code !== 200) {
    throw new Error(`${method} ${path}: HTTP ${response.status} ${JSON.stringify(result)}`);
  }
  return result.data;
}

function sign(base64: string, key: Keypair) {
  const transaction = Transaction.from(Buffer.from(base64, "base64"));
  transaction.partialSign(key);
  return transaction.serialize().toString("base64");
}

async function waitFor(read: () => Promise<any>, expected: (value: any) => boolean,
                       label: string, tries = 50): Promise<any> {
  let value: any;
  for (let index = 0; index < tries; index++) {
    value = await read();
    if (expected(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error(`${label} did not reach the expected state: ${JSON.stringify(value)}`);
}

async function login() {
  const client = await api("POST", "/auth/sign-in", undefined, {
    email: "nguyenhuutrong11133@gmail.com", password: values.DEMO_CLIENT_PASSWORD,
  });
  const freelancer = await api("POST", "/auth/sign-in", undefined, {
    email: "freelancer.seed@example.com", password: values.DEMO_FREELANCER_PASSWORD,
  });
  return { client, freelancer };
}

async function setup() {
  const { client, freelancer } = await login();
  const clientToken = client.accessToken, freelancerToken = freelancer.accessToken;
  const mint = new PublicKey((await (await fetch("http://127.0.0.1:9193/api/v1/solana/config"))
    .json() as any).data.acceptedMint);
  const freelancerAta = getAssociatedTokenAddressSync(mint, freelancerWallet.publicKey);
  const before = (await getAccount(rpc, freelancerAta)).amount;
  const job = await api("POST", "/marketplace/jobs", clientToken, {
    title: `Local timeout escrow E2E ${randomUUID().slice(0, 8)}`,
    description: "Client silence timeout release proof", category: "WEB_FRONTEND",
    skills: ["TypeScript"], budgetUsd: 20,
    deliveryDueAt: new Date(Date.now() + 72 * 3600_000).toISOString(),
    reviewWindowHours: 24, maxRevisions: 0,
    deliverables: [{ title: "Timeout artifact", description: "One artifact", required: true }],
    acceptanceCriteria: [{ description: "Artifact URL opens", required: true }],
  });
  await api("POST", `/marketplace/jobs/${job.id}/apply`, freelancerToken);
  const assigned = await api("POST", `/marketplace/jobs/${job.id}/assignments`, clientToken,
    { freelancerId: freelancer.userId });
  const contract = assigned.contract;
  const escrowPath = `/contracts/${contract.id}/milestones/${contract.milestoneId}/escrow`;
  const build = await api("POST", escrowPath + "/fund/build", clientToken,
    { walletAddress: clientWallet.publicKey.toBase58() });
  const fund = await api("POST", escrowPath + "/fund/submit", clientToken, {
    buildSessionId: build.buildSessionId,
    transactionBase64: sign(build.transactionBase64, clientWallet),
  });
  await waitFor(() => api("GET", escrowPath, clientToken),
    state => state.status === "Funded", "funding");
  const active = await api("GET", `/marketplace/jobs/${job.id}`, clientToken);
  const payload = {
    summary: "Submitted for timeout release",
    deliverables: active.contract.deliverables.map((item: any) => ({
      requirementId: item.id, url: "https://example.com/timeout-artifact", description: "E2E",
    })),
    acceptanceEvidence: active.contract.acceptanceCriteria.map((item: any) => ({
      criterionId: item.id, url: "https://example.com/timeout-artifact", note: "Done",
    })),
  };
  const actionPath = `/contracts/${contract.id}/escrow/actions`;
  const submissionBuild = await api("POST", actionPath + "/submit/build", freelancerToken,
    { submission: payload });
  const submissionAction = await api("POST", actionPath + `/${submissionBuild.intentId}/submit`,
    freelancerToken, { transactionBase64: sign(submissionBuild.transactionBase64, freelancerWallet) });
  const submitted = await waitFor(() => api("GET", escrowPath, clientToken),
    state => state.status === "Submitted", "submission");
  await api("POST", `/contracts/${contract.id}/submissions`, freelancerToken, payload,
    { "Idempotency-Key": randomUUID() });
  const slot = await rpc.getSlot("confirmed");
  const proof = { jobId: job.id, contractId: contract.id, milestoneId: contract.milestoneId,
    mint: mint.toBase58(), reviewDueAt: submitted.reviewDueAt,
    fundingSignature: fund.fundSignature, submissionSignature: submissionAction.signature,
    freelancerBalanceBefore: String(before), slot };
  writeFileSync(statePath, JSON.stringify(proof, null, 2));
  console.log(JSON.stringify(proof, null, 2));
}

async function finish() {
  const proof = JSON.parse(readFileSync(statePath, "utf8"));
  const { client } = await login();
  const path = `/contracts/${proof.contractId}/milestones/${proof.milestoneId}/escrow`;
  const completed = await waitFor(() => api("GET", `/marketplace/jobs/${proof.jobId}`,
    client.accessToken), state => state.status === "COMPLETED", "timeout release", 90);
  const escrow = await api("GET", path, client.accessToken);
  const freelancerAta = getAssociatedTokenAddressSync(new PublicKey(proof.mint),
    freelancerWallet.publicKey);
  const after = (await getAccount(rpc, freelancerAta)).amount;
  const transaction = escrow.releaseSignature
    ? await rpc.getSignatureStatus(escrow.releaseSignature, { searchTransactionHistory: true }) : null;
  if (escrow.status !== "Released" || escrow.vaultBalanceBaseUnits !== "0"
      || completed.contract.status !== "COMPLETED"
      || !escrow.releaseSignature || transaction?.value?.err
      || after !== BigInt(proof.freelancerBalanceBefore) + 20_000_000n) {
    throw new Error(`Timeout release proof mismatch: ${JSON.stringify({ escrowStatus: escrow.status,
      vaultBalance: escrow.vaultBalanceBaseUnits, jobStatus: completed.status,
      contractStatus: completed.contract.status, releaseSignature: escrow.releaseSignature,
      transaction: transaction?.value, freelancerReceived: String(after - BigInt(proof.freelancerBalanceBefore)) })}`);
  }
  console.log(JSON.stringify({ ...proof, jobStatus: completed.status,
    releaseSignature: escrow.releaseSignature, vaultAfter: escrow.vaultBalanceBaseUnits,
    freelancerReceivedBaseUnits: String(after - BigInt(proof.freelancerBalanceBefore)),
    signatureInRpcHistory: !!transaction?.value }, null, 2));
}

async function claim() {
  const proof = JSON.parse(readFileSync(statePath, "utf8"));
  const response = await fetch(`http://127.0.0.1:9193/api/v1/solana/escrows/${proof.milestoneId}/actions/claim`, {
    method: "POST",
    headers: { "Content-Type": "application/json",
      "X-Internal-Api-Key": values.SOLANA_INTERNAL_API_KEY },
    body: JSON.stringify({ actor: "AiNiVkfTY33inML6ZEBuPp7ymgjkzpsQe1GPfvDdBfCY", mode: "send" }),
  });
  const result: any = await response.json();
  if (!response.ok) throw new Error(`Gateway claim failed: ${JSON.stringify(result)}`);
  console.log(JSON.stringify({ milestoneId: proof.milestoneId, ...result }, null, 2));
}

describe("Marketplace scheduler timeout release", function () {
  this.timeout(120_000);
  const phase = process.env.ESCROW_E2E_PHASE ?? "setup";
  it(phase, phase === "finish" ? finish : phase === "claim" ? claim : setup);
});
