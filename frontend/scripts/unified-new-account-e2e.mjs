// Browser E2E for UNIFIED_USDC_PAYOUT with brand-new accounts: registration, automatic local wallet,
// Job, application, assignment, USD order, on-ramp, escrow, delivery, release, VND payout after
// fee and 10% tax, tax certificate, and a second Job refunded by mutual signature then USD.
// Local validator and mock providers only.
// Requires PAYMENT_FLOW_CUTOVER_ENABLED=true on Marketplace while the Jobs are created.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { chromium } from 'playwright';

const env = Object.fromEntries(readFileSync(new URL('../../.env', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => line.includes('=') && !line.startsWith('#'))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const site = 'http://127.0.0.1:8080';
const api = 'http://127.0.0.1:9191/api/v1';
const output = new URL('../../target/browser-e2e/new-account/', import.meta.url);
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
const flowPath = ids => `/contracts/${ids.contractId}/milestones/${ids.milestoneId}/payment-flow`;
// terms-v2: fee 3% of the Job price, then 10% tax withheld from the VND value after fee.
function expectedPayout(usd) {
  const fee = Math.round(usd * 3) / 100;
  const taxable = Math.round((usd - fee) * 25000);
  const tax = Math.round(taxable * 0.1);
  return { fee, taxable, tax, payout: taxable - tax };
}

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
  // The local auto wallet is issued on first use; the page must never need a wallet extension.
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
  const net = expectedPayout(budget);
  assert(text.includes(net.payout.toLocaleString('vi-VN') + ' VND'), `net payout ${net.payout} missing from terms`);
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
  await linkWallet(freelancer.page);
  await client.page.goto(`${site}/work/${jobId}`);
  await linkWallet(client.page);
  const bank = client.page.getByRole('form', { name: 'Tài khoản ngân hàng Client' });
  const order = client.page.getByRole('button', { name: 'Bắt đầu thanh toán' });
  await order.waitFor({ timeout: 20000 });
  // The bank lookup resolves after the timeline; give it a moment before deciding.
  await bank.waitFor({ timeout: 5000 }).catch(() => {});
  if (await bank.count()) {
    await bank.getByLabel('Số tài khoản · 6–34 chữ số').fill('12345678' + run.slice(-4).replace(/\D/g, '0'));
    await bank.getByLabel('Tên chủ tài khoản').fill(people.client.name.toUpperCase());
    await bank.getByRole('button', { name: 'Lưu ngân hàng' }).click();
    await bank.waitFor({ state: 'detached', timeout: 15000 });
  }
  await client.page.getByRole('button', { name: 'Bắt đầu thanh toán' }).click();
  await client.page.getByRole('button', { name: 'Xem và xác nhận thanh toán' }).click({ timeout: 20000 });
  await client.page.getByRole('group', { name: 'Xác nhận thanh toán' })
    .getByRole('button', { name: 'Xác nhận thanh toán' }).click();
  const escrow = client.page.getByRole('region', { name: 'Giữ tiền cho công việc' });
  await escrow.getByRole('button', { name: 'Chuẩn bị giữ tiền' }).waitFor({ timeout: 120000 });
  await until('USDC in client wallet', async () => {
    const job = await call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token);
    return call('GET', flowPath({ contractId: job.contract.id, milestoneId: job.contract.milestoneId }),
      undefined, people.client.token);
  }, flow => stage(flow, 'CLIENT_USDC').status === 'CONFIRMED');
  await escrow.getByRole('button', { name: 'Chuẩn bị giữ tiền' }).click();
  await escrow.getByLabel('Tôi đồng ý giữ khoản tiền này theo điều khoản công việc.').check();
  await escrow.getByRole('button', { name: 'Xác nhận giữ tiền' }).click();
  await until('work activated', () => call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token),
    job => job.status === 'IN_PROGRESS', 180_000);
  const job = await call('GET', `/marketplace/jobs/${jobId}`, undefined, people.client.token);
  return { contractId: job.contract.id, milestoneId: job.contract.milestoneId };
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

const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
const views = {};
try {
  const client = views.client = await open(browser, 'client');
  const freelancer = views.freelancer = await open(browser, 'freelancer');
  await register(client.page, 'client');
  await register(freelancer.page, 'freelancer');

  // Release branch.
  const release = await createJob(client.page, 'release', 12);
  const freelancerTerms = await applyAndAssign(client, freelancer, release.jobId);
  assert.equal(freelancerTerms, release.terms, 'Client and Freelancer saw different terms');
  const releaseIds = await fund(client, freelancer, release.jobId);
  await freelancer.page.goto(`${site}/work/${release.jobId}`);
  const composer = freelancer.page.locator('.work-composer');
  await composer.locator('textarea').first().fill('Delivered through the browser E2E.');
  for (const row of await composer.locator('.evidence-input').all()) {
    await row.locator('input[type="checkbox"]').check();
    await row.locator('input[type="url"]').fill('https://example.com/new-account-proof');
  }
  await composer.getByRole('button', { name: 'Gửi bàn giao' }).click();
  await until('submission on chain', () => call('GET', `/contracts/${releaseIds.contractId}/submissions`,
    undefined, people.client.token), rows => rows.length > 0);
  await client.page.goto(`${site}/work/${release.jobId}`);
  await client.page.getByRole('button', { name: 'Duyệt bàn giao' }).click({ timeout: 30000 });
  await client.page.getByRole('button', { name: 'Xác nhận duyệt' }).click();
  await until('USDC released', () => call('GET', flowPath(releaseIds), undefined, people.client.token),
    flow => stage(flow, 'USDC_RELEASE').status === 'CONFIRMED');
  await freelancer.page.goto(`${site}/finance?jobId=${release.jobId}`);
  await freelancer.page.getByRole('button', { name: 'Xem số tiền sẽ nhận' }).click({ timeout: 30000 });
  await freelancer.page.getByRole('button', { name: 'Xác nhận nhận tiền', exact: true }).click();
  const paid = await until('VND payout and fee', () => call('GET', flowPath(releaseIds), undefined,
    people.freelancer.token), flow => stage(flow, 'VND_PAYOUT').status === 'CONFIRMED'
      && stage(flow, 'PLATFORM_FEE').status === 'CONFIRMED');
  const net = expectedPayout(12);
  assert.equal(Number(stage(paid, 'VND_PAYOUT').amount), net.payout);
  assert.equal(Number(stage(paid, 'PLATFORM_FEE').amount), net.fee);
  const certificate = await until('tax certificate', () => call('GET', `/marketplace/tax-records/jobs/${release.jobId}`,
    undefined, people.freelancer.token), record => record?.status === 'ACCEPTED', 240_000);
  assert.equal(Number(certificate.taxableIncomeVnd), net.taxable);
  assert.equal(Number(certificate.taxWithheldVnd), net.tax);
  assert.equal(certificate.rateSource, 'LOCKED_PAYOUT_QUOTE');
  await freelancer.page.reload();
  await freelancer.page.getByRole('link', { name: 'Xem chứng từ thuế' }).first().waitFor({ timeout: 15000 });
  await shot(freelancer, 'freelancer-release-finance');
  evidence.jobs.release = { jobId: release.jobId, paymentFlowId: paid.paymentFlowId,
    steps: Object.fromEntries(paid.steps.map(step => [step.kind, step.status])),
    payoutVnd: net.payout, certificate: { taxableIncomeVnd: net.taxable, taxWithheldVnd: net.tax } };

  // Refund branch: second Job, cancelled by both signatures before release.
  const refund = await createJob(client.page, 'refund', 8);
  await applyAndAssign(client, freelancer, refund.jobId);
  const refundIds = await fund(client, freelancer, refund.jobId);
  await client.page.goto(`${site}/work/${refund.jobId}`);
  const clientRefund = client.page.getByRole('region', { name: 'Hoàn tiền theo thỏa thuận' });
  await clientRefund.getByLabel('Tôi đồng ý hoàn toàn bộ số tiền cho khách hàng.').check({ timeout: 30000 });
  await clientRefund.getByRole('button', { name: 'Xác nhận hoàn tiền' }).click();
  await clientRefund.getByText('Khách hàng đã xác nhận.', { exact: false }).waitFor({ timeout: 20000 });
  await freelancer.page.goto(`${site}/work/${refund.jobId}`);
  const freelancerRefund = freelancer.page.getByRole('region', { name: 'Hoàn tiền theo thỏa thuận' });
  await freelancerRefund.getByLabel('Tôi đồng ý hoàn toàn bộ số tiền cho khách hàng.').check({ timeout: 30000 });
  await freelancerRefund.getByRole('button', { name: 'Đồng ý hoàn tiền' }).click({ timeout: 30000 });
  await until('USDC refunded', () => call('GET', flowPath(refundIds), undefined, people.client.token),
    flow => stage(flow, 'USDC_REFUND').status === 'CONFIRMED');
  await client.page.goto(`${site}/finance?jobId=${refund.jobId}`);
  await client.page.getByRole('button', { name: 'Xem khoản hoàn tiền' }).click({ timeout: 30000 });
  await client.page.getByRole('group', { name: 'Xác nhận nhận tiền' })
    .getByRole('button', { name: 'Xác nhận hoàn tiền' }).click();
  const refunded = await until('USD refund', () => call('GET', flowPath(refundIds), undefined,
    people.client.token), flow => stage(flow, 'USD_REFUND').status === 'CONFIRMED');
  assert.equal(Number(stage(refunded, 'USD_REFUND').amount), 8);
  assert.notEqual(stage(refunded, 'VND_PAYOUT').status, 'CONFIRMED');
  assert.notEqual(stage(refunded, 'PLATFORM_FEE').status, 'CONFIRMED');
  await client.page.reload();
  await shot(client, 'client-refund-finance');
  evidence.jobs.refund = { jobId: refund.jobId, paymentFlowId: refunded.paymentFlowId,
    steps: Object.fromEntries(refunded.steps.map(step => [step.kind, step.status])) };

  // Admin sees all four boundaries matched for both flows.
  const admin = await call('POST', '/auth/sign-in', { email: 'admin.e2e@example.test',
    password: env.DEMO_ADMIN_PASSWORD });
  const cases = await until('reconciliation', () => call('GET', '/admin/payment-flows/reconciliation',
    undefined, admin.accessToken), rows => [paid.paymentFlowId, refunded.paymentFlowId].every(id => {
      const row = rows.find(item => item.paymentFlowId === id);
      return row && ['usdToClientUsdc', 'clientUsdcToVault', 'vaultToRecipient', 'withdrawalToFiat']
        .every(key => row[key].status === 'MATCHED');
    }));
  evidence.reconciliation = Object.fromEntries([paid.paymentFlowId, refunded.paymentFlowId].map(id => {
    const row = cases.find(item => item.paymentFlowId === id);
    return [id, ['usdToClientUsdc', 'clientUsdcToVault', 'vaultToRecipient', 'withdrawalToFiat'].map(key => row[key].code)];
  }));
  assert.deepEqual(errors, [], 'browser JavaScript errors');
  writeFileSync(new URL('evidence.json', output), JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify({ result: 'PASS', ...evidence }, null, 2));
} catch (error) {
  console.error('API failures:\n' + apiFailures.join('\n'));
  for (const [role, view] of Object.entries(views))
    await view.page.screenshot({ path: new URL(`failure-${role}.png`, output).pathname, fullPage: true }).catch(() => {});
  throw error;
} finally {
  await browser.close();
}
