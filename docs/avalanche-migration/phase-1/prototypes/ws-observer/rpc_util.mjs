// Shared conservative JSON-RPC helper. Node >= 22.
// - paces requests (MIN_GAP_MS between calls to the same host, default 600 ms ~ 1.6 req/s)
// - checks HTTP status BEFORE parsing JSON (a throttled public endpoint answers 429 with an HTML body)
// - honours Retry-After; retries a bounded number of times; records every anomaly
export const anomalies = [];
let id = 1; const last = {};
const MIN_GAP_MS = Number(process.env.MIN_GAP_MS || 600);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
export async function rpc(url, method, params, { retries = 4 } = {}) {
  const host = new URL(url).host;
  for (let attempt = 0; attempt <= retries; attempt++) {
    const wait = (last[host] || 0) + MIN_GAP_MS - Date.now(); if (wait > 0) await sleep(wait);
    last[host] = Date.now();
    let status = null, txt = '', retryAfter = null;
    try {
      const r = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: ++id, method, params }), signal: AbortSignal.timeout(20000) });
      status = r.status; retryAfter = r.headers.get('retry-after'); txt = await r.text();
      if (status === 200) { try { return JSON.parse(txt); } catch { anomalies.push({ at: new Date().toISOString(), host, method, attempt, http: 200, nonJson: true, bodyPrefix: txt.slice(0, 60) }); } }
      else anomalies.push({ at: new Date().toISOString(), host, method, attempt, http: status, retryAfter, bodyPrefix: txt.slice(0, 40).replace(/\s+/g, ' ') });
    } catch (e) { anomalies.push({ at: new Date().toISOString(), host, method, attempt, error: String(e).slice(0, 80) }); }
    await sleep(retryAfter ? (Number(retryAfter) + 1) * 1000 : 1500 * (attempt + 1));
  }
  throw new Error('RPC failed after retries: ' + method);
}
