// Part B task D (AVALANCHE_FUJI only, read-only, conservative). Node >= 22.
// Per provider: eth_getLogs range ladder (address-filtered and not), historical availability with <=99-block windows,
// and a gentle rate probe (5 req/s for 30 s, abort at the first HTTP 429/5xx or JSON-RPC rate-limit error).
// usage: node provider_probe.mjs <out.json>
import { writeFileSync } from 'node:fs';
const PROVIDERS = {
  official: 'https://api.avax-test.network/ext/bc/C/rpc',
  publicnode: 'https://avalanche-fuji-c-chain-rpc.publicnode.com',
  drpc: 'https://avalanche-fuji.drpc.org',
};
const USDC = '0x5425890298aed601595a70AB815c96711a31Bc65';
const T = '0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
let id = 0;
async function rpc(url, method, params) {
  const t = performance.now();
  try {
    const r = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: ++id, method, params }), signal: AbortSignal.timeout(30000) });
    const txt = await r.text(); let j; try { j = JSON.parse(txt); } catch { j = { raw: txt.slice(0, 120) }; }
    return { http: r.status, ms: Math.round(performance.now() - t), j };
  } catch (e) { return { http: null, ms: Math.round(performance.now() - t), j: { exception: String(e).slice(0, 100) } }; }
}
const hex = (n) => '0x' + n.toString(16);
const summarize = (r) => r.j.result ? `OK logs=${r.j.result.length}` : `ERR http=${r.http} ${JSON.stringify(r.j.error ?? r.j).slice(0, 140)}`;
const out = { at: new Date().toISOString(), note: 'single host, single run, conservative rates; no generalisation to other providers', providers: {} };
for (const [name, url] of Object.entries(PROVIDERS)) {
  const p = { chainId: null };
  const cid = await rpc(url, 'eth_chainId', []); p.chainId = cid.j.result;
  if (p.chainId !== '0xa869') { p.skipped = 'chain id is not Fuji (43113): provider skipped by guard'; out.providers[name] = p; continue; }
  const head = parseInt((await rpc(url, 'eth_blockNumber', [])).j.result, 16); p.head = head;
  p.ladderAddr = {}; p.ladderNoAddr = {};
  for (const span of [99, 100, 101, 500, 1000, 2000, 5000, 10000, 50000, 100000]) {
    const f = { fromBlock: hex(head - span - 1), toBlock: hex(head - 1), topics: [T] };
    p.ladderAddr[span] = summarize(await rpc(url, 'eth_getLogs', [{ ...f, address: USDC }])); await sleep(250);
    if (span <= 1000) { p.ladderNoAddr[span] = summarize(await rpc(url, 'eth_getLogs', [f])); await sleep(250); }
  }
  p.history = {};
  for (const back of [1000, 100000, 1000000, 10000000, 30000000, 55000000]) {
    const from = head - back; if (from < 1) continue;
    p.history[back] = summarize(await rpc(url, 'eth_getLogs', [{ fromBlock: hex(from), toBlock: hex(from + 98), address: USDC, topics: [T] }])); await sleep(250);
  }
  // gentle rate probe
  const lat = []; const codes = {}; let aborted = null;
  for (let i = 0; i < 150; i++) {
    const r = await rpc(url, 'eth_blockNumber', []); lat.push(r.ms); const k = String(r.http); codes[k] = (codes[k] || 0) + 1;
    const rl = r.j.error && /rate|limit|too many|exceed/i.test(JSON.stringify(r.j.error));
    if (r.http === 429 || (r.http && r.http >= 500) || rl) { aborted = { at: i, http: r.http, err: JSON.stringify(r.j.error ?? r.j).slice(0, 120) }; break; }
    await sleep(200);
  }
  lat.sort((a, b) => a - b);
  p.rate = { requested: 150, sent: lat.length, perSecondTarget: 5, httpCodes: codes, aborted, latencyMs: { min: lat[0], p50: lat[lat.length >> 1], max: lat.at(-1) } };
  out.providers[name] = p;
  console.log(name, JSON.stringify({ ladderAddr: p.ladderAddr, history: p.history, rate: p.rate }, null, 0).slice(0, 1500));
}
writeFileSync(process.argv[2], JSON.stringify(out, null, 1));
