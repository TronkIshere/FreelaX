// Register item A2: end-to-end drive of the Avalanche rail on ONE running LOCAL_SIMULATION stack started by run.sh.
// Real HTTP APIs of the marketplace and the gateway; real transactions on Anvil (chain 31337, MockUSDC, no value).
// The two party wallets are THROWAWAY keys created inside the Anvil container for this run only (LOCAL_SIMULATION; the same
// pattern as the Phase 1 human-signed LOCAL validation). They are held in this process's memory, passed to `cast` in the
// Anvil container, never written to disk, never logged and never placed in the evidence file. Fuji and Mainnet are refused.
// Flows: (1) wallet link (A8) for both parties; (2) fund -> release with the users' own wallets, FINALIZED-only business
// effects and settlement notifications; (3) a second contract funded and closed by a two-party wallet mutual refund (F13);
// (4) R12: two more contracts, disputed on chain and settled by both parties' own signatures (RELEASE, then REFUND), with the
// negative checks (no signature, one signature, non-party, wrong digest, forged signature, mutual refund while Disputed).
import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';

const { MARKET, GATEWAY, GATEWAY_KEY, ANVIL, OUT } = process.env;
if (!MARKET || !GATEWAY || !GATEWAY_KEY || !ANVIL || !OUT) throw new Error('run through run.sh');
for (const url of [MARKET, GATEWAY]) {
  const host = new URL(url).hostname;
  if (host !== '127.0.0.1' && host !== 'localhost') throw new Error('A2 talks to the local stack only');
}

const evidence = { label: 'LOCAL_SIMULATION', tool: 'phase-2/a2-local-stack', startedAt: new Date().toISOString(), steps: [] };
const record = (step, data = {}) => { evidence.steps.push({ step, at: new Date().toISOString(), ...data }); console.log('[a2]', step, JSON.stringify(data)); };
const sleep = ms => new Promise(r => setTimeout(r, ms));

// Throwaway keys must never reach a log, an error message or the evidence: every failure is reported with keys redacted.
const redact = text => String(text).replace(/0x[0-9a-fA-F]{64}(?![0-9a-fA-F])/g, m => (KNOWN_KEYS.has(m.toLowerCase()) ? '<redacted-key>' : m))
  .replace(/(--private-key\s+)\S+/g, '$1<redacted-key>');
const KNOWN_KEYS = new Set();
function cast(args) {
  try {
    return execFileSync('docker', ['exec', ANVIL, 'cast', ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
  } catch (error) {
    const safeArgs = args.map((a, i) => (args[i - 1] === '--private-key' ? '<redacted-key>' : a));
    throw new Error(redact(`cast ${safeArgs.join(' ')} failed: ${(error.stderr || error.message || '').toString().slice(0, 500)}`));
  }
}
const rpc = ['--rpc-url', 'http://localhost:8545'];

function newWallet() {
  const parsed = JSON.parse(cast(['wallet', 'new', '--json']));
  const w = Array.isArray(parsed) ? parsed[0] : parsed.data[0];
  KNOWN_KEYS.add(w.private_key.toLowerCase());
  cast(['rpc', 'anvil_setBalance', w.address, '0x56BC75E2D63100000', ...rpc]); // 100 simulated ETH for gas
  return { address: w.address.toLowerCase(), key: w.private_key };
}

async function call(base, path, { method = 'GET', body, token, internal, headers: extra = {} } = {}) {
  const headers = { 'Content-Type': 'application/json', ...extra };
  if (token) headers.Authorization = 'Bearer ' + token;
  if (internal) headers['X-Internal-Api-Key'] = GATEWAY_KEY;
  const res = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  let json; try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text.slice(0, 300) }; }
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${JSON.stringify(json).slice(0, 400)}`);
  return json && Object.prototype.hasOwnProperty.call(json, 'data') ? json.data : json;
}
const market = (path, opts) => call(MARKET, '/api/v1' + path, opts);

async function user(kind) {
  const email = `a2-${kind.toLowerCase()}-${randomUUID().slice(0, 8)}@example.test`;
  const password = 'A2-' + randomUUID();
  // Freelancers must register tax and bank details (existing rule). These are obviously fake demonstration values: the stack
  // is a disposable local simulation and the MISA and payout services are not running in it.
  const freelancerDetails = kind === 'FREELANCER' ? { taxCode: '0000000000', identityNumber: '000000000000', nationality: 'SIMULATED',
    taxAddress: 'A2 local simulation (not a real address)', bankCode: 'VIETCOMBANK', bankAccountNumber: '0000000000',
    bankAccountHolderName: 'A2 SIMULATION' } : {};
  await market('/auth/register', { method: 'POST', body: { email, password, displayName: `A2 ${kind}`, userType: kind, ...freelancerDetails } });
  const signIn = await market('/auth/sign-in', { method: 'POST', body: { email, password } });
  const me = await market('/auth/me', { token: signIn.accessToken }).catch(() => null);
  return { email, token: signIn.accessToken, id: me?.id ?? signIn.id ?? signIn.userId };
}

async function linkWallet(party, wallet) {
  const challenge = await market('/avalanche/wallet-link/challenge', { method: 'POST', token: party.token, body: { walletAddress: wallet.address } });
  const signature = cast(['wallet', 'sign', '--private-key', wallet.key, challenge.message]);
  const bound = await market('/avalanche/wallet-link/verify', { method: 'POST', token: party.token, body: { challengeId: challenge.challengeId, signature } });
  if (bound.walletAddress !== wallet.address) throw new Error('linked a different address');
  const replay = await market('/avalanche/wallet-link/verify', { method: 'POST', token: party.token, body: { challengeId: challenge.challengeId, signature } })
    .then(() => 'ACCEPTED', () => 'REFUSED');
  if (replay !== 'REFUSED') throw new Error('a used challenge was accepted again');
  return { linked: bound.walletAddress, replayOfUsedChallenge: replay };
}

async function newContract(client, freelancer, title) {
  const job = await market('/marketplace/jobs', { method: 'POST', token: client.token, body: {
    title, description: 'A2 LOCAL_SIMULATION demonstration job (no real funds).', category: 'BACKEND_API', skills: [],
    budgetUsd: 5, deliveryDueAt: new Date(Date.now() + 7 * 86400_000).toISOString(), reviewWindowHours: 72, maxRevisions: 2,
    deliverables: [{ title: 'Source code', description: 'Repository and instructions' }],
    acceptanceCriteria: [{ description: 'Tests pass' }] } });
  await market(`/marketplace/jobs/${job.id}/apply`, { method: 'POST', token: freelancer.token, body: {} });
  const assigned = await market(`/marketplace/jobs/${job.id}/assignments`, { method: 'POST', token: client.token, body: { freelancerId: freelancer.id } });
  const contractId = assigned.contract?.id;
  if (!contractId) throw new Error('no contract after assignment');
  const enabled = await market(`/contracts/${contractId}/avalanche/enable`, { method: 'POST', token: client.token });
  return { jobId: job.id, contractId, networkId: enabled.networkId, paymentNetworkIdInJob: assigned.contract?.paymentNetworkId ?? null };
}

function sendPrepared(wallet, tx) {
  if (Number(tx.chainId) !== 31337) throw new Error('A2 sends only on chain 31337, got ' + tx.chainId);
  const out = JSON.parse(cast(['send', '--json', '--private-key', wallet.key, ...rpc, tx.to, tx.data]));
  if (out.status !== '0x1' && out.status !== 1 && out.status !== '1') throw new Error('transaction reverted: ' + out.transactionHash);
  return out.transactionHash;
}

async function walletAction(party, wallet, contractId, action, extra = {}) {
  const input = { idempotencyKey: randomUUID(), ...extra };
  const prepared = await market(`/contracts/${contractId}/avalanche/actions/${action}/prepare`, { method: 'POST', token: party.token, body: input });
  let last = null;
  for (const tx of prepared.transactions) last = sendPrepared(wallet, tx);
  if (!last) throw new Error(`${action}: nothing to send`);
  await market(`/contracts/${contractId}/avalanche/actions/${action}/transactions`, { method: 'POST', token: party.token, body: { txHash: last, input } });
  return last;
}

async function awaitApplied(party, contractId, action) {
  const seen = [];
  for (let i = 0; i < 90; i++) {
    const view = await market(`/contracts/${contractId}/avalanche/refresh`, { method: 'POST', token: party.token });
    const row = view.transactions.filter(t => t.action === action).at(-1);
    if (row && seen.at(-1) !== row.status) seen.push(row.status);
    if (row?.businessApplied) return { states: seen, chainStatus: view.chain?.status ?? null, txHash: row.txHash };
    if (row && ['REVERTED', 'FAILED', 'REPLACED'].includes(row.status)) throw new Error(`${action} ended ${row.status}`);
    if (row?.errorClass === 'CHAIN_MISMATCH') throw new Error(`${action}: CHAIN_MISMATCH`);
    await sleep(1000);
  }
  throw new Error(`${action} was not applied in time; states seen ${seen.join(' > ')}`);
}

async function contractState(party, jobId) {
  const job = await market(`/marketplace/jobs/${jobId}`, { token: party.token });
  return { contract: job.contract?.status ?? null, milestone: job.contract?.milestoneStatus ?? null };
}

async function main() {
  const config = await call(GATEWAY, '/api/v1/avalanche/config');
  if (config.environment !== 'LOCAL_SIMULATION' || Number(config.chainId) !== 31337) throw new Error('not a LOCAL_SIMULATION gateway');
  record('gateway', { environment: config.environment, chainId: config.chainId, escrow: config.escrow, token: config.token, tokenKind: config.tokenKind });

  const client = await user('CLIENT');
  const freelancer = await user('FREELANCER');
  record('users registered', { client: client.email, freelancer: freelancer.email });

  const clientWallet = newWallet();
  const freelancerWallet = newWallet();
  record('A8 wallet link client', await linkWallet(client, clientWallet));
  record('A8 wallet link freelancer', await linkWallet(freelancer, freelancerWallet));

  // ---- flow 1: fund -> release ---------------------------------------------------------------------------------------
  const c1 = await newContract(client, freelancer, 'A2 release flow');
  record('contract 1 on the Avalanche rail', c1);
  await call(GATEWAY, '/api/v1/avalanche/assets/faucet', { method: 'POST', internal: true, body: { address: clientWallet.address, amount: '20000000' } });
  record('MockUSDC faucet to the client wallet (LOCAL only)', { amountBaseUnits: '20000000' });
  await walletAction(client, clientWallet, c1.contractId, 'fund');
  record('fund applied', { ...(await awaitApplied(client, c1.contractId, 'fund')), ...(await contractState(client, c1.jobId)) });

  // the escrow releases only from Submitted (D4): the freelancer records work in the marketplace, then anchors it on chain
  const job1 = await market(`/marketplace/jobs/${c1.jobId}`, { token: freelancer.token });
  const requirement = job1.contract?.deliverables?.[0]?.id;
  if (!requirement) throw new Error('no deliverable requirement on the contract');
  const submission = await market(`/contracts/${c1.contractId}/submissions`, { method: 'POST', token: freelancer.token,
    headers: { 'Idempotency-Key': 'a2-submit-' + randomUUID() },
    body: { summary: 'A2 demonstration delivery (local simulation).',
      deliverables: [{ requirementId: requirement, url: 'https://example.test/a2-delivery', description: 'A2 demonstration artefact' }] } });
  record('work submitted in the marketplace', { submissionStatus: submission.status, jobStatus: job1.status });
  await walletAction(freelancer, freelancerWallet, c1.contractId, 'submit', { submissionId: submission.id });
  record('submission anchored on chain', await awaitApplied(freelancer, c1.contractId, 'submit'));
  const early = await market(`/avalanche/payouts/contracts/${c1.contractId}`, { method: 'POST', token: freelancer.token })
    .then(() => 'ACCEPTED', () => 'REFUSED');
  if (early !== 'REFUSED') throw new Error('a payout was accepted before the escrow released');
  await walletAction(client, clientWallet, c1.contractId, 'release');
  const released = await awaitApplied(client, c1.contractId, 'release');
  const after1 = await contractState(client, c1.jobId);
  record('release applied', { ...released, ...after1 });
  if (after1.contract !== 'COMPLETED' || after1.milestone !== 'RELEASED') throw new Error('release did not complete the contract');
  const notes = await market('/notifications', { token: freelancer.token });
  const list = Array.isArray(notes) ? notes : (notes?.content ?? notes?.data ?? []);
  record('settlement notification to the freelancer', { types: list.map(n => n.type).filter(Boolean) });

  // ---- flow 1b: non-custodial payout (Phase 3, LOCAL mock partner, simulated) ------------------------------------------
  // The released USDC is in the freelancer's OWN wallet; the freelancer sends it to the partner deposit address; the gateway
  // proves the transfer from the receipt; only then is the (simulated) partner asked about the fiat payout.
  const byClient = await market(`/avalanche/payouts/contracts/${c1.contractId}`, { method: 'POST', token: client.token })
    .then(() => 'ACCEPTED', () => 'REFUSED');
  if (byClient !== 'REFUSED') throw new Error('the client could request the freelancer payout');
  const requested = await market(`/avalanche/payouts/contracts/${c1.contractId}`, { method: 'POST', token: freelancer.token });
  if (requested.outcome !== 'CREATED') throw new Error('payout not created: ' + JSON.stringify(requested));
  const po = requested.payout;
  record('payout requested (local mock partner)', { outcome: requested.outcome, status: po.status, amountBaseUnits: po.amountBaseUnits,
    depositAddress: po.depositAddress, partnerId: po.partnerId, partnerSimulated: po.partnerSimulated,
    partnerReportedFee: po.partnerReportedFee, taxTreatment: po.taxTreatment, before: 'refused before release: ' + early });
  const transfer = JSON.parse(cast(['send', '--json', '--private-key', freelancerWallet.key, ...rpc, config.token,
    'transfer(address,uint256)', po.depositAddress, po.amountBaseUnits]));
  let pv = await market(`/avalanche/payouts/${po.id}/transfer`, { method: 'POST', token: freelancer.token, body: { txHash: transfer.transactionHash } });
  const payoutStates = [pv.status];
  for (let i = 0; i < 60 && pv.status !== 'FIAT_PAYOUT_CONFIRMED'; i++) {
    await sleep(1000);
    pv = await market(`/avalanche/payouts/${po.id}/refresh`, { method: 'POST', token: freelancer.token });
    if (payoutStates.at(-1) !== pv.status) payoutStates.push(pv.status);
    if (['TRANSFER_FAILED', 'FIAT_PAYOUT_FAILED'].includes(pv.status)) throw new Error('payout ended ' + pv.status + ' ' + pv.failureReason);
  }
  if (pv.status !== 'FIAT_PAYOUT_CONFIRMED' || !pv.onChainTransferConfirmed || !pv.fiatPayoutConfirmed) throw new Error('payout did not complete');
  if (new Date(pv.transferConfirmedAt) > new Date(pv.fiatConfirmedAt)) throw new Error('fiat confirmed before the on-chain transfer');
  const deposited = await call(GATEWAY, `/api/v1/avalanche/assets/balance?address=${po.depositAddress}`);
  if (deposited.balanceBaseUnits !== po.amountBaseUnits) throw new Error('the deposit address did not receive the amount');
  const freelancerLeft = await call(GATEWAY, `/api/v1/avalanche/assets/balance?address=${freelancerWallet.address}`);
  record('payout completed (simulated fiat)', { states: payoutStates, transferChainState: pv.transferChainState, transferMatch: pv.transferMatch,
    transferConfirmedAt: pv.transferConfirmedAt, fiatConfirmedAt: pv.fiatConfirmedAt, fiatReference: pv.fiatReference,
    depositBalance: deposited.balanceBaseUnits, freelancerBalanceAfter: freelancerLeft.balanceBaseUnits });
  const reuse = await market(`/avalanche/payouts/${po.id}/transfer`, { method: 'POST', token: freelancer.token, body: { txHash: transfer.transactionHash } })
    .then(() => 'ACCEPTED', () => 'REFUSED');
  const again = await market(`/avalanche/payouts/contracts/${c1.contractId}`, { method: 'POST', token: freelancer.token });
  if (reuse !== 'REFUSED' || again.outcome !== 'EXISTING') throw new Error('a completed payout could be reused');
  record('completed payout cannot be reused', { transferReuse: reuse, secondRequest: again.outcome });

  // ---- flow 2: fund -> two-party wallet mutual refund (F13) ---------------------------------------------------------
  const c2 = await newContract(client, freelancer, 'A2 mutual refund flow');
  record('contract 2 on the Avalanche rail', c2);
  await walletAction(client, clientWallet, c2.contractId, 'fund');
  record('fund applied (contract 2)', await awaitApplied(client, c2.contractId, 'fund'));

  // P3-C4 on the live stack: an issuer blacklist on the client is reported (refund blocked), then lifted; nothing is bypassed
  const ANVIL_ACCOUNT_0 = '0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266';
  const setBlacklisted = value => cast(['send', '--unlocked', '--from', ANVIL_ACCOUNT_0, ...rpc, config.token,
    'setBlacklisted(address,bool)', clientWallet.address, String(value)]);
  setBlacklisted(true);
  const stuck = await market(`/contracts/${c2.contractId}/avalanche`, { token: client.token });
  const stuckCodes = (stuck.issuerAlerts ?? []).map(a => a.code);
  if (!stuckCodes.includes('CLIENT_BLACKLISTED')) throw new Error('the issuer blacklist was not reported: ' + JSON.stringify(stuck.issuerAlerts));
  record('P3-C4 stuck escrow reported', { alerts: stuck.issuerAlerts, chainStatus: stuck.chain?.status ?? null });
  setBlacklisted(false);
  const clear = await market(`/contracts/${c2.contractId}/avalanche`, { token: client.token });
  if ((clear.issuerAlerts ?? []).length !== 0) throw new Error('the report did not clear after the control was lifted');
  record('P3-C4 report cleared after the issuer lifted the control', { alerts: clear.issuerAlerts });

  const first = await market(`/contracts/${c2.contractId}/avalanche/actions/mutual-refund/prepare`, { method: 'POST', token: client.token, body: {} });
  const clientSig = cast(['wallet', 'sign', '--private-key', clientWallet.key, '--data', first.typedData]);
  let view = await market(`/contracts/${c2.contractId}/avalanche/mutual-refund/signatures`, { method: 'POST', token: client.token,
    body: { authorizationDeadline: first.authorizationDeadline, typedDataDigest: first.typedDataDigest, signature: clientSig } });
  record('F13 client signed', { mutualRefund: view.mutualRefund });
  cast(['rpc', 'anvil_mine', '3', ...rpc]); // blocks between the two signatures: the condition that broke F13
  const second = await market(`/contracts/${c2.contractId}/avalanche/actions/mutual-refund/prepare`, { method: 'POST', token: freelancer.token,
    body: { authorizationDeadline: view.mutualRefund.authorizationDeadline } });
  if (second.typedDataDigest !== first.typedDataDigest) throw new Error('the second signer was shown different typed data');
  const freelancerSig = cast(['wallet', 'sign', '--private-key', freelancerWallet.key, '--data', second.typedData]);
  view = await market(`/contracts/${c2.contractId}/avalanche/mutual-refund/signatures`, { method: 'POST', token: freelancer.token,
    body: { authorizationDeadline: second.authorizationDeadline, typedDataDigest: second.typedDataDigest, signature: freelancerSig } });
  record('F13 freelancer signed', { mutualRefund: view.mutualRefund });
  await walletAction(freelancer, freelancerWallet, c2.contractId, 'mutual-refund');
  const refunded = await awaitApplied(freelancer, c2.contractId, 'mutual-refund');
  const after2 = await contractState(client, c2.jobId);
  record('mutual refund applied', { ...refunded, ...after2 });
  if (after2.contract !== 'CANCELLED' || after2.milestone !== 'REFUNDED') throw new Error('mutual refund did not cancel the contract');
  const finalView = await market(`/contracts/${c2.contractId}/avalanche`, { token: client.token });
  record('held signatures spent', { mutualRefund: finalView.mutualRefund ?? null });
  const refundPayout = await market(`/avalanche/payouts/contracts/${c2.contractId}`, { method: 'POST', token: freelancer.token })
    .then(() => 'ACCEPTED', () => 'REFUSED');
  if (refundPayout !== 'REFUSED') throw new Error('a payout was accepted for a refunded escrow');
  record('no payout for a refunded escrow', { request: refundPayout });

  // ---- flow 3 and 4: fund -> submit -> dispute -> two-party settlement of the Disputed escrow (R12, F14) --------------------
  // Contract 3 settles RELEASE (negative checks first); contract 4 settles REFUND. The settlement needs BOTH parties' own wallet
  // signatures over the same typed data; the arbiter (the gateway's ephemeral signer) takes no part.
  const thirdParty = newWallet(); // throwaway key that is neither party (forged-signature check)
  await settleDisputedEscrow(client, freelancer, clientWallet, freelancerWallet, thirdParty, config, true);
  await settleDisputedEscrow(client, freelancer, clientWallet, freelancerWallet, thirdParty, config, false);

  evidence.result = 'PASS';
}

const refused = promise => promise.then(() => 'ACCEPTED', e => 'REFUSED: ' + String(e.message).slice(0, 160));

async function settleDisputedEscrow(client, freelancer, clientWallet, freelancerWallet, thirdParty, config, release) {
  const tag = release ? 'RELEASE' : 'REFUND';
  const c = await newContract(client, freelancer, `A2 mutual settlement ${tag}`);
  record(`R12 ${tag}: contract on the Avalanche rail`, c);
  await call(GATEWAY, '/api/v1/avalanche/assets/faucet', { method: 'POST', internal: true, body: { address: clientWallet.address, amount: '5000000' } });
  await walletAction(client, clientWallet, c.contractId, 'fund');
  await awaitApplied(client, c.contractId, 'fund');
  const job = await market(`/marketplace/jobs/${c.jobId}`, { token: freelancer.token });
  const requirement = job.contract?.deliverables?.[0]?.id;
  const submission = await market(`/contracts/${c.contractId}/submissions`, { method: 'POST', token: freelancer.token,
    headers: { 'Idempotency-Key': 'a2-submit-' + randomUUID() },
    body: { summary: 'A2 delivery (local simulation).', deliverables: [{ requirementId: requirement, url: 'https://example.test/a2-delivery', description: 'A2 artefact' }] } });
  await walletAction(freelancer, freelancerWallet, c.contractId, 'submit', { submissionId: submission.id });
  await awaitApplied(freelancer, c.contractId, 'submit');
  record(`R12 ${tag}: funded and submitted on chain`, {});

  const dispute = await market(`/contracts/${c.contractId}/disputes`, { method: 'POST', token: client.token,
    body: { reasonCode: 'QUALITY', description: 'A2 LOCAL_SIMULATION dispute (no real funds).' } });
  await walletAction(client, clientWallet, c.contractId, 'dispute', { disputeId: dispute.disputeId });
  const disputed = await awaitApplied(client, c.contractId, 'dispute');
  if (disputed.chainStatus !== 'DISPUTED') throw new Error('the escrow is not Disputed on chain: ' + disputed.chainStatus);
  record(`R12 ${tag}: escrow Disputed on chain`, { disputeId: dispute.disputeId, chainStatus: disputed.chainStatus });

  const settleInput = { disputeId: dispute.disputeId, releaseToFreelancer: release };
  const prepare = (party, extra = {}) => market(`/contracts/${c.contractId}/avalanche/actions/mutual-settle/prepare`,
    { method: 'POST', token: party.token, body: { idempotencyKey: randomUUID(), ...settleInput, ...extra } });
  const sign = (wallet, prepared) => market(`/contracts/${c.contractId}/avalanche/mutual-settle/signatures`, { method: 'POST',
    token: wallet === clientWallet ? client.token : freelancer.token,
    body: { ...settleInput, authorizationDeadline: prepared.authorizationDeadline, typedDataDigest: prepared.typedDataDigest,
      signature: cast(['wallet', 'sign', '--private-key', wallet.key, '--data', prepared.typedData]) } });

  const negatives = {};
  const first = await prepare(client);
  if (first.transactions.length !== 0) throw new Error('a settlement transaction was prepared without any signature');
  negatives.noSignatureNoTransaction = 'no transaction prepared';
  negatives.nonParty = await refused(market(`/contracts/${c.contractId}/avalanche/mutual-settle/signatures`, { method: 'POST', token: (await user('CLIENT')).token,
    body: { ...settleInput, authorizationDeadline: first.authorizationDeadline, typedDataDigest: first.typedDataDigest, signature: '0x' + '11'.repeat(65) } }));
  if (!negatives.nonParty.startsWith('REFUSED')) throw new Error('a non-party stored a settlement signature');
  negatives.wrongDigest = await refused(market(`/contracts/${c.contractId}/avalanche/mutual-settle/signatures`, { method: 'POST', token: client.token,
    body: { ...settleInput, authorizationDeadline: first.authorizationDeadline, typedDataDigest: '0x' + '22'.repeat(32), signature: '0x' + '11'.repeat(65) } }));
  if (!negatives.wrongDigest.startsWith('REFUSED')) throw new Error('a signature over other data was held');

  await sign(clientWallet, first);
  const oneSigned = await prepare(freelancer, { authorizationDeadline: first.authorizationDeadline });
  if (oneSigned.transactions.length !== 0) throw new Error('a settlement was prepared with only one party signature');
  negatives.oneSignatureOnly = 'no transaction prepared';
  const mutualRefundWhileDisputed = await refused(walletAction(client, clientWallet, c.contractId, 'mutual-refund'));
  negatives.mutualRefundWhileDisputed = mutualRefundWhileDisputed;
  if (!mutualRefundWhileDisputed.startsWith('REFUSED')) throw new Error('mutual refund was possible while Disputed');

  // a signature of a non-party, held as the freelancer's: the escrow contract must reject it
  const forged = cast(['wallet', 'sign', '--private-key', thirdParty.key, '--data', first.typedData]);
  await market(`/contracts/${c.contractId}/avalanche/mutual-settle/signatures`, { method: 'POST', token: freelancer.token,
    body: { ...settleInput, authorizationDeadline: first.authorizationDeadline, typedDataDigest: first.typedDataDigest, signature: forged } });
  negatives.forgedPartySignature = await refused(walletAction(freelancer, freelancerWallet, c.contractId, 'mutual-settle', settleInput));
  if (!negatives.forgedPartySignature.startsWith('REFUSED')) throw new Error('the escrow accepted a settlement signed by a non-party');
  const stillDisputed = await market(`/contracts/${c.contractId}/avalanche`, { token: client.token });
  if (stillDisputed.chain?.status !== 'DISPUTED') throw new Error('the escrow left Disputed without a valid settlement: ' + stillDisputed.chain?.status);
  record(`R12 ${tag}: negative checks before the settlement`, { negatives, chainStatusAfter: stillDisputed.chain.status });

  // both parties sign the same typed data with their own wallets; the freelancer's genuine signature replaces the forged one
  const genuine = await prepare(freelancer, { authorizationDeadline: first.authorizationDeadline });
  if (genuine.typedDataDigest !== first.typedDataDigest) throw new Error('the second signer was shown different typed data');
  const view = await sign(freelancerWallet, genuine);
  record(`R12 ${tag}: both parties signed`, { mutualSettlements: view.mutualSettlements });
  const txHash = await walletAction(freelancer, freelancerWallet, c.contractId, 'mutual-settle', settleInput);
  const applied = await awaitApplied(freelancer, c.contractId, 'mutual-settle');
  const after = await contractState(client, c.jobId);
  const expected = release ? { contract: 'COMPLETED', milestone: 'RELEASED', chain: 'RELEASED' } : { contract: 'CANCELLED', milestone: 'REFUNDED', chain: 'REFUNDED' };
  if (after.contract !== expected.contract || after.milestone !== expected.milestone || applied.chainStatus !== expected.chain) {
    throw new Error(`R12 ${tag}: unexpected outcome ${JSON.stringify({ after, chain: applied.chainStatus })}`);
  }
  const resolved = await market(`/contracts/${c.contractId}/disputes`, { token: client.token });
  const settledAgain = await refused(prepare(client, { authorizationDeadline: first.authorizationDeadline }));
  const spent = await market(`/contracts/${c.contractId}/avalanche`, { token: client.token });
  const clientBalance = await call(GATEWAY, `/api/v1/avalanche/assets/balance?address=${clientWallet.address}`);
  const freelancerBalance = await call(GATEWAY, `/api/v1/avalanche/assets/balance?address=${freelancerWallet.address}`);
  record(`R12 ${tag}: settlement finalized`, { txHash, states: applied.states, chainStatus: applied.chainStatus, ...after,
    disputeStatus: resolved?.status ?? null, settleAgainWhenNotDisputed: settledAgain, heldSignaturesSpent: spent.mutualSettlements ?? null,
    clientBalanceBaseUnits: clientBalance.balanceBaseUnits, freelancerBalanceBaseUnits: freelancerBalance.balanceBaseUnits });
  if (!settledAgain.startsWith('REFUSED')) throw new Error('a second settlement could be prepared for a settled escrow');
}

main().catch(error => { evidence.result = 'FAIL'; evidence.error = redact(String(error.message ?? error)).slice(0, 800); process.exitCode = 1; })
  .finally(() => {
    evidence.finishedAt = new Date().toISOString();
    const text = JSON.stringify(evidence, null, 2);
    if (/private_key|"key"\s*:|--private-key\s+0x/i.test(text) || [...KNOWN_KEYS].some(k => text.toLowerCase().includes(k))) {
      throw new Error('refusing to write evidence that mentions key material');
    }
    writeFileSync(OUT, text);
    console.log('[a2] result', evidence.result, redact(evidence.error ?? ''));
  });
