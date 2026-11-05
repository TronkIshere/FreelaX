// Register item B7, LOCAL equivalent: real browser (Microsoft Edge driven by Playwright) against the real frontend (Vite dev server,
// VITE_AVALANCHE_ENABLED=true), the real marketplace and gateway, MySQL and Anvil (chain 31337, MockUSDC), started by run.sh with
// A2_FRONTEND=1 and A2_DRIVER=this file. LOCAL_SIMULATION only: no Fuji, no Mainnet, no real wallet.
//
// THE WALLET IS A TEST DOUBLE, NOT METAMASK. A small EIP-1193 provider is injected as window.ethereum; every request is forwarded to this
// Node process, which signs with a THROWAWAY key created inside the Anvil container for this run (cast, as drive.mjs does). The key never
// enters the page, a log or the evidence. It can accept, reject, switch network, refuse to switch, fail, or return an invented transaction
// hash, which is how the wallet-facing behaviour of the real UI is exercised. It proves the UI + backend + chain behave correctly against
// an EIP-1193 provider; it does NOT prove anything about the MetaMask extension (B7 in its original form stays an owner task).
import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { pathToFileURL } from 'node:url';

const { MARKET, GATEWAY, GATEWAY_KEY, ANVIL, OUT, FRONTEND, PLAYWRIGHT_CORE } = process.env;
if (!MARKET || !GATEWAY || !GATEWAY_KEY || !ANVIL || !OUT || !FRONTEND || !PLAYWRIGHT_CORE) throw new Error('run through run.sh with A2_FRONTEND=1 and PLAYWRIGHT_CORE set');
for (const url of [MARKET, GATEWAY, FRONTEND]) {
  const host = new URL(url).hostname;
  if (host !== '127.0.0.1' && host !== 'localhost') throw new Error('the browser validation talks to the local stack only');
}
const { chromium } = await import(pathToFileURL(PLAYWRIGHT_CORE).href);

const evidence = { label: 'LOCAL_SIMULATION', kind: 'browser wallet validation with an EIP-1193 test double (NOT MetaMask, NOT Fuji)',
  tool: 'phase-2/a2-local-stack/browser.mjs', startedAt: new Date().toISOString(), steps: [] };
const record = (step, data = {}) => { evidence.steps.push({ step, at: new Date().toISOString(), ...data }); console.log('[browser]', step, JSON.stringify(data).slice(0, 300)); };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const KNOWN_KEYS = new Set();
const redact = text => String(text).replace(/0x[0-9a-fA-F]{64}(?![0-9a-fA-F])/g, m => (KNOWN_KEYS.has(m.toLowerCase()) ? '<redacted-key>' : m)).replace(/(--private-key\s+)\S+/g, '$1<redacted-key>');
function cast(args) {
  try { return execFileSync('docker', ['exec', ANVIL, 'cast', ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim(); }
  catch (error) {
    const safeArgs = args.map((a, i) => (args[i - 1] === '--private-key' ? '<redacted-key>' : a));
    throw new Error(redact(`cast ${safeArgs.join(' ').slice(0, 200)} failed: ${(error.stderr || error.message || '').toString().slice(0, 300)}`));
  }
}
const rpc = ['--rpc-url', 'http://localhost:8545'];
function newWallet() {
  const parsed = JSON.parse(cast(['wallet', 'new', '--json']));
  const w = Array.isArray(parsed) ? parsed[0] : parsed.data[0];
  KNOWN_KEYS.add(w.private_key.toLowerCase());
  cast(['rpc', 'anvil_setBalance', w.address, '0x56BC75E2D63100000', ...rpc]);
  return { address: w.address.toLowerCase(), key: w.private_key };
}
async function call(base, path, { method = 'GET', body, token, internal } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  if (internal) headers['X-Internal-Api-Key'] = GATEWAY_KEY;
  const res = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  let json; try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text.slice(0, 300) }; }
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${JSON.stringify(json).slice(0, 300)}`);
  return json && Object.prototype.hasOwnProperty.call(json, 'data') ? json.data : json;
}
const market = (path, opts) => call(MARKET, '/api/v1' + path, opts);

async function user(kind) {
  const email = `b7-${kind.toLowerCase()}-${randomUUID().slice(0, 8)}@example.test`;
  const password = 'B7-' + randomUUID();
  const details = kind === 'FREELANCER' ? { taxCode: '0000000000', identityNumber: '000000000000', nationality: 'SIMULATED',
    taxAddress: 'B7 local simulation (not a real address)', bankCode: 'VIETCOMBANK', bankAccountNumber: '0000000000', bankAccountHolderName: 'B7 SIMULATION' } : {};
  await market('/auth/register', { method: 'POST', body: { email, password, displayName: `B7 ${kind}`, userType: kind, ...details } });
  const signIn = await market('/auth/sign-in', { method: 'POST', body: { email, password } });
  const me = await market('/auth/me', { token: signIn.accessToken }).catch(() => null);
  return { email, password, token: signIn.accessToken, id: me?.id ?? signIn.id ?? signIn.userId };
}
async function newJobWithContract(client, freelancer, title) {
  const job = await market('/marketplace/jobs', { method: 'POST', token: client.token, body: {
    title, description: 'B7 LOCAL_SIMULATION browser validation job (no real funds).', category: 'BACKEND_API', skills: [], budgetUsd: 5,
    deliveryDueAt: new Date(Date.now() + 7 * 86400_000).toISOString(), reviewWindowHours: 72, maxRevisions: 2,
    deliverables: [{ title: 'Source code', description: 'Repository and instructions' }], acceptanceCriteria: [{ description: 'Tests pass' }] } });
  await market(`/marketplace/jobs/${job.id}/apply`, { method: 'POST', token: freelancer.token, body: {} });
  const assigned = await market(`/marketplace/jobs/${job.id}/assignments`, { method: 'POST', token: client.token, body: { freelancerId: freelancer.id } });
  return { jobId: job.id, contractId: assigned.contract?.id };
}

// ---- the wallet test double (Node side) ------------------------------------------------------------------------------------------
const wallet = { address: null, key: null };
const mode = { chainId: '0x7a69', rejectConnect: false, refuseSwitch: false, rejectSign: false, rejectSend: false, fabricateHash: false, calls: [] };
const rpcError = (code, message) => ({ error: { code, message } });
function walletRpc(method, params = []) {
  mode.calls.push(method);
  switch (method) {
    case 'eth_requestAccounts': case 'eth_accounts': return mode.rejectConnect ? rpcError(4001, 'User rejected the request.') : { result: [wallet.address] };
    case 'eth_chainId': return { result: mode.chainId };
    case 'wallet_switchEthereumChain':
      if (mode.refuseSwitch) return rpcError(4902, 'Unrecognized chain.');
      mode.chainId = params[0].chainId; return { result: null };
    case 'personal_sign': {
      if (mode.rejectSign) return rpcError(4001, 'User rejected the request.');
      const message = Buffer.from(String(params[0]).slice(2), 'hex').toString('utf8');
      return { result: cast(['wallet', 'sign', '--private-key', wallet.key, message]) };
    }
    case 'eth_signTypedData_v4':
      if (mode.rejectSign) return rpcError(4001, 'User rejected the request.');
      return { result: cast(['wallet', 'sign', '--private-key', wallet.key, '--data', params[1]]) };
    case 'eth_sendTransaction': {
      if (mode.rejectSend) return rpcError(4001, 'User rejected the request.');
      if (mode.fabricateHash) return { result: '0x' + 'ab'.repeat(32) }; // a hash for a transaction that was never sent
      const tx = params[0];
      try {
        const out = JSON.parse(cast(['send', '--json', '--private-key', wallet.key, ...rpc, tx.to, tx.data]));
        if (out.status !== '0x1' && out.status !== 1 && out.status !== '1') return rpcError(-32000, 'transaction reverted');
        return { result: out.transactionHash };
      } catch (e) { return rpcError(-32000, String(e.message).slice(0, 120)); }
    }
    default: return rpcError(-32601, 'method not supported by the test double: ' + method);
  }
}

const INIT_SCRIPT = `(() => {
  const provider = {
    isFreelaXTestDouble: true,
    request: async ({ method, params }) => {
      const r = await window.__walletRpc(method, params ?? []);
      if (r.error) { const e = new Error(r.error.message); e.code = r.error.code; throw e; }
      return r.result;
    },
    on() {}, removeListener() {},
  };
  window.ethereum = provider;
})();`;

let currentPage = null;
let browserRef = null;

async function main() {
  const config = await call(GATEWAY, '/api/v1/avalanche/config');
  if (config.environment !== 'LOCAL_SIMULATION' || Number(config.chainId) !== 31337) throw new Error('not a LOCAL_SIMULATION gateway');
  record('stack', { environment: config.environment, chainId: config.chainId, frontend: FRONTEND });
  const client = await user('CLIENT');
  const freelancer = await user('FREELANCER');
  Object.assign(wallet, newWallet());
  // The escrow needs BOTH parties' linked EVM wallets. The freelancer's wallet is linked through the API (a second throwaway key); the
  // client's wallet is linked through the UI below.
  const freelancerWallet = newWallet();
  const challenge = await market('/avalanche/wallet-link/challenge', { method: 'POST', token: freelancer.token, body: { walletAddress: freelancerWallet.address } });
  const freelancerSignature = cast(['wallet', 'sign', '--private-key', freelancerWallet.key, challenge.message]);
  await market('/avalanche/wallet-link/verify', { method: 'POST', token: freelancer.token, body: { challengeId: challenge.challengeId, signature: freelancerSignature } });
  const A = await newJobWithContract(client, freelancer, 'B7 browser wallet contract A');
  const B = await newJobWithContract(client, freelancer, 'B7 browser wallet contract B (invented hash)');
  await market(`/contracts/${B.contractId}/avalanche/enable`, { method: 'POST', token: client.token });
  record('users, throwaway wallet and two contracts created through the API', { contractA: A.contractId, contractB: B.contractId });

  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  browserRef = browser;
  const context = await browser.newContext();
  await context.exposeFunction('__walletRpc', (method, params) => walletRpc(method, params));
  await context.addInitScript(INIT_SCRIPT);
  const page = await context.newPage();
  currentPage = page;
  page.setDefaultTimeout(30000);
  const consoleErrors = [];
  page.on('pageerror', e => consoleErrors.push(String(e.message).slice(0, 200)));
  const bodyText = () => page.evaluate(() => document.body.innerText);
  const waitText = (text, timeout = 30000) => page.waitForFunction(t => document.body.innerText.includes(t), text, { timeout });
  const alertText = () => page.evaluate(() => [...document.querySelectorAll('[role="alert"]')].map(e => e.textContent).join(' | '));
  const expect = (cond, message) => { if (!cond) throw new Error(message); };
  const clickButton = name => page.getByRole('button', { name, exact: true }).click();
  const walletNow = () => market('/avalanche/wallet-link', { token: client.token }).then(w => w?.walletAddress ?? null, () => null);
  const view = contractId => market(`/contracts/${contractId}/avalanche`, { token: client.token });

  // S0 login through the real form
  await page.goto(FRONTEND + '/');
  await page.fill('#signin-email', client.email);
  await page.fill('#signin-password', client.password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.waitForFunction(() => !document.querySelector('#signin-email'), null, { timeout: 30000 });
  record('S0 login through the UI form', { ok: true });

  // S1 open the contract and choose the Avalanche rail in the UI
  await page.goto(`${FRONTEND}/work/${A.jobId}`);
  await waitText('Dùng ký quỹ Avalanche cho hợp đồng này');
  await clickButton('Dùng ký quỹ Avalanche cho hợp đồng này');
  await waitText('Liên kết ví EVM');
  record('S1 Avalanche rail chosen in the UI; banner visible', { banner: (await bodyText()).includes('mô phỏng') });

  // S2 a wallet on a chain that is not allowed (Ethereum mainnet id 0x1): refused before anything is signed
  mode.chainId = '0x1'; mode.calls.length = 0;
  await clickButton('Liên kết ví EVM');
  await page.waitForFunction(() => document.querySelector('[role="alert"]')?.textContent?.length > 0, null, { timeout: 15000 });
  const s2 = await alertText();
  expect(/không được hỗ trợ/.test(s2) && !/[A-Za-z]{4} [a-z]+ [a-z]+ [a-z]+ not permitted/.test(s2), 'S2 message: ' + s2);
  expect(!mode.calls.includes('personal_sign'), 'S2 asked the wallet to sign on an unsupported chain');
  expect(await walletNow() === null, 'S2 a wallet was linked');
  record('S2 unsupported chain refused, nothing signed, no wallet linked', { uiMessage: s2.slice(0, 160), walletCalls: [...new Set(mode.calls)] });

  // S3 an allowed chain that is not the active environment (Fuji id 0xa869) and the wallet refuses to switch
  mode.chainId = '0xa869'; mode.refuseSwitch = true; mode.calls.length = 0;
  await clickButton('Liên kết ví EVM');
  await page.waitForFunction(() => /sai mạng/.test(document.body.innerText), null, { timeout: 15000 });
  expect(!mode.calls.includes('personal_sign'), 'S3 signed on the wrong network');
  expect(await walletNow() === null, 'S3 a wallet was linked');
  record('S3 wrong network and refused switch: blocked, nothing signed', { uiMessage: (await alertText()).slice(0, 160), walletCalls: [...new Set(mode.calls)] });

  // S4 the user rejects the signature
  mode.chainId = '0x7a69'; mode.refuseSwitch = false; mode.rejectSign = true; mode.calls.length = 0;
  await clickButton('Liên kết ví EVM');
  await page.waitForFunction(() => /từ chối/.test(document.body.innerText), null, { timeout: 15000 });
  expect(await walletNow() === null, 'S4 a wallet was linked after a rejected signature');
  record('S4 rejected signature: UI says so, no wallet linked', { uiMessage: (await alertText()).slice(0, 160) });

  // S5 link succeeds
  mode.rejectSign = false;
  await clickButton('Liên kết ví EVM');
  await waitText('Ví EVM đã liên kết');
  expect(await walletNow() === wallet.address, 'S5 the backend did not record the linked wallet');
  record('S5 wallet linked through the UI (EIP-191 challenge signed, verified by the gateway)', { linked: true });

  // S6 fund without any USDC: the wallet transaction fails; nothing is applied
  mode.calls.length = 0;
  await clickButton('Nạp tiền ký quỹ bằng ví');
  await page.waitForFunction(() => /không gửi được giao dịch|từ chối/.test(document.body.innerText), null, { timeout: 30000 });
  let v = await view(A.contractId);
  expect(!v.transactions.some(t => t.businessApplied) && v.chain?.status !== 'FUNDED', 'S6 a failed funding was applied');
  const jobA1 = await market(`/marketplace/jobs/${A.jobId}`, { token: client.token });
  expect(jobA1.contract?.status === 'PENDING_FUNDING', 'S6 contract state changed: ' + jobA1.contract?.status);
  record('S6 funding without USDC fails visibly and applies nothing', { uiMessage: (await alertText()).slice(0, 160), contractStatus: jobA1.contract?.status });

  // S7 the user rejects the funding transaction in the wallet
  await call(GATEWAY, '/api/v1/avalanche/assets/faucet', { method: 'POST', internal: true, body: { address: wallet.address, amount: '20000000' } });
  mode.rejectSend = true;
  await clickButton('Nạp tiền ký quỹ bằng ví');
  await page.waitForFunction(() => /từ chối/.test(document.body.innerText), null, { timeout: 30000 });
  v = await view(A.contractId);
  expect(!v.transactions.some(t => t.businessApplied), 'S7 a rejected transaction was applied');
  record('S7 transaction rejected in the wallet: UI says so, nothing applied', { uiMessage: (await alertText()).slice(0, 160) });
  mode.rejectSend = false;

  // S8 a wallet that returns a transaction hash for a transaction that was never sent (contract B): never shown as success
  await page.goto(`${FRONTEND}/work/${B.jobId}`);
  await waitText('Nạp tiền ký quỹ bằng ví');
  mode.fabricateHash = true;
  await clickButton('Nạp tiền ký quỹ bằng ví');
  await sleep(8000);
  const textB = await bodyText();
  const vB = await view(B.contractId);
  const jobB = await market(`/marketplace/jobs/${B.jobId}`, { token: client.token });
  expect(!vB.transactions.some(t => t.businessApplied), 'S8 an invented hash was applied');
  expect(jobB.contract?.status === 'PENDING_FUNDING' && jobB.contract?.milestoneStatus !== 'FUNDED', 'S8 the contract left PENDING_FUNDING: ' + jobB.contract?.status);
  expect(vB.chain?.status !== 'FUNDED', 'S8 the chain reads FUNDED');
  expect(!/Đã nạp tiền/.test(textB.replace(/Nạp tiền ký quỹ bằng (ví|tài khoản mô phỏng)/g, '')) , 'S8 the UI shows a funded escrow');
  record('S8 invented transaction hash: not applied, contract stays PENDING_FUNDING, UI shows no funded state', {
    rows: vB.transactions.map(t => ({ action: t.action, status: t.status, businessApplied: t.businessApplied })), contractStatus: jobB.contract?.status });
  mode.fabricateHash = false;

  // S9 genuine funding on contract A: the UI follows the chain to FINALIZED and the backend agrees
  await page.goto(`${FRONTEND}/work/${A.jobId}`);
  await waitText('Nạp tiền ký quỹ bằng ví');
  mode.calls.length = 0;
  await clickButton('Nạp tiền ký quỹ bằng ví');
  await waitText('Đã chốt', 90000);
  await waitText('Đã nạp tiền', 60000);
  v = await view(A.contractId);
  const fund = v.transactions.filter(t => t.action === 'fund').at(-1);
  const jobA2 = await market(`/marketplace/jobs/${A.jobId}`, { token: client.token });
  expect(fund?.status === 'FINALIZED' && fund.businessApplied && v.chain?.status === 'FUNDED', 'S9 backend state: ' + JSON.stringify(fund));
  expect(jobA2.contract?.status === 'ACTIVE' && jobA2.contract?.milestoneStatus === 'FUNDED', 'S9 contract state: ' + jobA2.contract?.status + '/' + jobA2.contract?.milestoneStatus);
  record('S9 funding through the UI: FINALIZED, chain FUNDED, contract ACTIVE, UI and backend agree', { txStatus: fund.status, chainStatus: v.chain.status,
    contract: jobA2.contract.status, milestone: jobA2.contract.milestoneStatus, walletCalls: [...new Set(mode.calls)] });

  // S10 no raw English enum or wallet error text is left on the contract screen
  const finalText = await bodyText();
  const leaks = ['Wallet connection was declined', 'is not permitted', 'could not be connected', 'DISPUTED', 'FUNDED', 'RELEASED'].filter(t => finalText.includes(t));
  expect(leaks.length === 0, 'S10 English or raw status text on screen: ' + leaks.join(', '));
  record('S10 no raw status enum or English wallet error on the screen', { pageErrors: consoleErrors.length });
  await browser.close();
  evidence.result = 'PASS';
}

main().catch(async error => {
  evidence.result = 'FAIL'; evidence.error = redact(String(error.message ?? error)).slice(0, 800); process.exitCode = 1;
  try { evidence.pageAtFailure = { url: currentPage.url(), panel: await currentPage.evaluate(() => !!document.querySelector('[aria-label="Ký quỹ Avalanche"]')), alerts: await currentPage.evaluate(() => [...document.querySelectorAll('[role="alert"]')].map(e => e.textContent.slice(0, 200))), tail: (await currentPage.evaluate(() => document.body.innerText)).slice(-700) }; } catch { /* no page */ }
})
  .finally(async () => {
    try { await browserRef?.close(); } catch { /* already closed */ }
    evidence.finishedAt = new Date().toISOString();
    const text = JSON.stringify(evidence, null, 2);
    if (/private_key|"key"\s*:|--private-key\s+0x/i.test(text) || [...KNOWN_KEYS].some(k => text.toLowerCase().includes(k))) throw new Error('refusing to write evidence that mentions key material');
    writeFileSync(OUT, text);
    console.log('[browser] result', evidence.result, redact(evidence.error ?? ''));
  });
