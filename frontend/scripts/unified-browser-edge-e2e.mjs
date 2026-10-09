// Browser edge cases on UNIFIED_USDC_PAYOUT with new accounts: keyboard-only terms consent
// and application, a slow provider confirmation that must not open work, and escrow funding
// with the automatic local wallet (no wallet extension, funded exactly once). Local mock only.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { chromium } from 'playwright';

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
  client: { email: `client.${run}@e2e.test`, name: `Client ${run}` },
  freelancer: { email: `freelancer.${run}@e2e.test`, name: `Freelancer ${run}` },
};
const errors = [];
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
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
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
  assert.equal(await page.evaluate(() => Boolean(window.solana || window.phantom)), false);
  person.wallet = (await call('POST', '/solana/wallet-link/auto', undefined, person.token)).walletAddress;
  evidence.accounts[role] = { email: person.email, wallet: person.wallet };
}

async function linkWallet(page) {
  const panel = page.getByRole('region', { name: 'Kết nối ví' });
  await until('auto wallet connected', () => panel.getByRole('status').innerText(),
    text => text === 'Đã kết nối ví', 20000);
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
  assert(text.includes('72 giờ sau bàn giao hợp lệ'), 'review terms missing');
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

async function shot(view, name) {
  for (const width of [1440, 390]) {
    await view.page.setViewportSize({ width, height: 900 });
    const overflow = await view.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    assert.equal(overflow, false, `${name} overflow at ${width}px`);
    await view.page.screenshot({ path: new URL(`${name}-${width}.png`, output).pathname, fullPage: true });
  }
  await view.page.setViewportSize({ width: 1440, height: 900 });
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
  await linkWallet(freelancer.page);
  await client.page.goto(`${site}/work/${jobId}`);
  await linkWallet(client.page);
  const bank = client.page.getByRole('form', { name: 'Tài khoản ngân hàng Client' });
  await client.page.getByRole('button', { name: 'Bắt đầu thanh toán' }).waitFor({ timeout: 20000 });
  await bank.waitFor({ timeout: 5000 }).catch(() => {});
  if (await bank.count()) {
    await bank.getByLabel('Số tài khoản · 6–34 chữ số').fill('5566778899');
    await bank.getByLabel('Tên chủ tài khoản').fill(people.client.name.toUpperCase());
    await bank.getByRole('button', { name: 'Lưu ngân hàng' }).click();
    await bank.waitFor({ state: 'detached', timeout: 15000 });
  }
  const confirmOrder = () => client.page.getByRole('group', { name: 'Xác nhận thanh toán' })
    .getByRole('button', { name: 'Xác nhận thanh toán' }).click();
  await client.page.getByRole('button', { name: 'Bắt đầu thanh toán' }).click();
  await client.page.getByRole('button', { name: 'Xem và xác nhận thanh toán' }).click({ timeout: 20000 });
  payment('stop');
  try {
    await confirmOrder();
    await client.page.getByText('Chưa xác nhận được thanh toán', { exact: false })
      .or(client.page.getByText('Đang chờ xác nhận thanh toán.'))
      .or(client.page.locator('.payment-journey-status', { hasText: 'Đang kiểm tra' }))
      .first().waitFor({ timeout: 20000 });
    await new Promise(resolve => setTimeout(resolve, 10_000));
    const waiting = await call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token);
    assert.equal(waiting.status, 'AWAITING_PAYMENT', 'work opened before USD was confirmed');
    assert.equal(await client.page.getByRole('region', { name: 'Giữ tiền cho công việc' }).count(), 0, 'escrow offered before USDC');
    await client.page.screenshot({ path: new URL('slow-provider-client.png', output).pathname, fullPage: true });
  } finally { payment('start'); }
  // The order never reached the provider, so the Client submits it again once it is back.
  await until('provider back', () => call('GET', flowPath, undefined, people.client.token)
    .then(flow => stage(flow, 'USD_ORDER').status), status => ['AWAITING_CLIENT', 'PENDING', 'CONFIRMED'].includes(status), 120_000);
  await client.page.reload();
  const again = client.page.getByRole('button', { name: 'Xem và xác nhận thanh toán' });
  const usdcArrived = client.page.getByText('Tiền đã sẵn sàng. Hãy xác nhận giữ tiền để bắt đầu công việc.');
  const processing = client.page.getByText('Đang chờ xác nhận thanh toán.')
    .or(client.page.getByText('Đang cập nhật khoản tiền để tiếp tục công việc.'));
  await again.or(usdcArrived).or(processing).first().waitFor({ timeout: 30000 });
  if (await again.isVisible()) {
    await again.click();
    await confirmOrder();
  }
  await until('USDC in client wallet', () => call('GET', flowPath, undefined, people.client.token),
    flow => stage(flow, 'CLIENT_USDC').status === 'CONFIRMED', 180_000);
  await client.page.reload();
  await usdcArrived.waitFor({ timeout: 30000 });
  result.slowProvider = { workStayedClosed: true, recovered: true };

  // 3. Automatic local wallet: no extension prompt; preparing twice still funds the vault once.
  const escrow = client.page.getByRole('region', { name: 'Giữ tiền cho công việc' });
  await escrow.getByRole('button', { name: 'Chuẩn bị giữ tiền' }).click();
  await escrow.getByLabel('Tôi đồng ý giữ khoản tiền này theo điều khoản công việc.').check();
  await client.page.reload();
  await escrow.getByRole('button', { name: 'Chuẩn bị giữ tiền' }).click({ timeout: 30000 });
  await escrow.getByLabel('Tôi đồng ý giữ khoản tiền này theo điều khoản công việc.').check();
  await escrow.getByRole('button', { name: 'Xác nhận giữ tiền' }).click();
  await until('work activated', () => call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token),
    value => value.status === 'IN_PROGRESS', 180_000);
  const funded = await call('GET', flowPath, undefined, people.client.token);
  assert.equal(stage(funded, 'ESCROW').status, 'CONFIRMED');
  assert.equal(Number(stage(funded, 'ESCROW').amount), 4);
  // Once funded the Job leaves AWAITING_PAYMENT and the funding panel is replaced by the work view.
  await client.page.reload();
  await escrow.waitFor({ state: 'detached', timeout: 30000 });
  await client.page.screenshot({ path: new URL('auto-wallet-funded-client.png', output).pathname, fullPage: true });
  result.autoWallet = { clientWallet: people.client.wallet, abandonedBuildIgnored: true, fundedOnce: true };
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
