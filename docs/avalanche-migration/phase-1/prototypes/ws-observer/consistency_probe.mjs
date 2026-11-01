// Part B task D: provider consistency. Fuji only, read-only, conservative (<= ~5 req/s). Node >= 22.
// For up to N transactions announced by newAcceptedTransactions (official WS), ask official AND publicnode for by-hash and receipt
// at +0, +500 ms, +2000 ms and record the found/not-found matrix. A "miss" is NOT proof of absence; this measures how often misses happen.
// usage: node consistency_probe.mjs <seconds> <N> <out.json>
import { writeFileSync } from 'node:fs';
const [secs, N, OUT] = [Number(process.argv[2] || 90), Number(process.argv[3] || 25), process.argv[4]];
const P = { official: 'https://api.avax-test.network/ext/bc/C/rpc', publicnode: 'https://avalanche-fuji-c-chain-rpc.publicnode.com' };
import { rpc as paced, anomalies } from './rpc_util.mjs';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const rpc = async (url, m, p) => { try { return (await paced(url, m, p, { retries: 1 })).result ?? null; } catch (e) { return 'ERR'; } };
const rows = []; let started = 0;
const ws = new WebSocket('wss://api.avax-test.network/ext/bc/C/ws');
ws.onopen = () => ws.send(JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'eth_subscribe', params: ['newAcceptedTransactions'] }));
ws.onmessage = (m) => { const h = JSON.parse(m.data).params?.result; if (typeof h === 'string' && h.length === 66 && started < N) { started++; track(h); } };
async function track(hash) {
  const row = { hash, samples: [] }; const t0 = Date.now();
  for (const off of [0, 1500, 4000]) {
    const wait = off - (Date.now() - t0); if (wait > 0) await sleep(wait);
    const s = { offsetMs: off, actualMs: Date.now() - t0 };
    for (const [name, url] of Object.entries(P)) {
      const tx = await rpc(url, 'eth_getTransactionByHash', [hash]); const rc = await rpc(url, 'eth_getTransactionReceipt', [hash]);
      s[name] = { byHash: tx === 'ERR' ? 'err' : tx ? (tx.blockNumber ? 'found' : 'pending') : 'null', receipt: rc === 'ERR' ? 'err' : rc ? rc.status : 'null' };
    }
    row.samples.push(s);
  }
  rows.push(row);
}
await sleep(secs * 1000); ws.close(); await sleep(70000);
const tally = {};
for (const r of rows) for (const s of r.samples) for (const n of Object.keys(P)) { const k = `${n}@${s.offsetMs}ms byHash=${s[n].byHash} receipt=${s[n].receipt === '0x1' || s[n].receipt === '0x0' ? 'present' : s[n].receipt}`; tally[k] = (tally[k] || 0) + 1; }
const disagreements = rows.filter(r => r.samples.some(s => (s.official.byHash === 'found') !== (s.publicnode.byHash === 'found'))).map(r => r.hash.slice(0, 12));
const out = { at: new Date().toISOString(), pacing: 'rpc_util: >= 600 ms between calls per host', anomalies, transactions: rows.length, tally, rowsWithByHashDisagreement: disagreements, rows };
writeFileSync(OUT, JSON.stringify(out, null, 1));
console.log(JSON.stringify({ transactions: rows.length, tally, disagreements }, null, 1));
process.exit(0);
