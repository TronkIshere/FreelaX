// Read-only timestamp study on Fuji. Node >= 22. usage: node timestamp_probe.mjs <http-url> <out.json>
// 1) header timestamp vs timestampMilliseconds for N consecutive blocks (granularity, monotonicity, shared seconds)
// 2) the EVM TIMESTAMP opcode as seen by eth_call at historical blocks, using a state-override stub (no deployment, no funds)
// 3) wall-clock vs head timestamp sampled repeatedly
import { writeFileSync } from 'node:fs';
const [URL_, OUT] = [process.argv[2], process.argv[3]];
let id = 0;
const rpc = async (method, params) => { const r = await fetch(URL_, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: ++id, method, params }) }); return r.json(); };
const hex = (n) => '0x' + n.toString(16);
const head = parseInt((await rpc('eth_blockNumber', [])).result, 16);
const N = 240, blocks = [];
for (let start = head - N + 1; start <= head; start += 40) {
  const batch = []; for (let i = start; i < Math.min(start + 40, head + 1); i++) batch.push({ jsonrpc: '2.0', id: ++id, method: 'eth_getBlockByNumber', params: [hex(i), false] });
  const r = await (await fetch(URL_, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(batch) })).json();
  if (!Array.isArray(r)) { blocks.push({ batchError: r }); break; }
  for (const x of r) if (x.result) blocks.push({ n: parseInt(x.result.number, 16), ts: parseInt(x.result.timestamp, 16), ms: x.result.timestampMilliseconds ? parseInt(x.result.timestampMilliseconds, 16) : null, txs: x.result.transactions.length, settledHeight: x.result.settledHeight, minDelayExcess: x.result.minDelayExcess });
}
blocks.sort((a, b) => a.n - b.n);
const ok = blocks.filter(b => b.ms !== null && b.ts !== undefined);
const msFloorEqualsTs = ok.every(b => Math.floor(b.ms / 1000) === b.ts);
const deltasMs = ok.slice(1).map((b, i) => b.ms - ok[i].ms);
const deltasS = blocks.slice(1).map((b, i) => b.ts - blocks[i].ts);
const sorted = [...deltasMs].sort((a, b) => a - b);
const pct = (p) => sorted[Math.floor(p * (sorted.length - 1))];
const sameSecond = deltasS.filter(d => d === 0).length;
const monotonicTs = deltasS.every(d => d >= 0), strictMonotonicMs = deltasMs.every(d => d > 0);
// 2) TIMESTAMP opcode via state override: runtime code = TIMESTAMP PUSH1 0 MSTORE PUSH1 32 PUSH1 0 RETURN
const STUB = '0x4260005260206000f3', STUBADDR = '0x00000000000000000000000000000000000000f1';
const opcode = [];
for (const b of blocks.slice(-30, -1).filter((_, i) => i % 4 === 0)) {
  const r = await rpc('eth_call', [{ to: STUBADDR }, hex(b.n), { [STUBADDR]: { code: STUB } }]);
  opcode.push({ block: b.n, headerTs: b.ts, opcodeTs: r.result ? parseInt(r.result, 16) : null, err: r.error?.message });
}
const rLatest = await rpc('eth_call', [{ to: STUBADDR }, 'latest', { [STUBADDR]: { code: STUB } }]);
const rPending = await rpc('eth_call', [{ to: STUBADDR }, 'pending', { [STUBADDR]: { code: STUB } }]);
// 3) wall clock lag
const lag = [];
for (let i = 0; i < 12; i++) { const b = (await rpc('eth_getBlockByNumber', ['latest', false])).result; const w = Date.now(); lag.push({ wallMs: w, headTs: parseInt(b.timestamp, 16), headMs: parseInt(b.timestampMilliseconds, 16), wallMinusHeadMs: w - parseInt(b.timestampMilliseconds, 16) }); await new Promise(r => setTimeout(r, 1000)); }
const out = {
  url: URL_, runAt: new Date().toISOString(), blocksSampled: blocks.length, range: [blocks[0].n, blocks.at(-1).n],
  msFloorEqualsTs, monotonicTs, strictMonotonicMs, sameSecondPairs: sameSecond, pairs: deltasS.length,
  deltaMs: { min: sorted[0], p50: pct(0.5), p90: pct(0.9), p99: pct(0.99), max: sorted.at(-1) },
  deltaSecondsHistogram: deltasS.reduce((h, d) => (h[d] = (h[d] || 0) + 1, h), {}),
  txsPerBlock: { min: Math.min(...blocks.map(b => b.txs)), max: Math.max(...blocks.map(b => b.txs)), blocksWithTx: blocks.filter(b => b.txs > 0).length },
  stubOpcodeAtHistoricalBlocks: opcode, opcodeEqualsHeaderTsAll: opcode.every(o => o.opcodeTs === o.headerTs),
  stubAtLatest: rLatest.result ? parseInt(rLatest.result, 16) : rLatest.error, stubAtPending: rPending.result ? parseInt(rPending.result, 16) : rPending.error,
  wallClockLag: lag, wallMinusHeadMs: { min: Math.min(...lag.map(l => l.wallMinusHeadMs)), max: Math.max(...lag.map(l => l.wallMinusHeadMs)) },
  sampleBlocks: blocks.slice(-5)
};
writeFileSync(OUT, JSON.stringify(out, null, 1));
console.log(JSON.stringify({ ...out, wallClockLag: undefined, sampleBlocks: undefined }, null, 1));
