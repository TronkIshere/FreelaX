// Network-free tests for check_evidence.mjs: node --test check_evidence.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { checkEvidence } from './check_evidence.mjs';

const LOCAL_FILE = 'LOCAL_SIMULATION-human-signed-chain31337-20261103T150927.json';
const committed = JSON.parse(readFileSync(new URL('../../evidence/live/' + LOCAL_FILE, import.meta.url), 'utf8'));
const HASH = '0x' + 'ab'.repeat(32);
const base = () => ({ tool: 'human-signed/broadcast_signed.mjs', environment: 'AVALANCHE_FUJI', chainId: 43113, clientFamily: 'avalanchego',
  results: [{ id: 'E1-deploy', hash: HASH, obs: { blockHash: HASH } },
    { id: 'E2-success+E4-duplicate', hash: HASH, obs: { logsBeforeReceipt: { polls: 3, nonEmptyPolls: 0 }, logsAfterReceipt: 1 } }],
  usdcBalanceOf: { token: '0x5425890298aed601595a70ab815c96711a31bc65', holder: '0x' + '12'.repeat(20), callOk: true, balance: '0', decimals: 6 },
  finishedAt: '2026-11-04T00:00:00Z' });

test('the committed LOCAL_SIMULATION evidence passes', () => {
  const r = checkEvidence(committed, LOCAL_FILE);
  assert.deepEqual(r.problems, []);
});

test('a clean Fuji file passes; hashes are allowed under *hash fields', () => {
  assert.equal(checkEvidence(base(), 'AVALANCHE_FUJI-human-signed-chain43113-20261104T120000Z.json').ok, true);
});

test('32-byte hex outside a hash field is rejected as possible key material', () => {
  const e = base(); e.results[0].note = HASH;
  assert.match(checkEvidence(e).problems.join(), /possible private key/);
});

test('forbidden field names, PEM blocks and mnemonics are rejected', () => {
  const e = base(); e.privateKey = 'x'; e.results[0].memo = '-----BEGIN EC PRIVATE KEY-----';
  e.results[0].words = 'abandon ability able about above absent absorb abstract absurd abuse access accident';
  const p = checkEvidence(e).problems.join('\n');
  assert.match(p, /forbidden field name \$\.privateKey/);
  assert.match(p, /PEM/);
  assert.match(p, /mnemonic/);
});

test('Mainnet or mismatched labels are rejected', () => {
  const mainnet = { ...base(), chainId: 43114 };
  assert.match(checkEvidence(mainnet).problems.join(), /chain id 43114 not allowed/);
  const wrongFamily = { ...base(), clientFamily: 'anvil' };
  assert.match(checkEvidence(wrongFamily).problems.join(), /client family anvil/);
  assert.match(checkEvidence(base(), 'LOCAL_SIMULATION-human-signed-chain43113-20261104T120000Z.json').problems.join(), /file name environment/);
  assert.match(checkEvidence({ ...base(), environment: 'MAINNET' }).problems.join(), /not LOCAL_SIMULATION or AVALANCHE_FUJI/);
});

test('an unfinished or empty run is rejected', () => {
  assert.match(checkEvidence({ ...base(), results: [], finishedAt: undefined }).problems.join(), /no results.*did not finish/s);
});

test('a Fuji run must carry the #25 balanceOf read and the #16 eth_getLogs probe', () => {
  const e = base(); delete e.usdcBalanceOf; e.results.pop();
  const p = checkEvidence(e).problems.join('\n');
  assert.match(p, /balanceOf read \(#25\)/);
  assert.match(p, /eth_getLogs probe \(#16\)/);
});
