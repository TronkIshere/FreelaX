// Phase 1 E1 evidence review (read-only, no network). Checks the claims of the FUJI-OBS report against the committed
// evidence file and prints one line per claim: SUPPORTED, NOT SUPPORTED or NOT CHECKABLE. It reads one local file only.
// Usage: node review_e1_evidence.mjs [path-to-evidence.json]
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const file = process.argv[2] ?? join(here, 'live', 'AVALANCHE_FUJI-human-signed-chain43113-20261105T064313Z.json');
const text = readFileSync(file, 'utf8');
const e = JSON.parse(text);
const byId = Object.fromEntries(e.results.map(r => [r.id, r]));
const mined = e.results.filter(r => r.obs?.status !== undefined);
const lines = [];
const claim = (name, ok, detail) => lines.push(`${ok === null ? 'NOT CHECKABLE' : ok ? 'SUPPORTED    ' : 'NOT SUPPORTED'} | ${name} | ${detail}`);

// integrity
claim('label and network: AVALANCHE_FUJI, chain 43113, allow-listed host', e.environment === 'AVALANCHE_FUJI' && e.chainId === 43113 && e.host === 'api.avax-test.network', `${e.environment} ${e.chainId} ${e.host}`);
claim('no key material in the file', !/private|mnemonic|keystore|password|0x[0-9a-fA-F]{64}"\s*:\s*"?0x[0-9a-fA-F]{64}\b.*key/i.test(text.replace(/"hash":"0x[0-9a-f]{64}"/g, '')), 'pattern scan of the text, transaction hashes excluded');
const hashes = e.results.filter(r => r.hash).map(r => r.hash);
claim('every transaction hash is 32 bytes and unique', hashes.every(h => /^0x[0-9a-f]{64}$/.test(h)) && new Set(hashes).size === hashes.length, `${hashes.length} hashes`);
claim('obs.hash equals the entry hash', e.results.filter(r => r.obs?.hash).every(r => r.obs.hash === r.hash), '');
claim('19 entries', e.results.length === 19, `${e.results.length}`);
claim('run window 06:43:13Z to 06:57:11Z and 0 RPC anomalies', e.startedAt.startsWith('2026-11-05T06:43:13') && e.finishedAt.startsWith('2026-11-05T06:57:11') && Array.isArray(e.anomalies) && e.anomalies.length === 0, `${e.startedAt} .. ${e.finishedAt}, anomalies ${e.anomalies.length}`);
const nonces = e.results.filter(r => r.nonce !== undefined).map(r => r.nonce);
claim('sender nonce starts at 0 and the deploy is nonce 0', byId['E1-deploy'].nonce === 0 && Math.min(...nonces) === 0, `nonces ${[...new Set(nonces)].sort((a, b) => a - b).join(',')}`);
const blockTimes = mined.map(r => r.obs.blockTimestamp);
const startS = Date.parse(e.startedAt) / 1000;
const endS = Date.parse(e.finishedAt) / 1000;
claim('every block timestamp lies inside the run window (plus 15 s of clock tolerance)', blockTimes.every(t => t >= startS - 15 && t <= endS + 15), `${Math.min(...blockTimes)}..${Math.max(...blockTimes)} vs ${Math.floor(startS)}..${Math.floor(endS)}`);
claim('effective gas price is 160 wei (0xa0) for every mined transaction', mined.every(r => r.obs.effectiveGasPrice === '0xa0'), `${mined.length} mined entries`);

// observations
const d = byId['E1-deploy'];
claim('deploy: status 1, contract address recorded, ack 621 ms, receipt 3.6 s, finalized 8.5 s', d.obs.status === '0x1' && !!d.obs.contractAddress && d.ackMs === 621 && Math.abs(d.obs.receiptMs - 3600) < 100 && Math.abs(d.obs.finalizedMs - 8500) < 100, `ack ${d.ackMs}, receipt ${d.obs.receiptMs}, finalized ${d.obs.finalizedMs}`);
const s = byId['E2-success+E4-duplicate'];
claim('success: status 1, finalized 8.5 s, included 2 s after the head seen at send', s.obs.status === '0x1' && Math.abs(s.obs.finalizedMs - 8500) < 100 && s.obs.blockTimestamp - s.headTsAtSend === 2, `finalized ${s.obs.finalizedMs}, block minus head ${s.obs.blockTimestamp - s.headTsAtSend}`);
claim('duplicate: immediate resend accepted with the same hash', typeof s.immediateResend === 'string' && s.immediateResend.includes(s.hash), s.immediateResend);
claim('duplicate: resend after mining refused with "nonce too low: next nonce 3, tx nonce 2"', JSON.stringify(s).includes('nonce too low') && /next nonce 3, tx nonce 2/.test(JSON.stringify(s)), (JSON.stringify(s).match(/nonce too low[^"]*/) ?? ['not found'])[0]);
claim('revert: accepted at submission and mined with status 0 (receipt 3.6 s)', byId['E3-revert'].obs.status === '0x0' && Math.abs(byId['E3-revert'].obs.receiptMs - 3600) < 100, `status ${byId['E3-revert'].obs.status}, receipt ${byId['E3-revert'].obs.receiptMs}`);
const gap = byId['E5-gap'], filler = byId['E5-filler'];
claim('nonce gap: nonce n+1 accepted while n missing, mined only with the filler (same block 60556606)', gap.nonce === filler.nonce + 1 && gap.finalPass.block === 60556606 && filler.obs.block === 60556606, `gap nonce ${gap.nonce}, filler nonce ${filler.nonce}, blocks ${gap.finalPass.block}/${filler.obs.block}`);
const b100 = byId['E5-bump-100'];
claim('equal-fee replacement accepted (hash returned) but never executed and not visible by hash', !!b100.hash && b100.finalPass.visibleByHash === false && b100.finalPass.receiptStatus === null && b100.nonce === gap.nonce && b100.hash !== gap.hash, `visibleByHash ${b100.finalPass.visibleByHash}`);
const skipped = e.results.filter(r => r.id.startsWith('E5-bump') && r.skipped).map(r => r.id);
claim('replacement threshold INCONCLUSIVE: 105 to 200 % not sent', skipped.length === 6, `${skipped.length} skipped: ${skipped.join(',')}`);
const e7 = e.results.filter(r => r.id.startsWith('E7-'));
const e7ok = e7.filter(r => r.statusMatchesTsLeDeadline === true);
claim('deadline rule: block.timestamp <= deadline matched in 6 of 6 samples', e7.length === 6 && e7ok.length === 6, `${e7ok.length} of ${e7.length}`);
const after = e7.filter(r => r.includedMinusDeadline > 0), before = e7.filter(r => r.includedMinusDeadline <= 0);
claim('deadline: entries included after the deadline all reverted, entries at or before it all succeeded', after.every(r => r.obs.status === '0x0') && before.every(r => r.obs.status === '0x1'), `after ${after.map(r => r.id + ':' + r.includedMinusDeadline).join(' ')}; before ${before.map(r => r.id + ':' + r.includedMinusDeadline).join(' ')}`);
const k0 = byId['E7-k0'];
claim('k0 was sent about 16 s early (not a near-deadline sample)', k0.includedMinusDeadline <= -10, `includedMinusDeadline ${k0.includedMinusDeadline}`);
claim('balanceOf: callOk true, balance 0, decimals 6, pinned Circle test USDC', e.usdcBalanceOf.callOk === true && e.usdcBalanceOf.balance === '0' && e.usdcBalanceOf.decimals === 6 && e.usdcBalanceOf.token === '0x5425890298aed601595a70ab815c96711a31bc65', JSON.stringify(e.usdcBalanceOf));
const probe = s.obs.logsBeforeReceipt;
claim('eth_getLogs before execution INCONCLUSIVE: no poll happened before the receipt', probe && probe.pollsBeforeReceipt === 0 && probe.nonEmptyPollsBeforeReceipt === 0, JSON.stringify(probe));
const lag = mined.filter(r => r.obs.byHashMs >= 2000 && r.obs.byHashMs <= 3100);
claim('by-hash lag of about 3 s on several transactions', lag.length >= 3, `${lag.length} entries with byHashMs 2.0-3.1 s`);
const fin = mined.filter(r => r.obs.finalizedMs).map(r => r.obs.finalizedMs);
claim('finalized reached 8.5 to 12.2 s after send for the three entries that waited', fin.length === 3 && Math.min(...fin) > 8400 && Math.max(...fin) < 12300, `${fin.join(', ')} ms`);
claim('finality in this run says nothing about FreelaX escrow (lab contract only)', null, 'documentation boundary, not checkable from the file');

console.log(`E1 evidence review of ${file}`);
for (const l of lines) console.log(l);
const bad = lines.filter(l => l.startsWith('NOT SUPPORTED')).length;
console.log(`\n${lines.length} claims: ${lines.filter(l => l.startsWith('SUPPORTED')).length} supported, ${bad} not supported, ${lines.filter(l => l.startsWith('NOT CHECKABLE')).length} not checkable`);
process.exitCode = bad === 0 ? 0 : 1;
