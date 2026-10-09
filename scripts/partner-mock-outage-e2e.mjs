import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const stateFile = new URL('../target/partner-mock-outage-state.json', import.meta.url);
const settings = Object.fromEntries(readFileSync(new URL('.env', root), 'utf8')
  .split(/\r?\n/).filter(line => line.includes('=') && !line.startsWith('#'))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const marketplace = 'http://127.0.0.1:9191/api/v1';
const payment = 'http://127.0.0.1:9190';

async function request(base, method, path, body, headers = {}) {
  const response = await fetch(base + path, {
    method, headers: { 'Content-Type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const value = await response.json();
  if (response.status !== 200 || value.code !== 200)
    throw new Error(`${method} ${path}: HTTP ${response.status}, code ${value.code}, message ${value.message}`);
  return value.data;
}
async function signIn(email, password) {
  return request(marketplace, 'POST', '/auth/sign-in', { email, password });
}
function userRequest(token) {
  return (method, path, body, headers) => request(marketplace, method, path, body,
    { Authorization: `Bearer ${token}`, ...headers });
}
function partner(path) {
  return request(payment, 'GET', path, undefined,
    { 'X-Internal-Api-Key': settings.PAYMENT_INTERNAL_API_KEY });
}
async function until(label, read, ready, count = 120) {
  for (let i = 0; i < count; i++) {
    const value = await read();
    if (ready(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error(`${label}: timeout`);
}

async function main() {
  const stage = process.argv[2];
  assert(['prepare', 'recover', 'accept', 'verify'].includes(stage),
    'Use prepare, recover, accept or verify');
  const client = await signIn('nguyenhuutrong11133@gmail.com', settings.DEMO_CLIENT_PASSWORD);
  const freelancer = await signIn('freelancer.seed@example.com', settings.DEMO_FREELANCER_PASSWORD);
  const asClient = userRequest(client.accessToken);
  const asFreelancer = userRequest(freelancer.accessToken);
  if (stage === 'recover') {
    const page = await asClient('GET', '/marketplace/jobs?page=0&size=100');
    const candidates = page.data.filter(job => job.title.startsWith('Partner outage E2E '));
    for (const job of candidates) {
      const cancellation = await asClient('GET', `/contracts/${job.contract.id}/cancellations`);
      if (cancellation?.cancellationStatus !== 'REQUESTED') continue;
      const statement = await partner('/internal/partner-mock/escrows/statement');
      const state = { jobId: job.id, contractId: job.contract.id,
        milestoneId: job.contract.milestoneId, cancellationId: cancellation.cancellationId,
        balanceBeforeUsd: Number(statement.balanceUsd) - Number(cancellation.amount) };
      mkdirSync(new URL('../target/', import.meta.url), { recursive: true });
      writeFileSync(stateFile, JSON.stringify(state));
      console.log(JSON.stringify({ stage: 'recovered', ...state }));
      return;
    }
    throw new Error('No pending outage E2E cancellation found');
  }
  if (stage === 'prepare') {
    const bank = await asClient('GET', '/payment-methods/bank-account');
    if (!bank.ready) {
      await asClient('PUT', '/payment-methods/bank-account', {
        bankCode: 'VIETCOMBANK', bankAccountNumber: '123456789012',
        bankAccountHolderName: 'NGUYEN HUU TRONG',
      });
    }
    const before = await partner('/internal/partner-mock/escrows/statement');
    const job = await asClient('POST', '/marketplace/jobs', {
      title: `Partner outage E2E ${randomUUID().slice(0, 8)}`,
      description: 'Controlled Payment Backend outage during mock refund',
      category: 'WEB_FRONTEND', skills: ['TypeScript'], budgetUsd: 35,
      deliveryDueAt: new Date(Date.now() + 25 * 3600_000).toISOString(),
      reviewWindowHours: 72, maxRevisions: 2,
      deliverables: [{ title: 'Artifact', description: 'One verifiable artifact', required: true }],
      acceptanceCriteria: [{ description: 'Artifact URL opens', required: true }],
    });
    await asFreelancer('POST', `/marketplace/jobs/${job.id}/apply`);
    const assigned = await asClient('POST', `/marketplace/jobs/${job.id}/assignments`,
      { freelancerId: freelancer.userId });
    const { id: contractId, milestoneId } = assigned.contract;
    const fundPath = `/contracts/${contractId}/milestones/${milestoneId}/partner-escrow/fund`;
    await asClient('POST', fundPath, undefined, { 'Idempotency-Key': randomUUID() });
    await until('funding', () => asClient('GET', fundPath), value => value.fundingStatus === 'SUCCEEDED', 40);
    const request = await asClient('POST', `/contracts/${contractId}/cancellations`, {
      reasonCode: 'OUTAGE_E2E', description: 'Both parties agree to refund this test job',
    });
    assert.equal(request.cancellationStatus, 'REQUESTED');
    mkdirSync(new URL('../target/', import.meta.url), { recursive: true });
    writeFileSync(stateFile, JSON.stringify({ jobId: job.id, contractId, milestoneId,
      cancellationId: request.cancellationId, balanceBeforeUsd: before.balanceUsd }));
    console.log(JSON.stringify({ stage: 'prepared', jobId: job.id, contractId,
      milestoneId, cancellationId: request.cancellationId }));
    return;
  }
  const state = JSON.parse(readFileSync(stateFile, 'utf8'));
  const cancelPath = `/contracts/${state.contractId}/cancellations`;
  if (stage === 'accept') {
    const result = await asFreelancer('POST',
      `${cancelPath}/${state.cancellationId}/decisions`, { decision: 'ACCEPT' });
    assert.equal(result.cancellationStatus, 'REFUND_PENDING');
    assert.equal(result.refundStatus, 'UNKNOWN');
    assert.equal(result.lastError, 'PARTNER_REFUND_UNKNOWN');
    const job = await asClient('GET', `/marketplace/jobs/${state.jobId}`);
    assert.equal(job.status, 'IN_PROGRESS');
    console.log(JSON.stringify({ stage: 'unknown', jobId: state.jobId,
      cancellationStatus: result.cancellationStatus, refundStatus: result.refundStatus,
      jobStatus: job.status }));
    return;
  }
  const cancelled = await until('refund reconciliation', () => asClient('GET', cancelPath),
    value => value.cancellationStatus === 'CANCELLED' && value.refundStatus === 'SUCCEEDED');
  const job = await asClient('GET', `/marketplace/jobs/${state.jobId}`);
  assert.equal(job.status, 'CANCELLED');
  const escrow = await partner(`/internal/partner-mock/escrows/${state.milestoneId}`);
  assert.equal(escrow.status, 'REFUNDED');
  assert.equal(Number(escrow.feeUsd), 0);
  const statement = await partner('/internal/partner-mock/escrows/statement');
  const entries = statement.entries.filter(row => row.milestoneId === state.milestoneId);
  assert.deepEqual(entries.map(row => row.kind).sort(), ['FUND', 'REFUND']);
  assert.equal(Number(statement.balanceUsd), Number(state.balanceBeforeUsd));
  console.log(JSON.stringify({ stage: 'reconciled', jobId: state.jobId,
    milestoneId: state.milestoneId, cancellationStatus: cancelled.cancellationStatus,
    refundStatus: cancelled.refundStatus, statementEvents: entries.map(row => row.eventKey),
    balanceBeforeUsd: state.balanceBeforeUsd, balanceAfterUsd: statement.balanceUsd }));
}

main().catch(error => { console.error(error); process.exitCode = 1; });
