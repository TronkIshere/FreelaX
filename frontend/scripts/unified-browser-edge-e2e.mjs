// Browser edge cases on UNIFIED_USDC_PAYOUT with new accounts: keyboard-only terms consent
// and application, a slow provider confirmation that must not open work, and a Client who
// rejects the escrow signature in the wallet before signing again. Local mock only.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { chromium } from 'playwright';
import { Connection, Keypair, LAMPORTS_PER_SOL } from '@solana/web3.js';
import { ed25519 } from '@noble/curves/ed25519';

const env = Object.fromEntries(readFileSync(new URL('../../.env', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => line.includes('=') && !line.startsWith('#'))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const site = 'http://127.0.0.1:8080';
const api = 'http://127.0.0.1:9191/api/v1';
const output = new URL('../../target/browser-e2e/edge-cases/', import.meta.url);
mkdirSync(output, { recursive: true });
const run = Date.now().toString(36);
// Throwaway credentials for this run only; never committed.
const password = 'E2e!' + randomUUID().slice(0, 12);
const people = {
  client: { email: `client.${run}@e2e.test`, name: `Client ${run}`, wallet: Keypair.generate() },
  freelancer: { email: `freelancer.${run}@e2e.test`, name: `Freelancer ${run}`, wallet: Keypair.generate() },
};
const errors = [];
const rejectNext = { client: false, freelancer: false };
const apiFailures = [];
const evidence = { run, accounts: {}, jobs: {} };

async function call(method, path, body, token) {
  const response = await fetch(api + path, { method, headers: { 'Content-Type': 'application/json',
    ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const json = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`${method} ${path} -> ${response.status} ${JSON.stringify(json)}`);
  return json.data;
}
async function until(label, read, accept, timeout = 180_000) {
  const end = Date.now() + timeout;
  let last;
  while (Date.now() < end) {
    try { last = await read(); if (accept(last)) return last; } catch (error) { last = String(error); }
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  throw new Error(`Timed out waiting for ${label}: ${JSON.stringify(last)?.slice(0, 600)}`);
}
const stage = (flow, kind) => flow.steps.find(step => step.kind === kind);

async function open(browser, role) {
  const keypair = people[role].wallet;
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  // Stand-in for a browser wallet extension: the page asks, the test key signs.
  await context.exposeFunction('__freelaxReject', () => { const next = rejectNext[role]; rejectNext[role] = false; return next; });
  await context.exposeFunction('__freelaxSign', base64 => Buffer.from(
    ed25519.sign(Buffer.from(base64, 'base64'), keypair.secretKey.slice(0, 32))).toString('base64'));
  await context.addInitScript(({ address }) => {
    const toBase64 = bytes => { let text = ''; for (const byte of bytes) text += String.fromCharCode(byte); return btoa(text); };
    const fromBase64 = text => Uint8Array.from(atob(text), char => char.charCodeAt(0));
    const publicKey = { toBase58: () => address };
    window.solana = {
      publicKey,
      async connect() { return { publicKey }; },
      async signMessage(message) { return { signature: fromBase64(await window.__freelaxSign(toBase64(message))) }; },
      async signTransaction(transaction) {
        if (await window.__freelaxReject()) throw new Error('User rejected the request.');
        const signer = transaction.signatures.find(entry => entry.publicKey.toBase58() === address);
        if (!signer) throw new Error('Wallet is not a required signer');
        const signature = fromBase64(await window.__freelaxSign(toBase64(transaction.serializeMessage())));
        transaction.addSignature(signer.publicKey, signature);
        return transaction;
      },
    };
  }, { address: keypair.publicKey.toBase58() });
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(`${role}: ${error.message}`));
  page.on('response', async response => {
    if (!response.url().includes('/api/v1/') || response.status() < 400) return;
    const body = await response.text().catch(() => '');
    apiFailures.push(`${role} ${response.request().method()} ${new URL(response.url()).pathname} ${response.status()} ${body.slice(0, 300)}`);
  });
  return { context, page };
}

async function register(page, role) {
  const person = people[role];
  await page.goto(`${site}/register`);
  await page.locator(`input[name="userType"][value="${role === 'client' ? 'CLIENT' : 'FREELANCER'}"]`).check();
  await page.locator('#register-name').fill(person.name);
  await page.locator('#register-email').fill(person.email);
  await page.locator('#register-password').fill(password);
  if (role === 'freelancer') {
    await page.locator('#register-tax').fill('0312' + String(Date.now()).slice(-6));
    await page.locator('#register-identity').fill('0791' + String(Date.now()).slice(-8));
    await page.locator('#register-nationality').fill('VN');
    await page.locator('#register-address').fill('1 Le Loi, District 1, Ho Chi Minh City');
    await page.locator('#register-bank').selectOption('VIETCOMBANK');
    await page.locator('#register-account').fill('99' + String(Date.now()).slice(-8));
  }
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await page.waitForURL(/\/login/, { timeout: 15000 });
  await page.locator('#signin-email').fill(person.email);
  await page.locator('#signin-password').fill(password);
  // One refresh token per user: reuse the browser's own sign-in instead of signing in again.
  const signIn = page.waitForResponse(response => response.url().endsWith('/api/v1/auth/sign-in')
    && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  person.token = (await (await signIn).json()).data.accessToken;
  await page.locator('.masthead').waitFor({ timeout: 15000 });
  person.id = (await call('GET', '/auth/me', undefined, person.token)).id;
  evidence.accounts[role] = { email: person.email, wallet: person.wallet.publicKey.toBase58() };
}

async function linkWallet(page, role) {
  const address = people[role].wallet.publicKey.toBase58();
  const panel = page.getByRole('region', { name: 'Ví Solana của tài khoản' });
  const bound = panel.locator('p', { hasText: 'Ví Solana đã đăng ký:' }).locator('code');
  await until('wallet panel loaded', () => bound.innerText(), text => text !== 'Đang đối chiếu…', 20000);
  if ((await bound.innerText()) === address) return;
  await panel.getByRole('button', { name: 'Kết nối và xác minh ví' }).click();
  await until('wallet bound', () => bound.innerText(), text => text === address, 20000);
}

async function createJob(page, kind, budget) {
  await page.goto(`${site}/work/new`);
  const form = page.getByRole('form', { name: 'Đăng công việc' });
  await form.getByLabel('Tiêu đề', { exact: true }).fill(`New-account unified ${kind} ${run}`);
  await form.getByLabel('Mô tả', { exact: true }).fill('Browser E2E from registration through payout on the local mock rail.');
  await form.locator('label', { hasText: 'Danh mục' }).locator('select').selectOption({ index: 1 });
  await form.getByLabel('Kỹ năng', { exact: true }).fill('TypeScript, React');
  await form.getByLabel('Ngân sách (USD)').fill(String(budget));
  const due = new Date(Date.now() + 3 * 24 * 3600_000 - new Date().getTimezoneOffset() * 60_000)
    .toISOString().slice(0, 16);
  await form.getByLabel('Hạn bàn giao').fill(due);
  await form.getByLabel('Tên sản phẩm 1', { exact: true }).fill('Artifact');
  await form.getByLabel('Mô tả sản phẩm 1', { exact: true }).fill('Verifiable artifact URL');
  await form.getByLabel('Điều kiện 1', { exact: true }).fill('Artifact URL opens');
  await form.locator('button.job-editor-submit').click();
  await page.waitForURL(/\/work\/[0-9a-f-]{36}$/, { timeout: 20000 });
  const jobId = page.url().split('/').pop();
  const terms = page.getByRole('region', { name: 'Điều khoản thanh toán thống nhất' });
  await terms.waitFor({ timeout: 15000 });
  const text = await terms.innerText();
  assert(text.includes('72 giờ sau bàn giao hợp lệ, không gia hạn'), 'review terms missing');
  assert(/mint [1-9A-HJ-NP-Za-km-z]{32,44}/.test(text), 'mint missing from terms');
  return { jobId, terms: text };
}

async function applyAndAssign(client, freelancer, jobId) {
  await freelancer.page.goto(`${site}/work/${jobId}`);
  const terms = freelancer.page.getByRole('region', { name: 'Điều khoản thanh toán thống nhất' });
  await terms.waitFor({ timeout: 15000 });
  const seen = await terms.innerText();
  await freelancer.page.getByLabel(/Tôi đã đọc và đồng ý điều khoản thanh toán mô phỏng local ở dưới/).check();
  await freelancer.page.getByRole('button', { name: 'Ứng tuyển', exact: true }).click();
  await freelancer.page.getByRole('button', { name: 'Đã ứng tuyển' }).waitFor({ timeout: 15000 });
  await client.page.goto(`${site}/work/${jobId}/applications`);
  await client.page.getByRole('button', { name: /Chọn Freelancer/ }).first().click();
  await client.page.getByLabel(/Tôi đã đọc và đồng ý điều khoản thanh toán mô phỏng local này/).check();
  await client.page.getByRole('button', { name: 'Xác nhận chọn' }).click();
  await until('assignment', () => call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token),
    job => job.status === 'AWAITING_PAYMENT' && job.contract?.paymentRail === 'UNIFIED_USDC_PAYOUT');
  return seen;
}

async function fund(client, freelancer, jobId) {
  // Both parties link wallets from the Job page before any money moves.
  await freelancer.page.goto(`${site}/work/${jobId}`);
  await linkWallet(freelancer.page, 'freelancer');
  await client.page.goto(`${site}/work/${jobId}`);
  await linkWallet(client.page, 'client');
  const bank = client.page.getByRole('form', { name: 'Tài khoản ngân hàng Client' });
  const order = client.page.getByRole('button', { name: 'Tạo USD order mô phỏng' });
  await order.waitFor({ timeout: 20000 });
  // The bank lookup resolves after the timeline; give it a moment before deciding.
  await bank.waitFor({ timeout: 5000 }).catch(() => {});
  if (await bank.count()) {
    await bank.getByLabel('Số tài khoản · 6–34 chữ số').fill('12345678' + run.slice(-4).replace(/\D/g, '0'));
    await bank.getByLabel('Tên chủ tài khoản').fill(people.client.name.toUpperCase());
    await bank.getByRole('button', { name: 'Lưu ngân hàng' }).click();
    await bank.waitFor({ state: 'detached', timeout: 15000 });
  }
  await client.page.getByRole('button', { name: 'Tạo USD order mô phỏng' }).click();
  await client.page.getByRole('button', { name: 'Xem và xác nhận nộp USD' }).click({ timeout: 20000 });
  await client.page.getByRole('button', { name: 'Xác nhận nộp USD mô phỏng' }).click();
  await client.page.getByText('USDC đã vào ví Client theo receipt on-ramp').waitFor({ timeout: 120000 });
  const escrow = client.page.getByRole('region', { name: 'Ký quỹ Solana' });
  const connect = escrow.getByRole('button', { name: 'Kết nối ví Solana' });
  if (await connect.count()) await connect.click();
  await escrow.getByRole('button', { name: 'Chuẩn bị giao dịch ký quỹ' }).click();
  await escrow.getByLabel('Tôi đã kiểm tra mint, số tiền và hai ví.').check();
  await escrow.getByRole('button', { name: 'Ký và gửi giao dịch' }).click();
  await until('work activated', () => call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token),
    job => job.status === 'IN_PROGRESS', 180_000);
  const job = await call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token);
  return { contractId: job.contract.id, milestoneId: job.contract.milestoneId };
}

const flowPath = ids => `/contracts/${ids.contractId}/milestones/${ids.milestoneId}/payment-flow`;

async function shot(view, name) {
  for (const width of [1440, 390]) {
    await view.page.setViewportSize({ width, height: 900 });
    const overflow = await view.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    assert.equal(overflow, false, `${name} overflow at ${width}px`);
    await view.page.screenshot({ path: new URL(`${name}-${width}.png`, output).pathname, fullPage: true });
  }
  await view.page.setViewportSize({ width: 1440, height: 900 });
}

const rpcConn = new Connection('http://127.0.0.1:9123', 'confirmed');
for (const person of Object.values(people)) {
  const signature = await rpcConn.requestAirdrop(person.wallet.publicKey, 2 * LAMPORTS_PER_SOL);
  await rpcConn.confirmTransaction(signature, 'confirmed');
}
const payment = action => execSync(`docker compose ${action} payment-backend`, { cwd: new URL('../..', import.meta.url).pathname, stdio: 'pipe' });
const result = {};
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
const views = {};
try {
  const client = views.client = await open(browser, 'client');
  const freelancer = views.freelancer = await open(browser, 'freelancer');
  await register(client.page, 'client');
  await register(freelancer.page, 'freelancer');
  const { jobId } = await createJob(client.page, 'edge', 4);

  // 1. Keyboard only: consent checkbox and Apply reachable by Tab, toggled by Space, sent by Enter.
  await freelancer.page.goto(`${site}/work/${jobId}`);
  const consent = freelancer.page.getByLabel(/Tôi đã đọc và đồng ý điều khoản thanh toán mô phỏng local ở dưới/);
  await consent.waitFor({ timeout: 15000 });
  let reached = false;
  for (let i = 0; i < 80 && !reached; i++) {
    await freelancer.page.keyboard.press('Tab');
    reached = await consent.evaluate(element => element === document.activeElement);
  }
  assert(reached, 'terms checkbox is not reachable with Tab');
  const outline = await consent.evaluate(element => getComputedStyle(element).outlineStyle + ' ' + getComputedStyle(element).outlineWidth);
  await freelancer.page.keyboard.press('Space');
  assert(await consent.isChecked(), 'Space did not tick the terms checkbox');
  const apply = freelancer.page.getByRole('button', { name: 'Ứng tuyển', exact: true });
  let onApply = false;
  for (let i = 0; i < 20 && !onApply; i++) {
    await freelancer.page.keyboard.press('Tab');
    onApply = await apply.evaluate(element => element === document.activeElement);
  }
  assert(onApply, 'Apply button is not reachable with Tab after the checkbox');
  await freelancer.page.keyboard.press('Enter');
  await freelancer.page.getByRole('button', { name: 'Đã ứng tuyển' }).waitFor({ timeout: 15000 });
  result.keyboard = { consentFocusOutline: outline, appliedWithKeyboard: true };
  await client.page.goto(`${site}/work/${jobId}/applications`);
  await client.page.getByRole('button', { name: /Chọn Freelancer/ }).first().click();
  await client.page.getByLabel(/Tôi đã đọc và đồng ý điều khoản thanh toán mô phỏng local này/).check();
  await client.page.getByRole('button', { name: 'Xác nhận chọn' }).click();
  await until('assignment', () => call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token),
    job => job.status === 'AWAITING_PAYMENT');
  const job = await call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token);
  const flowPath = `/contracts/${job.contract.id}/milestones/${job.contract.milestoneId}/payment-flow`;

  // 2. Slow provider: the USD confirmation is late; the UI keeps work closed and says so.
  await freelancer.page.goto(`${site}/work/${jobId}`);
  await linkWallet(freelancer.page, 'freelancer');
  await client.page.goto(`${site}/work/${jobId}`);
  await linkWallet(client.page, 'client');
  const bank = client.page.getByRole('form', { name: 'Tài khoản ngân hàng Client' });
  await client.page.getByRole('button', { name: 'Tạo USD order mô phỏng' }).waitFor({ timeout: 20000 });
  await bank.waitFor({ timeout: 5000 }).catch(() => {});
  if (await bank.count()) {
    await bank.getByLabel('Số tài khoản · 6–34 chữ số').fill('5566778899');
    await bank.getByLabel('Tên chủ tài khoản').fill(people.client.name.toUpperCase());
    await bank.getByRole('button', { name: 'Lưu ngân hàng' }).click();
    await bank.waitFor({ state: 'detached', timeout: 15000 });
  }
  await client.page.getByRole('button', { name: 'Tạo USD order mô phỏng' }).click();
  await client.page.getByRole('button', { name: 'Xem và xác nhận nộp USD' }).click({ timeout: 20000 });
  payment('stop');
  try {
    await client.page.getByRole('button', { name: 'Xác nhận nộp USD mô phỏng' }).click();
    await client.page.getByText('Chưa xác nhận được lệnh nộp USD').or(client.page.getByText('USD_ORDER: UNKNOWN'))
      .first().waitFor({ timeout: 20000 });
    await new Promise(resolve => setTimeout(resolve, 10_000));
    const waiting = await call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token);
    assert.equal(waiting.status, 'AWAITING_PAYMENT', 'work opened before USD was confirmed');
    assert.equal(await client.page.getByRole('region', { name: 'Ký quỹ Solana' }).count(), 0, 'escrow offered before USDC');
    await client.page.screenshot({ path: new URL('slow-provider-client.png', output).pathname, fullPage: true });
  } finally { payment('start'); }
  // The order never reached the provider, so the Client submits it again once it is back.
  await until('provider back', () => call('GET', flowPath, undefined, people.client.token)
    .then(flow => stage(flow, 'USD_ORDER').status), status => ['AWAITING_CLIENT', 'PENDING', 'CONFIRMED'].includes(status), 120_000);
  await client.page.reload();
  const again = client.page.getByRole('button', { name: 'Xem và xác nhận nộp USD' });
  const usdcArrived = client.page.getByText('USDC đã vào ví Client theo receipt on-ramp');
  await again.or(usdcArrived).first().waitFor({ timeout: 30000 });
  if (await again.isVisible()) {
    await again.click();
    await client.page.getByRole('button', { name: 'Xác nhận nộp USD mô phỏng' }).click();
  }
  await client.page.getByText('USDC đã vào ví Client theo receipt on-ramp').waitFor({ timeout: 180000 });
  result.slowProvider = { workStayedClosed: true, recovered: true };

  // 3. Wallet rejection: nothing is sent; signing again funds the vault once.
  const escrow = client.page.getByRole('region', { name: 'Ký quỹ Solana' });
  await escrow.getByRole('button', { name: 'Chuẩn bị giao dịch ký quỹ' }).click();
  await escrow.getByLabel('Tôi đã kiểm tra mint, số tiền và hai ví.').check();
  rejectNext.client = true;
  await escrow.getByRole('button', { name: 'Ký và gửi giao dịch' }).click();
  await escrow.getByRole('alert').first().waitFor({ timeout: 15000 });
  const rejectionMessage = await escrow.getByRole('alert').first().innerText();
  await new Promise(resolve => setTimeout(resolve, 3000));
  const afterReject = await call('GET', `/contracts/${job.contract.id}/milestones/${job.contract.milestoneId}/escrow`,
    undefined, people.client.token).catch(() => null);
  assert(!afterReject?.fundSignature, 'a funding transaction was sent after the wallet rejected');
  await client.page.screenshot({ path: new URL('wallet-rejected-client.png', output).pathname, fullPage: true });
  const prepare = escrow.getByRole('button', { name: 'Chuẩn bị giao dịch ký quỹ' });
  if (await prepare.isVisible().catch(() => false)) {
    await prepare.click();
    await escrow.getByLabel('Tôi đã kiểm tra mint, số tiền và hai ví.').check();
  }
  await escrow.getByRole('button', { name: 'Ký và gửi giao dịch' }).click();
  await until('work activated', () => call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token),
    value => value.status === 'IN_PROGRESS', 180_000);
  result.walletRejection = { message: rejectionMessage.slice(0, 160), noTransactionSent: true, fundedAfterRetry: true };
  assert.deepEqual(errors, [], 'browser JavaScript errors');
  writeFileSync(new URL('evidence.json', output), JSON.stringify({ run, jobId, ...result }, null, 2));
  console.log(JSON.stringify({ result: 'PASS', run, jobId, ...result }, null, 2));
} catch (error) {
  console.error('API failures:\n' + apiFailures.join('\n'));
  for (const [role, view] of Object.entries(views))
    await view.page.screenshot({ path: new URL(`failure-${role}.png`, output).pathname, fullPage: true }).catch(() => {});
  try { payment('start'); } catch { /* best effort */ }
  throw error;
} finally {
  await browser.close();
}
