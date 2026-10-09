import assert from 'node:assert/strict';
import { mkdirSync, readFileSync } from 'node:fs';
import { chromium } from 'playwright';

const env = Object.fromEntries(readFileSync(new URL('../../.env', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => line.includes('=') && !line.startsWith('#'))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const jobs = JSON.parse(readFileSync(new URL('../../solana-stablecoin-payout/target/unified-e2e-jobs.json',
  import.meta.url), 'utf8'));
const release = jobs.find(row => row.kind === 'release');
const refund = jobs.find(row => row.kind === 'refund');
const site = 'http://127.0.0.1:8080';
const output = new URL('../../target/browser-e2e/', import.meta.url);
mkdirSync(output, { recursive: true });

async function login(browser, email, password, errors) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(`${email}: ${error.message}`));
  await page.goto(`${site}/login`);
  await page.locator('#signin-email').fill(email);
  await page.locator('#signin-password').fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.locator('.masthead').waitFor({ timeout: 15000 });
  return { context, page };
}

const errors = [];
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
try {
  const client = await login(browser, 'nguyenhuutrong11133@gmail.com',
    env.DEMO_CLIENT_PASSWORD, errors);
  const freelancer = await login(browser, 'freelancer.seed@example.com',
    env.DEMO_FREELANCER_PASSWORD, errors);
  const admin = await login(browser, 'admin.e2e@example.test',
    env.DEMO_ADMIN_PASSWORD, errors);
  const visibleTerms = [];
  for (const [role, view] of [['client', client], ['freelancer', freelancer]]) {
    await view.page.goto(`${site}/work/${release.jobId}`);
    const terms = view.page.getByRole('region', { name: 'Điều khoản thanh toán thống nhất' });
    await terms.waitFor({ timeout: 15000 });
    const content = await terms.innerText();
    assert(content.includes('15.00') && content.includes('15.000000')
      && content.includes('0.450000') && content.includes('363.750')
      && content.includes('phí FreelaX bằng 0'), `${role} payment terms differ`);
    visibleTerms.push(content);
    for (const width of [1440, 390]) {
      await view.page.setViewportSize({ width, height: 900 });
      const overflow = await view.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
      assert.equal(overflow, false, `${role} terms overflow at ${width}px`);
      await view.page.screenshot({ path: new URL(`unified-terms-${role}-${width}.png`, output).pathname,
        fullPage: true });
    }
  }
  assert.equal(visibleTerms[0], visibleTerms[1], 'Client and Freelancer see different terms');
  for (const [role, view, row, terminal] of [
    ['client-release', client, release, 'VND chi cho Freelancer'],
    ['freelancer-release', freelancer, release, 'VND chi cho Freelancer'],
    ['client-refund', client, refund, 'USD hoàn về Client'],
  ]) {
    await view.page.goto(`${site}/finance?jobId=${row.jobId}`);
    const flow = view.page.getByRole('region', { name: 'Luồng tài chính thống nhất' });
    await flow.getByText('Mã luồng:', { exact: false }).waitFor({ timeout: 15000 });
    await flow.getByText(terminal, { exact: false }).waitFor();
    const text = await flow.innerText();
    assert(text.includes('Đã xác nhận') && text.includes('Mô phỏng'));
    for (const width of [1440, 390]) {
      await view.page.setViewportSize({ width, height: 900 });
      const overflow = await view.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
      assert.equal(overflow, false, `${role} overflow at ${width}px`);
      await view.page.screenshot({ path: new URL(`unified-${role}-${width}.png`, output).pathname,
        fullPage: true });
    }
  }
  await admin.page.goto(`${site}/admin/unified-reconciliation`);
  const releaseCase = admin.page.getByRole('region', { name: /Đối soát flow/ }).filter({
    hasText: release.jobId });
  await releaseCase.waitFor({ timeout: 15000 });
  await releaseCase.getByText('RECIPIENT_BY_ESCROW_INVARIANT', { exact: false }).waitFor();
  for (const width of [1440, 390]) {
    await admin.page.setViewportSize({ width, height: 900 });
    const overflow = await admin.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    assert.equal(overflow, false, `admin overflow at ${width}px`);
    await admin.page.screenshot({ path: new URL(`unified-admin-${width}.png`, output).pathname,
      fullPage: true });
  }
  assert.deepEqual(errors, []);
  console.log(JSON.stringify({ result: 'PASS', screens: 12, widths: [1440, 390],
    pageErrors: errors.length, releaseJobId: release.jobId, refundJobId: refund.jobId }));
} finally {
  await browser.close();
}
