import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync } from 'node:fs';
import { chromium } from 'playwright';

const settings = Object.fromEntries(readFileSync(new URL('../../.env', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => line.includes('=') && !line.startsWith('#'))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const apiBase = 'http://127.0.0.1:9191/api/v1';
const paymentBase = 'http://127.0.0.1:9190';
const site = 'http://127.0.0.1:8080';
const output = new URL('../../target/browser-e2e/', import.meta.url);
mkdirSync(output, { recursive: true });

async function api(method, path, body, token, headers = {}) {
  const response = await fetch(apiBase + path, {
    method, headers: { 'Content-Type': 'application/json', ...headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const value = await response.json();
  if (response.status !== 200 || value.code !== 200)
    throw new Error(`${method} ${path}: HTTP ${response.status}, code ${value.code}, message ${value.message}`);
  return value.data;
}
async function partner(method, path, body) {
  const response = await fetch(paymentBase + path, {
    method, headers: { 'Content-Type': 'application/json',
      'X-Internal-Api-Key': settings.PAYMENT_INTERNAL_API_KEY },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const value = await response.json();
  if (response.status !== 200 || value.code !== 200)
    throw new Error(`Partner ${method} ${path}: HTTP ${response.status}, code ${value.code}`);
  return value.data;
}
async function until(label, read, ready, attempts = 100) {
  for (let i = 0; i < attempts; i++) {
    const value = await read();
    if (ready(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error(`${label}: timeout`);
}
async function browserLogin(browser, email, password, errors) {
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

async function main() {
  const client = await api('POST', '/auth/sign-in', {
    email: 'nguyenhuutrong11133@gmail.com', password: settings.DEMO_CLIENT_PASSWORD,
  });
  const freelancer = await api('POST', '/auth/sign-in', {
    email: 'freelancer.seed@example.com', password: settings.DEMO_FREELANCER_PASSWORD,
  });
  if (process.argv.includes('--visual-only')) {
    const errors = [];
    const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
    const orphanId = randomUUID();
    try {
      const adminView = await browserLogin(browser, 'admin.e2e@example.test',
        settings.DEMO_ADMIN_PASSWORD, errors);
      const adminPage = adminView.page;
      await adminPage.goto(`${site}/admin/partner-reconciliation`);
      await partner('POST', '/internal/partner-mock/escrows', {
        milestoneId: orphanId, contractId: randomUUID(), jobId: randomUUID(),
        clientId: randomUUID(), freelancerId: randomUUID(), grossUsd: 1,
        fundKey: `visual-mismatch-${orphanId}`,
      });
      try {
        await until('visual mismatch fixture', () => partner('GET',
          `/internal/partner-mock/escrows/${orphanId}`), value => value.status === 'FUNDED', 30);
        for (const width of [1440, 390]) {
          await adminPage.setViewportSize({ width, height: 900 });
          await adminPage.getByRole('button', { name: 'Làm mới đối soát' }).click();
          await adminPage.getByText('Cảnh báo lệch số dư').waitFor();
          await adminPage.getByText(orphanId).waitFor();
          const overflow = await adminPage.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
          assert.equal(overflow, false, `Horizontal overflow at ${width}px`);
          await adminPage.screenshot({ path: new URL(`admin-mismatch-${width}.png`, output).pathname,
            fullPage: true });
        }
      } finally {
        const orphan = await partner('GET', `/internal/partner-mock/escrows/${orphanId}`);
        if (orphan.status === 'FUNDED') await partner('POST',
          `/internal/partner-mock/escrows/${orphanId}/refund`,
          { refundKey: `visual-cleanup-${orphanId}` });
      }
      assert.deepEqual(errors, []);
      await adminView.context.close();
      console.log(JSON.stringify({ result: 'PASS', widths: [1440, 390],
        mismatchMilestoneId: orphanId, horizontalOverflow: false, pageErrors: 0 }));
    } finally {
      await browser.close();
    }
    return;
  }
  const resume = process.argv.includes('--resume');
  let job;
  if (resume) {
    const page = await api('GET', '/marketplace/jobs?page=0&size=100', undefined, client.accessToken);
    job = page.data.find(row => row.title.startsWith('Partner browser E2E ')
      && row.status === 'IN_PROGRESS' && row.contract?.paymentRail === 'PARTNER_ESCROW_MOCK');
    assert(job, 'No funded browser E2E Job to resume');
  } else {
    job = await api('POST', '/marketplace/jobs', {
      title: `Partner browser E2E ${randomUUID().slice(0, 8)}`,
      description: 'Browser proof for partner mock funding, submission and approval',
      category: 'WEB_FRONTEND', skills: ['TypeScript'], budgetUsd: 60,
      deliveryDueAt: new Date(Date.now() + 25 * 3600_000).toISOString(),
      reviewWindowHours: 72, maxRevisions: 2,
      deliverables: [{ title: 'Artifact', description: 'One verifiable artifact', required: true }],
      acceptanceCriteria: [{ description: 'Artifact URL opens', required: true }],
    }, client.accessToken);
    await api('POST', `/marketplace/jobs/${job.id}/apply`, undefined, freelancer.accessToken);
    job = await api('POST', `/marketplace/jobs/${job.id}/assignments`,
      { freelancerId: freelancer.userId }, client.accessToken);
  }
  const contract = job.contract;
  assert(contract?.id && contract?.milestoneId);

  const errors = [];
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const clientView = await browserLogin(browser, 'nguyenhuutrong11133@gmail.com',
      settings.DEMO_CLIENT_PASSWORD, errors);
    const freelancerView = await browserLogin(browser, 'freelancer.seed@example.com',
      settings.DEMO_FREELANCER_PASSWORD, errors);
    const adminView = await browserLogin(browser, 'admin.e2e@example.test',
      settings.DEMO_ADMIN_PASSWORD, errors);
    const clientPage = clientView.page;
    const freelancerPage = freelancerView.page;
    const adminPage = adminView.page;

    await clientPage.goto(`${site}/work/${job.id}`);
    if (!resume) {
      await clientPage.getByRole('button', { name: 'Chọn ký quỹ đối tác mock' }).click();
      const funding = clientPage.locator('section[aria-label="Ký quỹ đối tác mock"]');
      await funding.getByRole('button', { name: 'Gửi lệnh ký quỹ mock' }).click();
      await until('browser funding', () => api('GET', `/marketplace/jobs/${job.id}`,
        undefined, client.accessToken), value => value.status === 'IN_PROGRESS', 40);
      await clientPage.reload();
    }
    await clientPage.getByRole('heading', { name: job.title }).waitFor({ timeout: 15000 });
    await clientPage.screenshot({ path: new URL('client-funded.png', output).pathname, fullPage: true });

    await freelancerPage.goto(`${site}/work/${job.id}`);
    const composer = freelancerPage.locator('.work-composer');
    await composer.waitFor({ timeout: 15000 });
    await composer.getByRole('textbox', { name: 'Tóm tắt bàn giao' }).fill('Browser E2E artifact delivered');
    await composer.getByRole('checkbox').first().check();
    await composer.getByRole('checkbox').last().check();
    await composer.locator('input[type="url"]').first().fill('https://example.com/e2e-artifact');
    await composer.locator('input[type="url"]').last().fill('https://example.com/e2e-artifact');
    await composer.getByRole('button', { name: 'Gửi bàn giao' }).click();
    await until('browser submission', () => api('GET', `/marketplace/jobs/${job.id}`,
      undefined, client.accessToken), value => value.status === 'SUBMITTED_FOR_REVIEW');
    await freelancerPage.screenshot({ path: new URL('freelancer-submitted.png', output).pathname, fullPage: true });

    await clientPage.reload();
    await clientPage.getByRole('button', { name: 'Duyệt bàn giao' }).click();
    await clientPage.getByRole('button', { name: 'Xác nhận duyệt' }).click();
    await until('browser approved payout', () => api('GET', `/marketplace/jobs/${job.id}`,
      undefined, client.accessToken), value => value.status === 'COMPLETED');
    await clientPage.reload();
    await clientPage.locator('section[aria-label="Quyết toán hợp đồng"]').waitFor();
    await clientPage.getByText('1.8 USD', { exact: false }).first().waitFor({ timeout: 15000 });
    await clientPage.screenshot({ path: new URL('client-paid.png', output).pathname, fullPage: true });

    await adminPage.goto(`${site}/admin/partner-reconciliation`);
    const reconciliation = adminPage.locator('section[aria-label="Đối soát ký quỹ đối tác"]');
    await reconciliation.getByText('Khớp', { exact: true }).waitFor({ timeout: 15000 });
    const orphanId = randomUUID();
    await partner('POST', '/internal/partner-mock/escrows', {
      milestoneId: orphanId, contractId: randomUUID(), jobId: randomUUID(),
      clientId: randomUUID(), freelancerId: randomUUID(), grossUsd: 1,
      fundKey: `browser-mismatch-${orphanId}`,
    });
    try {
      await until('browser mismatch fixture', () => partner('GET', `/internal/partner-mock/escrows/${orphanId}`),
        value => value.status === 'FUNDED', 30);
      await reconciliation.getByRole('button', { name: 'Làm mới đối soát' }).click();
      await reconciliation.getByText('Cảnh báo lệch số dư').waitFor({ timeout: 15000 });
      await reconciliation.getByText(orphanId).waitFor({ timeout: 15000 });
      await adminPage.screenshot({ path: new URL('admin-mismatch.png', output).pathname, fullPage: true });
    } finally {
      const orphan = await partner('GET', `/internal/partner-mock/escrows/${orphanId}`);
      if (orphan.status === 'FUNDED') await partner('POST',
        `/internal/partner-mock/escrows/${orphanId}/refund`, { refundKey: `browser-cleanup-${orphanId}` });
    }
    await reconciliation.getByRole('button', { name: 'Làm mới đối soát' }).click();
    await reconciliation.getByText('Khớp', { exact: true }).waitFor({ timeout: 15000 });
    await adminPage.screenshot({ path: new URL('admin-matched.png', output).pathname, fullPage: true });
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({ result: 'PASS', jobId: job.id,
      contractId: contract.id, milestoneId: contract.milestoneId,
      browserScreens: ['client-funded', 'freelancer-submitted', 'client-paid',
        'admin-mismatch', 'admin-matched'], pageErrors: errors.length }, null, 2));
    await Promise.all([clientView.context.close(), freelancerView.context.close(), adminView.context.close()]);
  } finally {
    await browser.close();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
