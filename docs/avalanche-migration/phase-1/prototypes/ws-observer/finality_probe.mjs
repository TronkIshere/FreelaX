// Read-only: for transactions sent by OTHER Fuji users, measure (wall clock, single vantage point):
//  acceptance (newAcceptedTransactions) -> receipt available -> `finalized` tag >= tx block, and eth_getLogs vs receipt logs right at acceptance.
// usage: node finality_probe.mjs <seconds> <ws-url> <http-url> <out.json>   (Node >= 22)
import { writeFileSync } from 'node:fs';
const [secs, WS, HTTP, OUT] = [Number(process.argv[2] || 90), process.argv[3], process.argv[4], process.argv[5]];
let id = 1000;
const rpc = async (method, params) => { const r = await fetch(HTTP, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: ++id, method, params }) }); return (await r.json()); };
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = []; let started = 0; const MAX = 14;
const ws = new WebSocket(WS);
ws.onopen = () => ws.send(JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'eth_subscribe', params: ['newAcceptedTransactions'] }));
ws.onmessage = (m) => {
  const d = JSON.parse(m.data); const h = d.params?.result;
  if (typeof h !== 'string' || h.length !== 66 || started >= MAX) return;
  started++; track(h, Date.now());
};
async function track(hash, t0) {
  const rec = { hash, t0 };
  // EARLY: locate block via by-hash, then query logs with a fixed filter (address = tx.to) BEFORE asking for the receipt
  let tx = null; for (let i = 0; i < 20 && !(tx && tx.blockNumber); i++) { tx = (await rpc('eth_getTransactionByHash', [hash])).result; if (!(tx && tx.blockNumber)) await sleep(50); }
  rec.byHashFoundMs = tx ? Date.now() - t0 : null;
  if (tx && tx.blockNumber && tx.to) {
    const f = { fromBlock: tx.blockNumber, toBlock: tx.blockNumber, address: tx.to };
    const e = await rpc('eth_getLogs', [f]);
    rec.logsEarly = e.result ? e.result.filter(x => x.transactionHash === hash).length : ('ERR ' + e.error?.message); rec.logsEarlyMs = Date.now() - t0;
    var filter = f;
  }
  let rc = null, tries = 0;
  while (!rc && tries < 40) { const r = await rpc('eth_getTransactionReceipt', [hash]); rc = r.result; if (!rc) await sleep(100); tries++; }
  rec.receiptMs = rc ? Date.now() - t0 : null; rec.receiptTries = tries;
  if (rc) {
    rec.block = parseInt(rc.blockNumber, 16); rec.status = rc.status; rec.receiptLogs = rc.logs.length;
    rec.receiptLogsFromTo = rc.logs.filter(l => rc.to && l.address.toLowerCase() === rc.to.toLowerCase()).length;
    if (typeof filter !== 'undefined') { const l2 = await rpc('eth_getLogs', [filter]); rec.logsAfter = l2.result ? l2.result.filter(x => x.transactionHash === hash).length : ('ERR ' + l2.error?.message); }
    let fin = 0, n = 0;
    while (fin < rec.block && n < 120) { const b = await rpc('eth_getBlockByNumber', ['finalized', false]); fin = parseInt(b.result.number, 16); if (fin >= rec.block) break; await sleep(250); n++; }
    rec.finalizedMs = fin >= rec.block ? Date.now() - t0 : null;
  }
  results.push(rec);
}
await sleep(secs * 1000); ws.close(); await sleep(15000);
const ok = results.filter(r => r.receiptMs !== null);
const q = (a, p) => { const s = [...a].sort((x, y) => x - y); return s[Math.floor(p * (s.length - 1))]; };
const sum = { n: results.length, withReceipt: ok.length, receiptMs: ok.length ? { min: q(ok.map(r => r.receiptMs), 0), p50: q(ok.map(r => r.receiptMs), .5), max: q(ok.map(r => r.receiptMs), 1) } : null,
  finalizedMs: ok.filter(r => r.finalizedMs).length ? { min: q(ok.filter(r => r.finalizedMs).map(r => r.finalizedMs), 0), p50: q(ok.filter(r => r.finalizedMs).map(r => r.finalizedMs), .5), max: q(ok.filter(r => r.finalizedMs).map(r => r.finalizedMs), 1) } : null,
  reverted: ok.filter(r => r.status === '0x0').length, logsEarlyDiffersFromAfter: ok.filter(r => r.logsEarly !== undefined && r.logsEarly !== r.logsAfter).length, withEarlyLogsQuery: ok.filter(r => r.logsEarly !== undefined).length, byHashNotFoundImmediately: results.filter(r => r.byHashFoundMs === null).length };
writeFileSync(OUT, JSON.stringify({ at: new Date().toISOString(), http: HTTP, summary: sum, results }, null, 1));
console.log(JSON.stringify(sum, null, 1)); process.exit(0);
