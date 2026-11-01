// Part B tasks D+E: restart-safe finalized-cursor log scanning prototype. Fuji only, read-only. Node >= 22.
// Scans USDC Transfer logs over [finalized-SPAN, finalized] on the OFFICIAL RPC in 100-block chunks, persisting a cursor file after each chunk
// and a unique-key set (txHash:logIndex). A simulated crash (process exit) happens mid-scan; a restart resumes from the cursor.
// One chunk is deliberately re-processed to prove duplicate suppression. Ground truth = ONE 3000-block call to a different provider (publicnode).
// usage: node cursor_scan.mjs <state.json> <out.json> [crashAfterChunks]
import { readFileSync, writeFileSync, existsSync, rmSync } from 'node:fs';
const [STATE, OUT, CRASH] = [process.argv[2], process.argv[3], process.argv[4] ? Number(process.argv[4]) : null];
const OFFICIAL = 'https://api.avax-test.network/ext/bc/C/rpc', TRUTH = 'https://avalanche-fuji-c-chain-rpc.publicnode.com';
const USDC = '0x5425890298aed601595a70AB815c96711a31Bc65', T = '0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef';
const CHUNK = 100, SPAN = 3000, ENV = 'AVALANCHE_FUJI', CHAIN = '0xa869';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
import { rpc, anomalies } from './rpc_util.mjs';
const hex = (n) => '0x' + n.toString(16);
if (!existsSync(STATE)) {
  const cid = (await rpc(OFFICIAL, 'eth_chainId', [])).result; if (cid !== CHAIN) throw new Error('REFUSED: chain id ' + cid);
  const fin = parseInt((await rpc(OFFICIAL, 'eth_getBlockByNumber', ['finalized', false])).result.number, 16);
  writeFileSync(STATE, JSON.stringify({ environment: ENV, chainId: CHAIN, contract: USDC, start: fin - SPAN, end: fin, cursor: fin - SPAN - 1, events: {}, chunks: 0, duplicatesSuppressed: 0, restarts: 0 }));
}
const st = JSON.parse(readFileSync(STATE, 'utf8'));
if (st.environment !== ENV || st.chainId !== CHAIN) throw new Error('REFUSED: state belongs to another environment');   // record isolation
if (st.chunks > 0 && !st.finished) st.restarts++;
let processedThisRun = 0;
async function processChunk(from, to) {
  const r = await rpc(OFFICIAL, 'eth_getLogs', [{ fromBlock: hex(from), toBlock: hex(to), address: USDC, topics: [T] }]);
  if (!r.result) throw new Error('getLogs error ' + JSON.stringify(r.error));
  // duplicate injection: the first NON-EMPTY chunk is delivered twice (simulates a replayed/duplicated batch)
  const inject = !st.dupInjected && r.result.length > 0; if (inject) st.dupInjected = true;
  for (const rep of inject ? [1, 2] : [1]) for (const l of r.result) {
    const key = `${l.transactionHash}:${parseInt(l.logIndex, 16)}`;
    if (st.events[key]) { st.duplicatesSuppressed++; continue; }
    st.events[key] = { block: parseInt(l.blockNumber, 16) };
  }
}
while (st.cursor < st.end) {
  const from = st.cursor + 1, to = Math.min(from + CHUNK - 1, st.end);
  await processChunk(from, to);
  st.cursor = to; st.chunks++; processedThisRun++;
  writeFileSync(STATE, JSON.stringify(st));                         // cursor advances only after events are recorded (same write)
  if (CRASH && processedThisRun >= CRASH && st.cursor < st.end) { console.log(`SIMULATED CRASH after ${processedThisRun} chunks at cursor ${st.cursor}`); process.exit(3); }
  await sleep(250);
}
st.finished = true; writeFileSync(STATE, JSON.stringify(st));
const truth = await rpc(TRUTH, 'eth_getLogs', [{ fromBlock: hex(st.start), toBlock: hex(st.end), address: USDC, topics: [T] }]);
const truthKeys = new Set(truth.result.map(l => `${l.transactionHash}:${parseInt(l.logIndex, 16)}`));
const mine = new Set(Object.keys(st.events));
const missing = [...truthKeys].filter(k => !mine.has(k)).length, extra = [...mine].filter(k => !truthKeys.has(k)).length;
const out = { at: new Date().toISOString(), environment: ENV, range: [st.start, st.end], chunkSize: CHUNK, chunks: st.chunks, restarts: st.restarts, duplicatesSuppressed: st.duplicatesSuppressed,
  eventsRecorded: mine.size, truthEvents: truthKeys.size, missingVsTruth: missing, extraVsTruth: extra, equal: missing === 0 && extra === 0,
  anomalies, truthProvider: 'publicnode single 3000-block call (different provider than the scanner)' };
writeFileSync(OUT, JSON.stringify(out, null, 1)); console.log(JSON.stringify(out, null, 1));
