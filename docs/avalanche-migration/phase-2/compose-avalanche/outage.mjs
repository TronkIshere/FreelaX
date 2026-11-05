// Outage and recovery validation on the compose-built Avalanche stack (LOCAL_SIMULATION only). Run through run.sh:
//   bash docs/avalanche-migration/phase-2/compose-avalanche/run.sh <output-dir> drive.mjs outage.mjs
// Each scenario injects a fault with the Docker CLI (stop, pause, restart), watches what the marketplace API reports, removes the fault, and checks
// that the system converges to the state the chain and the records agree on: nothing is applied early, nothing is applied twice, nothing is lost.
// The assertions are about SAFETY and CONVERGENCE; how the API reports an outage (status code, wording) is recorded as observed, not asserted.
import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';

const { MARKET, GATEWAY, GATEWAY_KEY, ANVIL, OUT, STACK_PROJECT, MYSQL_CONTAINER } = process.env;
if (!MARKET || !GATEWAY || !GATEWAY_KEY || !ANVIL || !OUT || !STACK_PROJECT || !MYSQL_CONTAINER) throw new Error('run through run.sh');
for (const url of [MARKET, GATEWAY]) { const h = new URL(url).hostname; if (h !== '127.0.0.1' && h !== 'localhost') throw new Error('local stack only'); }
const only = (process.env.OUTAGE_SCENARIOS || '').split(',').map(x => x.trim()).filter(Boolean);
const run = id => only.length === 0 || only.includes(id);
const GATEWAY_C = `${STACK_PROJECT}-avalanche-integration-1`;
const MARKET_C = `${STACK_PROJECT}-marketplace-backend-1`;

const evidence = { label: 'LOCAL_SIMULATION', kind: 'outage and recovery on the compose-built Avalanche stack (not Fuji)', tool: 'phase-2/compose-avalanche/outage.mjs',
  startedAt: new Date().toISOString(), steps: [] };
const record = (step, data = {}) => { evidence.steps.push({ step, at: new Date().toISOString(), ...data }); console.log('[outage]', step, JSON.stringify(data).slice(0, 320)); };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const KNOWN_KEYS = new Set();
const redact = t => String(t).replace(/0x[0-9a-fA-F]{64}(?![0-9a-fA-F])/g, m => (KNOWN_KEYS.has(m.toLowerCase()) ? '<redacted-key>' : m)).replace(/(--private-key\s+)\S+/g, '$1<redacted-key>');
const expect = (cond, message) => { if (!cond) throw new Error(message); };

function docker(args) {
  try { return execFileSync('docker', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim(); }
  catch (e) { throw new Error(redact(`docker ${args.join(' ').slice(0, 120)} failed: ${(e.stderr || e.message).toString().slice(0, 300)}`)); }
}
const rpcUrl = ['--rpc-url', 'http://localhost:8545'];
const cast = args => docker(['exec', ANVIL, 'cast', ...args]);
function newWallet() {
  const parsed = JSON.parse(cast(['wallet', 'new', '--json']));
  const w = Array.isArray(parsed) ? parsed[0] : parsed.data[0];
  KNOWN_KEYS.add(w.private_key.toLowerCase());
  cast(['rpc', 'anvil_setBalance', w.address, '0x56BC75E2D63100000', ...rpcUrl]);
  return { address: w.address.toLowerCase(), key: w.private_key };
}
async function call(base, path, { method = 'GET', body, token, internal } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  if (internal) headers['X-Internal-Api-Key'] = GATEWAY_KEY;
  const res = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(25000) });
  const text = await res.text();
  let json; try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text.slice(0, 200) }; }
  if (!res.ok) { const e = new Error(`${method} ${path} -> ${res.status}`); e.status = res.status; throw e; }
  return json && Object.prototype.hasOwnProperty.call(json, 'data') ? json.data : json;
}
const market = (path, opts) => call(MARKET, '/api/v1' + path, opts);
const attempt = async work => { try { return { ok: true, value: await work() }; } catch (e) { return { ok: false, error: String(e.message).slice(0, 120), status: e.status ?? null }; } };
async function until(label, work, ms = 120000, every = 2000) {
  const end = Date.now() + ms; let last = null;
  while (Date.now() < end) { const r = await attempt(work); if (r.ok && r.value) return r.value; last = r; await sleep(every); }
  throw new Error(`timeout waiting for ${label}: ${JSON.stringify(last)}`);
}

async function user(kind) {
  const email = `o-${kind.toLowerCase()}-${randomUUID().slice(0, 8)}@example.test`; const password = 'O-' + randomUUID();
  const d = kind === 'FREELANCER' ? { taxCode: '0000000000', identityNumber: '000000000000', nationality: 'SIMULATED', taxAddress: 'outage simulation (not a real address)',
    bankCode: 'VIETCOMBANK', bankAccountNumber: '0000000000', bankAccountHolderName: 'OUTAGE SIMULATION' } : {};
  await market('/auth/register', { method: 'POST', body: { email, password, displayName: `Outage ${kind}`, userType: kind, ...d } });
  const s = await market('/auth/sign-in', { method: 'POST', body: { email, password } });
  const me = await market('/auth/me', { token: s.accessToken }).catch(() => null);
  return { email, token: s.accessToken, id: me?.id ?? s.id ?? s.userId };
}
async function link(party, wallet) {
  const ch = await market('/avalanche/wallet-link/challenge', { method: 'POST', token: party.token, body: { walletAddress: wallet.address } });
  const signature = cast(['wallet', 'sign', '--private-key', wallet.key, ch.message]);
  await market('/avalanche/wallet-link/verify', { method: 'POST', token: party.token, body: { challengeId: ch.challengeId, signature } });
}
async function newContract(client, freelancer, title) {
  const job = await market('/marketplace/jobs', { method: 'POST', token: client.token, body: { title, description: 'Outage validation (local simulation, no real funds).', category: 'BACKEND_API',
    skills: [], budgetUsd: 5, deliveryDueAt: new Date(Date.now() + 7 * 86400_000).toISOString(), reviewWindowHours: 72, maxRevisions: 2,
    deliverables: [{ title: 'Source code', description: 'Repository' }], acceptanceCriteria: [{ description: 'Tests pass' }] } });
  await market(`/marketplace/jobs/${job.id}/apply`, { method: 'POST', token: freelancer.token, body: {} });
  const a = await market(`/marketplace/jobs/${job.id}/assignments`, { method: 'POST', token: client.token, body: { freelancerId: freelancer.id } });
  await market(`/contracts/${a.contract.id}/avalanche/enable`, { method: 'POST', token: client.token });
  return { jobId: job.id, contractId: a.contract.id };
}
const prepareFund = (client, c) => { const input = { idempotencyKey: randomUUID() }; return market(`/contracts/${c.contractId}/avalanche/actions/fund/prepare`, { method: 'POST', token: client.token, body: input }).then(p => ({ input, txs: p.transactions })); };
function send(wallet, tx, extra = []) {
  const out = cast(['send', ...extra, '--private-key', wallet.key, ...rpcUrl, tx.to, tx.data]);
  return extra.includes('--json') ? JSON.parse(out).transactionHash : out.match(/0x[0-9a-fA-F]{64}/)[0];
}
const recordTx = (client, c, p, hash) => market(`/contracts/${c.contractId}/avalanche/actions/fund/transactions`, { method: 'POST', token: client.token, body: { txHash: hash, input: p.input } });
const refresh = (client, c) => market(`/contracts/${c.contractId}/avalanche/refresh`, { method: 'POST', token: client.token });
const viewOf = (client, c) => market(`/contracts/${c.contractId}/avalanche`, { token: client.token });
const jobOf = (client, c) => market(`/marketplace/jobs/${c.jobId}`, { token: client.token });
const contractStatus = async (client, c) => { const j = await jobOf(client, c); return `${j.contract?.status}/${j.contract?.milestoneStatus}`; };
const fundedRows = v => v.transactions.filter(t => t.action === 'fund');
const attempt2 = work => { try { const r = JSON.parse(work()); return { status: r.status, block: r.blockNumber }; } catch (e) { return String(e.message).slice(0, 80); } };
const restoreMining = () => {
  cast(['rpc', 'anvil_setAutomine', 'true', ...rpcUrl]);
  cast(['rpc', 'evm_setIntervalMining', '1', ...rpcUrl]);   // last: interval mining is what keeps blocks coming so that LOCAL finality is reached
};
const blocksAdvance = async () => { const a = Number(cast(['block-number', ...rpcUrl])); await sleep(3500); return Number(cast(['block-number', ...rpcUrl])) > a; };
// F3 (recorded limitation): the gateway's LOCAL development signers are ephemeral, so after a gateway restart the escrow roles must be handed over again to the new
// addresses, exactly as run.sh does at start. Until then every authorization the restarted gateway signs is refused by the escrow (InvalidAuthorization), nothing is applied.
const ANVIL_SENDER = '0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266';
async function handOverRoles() {
  const logs = docker(['logs', '--tail', '400', GATEWAY_C]);
  const found = [...logs.matchAll(/never persisted\): \{([^}]*)\}/g)].at(-1)?.[1] ?? '';
  const arbiter = /ARBITER=(0x[0-9a-fA-F]{40})/.exec(found)?.[1]; const auth = /AUTHORIZATION=(0x[0-9a-fA-F]{40})/.exec(found)?.[1];
  expect(arbiter && auth, 'the restarted gateway did not log its signer addresses');
  const escrow = (await call(GATEWAY, '/api/v1/avalanche/config')).escrow;
  for (const a of found.match(/0x[0-9a-fA-F]{40}/g)) cast(['rpc', 'anvil_setBalance', a, '0x56BC75E2D63100000', ...rpcUrl]);
  cast(['send', '--unlocked', '--from', ANVIL_SENDER, escrow, 'grantRole(bytes32,address)', cast(['keccak', 'AUTH_SIGNER_ROLE']), auth, ...rpcUrl]);
  cast(['send', '--unlocked', '--from', ANVIL_SENDER, escrow, 'setDefaultArbiter(address)', arbiter, ...rpcUrl]);
  return { auth, arbiter };
}
const gatewayUp = () => call(GATEWAY, '/api/v1/avalanche/config').then(c => c.chainId === 31337, () => false);

async function main() {
  const config = await call(GATEWAY, '/api/v1/avalanche/config');
  expect(config.environment === 'LOCAL_SIMULATION' && Number(config.chainId) === 31337, 'not a LOCAL_SIMULATION gateway');
  const client = await user('CLIENT'); const freelancer = await user('FREELANCER');
  const cw = newWallet(); const fw = newWallet();
  await link(client, cw); await link(freelancer, fw);
  await call(GATEWAY, '/api/v1/avalanche/assets/faucet', { method: 'POST', internal: true, body: { address: cw.address, amount: '60000000' } });
  record('setup: users, throwaway wallets linked, simulated USDC minted (LOCAL only)', { chainId: config.chainId });

  let A, B;
  // ---- O1 gateway unavailable while the user's wallet transaction is broadcast, then recovery and idempotent recording -------------
  if (run('O1')) {
  A = await newContract(client, freelancer, 'outage A');
  const pa = await prepareFund(client, A);
  docker(['stop', GATEWAY_C]);
  const viewDown = await attempt(() => viewOf(client, A));
  const hashes = pa.txs.map(tx => send(cw, tx, ['--json']));   // the chain accepts the transactions although the gateway is down
  const recordDown = await attempt(() => recordTx(client, A, pa, hashes.at(-1)));
  const stateDown = await contractStatus(client, A);
  // how the API reports the outage (refusal or an UNKNOWN row) is observed; what must hold is that nothing was applied
  expect(!(recordDown.ok && recordDown.value?.businessApplied), 'O1 the marketplace applied a transaction while the gateway was down');
  expect(stateDown === 'PENDING_FUNDING/PENDING_FUNDING' || stateDown.startsWith('PENDING_FUNDING'), 'O1 business state moved while the gateway was down: ' + stateDown);
  docker(['start', GATEWAY_C]);
  await until('gateway up', gatewayUp, 150000);
  const roles = await handOverRoles();
  const row1 = await until('record after recovery', () => recordTx(client, A, pa, hashes.at(-1)).then(r => r.id ? r : null), 90000);
  const row2 = await recordTx(client, A, pa, hashes.at(-1));
  expect(row2.id === row1.id, 'O1 recording the same hash twice made two rows');
  const appliedA = await until('fund applied after recovery', async () => { const v = await refresh(client, A); return fundedRows(v).some(t => t.businessApplied) ? v : null; }, 120000, 1500);
  expect(fundedRows(appliedA).length === 1, 'O1 duplicate fund rows: ' + fundedRows(appliedA).length);
  record('O1 gateway unavailable: read and record refused, no business effect; after the restart the same hash is recorded once and applied once', {
    readWhileDown: viewDown.ok ? 'answered' : viewDown.error, recordWhileDown: recordDown.error, contractWhileDown: stateDown,
    afterRecovery: await contractStatus(client, A), rows: fundedRows(appliedA).length, sameRowOnSecondRecord: true, rolesHandedOverToTheNewEphemeralSigners: !!roles.auth });
  }

  if (run('O2') && A) {
  // ---- O2 RPC unavailable (Anvil paused) -------------------------------------------------------------------------------------------
  docker(['pause', ANVIL]);
  const viewPaused = await attempt(() => viewOf(client, A));
  const refreshPaused = await attempt(() => refresh(client, A));
  const statePaused = await contractStatus(client, A);
  docker(['unpause', ANVIL]);
  await until('rpc back', () => attempt(() => cast(['block-number', ...rpcUrl])).then(r => r.ok), 60000, 1000);
  const afterPause = await until('status after rpc recovery', async () => { const v = await viewOf(client, A); return v.chain?.status === 'FUNDED' ? v : null; }, 90000);
  expect(statePaused.startsWith('ACTIVE/FUNDED') && (await contractStatus(client, A)).startsWith('ACTIVE/FUNDED'), 'O2 the contract state changed during the RPC outage: ' + statePaused);
  record('O2 RPC unavailable: the API degrades without changing business state; it reads FUNDED again after the RPC returns', {
    viewWhilePaused: viewPaused.ok ? `answered (chain ${viewPaused.value.chain?.status ?? 'null'})` : viewPaused.error, refreshWhilePaused: refreshPaused.ok ? 'answered' : refreshPaused.error, contractWhilePaused: statePaused, chainAfter: afterPause.chain.status });
  }

  if (run('O3') && A) {
  // ---- O3 Anvil restart (state persisted in a volume) -----------------------------------------------------------------------------
  const before = cast(['block-number', ...rpcUrl]);
  docker(['restart', '-t', '15', ANVIL]);
  await until('anvil back', () => attempt(() => cast(['block-number', ...rpcUrl])).then(r => r.ok), 90000, 1000);
  const after = cast(['block-number', ...rpcUrl]);
  const escrowAfter = await until('escrow still funded after the chain restart', async () => { const v = await viewOf(client, A); return v.chain?.status === 'FUNDED' ? v : null; }, 90000);
  const rowsAfterRestart = fundedRows(escrowAfter);
  expect(rowsAfterRestart.length === 1 && rowsAfterRestart[0].businessApplied, 'O3 the fund row changed after the chain restart');
  record('O3 Anvil restart: the chain keeps its state (volume), the escrow still reads FUNDED, nothing is re-applied', { blockBefore: before, blockAfter: after, chainStatus: escrowAfter.chain.status, rows: rowsAfterRestart.length });
  }

  if (run('O4') && A) {
  // ---- O4 backend restart keeps a held signature, and the refund completes afterwards -----------------------------------------------
  const first = await market(`/contracts/${A.contractId}/avalanche/actions/mutual-refund/prepare`, { method: 'POST', token: client.token, body: {} });
  const clientSig = cast(['wallet', 'sign', '--private-key', cw.key, '--data', first.typedData]);
  await market(`/contracts/${A.contractId}/avalanche/mutual-refund/signatures`, { method: 'POST', token: client.token,
    body: { authorizationDeadline: first.authorizationDeadline, typedDataDigest: first.typedDataDigest, signature: clientSig } });
  docker(['restart', '-t', '20', MARKET_C]);
  await until('marketplace back', async () => (await fetch(MARKET + '/api/v1/auth/sign-in', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' })).status === 400, 180000, 3000);
  const afterRestart = await until('held signature after the backend restart', async () => { const v = await viewOf(client, A); return v.mutualRefund?.clientSigned ? v : null; }, 90000);
  expect(afterRestart.mutualRefund.clientSigned && !afterRestart.mutualRefund.freelancerSigned, 'O4 the held signature was lost or duplicated');
  const second = await market(`/contracts/${A.contractId}/avalanche/actions/mutual-refund/prepare`, { method: 'POST', token: freelancer.token, body: { authorizationDeadline: afterRestart.mutualRefund.authorizationDeadline } });
  expect(second.typedDataDigest === first.typedDataDigest, 'O4 the second signer was shown other typed data after the restart');
  const fSig = cast(['wallet', 'sign', '--private-key', fw.key, '--data', second.typedData]);
  await market(`/contracts/${A.contractId}/avalanche/mutual-refund/signatures`, { method: 'POST', token: freelancer.token,
    body: { authorizationDeadline: second.authorizationDeadline, typedDataDigest: second.typedDataDigest, signature: fSig } });
  const fin = { input: { idempotencyKey: randomUUID() } };
  const refundPrep = await market(`/contracts/${A.contractId}/avalanche/actions/mutual-refund/prepare`, { method: 'POST', token: freelancer.token, body: fin.input });
  const refundHash = send(fw, refundPrep.transactions.at(-1), ['--json']);
  await market(`/contracts/${A.contractId}/avalanche/actions/mutual-refund/transactions`, { method: 'POST', token: freelancer.token, body: { txHash: refundHash, input: fin.input } });
  await until('refund applied', async () => { const v = await market(`/contracts/${A.contractId}/avalanche/refresh`, { method: 'POST', token: freelancer.token }); return v.transactions.some(t => t.action === 'mutual-refund' && t.businessApplied) ? v : null; }, 120000, 1500);
  const refundState = await contractStatus(client, A);
  expect(refundState === 'CANCELLED/REFUNDED', 'O4 refund state: ' + refundState);
  record('O4 backend restart: the first party signature survives, the second signature completes the pair, the refund is applied once', { stateAfterRefund: refundState });
  }

  if (run('O5')) {
  // ---- O5 database unavailable ---------------------------------------------------------------------------------------------------
  B = await newContract(client, freelancer, 'outage B (database)');
  const stateBefore = await contractStatus(client, B);
  docker(['stop', MYSQL_CONTAINER]);
  await sleep(4000);
  const readDown = await attempt(() => jobOf(client, B));
  const gatewayDown = await attempt(() => call(GATEWAY, '/api/v1/avalanche/escrows/0x' + '00'.repeat(32)));
  docker(['start', MYSQL_CONTAINER]);
  await until('mysql healthy', async () => docker(['inspect', '-f', '{{.State.Health.Status}}', MYSQL_CONTAINER]) === 'healthy', 150000, 3000);
  const back = await until('marketplace reads again', async () => { const s = await contractStatus(client, B); return s; }, 150000, 3000);
  expect(!readDown.ok || readDown.value?.contract?.status === 'PENDING_FUNDING', 'O5 a read answered with another state while the database was down');
  expect(back === stateBefore, `O5 state differs after the database came back: ${stateBefore} -> ${back}`);
  const gwBack = await until('gateway reads again', gatewayUp, 150000);
  record('O5 database unavailable: reads fail instead of answering wrongly; after the restart the state is unchanged and both services serve again', {
    marketplaceWhileDown: readDown.ok ? 'answered' : `${readDown.error}`, gatewayWhileDown: gatewayDown.ok ? 'answered' : gatewayDown.error, stateBefore, stateAfter: back, gatewayBack: gwBack });
  }

  if (run('O6')) {
  B ??= await newContract(client, freelancer, 'outage B (pending)');
  // ---- O6 pending transaction: nothing is applied until the chain mines and finalizes it ---------------------------------------------
  const pb = await prepareFund(client, B);
  const approveHash = send(cw, pb.txs[0], ['--json']);   // allowance, mined normally
  cast(['rpc', 'anvil_setAutomine', 'false', ...rpcUrl]); cast(['rpc', 'evm_setIntervalMining', '0', ...rpcUrl]);
  let pendingEvidence; let pendingHash = null;
  try {
    const fundHash = send(cw, pb.txs.at(-1), ['--async', '--gas-limit', '800000']);
    pendingHash = fundHash;
    const recorded = await recordTx(client, B, pb, fundHash);
    const v1 = await refresh(client, B);
    const row = fundedRows(v1).at(-1);
    expect(!row?.businessApplied && (await contractStatus(client, B)).startsWith('PENDING_FUNDING'), 'O6 a pending transaction was applied: ' + JSON.stringify({ s: row?.status, a: row?.businessApplied }));
    pendingEvidence = { recordedStatus: recorded.status, whilePending: row?.status ?? null };
    for (let i = 0; i < 20; i++) cast(['rpc', 'evm_mine', ...rpcUrl]);
  } finally {
    restoreMining();
  }
  expect(await blocksAdvance(), 'O6 the chain did not resume producing blocks after the manual mining');
  let lastRows = [];
  const doneB = await until('pending transaction applied after mining', async () => { const v = await refresh(client, B); lastRows = fundedRows(v).map(t => ({ status: t.status, applied: t.businessApplied, errorClass: t.errorClass })); return fundedRows(v).some(t => t.businessApplied) ? v : null; }, 90000, 1500)
    .catch(async e => {
      const errors = ['FundingExpired()', 'AuthorizationExpired()', 'InvalidAuthorization()', 'NotClient()', 'AlreadyExists()', 'BadTerms()', 'WrongToken()', 'EnforcedPause()', 'BadArbiter()',
        'ERC20InsufficientAllowance(address,uint256,uint256)', 'ERC20InsufficientBalance(address,uint256,uint256)'];
      let revert = null;
      try {
        const trace = JSON.parse(cast(['rpc', 'debug_traceTransaction', pendingHash, '{"tracer":"callTracer"}', ...rpcUrl]));
        const out = String(trace.output ?? '').slice(0, 10);
        revert = { outputSelector: out, error: trace.error ?? null, name: errors.find(n => cast(['sig', n]) === out) ?? null };
      } catch (te) { revert = String(te.message).slice(0, 120); }
      const latest = JSON.parse(cast(['block', 'latest', '--json', ...rpcUrl]));
      const diag = { rows: lastRows, revert, chainBlockTimeMinusWallS: Number(latest.timestamp) - Math.floor(Date.now() / 1000), chainBlock: cast(['block-number', ...rpcUrl]), contract: await contractStatus(client, B), chain: (await viewOf(client, B)).chain?.status ?? null };
      throw new Error(e.message + ' | diagnostics ' + JSON.stringify(diag));
    });
  expect(fundedRows(doneB).length === 1 && (await contractStatus(client, B)).startsWith('ACTIVE/FUNDED'), 'O6 state after mining');
  record('O6 pending transaction: recorded but not applied while unmined; applied once after it is mined and finalized', { ...pendingEvidence, afterMining: fundedRows(doneB).at(-1).status, approve: approveHash.slice(0, 10) });
  }

  if (run('O7')) {
  // ---- O7 a transaction that is mined and reverts ------------------------------------------------------------------------------------
  const C = await newContract(client, freelancer, 'outage C (reverted)');
  const pc = await prepareFund(client, C);
  const stranger = newWallet();   // not the client: the escrow refuses the call, the transaction is mined with status 0 (explicit gas limit skips the estimate)
  const revertHash = send(stranger, pc.txs.at(-1), ['--async', '--gas-limit', '600000']);
  await sleep(4000);
  const recRevert = await attempt(() => recordTx(client, C, pc, revertHash));
  const afterRevert = await attempt(() => refresh(client, C));
  const stateRevert = await contractStatus(client, C);
  const rowsRevert = afterRevert.ok ? fundedRows(afterRevert.value) : [];
  expect(!rowsRevert.some(t => t.businessApplied) && stateRevert.startsWith('PENDING_FUNDING'), 'O7 a reverted transaction had a business effect: ' + stateRevert);
  record('O7 reverted transaction: refused or recorded as reverted, never applied', { record: recRevert.ok ? `recorded (${recRevert.value.status})` : recRevert.error, rows: rowsRevert.map(t => t.status), contract: stateRevert });
  // the same contract still funds normally afterwards (the failure did not poison it)
  const pc2 = await prepareFund(client, C);
  const hs = pc2.txs.map(tx => send(cw, tx, ['--json']));
  await recordTx(client, C, pc2, hs.at(-1));
  let retryRows = [];
  await until('retry applied', async () => { const v = await refresh(client, C); retryRows = fundedRows(v).map(t => ({ status: t.status, applied: t.businessApplied, errorClass: t.errorClass, tx: (t.txHash || '').slice(0, 10) })); return fundedRows(v).some(t => t.businessApplied) ? v : null; }, 60000, 1500)
    .catch(async e => {
      const gw = await attempt(() => call(GATEWAY, `/api/v1/avalanche/transactions/by-hash/${hs.at(-1)}`));
      const diag = { rows: retryRows, retryHash: hs.at(-1).slice(0, 10), gatewayByHash: gw.ok ? { status: gw.value.status, errorClass: gw.value.errorClass ?? null } : gw.error,
        receipt: attempt2(() => cast(['receipt', hs.at(-1), '--json', ...rpcUrl])), chain: (await viewOf(client, C)).chain?.status ?? null, contract: await contractStatus(client, C) };
      throw new Error(e.message + ' | diagnostics ' + JSON.stringify(diag).slice(0, 900));
    });
  record('O7b a normal funding retry on the same contract succeeds afterwards', { contract: await contractStatus(client, C) });
  }

  evidence.result = 'PASS';
}

main().catch(e => { evidence.result = 'FAIL'; evidence.error = redact(String(e.message ?? e)).slice(0, 700); process.exitCode = 1; })
  .finally(() => {
    try { for (const c of [GATEWAY_C, MARKET_C, MYSQL_CONTAINER]) docker(['start', c]); docker(['unpause', ANVIL]); } catch { /* already running */ }
    try { restoreMining(); } catch { /* chain not reachable */ }
    evidence.finishedAt = new Date().toISOString();
    const text = JSON.stringify(evidence, null, 2);
    if (/private_key|"key"\s*:|--private-key\s+0x/i.test(text) || [...KNOWN_KEYS].some(k => text.toLowerCase().includes(k))) throw new Error('refusing to write evidence that mentions key material');
    writeFileSync(OUT, text);
    console.log('[outage] result', evidence.result, redact(evidence.error ?? ''));
  });
