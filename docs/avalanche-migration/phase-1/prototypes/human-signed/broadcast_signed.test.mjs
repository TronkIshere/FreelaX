// Network-free guard tests: node --test broadcast_signed.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkEndpoint, decodeSigned, loadPlan, usdcTokenFor, balanceOfCall, FUJI_USDC } from './broadcast_signed.mjs';

// Minimal RLP encoder to build syntactically valid (fake-signature) EIP-1559 transactions for the decoder.
const enc = (x) => {
  if (Array.isArray(x)) { const body = Buffer.concat(x.map(enc)); return Buffer.concat([len(body.length, 0xc0), body]); }
  const b = Buffer.isBuffer(x) ? x : big(x);
  if (b.length === 1 && b[0] < 0x80) return b;
  return Buffer.concat([len(b.length, 0x80), b]);
};
const len = (l, off) => (l < 56 ? Buffer.from([off + l]) : (() => { const h = big(BigInt(l)); return Buffer.concat([Buffer.from([off + 55 + h.length]), h]); })());
const big = (n) => { n = BigInt(n); if (n === 0n) return Buffer.alloc(0); let h = n.toString(16); if (h.length % 2) h = '0' + h; return Buffer.from(h, 'hex'); };
const tx = (chainId, nonce = 0n, signed = true) => '0x02' + enc([chainId, nonce, 1n, 2n, 21000n, Buffer.alloc(20, 1), 0n, Buffer.alloc(0), [],
  0n, signed ? Buffer.alloc(32, 7) : Buffer.alloc(0), signed ? Buffer.alloc(32, 9) : Buffer.alloc(0)]).toString('hex');

test('Mainnet endpoints are refused before any request in both environments', () => {
  for (const env of ['LOCAL_SIMULATION', 'AVALANCHE_FUJI']) {
    assert.throws(() => checkEndpoint(env, 'https://api.avax.network/ext/bc/C/rpc'), /REJECTED_MAINNET_ENDPOINT/);
    assert.throws(() => checkEndpoint(env, 'https://example.org/mainnet'), /REJECTED_MAINNET_ENDPOINT/);
  }
});

test('Fuji accepts only the two allow-listed https hosts (O-8 deferred) and never a local endpoint', () => {
  assert.equal(checkEndpoint('AVALANCHE_FUJI', 'https://api.avax-test.network/ext/bc/C/rpc'), 'api.avax-test.network');
  assert.equal(checkEndpoint('AVALANCHE_FUJI', 'https://avalanche-fuji-c-chain-rpc.publicnode.com'), 'avalanche-fuji-c-chain-rpc.publicnode.com');
  assert.throws(() => checkEndpoint('AVALANCHE_FUJI', 'https://api.avax-test.network.evil.example/rpc'), /FUJI_HOST_NOT_ALLOW_LISTED/);
  assert.throws(() => checkEndpoint('AVALANCHE_FUJI', 'http://api.avax-test.network/ext/bc/C/rpc'), /FUJI_REQUIRES_HTTPS/);
  assert.throws(() => checkEndpoint('AVALANCHE_FUJI', 'http://localhost:8545'), /FUJI_FORBIDS_LOCAL_ENDPOINT/);
  assert.throws(() => checkEndpoint('AVALANCHE_FUJI', 'https://user:pw@api.avax-test.network'), /ENDPOINT_CREDENTIALS_FORBIDDEN/);
});

test('LOCAL_SIMULATION accepts only local-class hosts', () => {
  assert.equal(checkEndpoint('LOCAL_SIMULATION', 'http://localhost:8545'), 'localhost');
  assert.equal(checkEndpoint('LOCAL_SIMULATION', 'http://anvil:8545'), 'anvil');
  assert.throws(() => checkEndpoint('LOCAL_SIMULATION', 'https://api.avax-test.network/ext/bc/C/rpc'), /LOCAL_REQUIRES_LOCAL_ENDPOINT/);
  assert.throws(() => checkEndpoint('MAINNET', 'http://localhost:8545'), /ENVIRONMENT_INVALID/);
});

test('signed transactions are decoded and checked against the environment chain allow-list', () => {
  const d = decodeSigned(tx(43113n, 5n));
  assert.equal(d.chainId, 43113n); assert.equal(d.nonce, 5n); assert.equal(d.signed, true);
  assert.throws(() => loadPlan('AVALANCHE_FUJI', JSON.stringify({ id: 'a', raw: tx(43114n) })), /REJECTED_MAINNET_CHAIN_ID/);
  assert.throws(() => loadPlan('AVALANCHE_FUJI', JSON.stringify({ id: 'a', raw: tx(31337n) })), /CHAIN_ID_NOT_ALLOWED/);
  assert.equal(loadPlan('LOCAL_SIMULATION', JSON.stringify({ id: 'a', raw: tx(31337n) })).length, 1);
  assert.throws(() => loadPlan('LOCAL_SIMULATION', JSON.stringify({ id: 'a', raw: tx(31337n, 0n, false) })), /not signed/);
  assert.throws(() => decodeSigned('0xf86b'), /only signed EIP-1559/);
});

test('a plan can never carry key material', () => {
  for (const field of ['privateKey', 'mnemonic', 'seed', 'secret', 'password', 'key']) {
    assert.throws(() => loadPlan('LOCAL_SIMULATION', JSON.stringify({ id: 'a', raw: tx(31337n), [field]: 'x' })), /PLAN_CONTAINS_SECRET_FIELD/);
  }
  assert.throws(() => loadPlan('LOCAL_SIMULATION', ''), /PLAN_EMPTY/);
});

test('#25: Fuji always reads the pinned Circle test USDC; LOCAL only with an explicit token', () => {
  assert.equal(usdcTokenFor('AVALANCHE_FUJI'), FUJI_USDC);
  assert.equal(usdcTokenFor('AVALANCHE_FUJI', FUJI_USDC.toUpperCase().replace('0X', '0x')), FUJI_USDC);
  assert.throws(() => usdcTokenFor('AVALANCHE_FUJI', '0x' + '11'.repeat(20)), /USDC_TOKEN_NOT_ALLOWED/);
  assert.equal(usdcTokenFor('LOCAL_SIMULATION'), null);
  assert.equal(usdcTokenFor('LOCAL_SIMULATION', '0x' + 'AB'.repeat(20)), '0x' + 'ab'.repeat(20));
  assert.throws(() => usdcTokenFor('LOCAL_SIMULATION', '0x1234'), /USDC_TOKEN_INVALID/);
  assert.equal(balanceOfCall('0x' + 'Cd'.repeat(20)), '0x70a08231' + '0'.repeat(24) + 'cd'.repeat(20));
});
