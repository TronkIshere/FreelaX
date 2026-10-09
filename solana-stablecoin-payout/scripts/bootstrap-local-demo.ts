import { readFileSync } from "fs";
import bs58 from "bs58";
import { Connection, Keypair, LAMPORTS_PER_SOL, PublicKey } from "@solana/web3.js";
import {
  getAccount,
  getAssociatedTokenAddressSync,
  getOrCreateAssociatedTokenAccount,
  mintTo,
} from "@solana/spl-token";
import { getTestEnvironment } from "../tests/helpers/test-environment";

const settings = Object.fromEntries(readFileSync("../.env", "utf8")
  .split(/\r?\n/).filter(line => line.includes("=") && !line.startsWith("#"))
  .map(line => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]));
const localSigners = (settings.SOLANA_LOCAL_PRIVATE_KEYS || "").split(";").filter(Boolean)
  .map(value => Keypair.fromSecretKey(bs58.decode(value)));

function signer(address: string): Keypair {
  const found = localSigners.find(value => value.publicKey.toBase58() === address);
  if (!found) throw new Error(`Missing local signer for ${address}`);
  return found;
}

async function main() {
  const rpcUrl = process.env.ANCHOR_PROVIDER_URL;
  if (!rpcUrl || !new URL(rpcUrl).hostname.match(/^(127\.0\.0\.1|localhost)$/)) {
    throw new Error("Set ANCHOR_PROVIDER_URL to the local validator RPC");
  }
  if (!process.env.ANCHOR_WALLET) throw new Error("Set ANCHOR_WALLET to the local authority keypair");
  const authority = Keypair.fromSecretKey(Uint8Array.from(JSON.parse(
    readFileSync(process.env.ANCHOR_WALLET, "utf8"))));
  const client = signer(settings.SOLANA_CUSTODIAL_CLIENT_PUBLIC_KEY);
  const freelancer = signer(settings.DEMO_FREELANCER_SOLANA_PUBLIC_KEY);
  signer(settings.SOLANA_SYSTEM_FEE_PAYER);
  const response = await fetch("http://127.0.0.1:9193/api/v1/solana/config", {
    headers: { "X-Internal-Api-Key": settings.SOLANA_INTERNAL_API_KEY },
  });
  if (!response.ok) throw new Error(`Gateway config: HTTP ${response.status}`);
  const config: any = await response.json();
  let mint: PublicKey;
  let mode = "existing";
  if (config.exists) {
    if (config.data.admin !== authority.publicKey.toBase58()) {
      throw new Error("Existing Config admin differs from the local upgrade authority");
    }
    mint = new PublicKey(config.data.acceptedMint);
  } else {
    const environment = await getTestEnvironment();
    mint = environment.mockUsdc.mint;
    mode = "initialized";
    for (const address of [settings.SOLANA_SYSTEM_FEE_PAYER,
      client.publicKey.toBase58(), freelancer.publicKey.toBase58()]) {
      const signature = await environment.provider.connection.requestAirdrop(
        new PublicKey(address), 3 * LAMPORTS_PER_SOL);
      await environment.provider.connection.confirmTransaction(signature, "confirmed");
    }
    const clientAta = (await getOrCreateAssociatedTokenAccount(
      environment.provider.connection, environment.payer, mint, client.publicKey)).address;
    await mintTo(environment.provider.connection, environment.payer, mint, clientAta,
      environment.mockUsdc.mintAuthority, 200_000_000n);
  }
  const connection = new Connection(rpcUrl, "confirmed");
  const clientAta = getAssociatedTokenAddressSync(mint, client.publicKey);
  const balance = (await getAccount(connection, clientAta)).amount;
  console.log(JSON.stringify({ mode, network: "localnet", mint: mint.toBase58(),
    clientWallet: client.publicKey.toBase58(), clientMockUsdcBaseUnits: String(balance) }));
}

describe("Local escrow demo bootstrap", function () {
  this.timeout(120_000);
  it("initializes a fresh ledger or verifies the existing fixture", main);
});
