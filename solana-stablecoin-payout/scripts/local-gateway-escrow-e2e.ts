import { randomUUID } from "crypto";
import { readFileSync } from "fs";
import { Keypair, PublicKey, Transaction, LAMPORTS_PER_SOL } from "@solana/web3.js";
import { getAccount, getAssociatedTokenAddressSync } from "@solana/spl-token";
import { getTestEnvironment } from "../tests/helpers/test-environment";

const gateway = process.env.GATEWAY_URL ?? "http://127.0.0.1:9193";
const configured = Object.fromEntries(readFileSync("../.env", "utf8")
  .split(/\r?\n/).filter(line => line.includes("=") && !line.startsWith("#"))
  .map(line => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]));
const apiKey = configured.SOLANA_INTERNAL_API_KEY;
if (!apiKey) throw new Error("SOLANA_INTERNAL_API_KEY is missing");

async function api(method: string, path: string, body?: object): Promise<any> {
  const response = await fetch(gateway + "/api/v1/solana" + path, {
    method,
    headers: { "Content-Type": "application/json", "X-Internal-Api-Key": apiKey },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const result = await response.json();
  if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status} ${JSON.stringify(result)}`);
  return result;
}

async function waitEscrow(id: string, status: string): Promise<any> {
  for (let attempt = 0; attempt < 30; attempt++) {
    const result = await api("GET", `/escrows/${id}`);
    if (result.exists && result.data.status === status) return result.data;
    await new Promise(resolve => setTimeout(resolve, 400));
  }
  throw new Error(`Escrow ${id} did not reach ${status}`);
}

async function sendBuilt(build: any, signer: Keypair): Promise<string> {
  if (!build.requiredSigners.includes(signer.publicKey.toBase58())) {
    throw new Error("Wallet signature is absent from the server-built transaction");
  }
  const transaction = Transaction.from(Buffer.from(build.transactionBase64, "base64"));
  transaction.partialSign(signer);
  const result = await api("POST", "/transactions/submit", {
    buildSessionId: build.buildSessionId,
    transactionBase64: transaction.serialize().toString("base64"),
  });
  return result.signature;
}

async function main() {
  const env = await getTestEnvironment();
  const feePayer = new PublicKey(configured.SOLANA_SYSTEM_FEE_PAYER);
  const airdrop = await env.provider.connection.requestAirdrop(feePayer, 5 * LAMPORTS_PER_SOL);
  await env.provider.connection.confirmTransaction(airdrop, "confirmed");
  const config = await api("GET", "/config");
  if (!config.exists || config.data.acceptedMint !== env.mockUsdc.mint.toBase58()) {
    throw new Error("Gateway did not read the initialized Config PDA");
  }

  async function fund(freelancer: Keypair, amount: number) {
    const id = randomUUID();
    const now = Math.floor(Date.now() / 1000);
    const build = await api("POST", `/escrows/${id}/fund`, {
      client: env.mockUsdc.client.publicKey.toBase58(),
      freelancer: freelancer.publicKey.toBase58(),
      amount: String(amount), fundingExpiresAt: String(now + 3600),
      deliveryDueAt: String(now + 7200), reviewWindowHours: 72,
      maxRevisions: 2, mode: "build",
    });
    const signature = await sendBuilt(build, env.mockUsdc.client);
    const escrow = await waitEscrow(id, "Funded");
    if (escrow.vaultBalanceBaseUnits !== String(amount)) {
      throw new Error("Vault balance does not match the funded milestone amount");
    }
    return { id, signature, escrow };
  }

  const releaseFreelancer = Keypair.generate();
  const release = await fund(releaseFreelancer, 75_000_000);
  const submission = await api("POST", `/escrows/${release.id}/actions/submit`, {
    actor: releaseFreelancer.publicKey.toBase58(), hash: "ab".repeat(32), mode: "build",
  });
  const submissionSignature = await sendBuilt(submission, releaseFreelancer);
  await waitEscrow(release.id, "Submitted");
  const approve = await api("POST", `/escrows/${release.id}/actions/release`, {
    actor: env.mockUsdc.client.publicKey.toBase58(), mode: "build",
  });
  const releaseSignature = await sendBuilt(approve, env.mockUsdc.client);
  const released = await waitEscrow(release.id, "Released");
  const releaseAta = getAssociatedTokenAddressSync(env.mockUsdc.mint, releaseFreelancer.publicKey);
  const received = (await getAccount(env.provider.connection, releaseAta)).amount;
  if (released.vaultBalanceBaseUnits !== "0" || received !== 75_000_000n) {
    throw new Error("Release did not move the full amount from vault to Freelancer");
  }

  const refundFreelancer = Keypair.generate();
  const beforeRefund = (await getAccount(env.provider.connection, env.mockUsdc.clientAta)).amount;
  const refund = await fund(refundFreelancer, 20_000_000);
  const refundSubmission = await api("POST", `/escrows/${refund.id}/actions/submit`, {
    actor: refundFreelancer.publicKey.toBase58(), hash: "bc".repeat(32), mode: "build",
  });
  const refundSubmissionSignature = await sendBuilt(refundSubmission, refundFreelancer);
  await waitEscrow(refund.id, "Submitted");
  const dispute = await api("POST", `/escrows/${refund.id}/actions/open-dispute`, {
    actor: env.mockUsdc.client.publicKey.toBase58(), hash: "cd".repeat(32), mode: "build",
  });
  const disputeSignature = await sendBuilt(dispute, env.mockUsdc.client);
  await waitEscrow(refund.id, "Disputed");
  const resolution = await api("POST", `/escrows/${refund.id}/actions/resolve-refund`, {
    actor: config.data.admin, hash: "ef".repeat(32), mode: "send",
  });
  const refunded = await waitEscrow(refund.id, "Refunded");
  const afterRefund = (await getAccount(env.provider.connection, env.mockUsdc.clientAta)).amount;
  if (refunded.vaultBalanceBaseUnits !== "0" || afterRefund !== beforeRefund) {
    throw new Error("Dispute refund did not return the full amount to Client");
  }

  console.log(JSON.stringify({
    release: { milestoneId: release.id, fundSignature: release.signature,
      submissionSignature, releaseSignature, vaultAfter: released.vaultBalanceBaseUnits,
      freelancerReceivedBaseUnits: String(received) },
    refund: { milestoneId: refund.id, fundSignature: refund.signature,
      submissionSignature: refundSubmissionSignature, disputeSignature,
      resolutionSignature: resolution.signature,
      vaultAfter: refunded.vaultBalanceBaseUnits,
      clientBalanceRestored: afterRefund === beforeRefund },
  }, null, 2));
}

main().catch(error => { console.error(error); process.exitCode = 1; });
