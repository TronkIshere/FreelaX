// Captures the screens used by docs/business/BROWSER_DEMO_GUIDE.md on the local stack:
// the Client decision panel with the auto-approval countdown, the dispute opened from it,
// the Admin dispute desk, the Freelancer tax certificates, and the Admin money summary.
// Usage: node scripts/demo-guide-screenshots.mjs <reviewJobId> <disputeJobId>
// Seed wallets are read from the git-ignored .env; local mock only.
import assert from 'node:assert/strict';
import { mkdirSync, readFileSync } from 'node:fs';
import { chromium } from 'playwright';
import { Keypair } from '@solana/web3.js';
import { ed25519 } from '@noble/curves/ed25519';
import bs58 from 'bs58';

const [reviewJobId, disputeJobId] = process.argv.slice(2);
assert(reviewJobId && disputeJobId, 'usage: demo-guide-screenshots.mjs <reviewJobId> <disputeJobId>');
const env = Object.fromEntries(readFileSync(new URL('../../.env', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => line.includes('=') && !line.startsWith('#'))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const keys = (env.SOLANA_LOCAL_PRIVATE_KEYS || '').split(';').filter(Boolean).map(v => Keypair.fromSecretKey(bs58.decode(v)));
const keyFor = address => keys.find(k => k.publicKey.toBase58() === address);
const site = 'http://127.0.0.1:8080';
const out = new URL('../../docs/business/images/', import.meta.url);
mkdirSync(out, { recursive: true });
const errors = [];

async function session(browser, email, password, walletAddress) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const keypair = walletAddress ? keyFor(walletAddress) : null;
  if (keypair) {
    await context.exposeFunction('__freelaxSign', b64 => Buffer.from(
      ed25519.sign(Buffer.from(b64, 'base64'), keypair.secretKey.slice(0, 32))).toString('base64'));
    await context.addInitScript(({ address }) => {
      const enc = bytes => { let t = ''; for (const b of bytes) t += String.fromCharCode(b); return btoa(t); };
      const dec = t => Uint8Array.from(atob(t), c => c.charCodeAt(0));
      const publicKey = { toBase58: () => address };
      window.solana = { publicKey, async connect() { return { publicKey }; },
        async signMessage(m) { return { signature: dec(await window.__freelaxSign(enc(m))) }; },
        async signTransaction(tx) {
          const signer = tx.signatures.find(s => s.publicKey.toBase58() === address);
          tx.addSignature(signer.publicKey, dec(await window.__freelaxSign(enc(tx.serializeMessage()))));
          return tx;
        } };
    }, { address: walletAddress });
  }
  const page = await context.newPage();
  page.on('pageerror', e => errors.push(`${email}: ${e.message}`));
  await page.goto(`${site}/login`);
  await page.locator('#signin-email').fill(email);
  await page.locator('#signin-password').fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.locator('.masthead').waitFor({ timeout: 15000 });
  return page;
}

const shot = (locator, name) => locator.screenshot({ path: new URL(name, out).pathname });

const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
try {
  const client = await session(browser, 'nguyenhuutrong11133@gmail.com', env.DEMO_CLIENT_PASSWORD,
    env.SOLANA_CUSTODIAL_CLIENT_PUBLIC_KEY);

  // 1. Client decision panel: three choices plus the countdown to auto-approval.
  await client.goto(`${site}/work/${reviewJobId}`);
  const latest = client.getByRole('region', { name: 'Bản bàn giao mới nhất' });
  await latest.getByText(/Còn \d+ phút đến hạn review/).waitFor({ timeout: 30000 });
  await client.getByRole('button', { name: 'Duyệt bàn giao' }).waitFor({ timeout: 30000 });
  await shot(latest, '01-ban-giao-dem-nguoc.png');
  await shot(client.locator('section.review-action'), '02-nut-lua-chon.png');

  // 2. Client opens a dispute on the second Job (wallet-signed on-chain).
  await client.goto(`${site}/work/${disputeJobId}`);
  const openDispute = client.getByRole('button', { name: 'Mở tranh chấp' });
  const existing = client.getByText('Lý do: THIEU_TINH_NANG');
  await openDispute.or(existing).first().waitFor({ timeout: 30000 });
  if (await openDispute.isVisible()) {
  await openDispute.click();
  const form = client.getByRole('form', { name: 'Xác nhận quyết định' });
  await form.getByLabel('Mã lý do').fill('THIEU_TINH_NANG');
  await form.getByLabel('Mô tả tranh chấp').fill('Bản bàn giao thiếu trang thanh toán đã thỏa thuận.');
  await shot(form, '03-mo-tranh-chap.png');
  await form.getByRole('button', { name: 'Xác nhận mở tranh chấp' }).click();
  await client.getByText(/Đang chờ Admin|Admin|tranh chấp/i).first().waitFor({ timeout: 60000 });
  await client.waitForTimeout(8000);
  }
  await client.reload();
  const disputePanel = client.locator('section').filter({ hasText: 'Lý do: THIEU_TINH_NANG' }).last();
  await disputePanel.waitFor({ timeout: 30000 });
  await shot(disputePanel, '04-tranh-chap-cho-admin.png');

  // 3. Admin dispute desk: claim, then the two outcome buttons (once per dispute; set
  // SKIP_ADMIN_DISPUTE=1 when it was already claimed and left the default queue).
  const admin = await session(browser, 'admin.e2e@example.test', env.DEMO_ADMIN_PASSWORD);
  if (!process.env.SKIP_ADMIN_DISPUTE) {
  await admin.goto(`${site}/admin/disputes`);
  const open = admin.getByRole('link', { name: 'THIEU_TINH_NANG' }).first();
  await open.waitFor({ timeout: 60000 });
  await shot(admin.locator('main'), '05-admin-hang-doi.png');
  await open.click({ timeout: 15000 });
  const claim = admin.getByRole('button', { name: 'Tiếp nhận hồ sơ' });
  const decide = admin.getByRole('button', { name: 'Release cho Freelancer' });
  await claim.or(decide).first().waitFor({ timeout: 30000 });
  if (await claim.isVisible()) await claim.click();
  await admin.getByRole('button', { name: 'Release cho Freelancer' }).waitFor({ timeout: 30000 });
  await shot(admin.locator('main'), '06-admin-quyet-dinh.png');
  }

  // 4. Freelancer tax certificates (issued after the VND payout of earlier Jobs).
  const freelancer = await session(browser, 'freelancer.seed@example.com', env.DEMO_FREELANCER_PASSWORD);
  await freelancer.goto(`${site}/finance/tax-records`);
  await freelancer.getByText('Xem chi tiết').first().waitFor({ timeout: 30000 });
  await shot(freelancer.locator('main'), '07-chung-tu-danh-sach.png');
  await freelancer.getByText('Xem chi tiết').first().click();
  await freelancer.getByRole('button', { name: /PDF/ }).or(freelancer.getByRole('link', { name: /PDF/ })).first()
    .waitFor({ timeout: 30000 });
  await shot(freelancer.locator('main'), '08-chung-tu-chi-tiet.png');

  // 5. Admin: where the money is, per currency.
  await admin.goto(`${site}/admin/unified-reconciliation`);
  const totals = admin.getByRole('region', { name: 'Tổng hợp theo đồng tiền' });
  await totals.waitFor({ timeout: 30000 });
  await shot(totals, '09-admin-tien-dang-o-dau.png');
  assert.deepEqual(errors, [], 'browser JavaScript errors');
  console.log('screenshots written to docs/business/images');
} finally {
  await browser.close();
}
