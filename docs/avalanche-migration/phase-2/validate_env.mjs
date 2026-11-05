// B9, LOCAL environment configuration validation. It NEVER reads the owner's .env or any ignored secret file.
// It reads only tracked files (docker-compose.yml, .env.example) and builds a throwaway env file from the placeholders of
// .env.example plus random disposable values for the required secrets, in the operating-system temp folder, deleted at the end.
// Checks: every required compose variable is documented in .env.example; no tracked example value looks like a secret or points
// to Mainnet; the Avalanche defaults keep the rail off; `docker compose config` renders with the throwaway file; omitting any
// required variable makes it fail (the "missing variable" case); an invalid value of an Avalanche setting is refused by the
// application (covered by named unit tests, listed below, not re-run here).
// Usage (repository root, Docker running): node docs/avalanche-migration/phase-2/validate_env.mjs
import { readFileSync, writeFileSync, rmSync, mkdtempSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const results = [];
const check = (name, ok, detail = '') => { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'} | ${name}${detail ? ' | ' + detail : ''}`); };

const compose = readFileSync('docker-compose.yml', 'utf8');
const example = readFileSync('.env.example', 'utf8');
const exampleVars = new Map();
for (const line of example.split(/\r?\n/)) {
  const m = /^([A-Z][A-Z0-9_]*)=(.*)$/.exec(line);
  if (m) exampleVars.set(m[1], m[2]);
}

// ---- compose variable references: required (`:?`), defaulted (`:-`) or bare
const required = new Set(), defaulted = new Set(), bare = new Set();
for (const m of compose.matchAll(/\$\{([A-Z][A-Z0-9_]*)(:\?[^}]*|\?[^}]*|:-[^}]*|-[^}]*)?\}/g)) {
  const [, name, rest] = m;
  if (rest?.startsWith(':?') || rest?.startsWith('?')) required.add(name);
  else if (rest) defaulted.add(name);
  else bare.add(name);
}
// A variable required in one place may have a default in another; required wins.
for (const r of required) { defaulted.delete(r); bare.delete(r); }
for (const d of defaulted) bare.delete(d);

const undocumentedRequired = [...required].filter(v => !exampleVars.has(v));
check('every required compose variable is documented in .env.example', undocumentedRequired.length === 0, undocumentedRequired.join(', ') || `${required.size} required variables`);
const undocumentedBare = [...bare].filter(v => !exampleVars.has(v));
check('no compose variable without a default is missing from .env.example', undocumentedBare.length === 0, undocumentedBare.join(', '));
const undocumentedDefaulted = [...defaulted].filter(v => !exampleVars.has(v));
console.log(`INFO | defaulted compose variables not listed in .env.example (optional, defaults apply): ${undocumentedDefaulted.join(', ') || 'none'}`);

// Avalanche rail of the marketplace must be reachable through compose and documented, and default to the current behaviour
const marketplaceVars = ['MARKETPLACE_PRIMARY_RAIL', 'AVALANCHE_GATEWAY_ENABLED', 'AVALANCHE_GATEWAY_BASE_URL', 'AVALANCHE_GATEWAY_NETWORK',
  'AVALANCHE_GATEWAY_SETTLEMENT_NOTIFICATIONS', 'AVALANCHE_PAYOUT_ENABLED', 'AVALANCHE_PAYOUT_PARTNER'];
check('the marketplace Avalanche settings are passed by compose and documented', marketplaceVars.every(v => compose.includes('${' + v) && exampleVars.has(v)), marketplaceVars.filter(v => !compose.includes('${' + v) || !exampleVars.has(v)).join(', '));
check('Avalanche defaults keep the rail off and Solana primary', exampleVars.get('AVALANCHE_GATEWAY_ENABLED') === 'false' && exampleVars.get('AVALANCHE_PAYOUT_ENABLED') === 'false'
  && exampleVars.get('MARKETPLACE_PRIMARY_RAIL') === 'SOLANA' && exampleVars.get('AVALANCHE_PAYOUT_PARTNER') === 'none' && exampleVars.get('AVALANCHE_SIGNERS_ENABLED') === 'false' && exampleVars.get('AVALANCHE_TX_ENABLED') === 'false');
check('AVALANCHE_ENVIRONMENT has no default (the gateway refuses to start without an explicit value)', exampleVars.get('AVALANCHE_ENVIRONMENT') === '');

// ---- tracked example values: no secret, no Mainnet
const suspicious = [];
for (const [k, v] of exampleVars) {
  if (/^(0x)?[0-9a-fA-F]{64}$/.test(v) || /BEGIN [A-Z ]*PRIVATE KEY/.test(v) || /^[1-9A-HJ-NP-Za-km-z]{80,}$/.test(v)) suspicious.push(k);
  if (/(PASSWORD|SECRET|KEY)$/.test(k) && v !== '' && !/^(false|true|dev)$/i.test(v) && !/^(change-me|placeholder|<.*>)$/i.test(v)) suspicious.push(k);
}
check('no tracked example value looks like a secret (non-empty password, secret or key values must be placeholders)', suspicious.length === 0, suspicious.join(', '));
const mainnet = [...exampleVars].filter(([k, v]) => /43114|api\.avax\.network|mainnet/i.test(v) && !/^#/.test(k));
check('no tracked example value points to Mainnet', mainnet.length === 0, mainnet.map(([k]) => k).join(', '));
check('.env is ignored by Git (checked with git check-ignore, file content not read)', (() => {
  try { execFileSync('git', ['check-ignore', '-q', '.env']); return true; } catch { return false; }
})());
check('.env is not tracked', execFileSync('git', ['ls-files', '.env'], { encoding: 'utf8' }).trim() === '');

// ---- throwaway env file: placeholders from the example, random disposable values for every required variable
const dir = mkdtempSync(join(tmpdir(), 'freelax-env-'));
const envFile = join(dir, 'throwaway.env');
const rnd = n => randomBytes(n).toString('hex');
const build = (omit = null) => {
  const values = new Map(exampleVars);
  for (const r of required) values.set(r, /EMAIL$/.test(r) ? 'throwaway@example.test' : 'throwaway-' + rnd(16));
  if (omit) values.delete(omit);
  return [...values].map(([k, v]) => `${k}=${v}`).join('\n') + '\n';
};
const render = (omit = null) => {
  writeFileSync(envFile, build(omit));
  try {
    execFileSync('docker', ['compose', '--env-file', envFile, '-f', 'docker-compose.yml', '--profile', 'avalanche', 'config', '-q'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
    return { ok: true, err: '' };
  } catch (e) { return { ok: false, err: String(e.stderr || e.message).split('\n')[0].slice(0, 160) }; }
};
try {
  const clean = render();
  check('docker compose config renders from the .env.example placeholders plus throwaway values (profile avalanche included)', clean.ok, clean.err);
  const missing = [...required].filter(r => render(r).ok);
  check('omitting any required variable makes docker compose config fail (missing-variable case)', missing.length === 0, `${required.size} variables tried${missing.length ? '; NOT refused: ' + missing.join(', ') : ''}`);
} finally { rmSync(dir, { recursive: true, force: true }); }

console.log('INFO | invalid-value cases (Mainnet endpoint or chain id 43114, endpoint that does not match the environment, malformed or credentialed endpoint, missing environment, wrong deployment manifest or contract address, signers outside LOCAL_SIMULATION) are refused by the application and covered by these unit tests, which are NOT re-run by this script: gateway EnvironmentGuardTest, StartupGuardContextTest, ManifestVerifierTest, ManifestIntegrationTest, DevSignerFactoryTest; marketplace PrimaryRailPolicyTest (a value other than SOLANA or AVALANCHE is rejected).');
const failed = results.filter(r => !r.ok).length;
console.log(`\n${results.length} checks, ${failed} failed`);
process.exitCode = failed ? 1 : 0;
