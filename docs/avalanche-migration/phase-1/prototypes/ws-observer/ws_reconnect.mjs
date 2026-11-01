// Part B task D: WebSocket reconnect, missed-event and duplicate behaviour. Fuji only, read-only. Node >= 22.
// Phases: A) subscribe newHeads for `a` s; B) client closes, stays offline `gap` s; C) reconnect and subscribe again for `c` s.
// Checks: does a new subscription replay missed heads (expected: no)? does a SECOND overlapping subscription duplicate events (expected: yes)?
// Recovery: missed heads are recovered by bounded eth_getBlockByNumber/eth_getLogs reads over [lastSeen+1, firstAfter-1] on the HTTP RPC.
// usage: node ws_reconnect.mjs <a> <gap> <c> <out.json>
import { writeFileSync } from 'node:fs';
const [A, GAP, C, OUT] = [Number(process.argv[2] || 20), Number(process.argv[3] || 15), Number(process.argv[4] || 20), process.argv[5]];
const WS = 'wss://api.avax-test.network/ext/bc/C/ws', HTTP = 'https://api.avax-test.network/ext/bc/C/rpc';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
import { rpc, anomalies } from './rpc_util.mjs';
const http = async (m, p) => (await rpc(HTTP, m, p)).result;
function session(label, subs, seconds) {
  return new Promise((resolve) => {
    const events = []; const t0 = Date.now(); const ws = new WebSocket(WS); const info = { label, openedMs: null, closeCode: null };
    ws.onopen = () => { info.openedMs = Date.now() - t0; for (let i = 0; i < subs; i++) ws.send(JSON.stringify({ jsonrpc: '2.0', id: i + 1, method: 'eth_subscribe', params: ['newHeads'] })); };
    ws.onmessage = (m) => { const d = JSON.parse(m.data); const r = d.params?.result; if (r?.number) events.push({ sub: d.params.subscription, n: parseInt(r.number, 16), hash: r.hash, t: Date.now() - t0 }); };
    ws.onclose = (e) => { info.closeCode = e.code; };
    setTimeout(() => { ws.close(); setTimeout(() => resolve({ info, events }), 500); }, seconds * 1000);
  });
}
const s1 = await session('A-initial', 1, A);
const lastSeen = Math.max(...s1.events.map(e => e.n));
await sleep(GAP * 1000);                                           // client offline
const s2 = await session('C-after-reconnect-2-overlapping-subscriptions', 2, C);
const firstAfter = Math.min(...s2.events.map(e => e.n));
// duplicates within the overlapping-subscription session
const byKey = {}; for (const e of s2.events) { byKey[e.hash] = (byKey[e.hash] || 0) + 1; }
const dupHashes = Object.values(byKey).filter(c => c > 1).length;
// recovery of the gap by bounded reads
const missedFrom = lastSeen + 1, missedTo = firstAfter - 1; const recovered = [];
for (let n = missedFrom; n <= missedTo; n++) { const b = await http('eth_getBlockByNumber', ['0x' + n.toString(16), false]); recovered.push(b ? parseInt(b.number, 16) : null); await sleep(150); }
const out = {
  at: new Date().toISOString(), endpoint: 'official Fuji WS/HTTP', config: { A, GAP, C },
  sessionA: { ...s1.info, heads: s1.events.length, first: Math.min(...s1.events.map(e => e.n)), lastSeen, gapsInsideSession: s1.events.map(e => e.n).slice(1).filter((n, i) => n - s1.events[i].n !== 1).length },
  sessionC: { ...s2.info, events: s2.events.length, distinctHeads: Object.keys(byKey).length, firstAfter, headsDeliveredTwice: dupHashes },
  offlineGapBlocks: Math.max(0, missedTo - missedFrom + 1), replayedMissedHeadsOnResubscribe: s2.events.filter(e => e.n <= lastSeen).length,
  recoveredByBoundedHttpReads: recovered.filter(Boolean).length, anomalies,
};
writeFileSync(OUT, JSON.stringify(out, null, 1)); console.log(JSON.stringify(out, null, 1));
