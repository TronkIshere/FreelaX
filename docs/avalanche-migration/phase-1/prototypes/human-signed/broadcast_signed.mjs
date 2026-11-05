// Phase 1 E1 evidence recorder for HUMAN-SIGNED transactions (Node >= 22, no dependencies).
//
// Owner rule M2: no backend private key and no backend signing operation in AVALANCHE_FUJI. This tool therefore has NO key
// input of any kind. A human signs every transaction beforehand with a signer they control (see README.md in this folder);
// this tool only (1) validates the environment fail-closed, (2) checks every signed transaction's chain id, (3) broadcasts the
// already-signed bytes in plan order and (4) records public observations (hashes, timings, receipts, block timestamps).
//
// usage: node broadcast_signed.mjs <LOCAL_SIMULATION|AVALANCHE_FUJI> <rpc-url> <plan.jsonl> <out.json>
// A plan line is JSON: {"id":"E2-success","raw":"0x02...","wait":"finalized"|"receipt"|"none",
//                       "group":"ladder" (optional), "notBeforeHeadTs":<sec> (optional), "deadline":<sec> (optional),
//                       "resendAfterReceipt":true (optional), "probeLogs":true (optional: poll eth_getLogs for the lab
//                       contract between acceptance and receipt, criterion #16)}
// Criterion #25: once the sender is known the tool reads USDC balanceOf(sender) and decimals() with eth_call (read-only).
// AVALANCHE_FUJI always uses the Circle test USDC below (same constant as the gateway's ManifestVerifier.FUJI_USDC); on
// LOCAL_SIMULATION the token is optional (USDC_TOKEN, for example a MockUSDC deployed on Anvil).
import { readFileSync, writeFileSync } from 'node:fs';

const [ENV, RPC_URL, PLAN, OUT] = process.argv.slice(2);
const FUJI_HOSTS = new Set(['api.avax-test.network', 'avalanche-fuji-c-chain-rpc.publicnode.com']);
const CHAINS = { LOCAL_SIMULATION: new Set([31337n, 43113n]), AVALANCHE_FUJI: new Set([43113n]) };
const REJECTED = new Set([0n, 1n, 43114n]);
const MAX_BALANCE_WEI = 10n * 10n ** 18n; // a faucet wallet never holds this much: refuse a possible real-funds wallet
const FORBIDDEN_FIELDS = /key|mnemonic|seed|secret|password/i;
export const FUJI_USDC = '0x5425890298aed601595a70ab815c96711a31bc65'; // Circle test USDC on Fuji (ManifestVerifier.FUJI_USDC)
const SEL_BALANCE_OF = '0x70a08231'; const SEL_DECIMALS = '0x313ce567';

/** The token whose balanceOf is read for criterion #25; Fuji is pinned and cannot be overridden. */
export function usdcTokenFor(env, override) {
  if (env === 'AVALANCHE_FUJI') {
    if (override && override.toLowerCase() !== FUJI_USDC) throw new GuardError('USDC_TOKEN_NOT_ALLOWED', 'Fuji uses the pinned Circle test USDC only');
    return FUJI_USDC;
  }
  if (!override) return null;
  if (!/^0x[0-9a-fA-F]{40}$/.test(override)) throw new GuardError('USDC_TOKEN_INVALID', 'USDC_TOKEN must be a 20-byte hex address');
  return override.toLowerCase();
}
export const balanceOfCall = (holder) => SEL_BALANCE_OF + holder.toLowerCase().replace(/^0x/, '').padStart(64, '0');

class GuardError extends Error { constructor(code, msg) { super(`${code}: ${msg}`); this.code = code; } }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------- guards that run before ANY network request ----------
export function checkEndpoint(env, url) {
  if (!CHAINS[env]) throw new GuardError('ENVIRONMENT_INVALID', 'environment must be LOCAL_SIMULATION or AVALANCHE_FUJI');
  let u; try { u = new URL(url); } catch { throw new GuardError('BAD_ENDPOINT', 'not a URL'); }
  const host = u.hostname.toLowerCase().replace(/\.$/, '');
  if (!['http:', 'https:'].includes(u.protocol) || !host) throw new GuardError('BAD_ENDPOINT', 'http(s) with a host required');
  if (u.username || u.password) throw new GuardError('ENDPOINT_CREDENTIALS_FORBIDDEN', host);
  const lowered = url.toLowerCase();
  if (host === 'api.avax.network' || host.endsWith('.avax.network') || lowered.includes('mainnet') || lowered.includes('43114')) {
    throw new GuardError('REJECTED_MAINNET_ENDPOINT', host);
  }
  const local = host === 'localhost' || host === '::1' || host === '[::1]' || /^127(\.\d{1,3}){3}$/.test(host)
    || host === 'host.docker.internal' || (!host.includes('.') && !host.includes(':'));
  if (env === 'LOCAL_SIMULATION' && !local) throw new GuardError('LOCAL_REQUIRES_LOCAL_ENDPOINT', host);
  if (env === 'AVALANCHE_FUJI') {
    if (local) throw new GuardError('FUJI_FORBIDS_LOCAL_ENDPOINT', host);
    if (u.protocol !== 'https:') throw new GuardError('FUJI_REQUIRES_HTTPS', host);
    if (!FUJI_HOSTS.has(host)) throw new GuardError('FUJI_HOST_NOT_ALLOW_LISTED', host); // O-8 deferred: allow-list unchanged
  }
  return host;
}

// Minimal RLP decoder (enough to read an EIP-1559 typed transaction).
function rlpDecode(buf, pos = 0) {
  const b = buf[pos];
  if (b < 0x80) return [buf.subarray(pos, pos + 1), pos + 1];
  if (b < 0xb8) { const l = b - 0x80; return [buf.subarray(pos + 1, pos + 1 + l), pos + 1 + l]; }
  if (b < 0xc0) { const ll = b - 0xb7; const l = Number(BigInt('0x' + buf.subarray(pos + 1, pos + 1 + ll).toString('hex'))); return [buf.subarray(pos + 1 + ll, pos + 1 + ll + l), pos + 1 + ll + l]; }
  let start, end;
  if (b < 0xf8) { start = pos + 1; end = start + (b - 0xc0); } else { const ll = b - 0xf7; start = pos + 1 + ll; end = start + Number(BigInt('0x' + buf.subarray(pos + 1, start).toString('hex'))); }
  const items = []; let p = start;
  while (p < end) { const [item, next] = rlpDecode(buf, p); items.push(item); p = next; }
  if (p !== end) throw new GuardError('BAD_SIGNED_TX', 'malformed RLP list');
  return [items, end];
}
const toBig = (bytes) => (bytes.length ? BigInt('0x' + Buffer.from(bytes).toString('hex')) : 0n);

/** Reads chain id, nonce, fees and target from an already-signed EIP-1559 transaction. Only type 2 is accepted. */
export function decodeSigned(raw) {
  if (typeof raw !== 'string' || !/^0x02([0-9a-fA-F]{2})+$/.test(raw)) throw new GuardError('BAD_SIGNED_TX', 'only signed EIP-1559 (type 0x02) transactions are accepted');
  const buf = Buffer.from(raw.slice(4), 'hex');
  const [f, end] = rlpDecode(buf);
  if (end !== buf.length || !Array.isArray(f) || f.length !== 12) throw new GuardError('BAD_SIGNED_TX', 'unexpected EIP-1559 field count');
  return { chainId: toBig(f[0]), nonce: toBig(f[1]), maxPriorityFeePerGas: toBig(f[2]), maxFeePerGas: toBig(f[3]), gasLimit: toBig(f[4]),
    to: f[5].length ? '0x' + Buffer.from(f[5]).toString('hex') : null, signed: f[10].length > 0 && f[11].length > 0 };
}

export function loadPlan(env, text) {
  const lines = text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l && !l.startsWith('#'));
  if (!lines.length) throw new GuardError('PLAN_EMPTY', 'no transactions');
  return lines.map((line, i) => {
    let e; try { e = JSON.parse(line); } catch { throw new GuardError('PLAN_INVALID', `line ${i + 1} is not JSON`); }
    for (const k of Object.keys(e)) if (FORBIDDEN_FIELDS.test(k)) throw new GuardError('PLAN_CONTAINS_SECRET_FIELD', `line ${i + 1} field "${k}" is refused`);
    if (typeof e.id !== 'string' || !e.id) throw new GuardError('PLAN_INVALID', `line ${i + 1} needs an id`);
    const tx = decodeSigned(e.raw);
    if (!tx.signed) throw new GuardError('BAD_SIGNED_TX', `${e.id} is not signed`);
    if (REJECTED.has(tx.chainId)) throw new GuardError('REJECTED_MAINNET_CHAIN_ID', `${e.id} chain id ${tx.chainId}`);
    if (!CHAINS[env].has(tx.chainId)) throw new GuardError('CHAIN_ID_NOT_ALLOWED', `${e.id} chain id ${tx.chainId} in ${env}`);
    return { ...e, tx };
  });
}

// ---------- network ----------
let rpcId = 1; let lastCall = 0;
const MIN_GAP_MS = Number(process.env.MIN_GAP_MS || 600); // conservative pacing for public endpoints
async function rpc(method, params) {
  for (let attempt = 0; attempt < 5; attempt++) {
    const wait = lastCall + MIN_GAP_MS - Date.now(); if (wait > 0) await sleep(wait);
    lastCall = Date.now();
    let res;
    try { res = await fetch(RPC_URL, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: rpcId++, method, params }), signal: AbortSignal.timeout(20000) }); }
    catch (e) { anomalies.push({ at: Date.now(), method, error: String(e).slice(0, 80) }); await sleep(1500 * (attempt + 1)); continue; }
    const body = await res.text();
    if (res.status !== 200) { // HTTP status before JSON: a throttled endpoint answers 429 with HTML
      const ra = res.headers.get('retry-after'); anomalies.push({ at: Date.now(), method, http: res.status, retryAfter: ra });
      await sleep(Math.min(Number(ra) || 5, 120) * 1000); continue;
    }
    try { return JSON.parse(body); } catch { anomalies.push({ at: Date.now(), method, nonJson: true }); await sleep(1500); }
  }
  throw new Error('RPC failed after retries: ' + method);
}
const anomalies = [];
const num = (h) => (h == null ? null : Number(BigInt(h)));

async function observe(hash, t0, wait, probe = null) {
  const o = { hash };
  // #16: what a log reader sees between acceptance and execution (counts only; logs are public data). A poll counts as
  // "before receipt" only when the receipt read right after it is still empty.
  const lp = probe ? { address: probe.address, fromBlock: probe.fromBlock, polls: 0, pollsBeforeReceipt: 0, nonEmptyPollsBeforeReceipt: 0, errors: 0 } : null;
  let rc = null;
  const pollLogs = async () => {
    if (!lp || rc) return;
    const lg = await rpc('eth_getLogs', [{ address: lp.address, fromBlock: lp.fromBlock, toBlock: 'latest' }]);
    lp.polls++;
    const seen = !lg.error && Array.isArray(lg.result) && lg.result.some((l) => l.transactionHash?.toLowerCase() === hash.toLowerCase());
    if (lg.error || !Array.isArray(lg.result)) lp.errors++;
    const after = (await rpc('eth_getTransactionReceipt', [hash])).result;
    if (after) { rc = after; return; }
    lp.pollsBeforeReceipt++; if (seen) lp.nonEmptyPollsBeforeReceipt++;
  };
  await pollLogs(); // first probe immediately after acceptance, before any other read
  for (let i = 0; i < 60 && o.byHashMs == null; i++) { const r = await rpc('eth_getTransactionByHash', [hash]); if (r.result) o.byHashMs = Date.now() - t0; else { await pollLogs(); await sleep(250); } }
  if (wait === 'none') return o;
  for (let i = 0; i < 120 && !rc; i++) {
    rc = (await rpc('eth_getTransactionReceipt', [hash])).result;
    if (!rc) { await pollLogs(); if (!rc) await sleep(250); }
  }
  if (lp) {
    o.logsBeforeReceipt = lp;
    if (rc) { const lg = await rpc('eth_getLogs', [{ address: lp.address, fromBlock: rc.blockNumber, toBlock: rc.blockNumber }]);
      o.logsAfterReceipt = Array.isArray(lg.result) ? lg.result.filter((l) => l.transactionHash?.toLowerCase() === hash.toLowerCase()).length : null; }
  }
  if (!rc) { o.receipt = 'NOT_SEEN_WITHIN_WINDOW'; return o; } // not evidence of absence
  o.receiptMs = Date.now() - t0; o.status = rc.status; o.block = num(rc.blockNumber); o.from = rc.from; o.contractAddress = rc.contractAddress;
  o.gasUsed = num(rc.gasUsed); o.effectiveGasPrice = rc.effectiveGasPrice;
  const blk = (await rpc('eth_getBlockByNumber', [rc.blockNumber, false])).result;
  o.blockTimestamp = num(blk?.timestamp); o.blockTimestampMs = blk?.timestampMilliseconds != null ? num(blk.timestampMilliseconds) : null;
  if (wait === 'finalized') {
    const tag = ENV === 'AVALANCHE_FUJI' ? 'finalized' : 'latest'; // anvil's finalized tag does not advance (Phase 1)
    for (let i = 0; i < 240; i++) { const b = (await rpc('eth_getBlockByNumber', [tag, false])).result; if (b && num(b.number) >= o.block + (tag === 'latest' ? 1 : 0)) { o.finalizedMs = Date.now() - t0; o.finalityTag = tag; break; } await sleep(250); }
  }
  return o;
}

async function main() {
  if (!ENV || !RPC_URL || !PLAN || !OUT) { console.error('usage: node broadcast_signed.mjs <env> <rpc-url> <plan.jsonl> <out.json>'); process.exit(2); }
  const host = checkEndpoint(ENV, RPC_URL); // before any request
  const plan = loadPlan(ENV, readFileSync(PLAN, 'utf8')); // before any request
  const evidence = { tool: 'human-signed/broadcast_signed.mjs', environment: ENV, host, startedAt: new Date().toISOString(),
    note: 'All transactions were signed by a human-held signer before this run; this tool holds no key.', results: [] };
  const chainId = BigInt((await rpc('eth_chainId', [])).result);
  if (REJECTED.has(chainId) || !CHAINS[ENV].has(chainId)) throw new GuardError('CHAIN_ID_NOT_ALLOWED', `endpoint reports ${chainId}`);
  const client = String((await rpc('web3_clientVersion', [])).result || '').toLowerCase();
  const family = ENV === 'LOCAL_SIMULATION' ? 'anvil/' : 'avalanchego/';
  if (!client.startsWith(family)) throw new GuardError('CLIENT_FAMILY_MISMATCH', `${ENV} expects ${family}`);
  if (plan.some((p) => p.tx.chainId !== chainId)) throw new GuardError('SIGNED_FOR_ANOTHER_CHAIN', 'a plan transaction is signed for a different chain id than the endpoint');
  Object.assign(evidence, { chainId: Number(chainId), clientFamily: family.slice(0, -1) });

  const usdc = usdcTokenFor(ENV, process.env.USDC_TOKEN); // before any request
  let sender = null; let ladderDone = new Set(); let lab = null;
  for (const p of plan) {
    if (p.group && ladderDone.has(p.group)) { evidence.results.push({ id: p.id, skipped: 'an earlier entry of this group was accepted' }); continue; }
    if (p.notBeforeHeadTs) {
      // Time-bounded wait (2026-11-05 Fuji run: the former 600-iteration cap expired ~16 s before T0 because one iteration takes
      // under a second, so E7-k0 was sent early). Wait until the head reaches the target, at most target + 120 s by the local clock.
      const giveUpAt = Date.now() + Math.max(0, p.notBeforeHeadTs * 1000 - Date.now()) + 120_000;
      let reached = false;
      while (!reached && Date.now() < giveUpAt) { const b = (await rpc('eth_getBlockByNumber', ['latest', false])).result; reached = num(b.timestamp) >= p.notBeforeHeadTs; if (!reached) await sleep(500); }
      if (!reached) anomalies.push({ at: Date.now(), id: p.id, waitTimedOut: true });
    }
    const head = (await rpc('eth_getBlockByNumber', ['latest', false])).result;
    const r = { id: p.id, nonce: Number(p.tx.nonce), maxFeePerGas: p.tx.maxFeePerGas.toString(), maxPriorityFeePerGas: p.tx.maxPriorityFeePerGas.toString(),
      headTsAtSend: num(head.timestamp), deadline: p.deadline ?? null };
    const t0 = Date.now();
    const sent = await rpc('eth_sendRawTransaction', [p.raw]);
    r.ackMs = Date.now() - t0;
    if (sent.error) { r.rejected = sent.error.message; evidence.results.push(r); continue; }
    r.hash = sent.result;
    if (p.group) ladderDone.add(p.group);
    if (p.resendImmediately) { const again = await rpc('eth_sendRawTransaction', [p.raw]); r.immediateResend = again.error ? again.error.message : 'ACCEPTED ' + again.result; }
    const probe = p.probeLogs && lab ? { address: lab, fromBlock: head.number } : null;
    if (p.probeLogs && !lab) r.logsProbe = 'NOT_RUN (lab contract address unknown)';
    r.obs = await observe(r.hash, t0, p.wait || 'receipt', probe);
    if (r.obs.contractAddress && !lab) lab = r.obs.contractAddress.toLowerCase();
    if (r.obs.from) {
      if (!sender) sender = r.obs.from.toLowerCase();
      else if (sender !== r.obs.from.toLowerCase()) r.senderChanged = true;
    }
    if (ENV === 'AVALANCHE_FUJI' && sender && !evidence.balanceChecked) {
      const bal = BigInt((await rpc('eth_getBalance', [sender, 'latest'])).result);
      evidence.balanceChecked = true;
      if (bal > MAX_BALANCE_WEI) { evidence.results.push(r); evidence.stopped = 'REFUSED_SIGNER_BALANCE_TOO_HIGH (possible real-funds wallet)'; break; }
    }
    if (sender && !evidence.usdcBalanceOf) {
      if (!usdc) evidence.usdcBalanceOf = { token: null, result: 'NOT_APPLICABLE (LOCAL_SIMULATION without USDC_TOKEN)' };
      else {
        const b = await rpc('eth_call', [{ to: usdc, data: balanceOfCall(sender) }, 'latest']);
        const d = await rpc('eth_call', [{ to: usdc, data: SEL_DECIMALS }, 'latest']);
        const okHex = (x) => typeof x?.result === 'string' && /^0x[0-9a-fA-F]{64}$/.test(x.result);
        evidence.usdcBalanceOf = { token: usdc, holder: sender, callOk: okHex(b),
          balance: okHex(b) ? BigInt(b.result).toString() : null, decimals: okHex(d) ? Number(BigInt(d.result)) : null,
          error: okHex(b) ? null : String(b.error?.message ?? b.result ?? 'no result').slice(0, 120) };
      }
    }
    if (p.resendAfterReceipt) { const again = await rpc('eth_sendRawTransaction', [p.raw]); r.resendAfterReceipt = again.error ? again.error.message : 'ACCEPTED ' + again.result; }
    if (p.deadline != null && r.obs.blockTimestamp != null) {
      r.includedMinusDeadline = r.obs.blockTimestamp - p.deadline;
      r.statusMatchesTsLeDeadline = (r.obs.status === '0x1') === (r.obs.blockTimestamp <= p.deadline);
    }
    evidence.results.push(r);
  }
  // Final pass: entries broadcast with wait "none" (gap and ladder) get their outcome after the filler has mined.
  for (const r of evidence.results) {
    if (!r.hash || r.obs?.status) continue;
    const rc = (await rpc('eth_getTransactionReceipt', [r.hash])).result;
    const byHash = (await rpc('eth_getTransactionByHash', [r.hash])).result;
    r.finalPass = { receiptStatus: rc ? rc.status : null, block: rc ? num(rc.blockNumber) : null, visibleByHash: !!byHash };
  }
  evidence.sender = sender; evidence.anomalies = anomalies; evidence.finishedAt = new Date().toISOString();
  writeFileSync(OUT, JSON.stringify(evidence, null, 2));
  console.log(`wrote ${OUT}: ${evidence.results.length} entries; sender ${sender}`);
}

if (import.meta.url === `file://${process.argv[1].replace(/\\/g, '/')}` || process.argv[1]?.endsWith('broadcast_signed.mjs')) {
  main().catch((e) => { console.error(e.code ? e.message : 'FAILED: ' + e.message); process.exit(1); });
}
