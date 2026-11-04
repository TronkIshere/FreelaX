// Phase 1 E1: pre-commit check of an evidence file written by broadcast_signed.mjs (Node >= 22, no dependencies).
// Automates step 4 of the README procedure ("review evidence.json: no key material may appear") and the labelling rules of
// SIMULATION_SCOPE (environment and chain id on every record, Fuji never mixed with local results, no Mainnet).
// It reads one local file and makes no network request.
// Usage: node check_evidence.mjs <evidence.json> [published-file-name]
import { readFileSync } from 'node:fs';
import { basename } from 'node:path';

const FORBIDDEN_FIELDS = /key|mnemonic|seed|secret|password|keystore/i;
const HEX32 = /^0x[0-9a-fA-F]{64}$/;
const HASH_FIELD = /hash$/i; // tx hashes, block hashes: 32-byte hex is expected only under these names
const PEM = /-----BEGIN [A-Z ]*PRIVATE KEY-----/;
const MNEMONIC = /^([a-z]{3,8} ){11,23}[a-z]{3,8}$/; // 12 to 24 lowercase words
const ALLOWED = { LOCAL_SIMULATION: { chains: [31337, 43113], families: ['anvil'] }, AVALANCHE_FUJI: { chains: [43113], families: ['avalanchego'] } };
const NAME = /^(LOCAL_SIMULATION|AVALANCHE_FUJI)-human-signed-chain(\d+)-\d{8}T\d{6}Z?\.json$/;

export function checkEvidence(evidence, fileName) {
  const problems = [];
  const walk = (value, path) => {
    if (Array.isArray(value)) { value.forEach((v, i) => walk(v, `${path}[${i}]`)); return; }
    if (value && typeof value === 'object') {
      for (const [k, v] of Object.entries(value)) {
        if (FORBIDDEN_FIELDS.test(k)) problems.push(`forbidden field name ${path}.${k}`);
        walk(v, `${path}.${k}`);
      }
      return;
    }
    if (typeof value !== 'string') return;
    const field = path.slice(path.lastIndexOf('.') + 1).replace(/\[\d+\]$/, '');
    if (HEX32.test(value) && !HASH_FIELD.test(field)) problems.push(`32-byte hex value outside a *hash field at ${path} (possible private key)`);
    if (PEM.test(value)) problems.push(`PEM private key block at ${path}`);
    if (MNEMONIC.test(value.trim())) problems.push(`mnemonic-like word list at ${path}`);
  };
  walk(evidence, '$');

  if (evidence.tool !== 'human-signed/broadcast_signed.mjs') problems.push('not produced by human-signed/broadcast_signed.mjs');
  const rule = ALLOWED[evidence.environment];
  if (!rule) problems.push(`environment ${evidence.environment} is not LOCAL_SIMULATION or AVALANCHE_FUJI`);
  else {
    if (!rule.chains.includes(evidence.chainId)) problems.push(`chain id ${evidence.chainId} not allowed for ${evidence.environment}`);
    if (!rule.families.includes(evidence.clientFamily)) problems.push(`client family ${evidence.clientFamily} not expected for ${evidence.environment}`);
  }
  if (!Array.isArray(evidence.results) || evidence.results.length === 0) problems.push('no results recorded');
  else evidence.results.forEach((r, i) => { if (!r || typeof r.id !== 'string') problems.push(`results[${i}] has no entry id`); });
  if (!evidence.finishedAt) problems.push('run did not finish (no finishedAt)');

  if (fileName !== undefined) {
    const m = NAME.exec(fileName);
    if (!m) problems.push(`file name ${fileName} does not follow <ENV>-human-signed-chain<id>-<utc>.json`);
    else {
      if (m[1] !== evidence.environment) problems.push(`file name environment ${m[1]} differs from recorded ${evidence.environment}`);
      if (Number(m[2]) !== evidence.chainId) problems.push(`file name chain ${m[2]} differs from recorded ${evidence.chainId}`);
    }
  }
  return { ok: problems.length === 0, problems, stopped: evidence.stopped ?? null, anomalies: evidence.anomalies ?? [] };
}

if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('check_evidence.mjs')) {
  const [file, publishedName] = process.argv.slice(2);
  if (!file) { console.error('usage: node check_evidence.mjs <evidence.json> [published-file-name]'); process.exit(2); }
  const result = checkEvidence(JSON.parse(readFileSync(file, 'utf8')), publishedName ?? basename(file));
  if (result.stopped) console.log(`note: run stopped: ${result.stopped}`);
  if (result.anomalies.length) console.log(`note: ${result.anomalies.length} anomalies recorded (review them in the report)`);
  if (!result.ok) { for (const p of result.problems) console.error(`FAIL ${p}`); process.exit(1); }
  console.log(`OK ${file}: no key material, labels consistent`);
}
