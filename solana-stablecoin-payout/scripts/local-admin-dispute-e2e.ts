import { randomUUID } from "crypto";
import { readFileSync } from "fs";
import bs58 from "bs58";
import { Connection, Keypair, PublicKey, Transaction } from "@solana/web3.js";
import { getAccount, getAssociatedTokenAddressSync } from "@solana/spl-token";

const settings = Object.fromEntries(readFileSync("../.env", "utf8")
  .split(/\r?\n/).filter(line => line.includes("=") && !line.startsWith("#"))
  .map(line => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]));
const signers = (settings.SOLANA_LOCAL_PRIVATE_KEYS || "").split(";").filter(Boolean)
  .map(value => Keypair.fromSecretKey(bs58.decode(value)));
function signer(address: string): Keypair {
  const key = signers.find(value => value.publicKey.toBase58() === address);
  if (!key) throw new Error(`Missing local signer for ${address}`);
  return key;
}
const clientWallet = signer(settings.SOLANA_CUSTODIAL_CLIENT_PUBLIC_KEY);
const freelancerWallet = signer(settings.DEMO_FREELANCER_SOLANA_PUBLIC_KEY);
const rpc = new Connection(process.env.ANCHOR_PROVIDER_URL || "http://127.0.0.1:9123", "confirmed");
const marketplace = "http://127.0.0.1:9191/api/v1";

async function api(method: string, path: string, token?: string, body?: object,
                   headers: Record<string, string> = {}): Promise<any> {
  const response = await fetch(marketplace + path, { method,
    headers: { "Content-Type": "application/json", ...headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body) });
  const result: any = await response.json();
  if (!response.ok || result.code !== 200)
    throw new Error(`${method} ${path}: HTTP ${response.status} ${JSON.stringify(result)}`);
  return result.data;
}

function signed(base64: string, key: Keypair): string {
  const transaction = Transaction.from(Buffer.from(base64, "base64"));
  transaction.partialSign(key);
  return transaction.serialize().toString("base64");
}

async function until(label: string, read: () => Promise<any>, ready: (value: any) => boolean,
                     attempts = 100): Promise<any> {
  let last: any;
  for (let index = 0; index < attempts; index++) {
    last = await read();
    if (ready(last)) return last;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error(`${label} timed out: ${JSON.stringify(last)}`);
}

async function run() {
  const [client, freelancer, admin] = await Promise.all([
    api("POST", "/auth/sign-in", undefined,
      { email: "nguyenhuutrong11133@gmail.com", password: settings.DEMO_CLIENT_PASSWORD }),
    api("POST", "/auth/sign-in", undefined,
      { email: "freelancer.seed@example.com", password: settings.DEMO_FREELANCER_PASSWORD }),
    api("POST", "/auth/sign-in", undefined,
      { email: "admin.e2e@example.test", password: settings.DEMO_ADMIN_PASSWORD }),
  ]);
  const clientToken = client.accessToken;
  const freelancerToken = freelancer.accessToken;
  const adminToken = admin.accessToken;
  const gateway = await fetch("http://127.0.0.1:9193/api/v1/solana/config",
    { headers: { "X-Internal-Api-Key": settings.SOLANA_INTERNAL_API_KEY } });
  if (!gateway.ok) throw new Error(`Gateway config HTTP ${gateway.status}`);
  const config: any = await gateway.json();
  const mint = new PublicKey(config.data.acceptedMint);
  const clientAta = getAssociatedTokenAddressSync(mint, clientWallet.publicKey);
  const amount = 12_000_000n;

  const job = await api("POST", "/marketplace/jobs", clientToken, {
    title: `Local admin escrow dispute E2E ${randomUUID().slice(0, 8)}`,
    description: "Admin refund through Marketplace scheduler and Anchor",
    category: "WEB_FRONTEND", skills: ["TypeScript"], budgetUsd: 12,
    deliveryDueAt: new Date(Date.now() + 48 * 3600_000).toISOString(),
    reviewWindowHours: 72, maxRevisions: 0,
    deliverables: [{ title: "Disputed artifact", description: "One artifact", required: true }],
    acceptanceCriteria: [{ description: "Artifact URL opens", required: true }],
  });
  await api("POST", `/marketplace/jobs/${job.id}/apply`, freelancerToken);
  const assigned = await api("POST", `/marketplace/jobs/${job.id}/assignments`, clientToken,
    { freelancerId: freelancer.userId });
  const contract = assigned.contract;
  const escrowPath = `/contracts/${contract.id}/milestones/${contract.milestoneId}/escrow`;
  const fundBuild = await api("POST", escrowPath + "/fund/build", clientToken,
    { walletAddress: clientWallet.publicKey.toBase58() });
  const fund = await api("POST", escrowPath + "/fund/submit", clientToken, {
    buildSessionId: fundBuild.buildSessionId,
    transactionBase64: signed(fundBuild.transactionBase64, clientWallet),
  });
  await until("funding", () => api("GET", escrowPath, clientToken),
    value => value.status === "Funded" && value.vaultBalanceBaseUnits === String(amount));
  const clientBeforeRefund = (await getAccount(rpc, clientAta)).amount;
  const active = await api("GET", `/marketplace/jobs/${job.id}`, clientToken);
  const payload = {
    summary: "Artifact submitted for Admin dispute E2E",
    deliverables: active.contract.deliverables.map((item: any) => ({
      requirementId: item.id, url: "https://example.com/disputed-artifact", description: "E2E" })),
    acceptanceEvidence: active.contract.acceptanceCriteria.map((item: any) => ({
      criterionId: item.id, url: "https://example.com/disputed-artifact", note: "Submitted" })),
  };
  const actionPath = `/contracts/${contract.id}/escrow/actions`;
  const submissionBuild = await api("POST", actionPath + "/submit/build", freelancerToken,
    { submission: payload });
  await api("POST", actionPath + `/${submissionBuild.intentId}/submit`, freelancerToken,
    { transactionBase64: signed(submissionBuild.transactionBase64, freelancerWallet) });
  await until("submission", () => api("GET", escrowPath, clientToken),
    value => value.status === "Submitted");
  await api("POST", `/contracts/${contract.id}/submissions`, freelancerToken, payload,
    { "Idempotency-Key": randomUUID() });

  const reason = { reasonCode: "E2E_ADMIN_REFUND", description: "Client contests submitted artifact", evidence: [] };
  const disputeBuild = await api("POST", actionPath + "/open-dispute/build", clientToken,
    { reasonCode: reason.reasonCode, description: reason.description });
  const disputeAction = await api("POST", actionPath + `/${disputeBuild.intentId}/submit`,
    clientToken, { transactionBase64: signed(disputeBuild.transactionBase64, clientWallet) });
  await until("on-chain dispute", () => api("GET", escrowPath, clientToken),
    value => value.status === "Disputed");
  let dispute = await api("GET", `/contracts/${contract.id}/disputes`, clientToken);
  if (!dispute) dispute = await api("POST", `/contracts/${contract.id}/disputes`,
    clientToken, reason);
  if (dispute.status !== "OPEN") throw new Error(`Dispute not open: ${JSON.stringify(dispute)}`);
  await api("POST", `/admin/disputes/${dispute.disputeId}/claim`, adminToken);
  const decision = await api("POST", `/admin/disputes/${dispute.disputeId}/resolve`, adminToken,
    { outcome: "REFUND_TO_CLIENT", reason: "E2E verified refund to Client" },
    { "Idempotency-Key": randomUUID() });
  if (decision.status !== "DECISION_PENDING_REFUND")
    throw new Error(`Admin decision not pending refund: ${JSON.stringify(decision)}`);
  const cancelled = await until("Admin refund reconciliation",
    () => api("GET", `/marketplace/jobs/${job.id}`, clientToken),
    value => value.status === "CANCELLED" && value.contract?.status === "CANCELLED", 120);
  const escrow = await api("GET", escrowPath, clientToken);
  const resolved = await api("GET", `/admin/disputes/${dispute.disputeId}`, adminToken);
  const clientAfterRefund = (await getAccount(rpc, clientAta)).amount;
  const signature = escrow.resolutionSignature;
  const transaction = signature ? await rpc.getSignatureStatus(signature, { searchTransactionHistory: true }) : null;
  if (escrow.status !== "Refunded" || escrow.vaultBalanceBaseUnits !== "0"
      || clientAfterRefund !== clientBeforeRefund + amount
      || resolved.dispute.status !== "RESOLVED_REFUND"
      || !signature || !transaction?.value || transaction.value.err) {
    throw new Error(`Admin refund proof mismatch: ${JSON.stringify({ escrow, resolved,
      clientBeforeRefund: String(clientBeforeRefund), clientAfterRefund: String(clientAfterRefund),
      transaction: transaction?.value })}`);
  }
  console.log(JSON.stringify({ result: "PASS", jobId: job.id, contractId: contract.id,
    milestoneId: contract.milestoneId, disputeId: dispute.disputeId,
    fundingSignature: fund.fundSignature, disputeSignature: disputeAction.signature,
    resolutionSignature: signature, jobStatus: cancelled.status,
    disputeStatus: resolved.dispute.status, vaultAfter: escrow.vaultBalanceBaseUnits,
    clientRestoredBaseUnits: String(clientAfterRefund - clientBeforeRefund) }, null, 2));
}

describe("Marketplace Admin → scheduler → Gateway → Anchor refund", function () {
  this.timeout(180_000);
  it("refunds a disputed on-chain escrow with verified token balances", run);
});
