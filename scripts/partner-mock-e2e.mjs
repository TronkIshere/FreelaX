import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

const settings = Object.fromEntries(readFileSync(new URL('../.env', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => line.includes('=') && !line.startsWith('#'))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const marketplace = process.env.MARKETPLACE_URL || 'http://127.0.0.1:9191/api/v1';
const payment = process.env.PAYMENT_URL || 'http://127.0.0.1:9190';
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

async function call(base, method, path, body, token, extraHeaders = {}) {
  const response = await fetch(base + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...extraHeaders,
      ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const value = await response.json();
  return { status: response.status, value };
}

async function api(method, path, body, token, headers) {
  const result = await call(marketplace, method, path, body, token, headers);
  if (result.status !== 200 || result.value.code !== 200) {
    throw new Error(`${method} ${path}: HTTP ${result.status}, code ${result.value.code}, message ${result.value.message}`);
  }
  return result.value.data;
}

async function partner(path) {
  const result = await call(payment, 'GET', path, undefined, undefined,
    { 'X-Internal-Api-Key': settings.PAYMENT_INTERNAL_API_KEY });
  if (result.status !== 200 || result.value.code !== 200) {
    throw new Error(`Partner ${path}: HTTP ${result.status}, code ${result.value.code}`);
  }
  return result.value.data;
}

async function until(label, read, ready, count = 40) {
  for (let i = 0; i < count; i++) {
    const value = await read();
    if (ready(value)) return value;
    await pause(1000);
  }
  throw new Error(`${label}: timeout`);
}

function entriesFor(statement, milestoneId) {
  return statement.entries.filter(entry => entry.milestoneId === milestoneId);
}

async function main() {
  const client = await api('POST', '/auth/sign-in', {
    email: 'nguyenhuutrong11133@gmail.com', password: settings.DEMO_CLIENT_PASSWORD,
  });
  const freelancer = await api('POST', '/auth/sign-in', {
    email: 'freelancer.seed@example.com', password: settings.DEMO_FREELANCER_PASSWORD,
  });
  const clientToken = client.accessToken;
  const freelancerToken = freelancer.accessToken;
  assert(clientToken && freelancerToken, 'Demo login did not return tokens');
  const clientMe = await api('GET', '/auth/me', undefined, clientToken);
  const freelancerMe = await api('GET', '/auth/me', undefined, freelancerToken);
  assert.equal(clientMe.userType, 'CLIENT');
  assert.equal(freelancerMe.userType, 'FREELANCER');
  const deniedAdmin = await call(marketplace, 'GET', '/admin/partner-reconciliation',
    undefined, clientToken);
  assert.equal(deniedAdmin.status, 403, 'Client could access Admin reconciliation');

  const bank = await api('GET', '/payment-methods/bank-account', undefined, clientToken);
  if (!bank.ready) {
    await api('PUT', '/payment-methods/bank-account', {
      bankCode: 'VIETCOMBANK', bankAccountNumber: '123456789012',
      bankAccountHolderName: 'NGUYEN HUU TRONG',
    }, clientToken);
  }
  const initialStatement = await partner('/internal/partner-mock/escrows/statement');
  const initialBalance = Number(initialStatement.balanceUsd);

  async function assignedJob(amount) {
    const job = await api('POST', '/marketplace/jobs', {
      title: `Partner mock E2E ${randomUUID().slice(0, 8)}`,
      description: 'Automated partner mock escrow end-to-end proof',
      category: 'WEB_FRONTEND', skills: ['TypeScript'], budgetUsd: amount,
      deliveryDueAt: new Date(Date.now() + 25 * 3600_000).toISOString(),
      reviewWindowHours: 72, maxRevisions: 2,
      deliverables: [{ title: 'E2E artifact', description: 'One verifiable artifact', required: true }],
      acceptanceCriteria: [{ description: 'Artifact URL opens', required: true }],
    }, clientToken);
    await api('POST', `/marketplace/jobs/${job.id}/apply`, undefined, freelancerToken);
    const assigned = await api('POST', `/marketplace/jobs/${job.id}/assignments`,
      { freelancerId: freelancer.userId }, clientToken);
    assert(assigned.contract?.id && assigned.contract?.milestoneId);
    return assigned;
  }

  async function fund(job, checkSubmitBeforeFunding = false) {
    const { id, milestoneId } = job.contract;
    if (checkSubmitBeforeFunding) {
      const rejected = await call(marketplace, 'POST', `/contracts/${id}/submissions`, {
        summary: 'Must be rejected before funding',
        deliverables: [{ requirementId: job.contract.deliverables[0].id,
          url: 'https://example.com/e2e-artifact' }],
      }, freelancerToken, { 'Idempotency-Key': randomUUID() });
      assert(rejected.status >= 400, 'Submission was accepted before funding');
    }
    const path = `/contracts/${id}/milestones/${milestoneId}/partner-escrow/fund`;
    const key = randomUUID();
    const first = await api('POST', path, undefined, clientToken, { 'Idempotency-Key': key });
    const funded = await until('partner funding', () => api('GET', path, undefined, clientToken),
      value => value.fundingStatus === 'SUCCEEDED');
    const repeated = await api('POST', path, undefined, clientToken, { 'Idempotency-Key': key });
    assert.equal(repeated.fundingTransactionId, first.fundingTransactionId);
    assert.equal(funded.fundingTransactionId, first.fundingTransactionId);
    const active = await until('job activation', () => api('GET', `/marketplace/jobs/${job.id}`, undefined, clientToken),
      value => value.status === 'IN_PROGRESS');
    assert.equal(active.contract.status, 'ACTIVE');
    assert.equal(active.contract.milestoneStatus, 'FUNDED');
    const escrow = await partner(`/internal/partner-mock/escrows/${milestoneId}`);
    assert.equal(escrow.status, 'FUNDED');
    return active;
  }

  const releaseJob = await assignedJob(100);
  const releaseActive = await fund(releaseJob, true);
  const releaseContract = releaseActive.contract;
  const submission = await api('POST', `/contracts/${releaseContract.id}/submissions`, {
    summary: 'E2E deliverable submitted',
    deliverables: releaseContract.deliverables.map(item => ({
      requirementId: item.id, url: 'https://example.com/e2e-artifact', description: 'E2E proof',
    })),
    acceptanceEvidence: releaseContract.acceptanceCriteria.map(item => ({
      criterionId: item.id, url: 'https://example.com/e2e-artifact', note: 'Criterion met',
    })),
  }, freelancerToken, { 'Idempotency-Key': randomUUID() });
  assert.equal(submission.status, 'SUBMITTED');
  await api('POST', `/contracts/${releaseContract.id}/submissions/${submission.id}/decisions`,
    { decision: 'APPROVE' }, clientToken);
  const completed = await until('partner release',
    () => api('GET', `/marketplace/jobs/${releaseJob.id}`, undefined, clientToken),
    value => value.status === 'COMPLETED');
  assert.equal(completed.contract.status, 'COMPLETED');
  const settlement = await api('GET', `/contracts/${releaseContract.id}/settlement`, undefined, clientToken);
  assert.equal(settlement.moneyStatus, 'SUCCEEDED');
  assert.equal(Number(settlement.platformFeeUsd), 3);
  assert.equal(Number(settlement.freelancerUsd), 97);
  assert.equal(Number(settlement.partnerPayoutVnd), 2425000);
  assert.equal(settlement.simulation, true);

  const refundJob = await assignedJob(20);
  const refundActive = await fund(refundJob);
  const request = await api('POST', `/contracts/${refundActive.contract.id}/cancellations`, {
    reasonCode: 'MUTUAL_E2E', description: 'Both parties agree to refund this test job',
  }, clientToken);
  assert.equal(request.cancellationStatus, 'REQUESTED');
  await api('POST', `/contracts/${refundActive.contract.id}/cancellations/${request.cancellationId}/decisions`,
    { decision: 'ACCEPT' }, freelancerToken);
  const cancelled = await until('partner refund',
    () => api('GET', `/marketplace/jobs/${refundJob.id}`, undefined, clientToken),
    value => value.status === 'CANCELLED');
  assert.equal(cancelled.contract.status, 'CANCELLED');
  const cancellation = await api('GET', `/contracts/${refundActive.contract.id}/cancellations`,
    undefined, clientToken);
  assert.equal(cancellation.cancellationStatus, 'CANCELLED');
  assert.equal(cancellation.refundStatus, 'SUCCEEDED');
  assert.equal(Number(cancellation.amount), 20);
  const repeatedDecision = await api('POST',
    `/contracts/${refundActive.contract.id}/cancellations/${request.cancellationId}/decisions`,
    { decision: 'ACCEPT' }, freelancerToken);
  assert.equal(repeatedDecision.cancellationId, request.cancellationId);

  const statement = await partner('/internal/partner-mock/escrows/statement');
  const releaseEntries = entriesFor(statement, releaseContract.milestoneId);
  const refundEntries = entriesFor(statement, refundActive.contract.milestoneId);
  assert.deepEqual(releaseEntries.map(row => row.kind).sort(), ['FUND', 'RELEASE']);
  assert.deepEqual(refundEntries.map(row => row.kind).sort(), ['FUND', 'REFUND']);
  assert.equal(Number(statement.balanceUsd), initialBalance);
  const releasedEscrow = await partner(`/internal/partner-mock/escrows/${releaseContract.milestoneId}`);
  const refundedEscrow = await partner(`/internal/partner-mock/escrows/${refundActive.contract.milestoneId}`);
  assert.equal(releasedEscrow.status, 'PAID');
  assert.equal(refundedEscrow.status, 'REFUNDED');
  assert.equal(Number(refundedEscrow.feeUsd), 0);

  console.log(JSON.stringify({
    result: 'PASS', rail: 'PARTNER_ESCROW_MOCK',
    release: { jobId: releaseJob.id, contractId: releaseContract.id,
      milestoneId: releaseContract.milestoneId, feeUsd: settlement.platformFeeUsd,
      freelancerUsd: settlement.freelancerUsd, payoutVnd: settlement.partnerPayoutVnd,
      statementEvents: releaseEntries.map(row => row.eventKey) },
    refund: { jobId: refundJob.id, contractId: refundActive.contract.id,
      milestoneId: refundActive.contract.milestoneId, amountUsd: cancellation.amount,
      feeUsd: refundedEscrow.feeUsd, statementEvents: refundEntries.map(row => row.eventKey) },
    statementBalanceBeforeUsd: initialStatement.balanceUsd,
    statementBalanceAfterUsd: statement.balanceUsd,
  }, null, 2));
}

main().catch(error => { console.error(error); process.exitCode = 1; });
