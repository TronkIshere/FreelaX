import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

const settings = Object.fromEntries(readFileSync(new URL('../.env', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => line.includes('=') && !line.startsWith('#'))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const base = 'http://127.0.0.1:9191/api/v1';
const payment = 'http://127.0.0.1:9190';
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

async function call(origin, method, path, body, headers = {}) {
  const response = await fetch(origin + path, {
    method, headers: { 'Content-Type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const value = await response.json();
  return { status: response.status, value };
}
async function ok(origin, method, path, body, headers) {
  const result = await call(origin, method, path, body, headers);
  if (result.status !== 200 || result.value.code !== 200)
    throw new Error(`${method} ${path}: HTTP ${result.status}, code ${result.value.code}, message ${result.value.message}`);
  return result.value.data;
}
async function login(email, password) {
  return ok(base, 'POST', '/auth/sign-in', { email, password });
}
function actor(token) {
  return (method, path, body, headers = {}) => ok(base, method, path, body,
    { Authorization: `Bearer ${token}`, ...headers });
}
function partner(method, path, body) {
  return ok(payment, method, path, body,
    { 'X-Internal-Api-Key': settings.PAYMENT_INTERNAL_API_KEY });
}
async function until(label, read, ready, count = 130) {
  for (let i = 0; i < count; i++) {
    const value = await read();
    if (ready(value)) return value;
    await sleep(1000);
  }
  throw new Error(`${label}: timeout`);
}

async function main() {
  assert(settings.DEMO_ADMIN_PASSWORD, 'Enable the dev-only Admin fixture first');
  const client = await login('nguyenhuutrong11133@gmail.com', settings.DEMO_CLIENT_PASSWORD);
  const freelancer = await login('freelancer.seed@example.com', settings.DEMO_FREELANCER_PASSWORD);
  const admin = await login('admin.e2e@example.test', settings.DEMO_ADMIN_PASSWORD);
  const asClient = actor(client.accessToken);
  const asFreelancer = actor(freelancer.accessToken);
  const asAdmin = actor(admin.accessToken);
  const adminMe = await asAdmin('GET', '/auth/me');
  assert(adminMe.authorities.includes('ROLE_ADMIN'));
  assert.notEqual(adminMe.id, client.userId);
  assert.notEqual(adminMe.id, freelancer.userId);
  const baseline = await asAdmin('GET', '/admin/partner-reconciliation');
  assert.equal(baseline.matched, true, 'Existing mock statement must reconcile before this E2E');

  async function assignedFunded(amount) {
    const job = await asClient('POST', '/marketplace/jobs', {
      title: `Partner Admin E2E ${randomUUID().slice(0, 8)}`,
      description: 'Partner mock Admin E2E proof',
      category: 'WEB_FRONTEND', skills: ['TypeScript'], budgetUsd: amount,
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
    const active = await asClient('GET', `/marketplace/jobs/${job.id}`);
    assert.equal(active.status, 'IN_PROGRESS');
    return active;
  }

  const payoutJob = await assignedFunded(50);
  const payoutContract = payoutJob.contract;
  const submission = await asFreelancer('POST', `/contracts/${payoutContract.id}/submissions`, {
    summary: 'Partner Admin E2E artifact',
    deliverables: payoutContract.deliverables.map(item => ({
      requirementId: item.id, url: 'https://example.com/e2e-artifact',
    })),
    acceptanceEvidence: payoutContract.acceptanceCriteria.map(item => ({
      criterionId: item.id, url: 'https://example.com/e2e-artifact',
    })),
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(submission.status, 'SUBMITTED');

  // An independent partner-only escrow creates a real statement/ledger mismatch through the
  // existing internal API. Refund it in finally so the local demo returns to matched state.
  const orphanId = randomUUID();
  await partner('POST', '/internal/partner-mock/escrows', {
    milestoneId: orphanId, contractId: randomUUID(), jobId: randomUUID(),
    clientId: randomUUID(), freelancerId: randomUUID(), grossUsd: 1,
    fundKey: `e2e-orphan-${orphanId}`,
  });
  let mismatch;
  try {
    await until('partner-only funding', () => partner('GET', `/internal/partner-mock/escrows/${orphanId}`),
      value => value.status === 'FUNDED', 30);
    mismatch = await asAdmin('GET', '/admin/partner-reconciliation');
    assert.equal(mismatch.matched, false);
    assert.equal(Number(mismatch.differenceUsd), 1);
    assert(mismatch.differences.some(row => row.milestoneId === orphanId && Number(row.amountUsd) === 1));
    await asClient('POST', `/contracts/${payoutContract.id}/submissions/${submission.id}/decisions`,
      { decision: 'APPROVE' });
    const held = await until('payout held by mismatch',
      () => asClient('GET', `/contracts/${payoutContract.id}/settlement`),
      value => value?.moneyStatus === 'UNKNOWN');
    assert.equal(held.moneyStatus, 'UNKNOWN');
    assert.equal(held.lastError, 'PARTNER_RECONCILIATION_MISMATCH');
    const heldJob = await asClient('GET', `/marketplace/jobs/${payoutJob.id}`);
    assert.equal(heldJob.status, 'SUBMITTED_FOR_REVIEW');
  } finally {
    const orphan = await partner('GET', `/internal/partner-mock/escrows/${orphanId}`);
    if (orphan.status === 'FUNDED') {
      await partner('POST', `/internal/partner-mock/escrows/${orphanId}/refund`,
        { refundKey: `e2e-cleanup-${orphanId}` });
    }
  }
  const matchedAgain = await asAdmin('GET', '/admin/partner-reconciliation');
  assert.equal(matchedAgain.matched, true);
  const paid = await until('payout after reconciliation',
    () => asClient('GET', `/marketplace/jobs/${payoutJob.id}`), value => value.status === 'COMPLETED');
  assert.equal(paid.contract.status, 'COMPLETED');
  const payout = await asClient('GET', `/contracts/${payoutContract.id}/settlement`);
  assert.equal(payout.moneyStatus, 'SUCCEEDED');
  assert.equal(Number(payout.platformFeeUsd), 1.5);

  const disputeJob = await assignedFunded(40);
  const disputeContract = disputeJob.contract;
  const dispute = await asClient('POST', `/contracts/${disputeContract.id}/disputes`, {
    reasonCode: 'SCOPE_CONFLICT', description: 'E2E Admin refund decision',
    evidence: [{ kind: 'TEXT', text: 'No deliverable was accepted' }],
  });
  assert.equal(dispute.status, 'OPEN');
  const frozen = await partner('GET', `/internal/partner-mock/escrows/${disputeContract.milestoneId}`);
  assert.equal(frozen.status, 'FROZEN');
  const listed = await asAdmin('GET', '/admin/disputes?status=OPEN');
  assert(listed.content.some(row => row.disputeId === dispute.disputeId));
  const earlyClaim = await call(base, 'POST', `/admin/disputes/${dispute.disputeId}/claim`,
    undefined, { Authorization: `Bearer ${admin.accessToken}` });
  assert(earlyClaim.status >= 400, 'Admin claimed before negotiation deadline');
  const negotiationEnd = Date.parse(dispute.negotiationUntil);
  if (Date.now() <= negotiationEnd) await sleep(negotiationEnd - Date.now() + 150);
  const claimed = await asAdmin('POST', `/admin/disputes/${dispute.disputeId}/claim`);
  assert.equal(claimed.status, 'UNDER_REVIEW');
  const detail = await asAdmin('GET', `/admin/disputes/${dispute.disputeId}`);
  assert(detail.audit?.length > 0 || detail.auditTrail?.length > 0);
  const resolutionKey = randomUUID();
  const decided = await asAdmin('POST', `/admin/disputes/${dispute.disputeId}/resolve`,
    { outcome: 'REFUND_TO_CLIENT', reason: 'Evidence supports full mock refund' },
    { 'Idempotency-Key': resolutionKey });
  assert.equal(decided.status, 'DECISION_PENDING_REFUND');
  const repeated = await asAdmin('POST', `/admin/disputes/${dispute.disputeId}/resolve`,
    { outcome: 'REFUND_TO_CLIENT', reason: 'Evidence supports full mock refund' },
    { 'Idempotency-Key': resolutionKey });
  assert.equal(repeated.disputeId, dispute.disputeId);
  const resolved = await until('Admin refund',
    () => asClient('GET', `/contracts/${disputeContract.id}/disputes`),
    value => value.status === 'RESOLVED_REFUND');
  assert.equal(resolved.refundStatus, 'SUCCEEDED');
  const refundedJob = await asClient('GET', `/marketplace/jobs/${disputeJob.id}`);
  assert.equal(refundedJob.status, 'CANCELLED');
  const refunded = await partner('GET', `/internal/partner-mock/escrows/${disputeContract.milestoneId}`);
  assert.equal(refunded.status, 'REFUNDED');
  assert.equal(Number(refunded.feeUsd), 0);
  const finalView = await asAdmin('GET', '/admin/partner-reconciliation');
  assert.equal(finalView.matched, true);
  assert.equal(Number(finalView.partnerBalanceUsd), Number(baseline.partnerBalanceUsd));

  console.log(JSON.stringify({ result: 'PASS', adminId: adminMe.id,
    mismatch: { milestoneId: orphanId, differenceUsd: mismatch.differenceUsd,
      payoutHeld: 'UNKNOWN', payoutAfter: payout.moneyStatus },
    dispute: { jobId: disputeJob.id, disputeId: dispute.disputeId,
      outcome: resolved.status, refundStatus: resolved.refundStatus },
    reconciliation: { before: baseline.partnerBalanceUsd, after: finalView.partnerBalanceUsd,
      matched: finalView.matched } }, null, 2));
}

main().catch(error => { console.error(error); process.exitCode = 1; });
