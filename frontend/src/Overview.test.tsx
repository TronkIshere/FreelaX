// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from './api';
import { Overview } from './Overview';
import type { ContractCancellationRecord, ContractSettlement, Job, MyApplication, Page, User } from './types';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const client: User = { id: 'client', email: 'client@example.test', displayName: 'Client', userType: 'CLIENT' };
const freelancer: User = { ...client, id: 'freelancer', userType: 'FREELANCER' };
const job = (id: string, status: string): Job => ({ id, status, title: 'Job ' + id, description: 'Brief',
  budgetUsd: 450, clientUserId: client.id, freelancerId: freelancer.id, createdAt: null });
const page = <T,>(data: T[], totalElements = data.length): Page<T> => ({
  data, totalElements, currentPage: 0, pageSize: 8, totalPages: Math.ceil(totalElements / 8),
});

describe('P1 server-owned attention', () => {
  const work = (status = 'SUBMITTED_FOR_REVIEW', milestoneStatus = 'SUBMITTED', contractStatus = 'UNDER_REVIEW'): Job => ({
    ...job('contract-job', status), contract: { id: 'contract', status: contractStatus, milestoneId: 'milestone', milestoneStatus,
      amount: '450', currency: 'USD', deliveryDueAt: null, reviewWindowHours: 72, maxRevisions: 2, revisionsUsed: 0,
      deliverables: [], acceptanceCriteria: [] } });
  beforeEach(() => {
    vi.spyOn(api, 'settlement').mockResolvedValue(null);
    vi.spyOn(api, 'cancellation').mockResolvedValue(null);
  });
  it('replaces stale Client review with Finance during release pending', async () => {
    vi.mocked(api.myJobs).mockResolvedValue(page([work('SUBMITTED_FOR_REVIEW', 'RELEASE_PENDING')]));
    await render(client);
    expect(host.textContent).not.toContain('Duyệt bản bàn giao');
    expect(host.querySelector('.overview-attention .button')?.getAttribute('href')).toBe('/finance?jobId=contract-job');
  });
  it('uses milestone refund pending even before cancellation evidence appears', async () => {
    vi.mocked(api.myJobs).mockResolvedValue(page([work('IN_PROGRESS', 'REFUND_PENDING', 'ACTIVE')]));
    await render(freelancer);
    expect(host.textContent).toContain('Theo dõi hoàn tiền');
    expect(host.textContent).not.toContain('Bàn giao công việc');
  });
  it.each([client, freelancer])('suppresses incompatible work during refund pending for $userType', async user => {
    vi.mocked(api.myJobs).mockResolvedValue(page([work(user.userType === 'CLIENT' ? 'SUBMITTED_FOR_REVIEW' : 'IN_PROGRESS', 'FUNDED', 'ACTIVE')]));
    vi.mocked(api.cancellation).mockResolvedValue({ cancellationStatus: 'REFUND_PENDING', refundStatus: 'UNKNOWN' } as ContractCancellationRecord);
    await render(user);
    expect(host.querySelector('.overview-attention')?.textContent).toContain('Theo dõi hoàn tiền');
    expect(host.textContent).not.toContain('Duyệt bản bàn giao');
    expect(host.textContent).not.toContain('Bàn giao công việc');
  });
  it.each(['REQUESTED', 'REJECTED'] as const)('retains continuing work for %s cancellation', async status => {
    vi.mocked(api.myJobs).mockResolvedValue(page([work('IN_PROGRESS', 'FUNDED', 'ACTIVE')]));
    vi.mocked(api.cancellation).mockResolvedValue({ cancellationStatus: status, refundStatus: null } as ContractCancellationRecord);
    await render(freelancer);
    expect(host.querySelector('.overview-attention')?.textContent).toContain('Bàn giao công việc');
    expect(host.textContent).not.toContain('Đã hủy');
  });
  it('removes review actions for a confirmed release even when job status is stale', async () => {
    vi.mocked(api.myJobs).mockResolvedValue(page([work()]));
    vi.mocked(api.settlement).mockResolvedValue({ moneyStatus: 'SUCCEEDED', onChainStatus: 'SUCCEEDED',
      offRampStatus: 'SUCCEEDED', taxStatus: 'SUCCEEDED', retryable: false } as ContractSettlement);
    await render(client);
    expect(host.textContent).not.toContain('Duyệt bản bàn giao');
    expect(host.querySelectorAll('.overview-attention-row')).toHaveLength(0);
  });
  it('uses completed/released contract truth despite stale job status and missing release proof', async () => {
    vi.mocked(api.myJobs).mockResolvedValue(page([work('IN_PROGRESS', 'RELEASED', 'COMPLETED')]));
    await render(freelancer);
    expect(host.textContent).not.toContain('Bàn giao công việc');
    expect(host.querySelector('.overview-attention .button')?.getAttribute('href')).toBe('/finance?jobId=contract-job');
  });
  it('fails closed on cancellation read error, then restores workflow after a verified retry', async () => {
    vi.mocked(api.myJobs).mockResolvedValue(page([work()]));
    vi.mocked(api.cancellation).mockRejectedValueOnce(new Error('Cancellation unavailable'));
    await render(client);
    expect(host.textContent).not.toContain('Duyệt bản bàn giao');
    await act(async () => window.dispatchEvent(new Event('focus')));
    expect(host.textContent).toContain('Duyệt bản bàn giao');
  });
  it('does not let an older financial read replace the latest focus refresh', async () => {
    let resolve!: (value: ContractCancellationRecord | null) => void;
    vi.mocked(api.myJobs).mockResolvedValue(page([work()]));
    vi.mocked(api.cancellation).mockImplementationOnce(() => new Promise(done => { resolve = done; }))
      .mockResolvedValue({ cancellationStatus: 'REFUND_PENDING', refundStatus: 'UNKNOWN' } as ContractCancellationRecord);
    await render(client);
    await act(async () => window.dispatchEvent(new Event('focus')));
    await act(async () => resolve(null));
    expect(host.textContent).toContain('Theo dõi hoàn tiền');
    expect(host.textContent).not.toContain('Duyệt bản bàn giao');
  });
});
const application = (status: MyApplication['status']): MyApplication => ({ id: 'app', status,
  createdAt: null, updatedAt: null, job: { id: 'applied', title: 'Application job', description: 'Brief',
    budgetUsd: 250, status: 'OPEN', clientDisplayName: 'Client', createdAt: null } });
let host: HTMLDivElement;
let root: Root;
beforeEach(() => {
  host = document.createElement('div'); document.body.append(host); root = createRoot(host);
  vi.spyOn(api, 'myJobs').mockResolvedValue(page([]));
  vi.spyOn(api, 'myApplications').mockResolvedValue(page([]));
});
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); });
async function render(user: User) {
  await act(async () => root.render(<MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
    <Overview user={user} /></MemoryRouter>));
}

describe('P06.3 operational overview', () => {
  it.each([
    [freelancer, 'Tìm công việc đầu tiên phù hợp với bạn.', 'Hiện chưa có công việc được giao.', 'Khám phá công việc'],
    [client, 'Bắt đầu công việc đầu tiên.', 'Bạn chưa có công việc trong tài khoản.', 'Xem công việc'],
  ] as const)('gives an empty $userType Overview a next action while retaining server truth', async (user, heading, truth, action) => {
    await render(user);
    const next = host.querySelector('.overview-clear')!;
    expect(next.querySelector('h3')?.textContent).toBe(heading);
    expect(next.querySelector('p')?.textContent).toBe(truth);
    expect(next.querySelector('.button')?.textContent).toContain(action);
    expect(next.querySelector('.button')?.getAttribute('href')).toBe('/work');
    expect(host.querySelector('.overview-total')?.textContent).toBe('0 công việc trong tài khoản');
    expect(host.querySelectorAll('.overview-attention-row')).toHaveLength(0);
  });

  it('puts Client reviews, recruiting and pending payment before a separate recent ledger', async () => {
    vi.mocked(api.myJobs).mockResolvedValue(page([
      job('active', 'IN_PROGRESS'), job('open', 'OPEN'), job('funding', 'AWAITING_PAYMENT'),
      job('review', 'SUBMITTED_FOR_REVIEW'), job('done', 'COMPLETED'),
    ], 18));
    await render(client);
    const attention = host.querySelector('.overview-attention')!;
    expect([...attention.querySelectorAll('h3')].map(item => item.textContent))
      .toEqual(['Job review', 'Job open', 'Job funding']);
    expect(attention.querySelector('.button:not(.button-secondary)')?.getAttribute('href')).toBe('/work/review');
    expect(attention.querySelector('a[href="/work/open/applications"]')?.textContent).toContain('Xem ứng tuyển');
    expect(attention.querySelector('.button[href="/work/funding"]')?.textContent).toContain('Xem trạng thái');
    expect(attention.textContent).toContain('Trong 5 công việc gần nhất');
    expect(host.querySelector('.overview-recent')?.textContent).toContain('Job active');
    expect(host.querySelector('.overview-recent')?.textContent).not.toContain('Job review');
    expect(host.textContent).toContain('18 công việc trong tài khoản');
    expect(api.myJobs).toHaveBeenCalledExactlyOnceWith(0, 8);
    expect(api.myApplications).not.toHaveBeenCalled();
    expect(host.querySelector('.overview-applications')).toBeNull();
  });

  it('prioritizes Freelancer revisions over working jobs and keeps real pending applications', async () => {
    vi.mocked(api.myJobs).mockResolvedValue(page([
      job('working', 'IN_PROGRESS'), job('review', 'SUBMITTED_FOR_REVIEW'), job('revision', 'REVISION_REQUESTED'),
    ]));
    vi.mocked(api.myApplications).mockResolvedValue(page([application('PENDING')]));
    await render(freelancer);
    expect([...host.querySelectorAll('.overview-attention h3')].map(item => item.textContent))
      .toEqual(['Job revision', 'Job working']);
    expect(host.querySelector('.overview-attention .button')?.textContent).toContain('Gửi bản sửa');
    expect(host.querySelector('.overview-recent')?.textContent).toContain('Job review');
    expect(host.querySelector('.overview-applications')?.textContent).toContain('Đang chờ');
    expect(api.myApplications).toHaveBeenCalledExactlyOnceWith(0, 'ALL', 4);
  });

  it('uses a pending application destination when no work needs attention', async () => {
    vi.mocked(api.myApplications).mockResolvedValue(page([application('PENDING')]));
    await render(freelancer);
    expect(host.querySelector('.overview-clear h3')?.textContent).toBe('Theo dõi những cơ hội bạn đã ứng tuyển.');
    expect(host.querySelector('.overview-clear .button')?.getAttribute('href')).toBe('/work/applications');
    expect(host.querySelector('.overview-clear .button')?.textContent).toContain('Theo dõi ứng tuyển');
  });

  it('does not infer pending work from rejected applications or awaiting-payment Freelancer jobs', async () => {
    vi.mocked(api.myJobs).mockResolvedValue(page([job('funding', 'AWAITING_PAYMENT')]));
    vi.mocked(api.myApplications).mockResolvedValue(page([application('REJECTED')]));
    await render(freelancer);
    expect(host.querySelectorAll('.overview-attention-row')).toHaveLength(0);
    expect(host.querySelector('.overview-clear .button')?.getAttribute('href')).toBe('/work');
    expect(host.textContent).toContain('Không được chọn');
    expect(host.querySelector('.overview-recent')?.textContent).toContain('Chờ funding');
  });

  it('keeps available work visible when applications fail and retries the same requests', async () => {
    vi.mocked(api.myJobs).mockResolvedValue(page([job('working', 'IN_PROGRESS')]));
    vi.mocked(api.myApplications).mockRejectedValueOnce(new Error('Ứng tuyển chưa sẵn sàng'));
    await render(freelancer);
    expect(host.querySelector('.overview-attention')?.textContent).toContain('Bàn giao công việc');
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Ứng tuyển chưa sẵn sàng');
    await act(async () => (host.querySelector('[role="alert"] button') as HTMLButtonElement).click());
    expect(api.myJobs).toHaveBeenCalledTimes(2);
    expect(api.myApplications).toHaveBeenCalledTimes(2);
    expect(host.querySelector('[role="alert"]')).toBeNull();
  });

  it('preserves loading/error/retry without showing stale attention', async () => {
    let resolve!: (value: Page<Job>) => void;
    vi.mocked(api.myJobs).mockImplementationOnce(() => new Promise(done => { resolve = done; }));
    await render(client);
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    await act(async () => resolve(page([job('review', 'SUBMITTED_FOR_REVIEW')])));
    expect(host.querySelector('.overview-attention-row')).not.toBeNull();
    act(() => root.unmount()); root = createRoot(host);
    vi.mocked(api.myJobs).mockRejectedValueOnce(new Error('Marketplace unavailable'));
    await render(client);
    expect(host.querySelector('h1')).not.toBeNull();
    expect(host.querySelector('.overview-attention')).toBeNull();
    await act(async () => (host.querySelector('[role="alert"] button') as HTMLButtonElement).click());
    expect(host.querySelector('[role="alert"]')).toBeNull();
    expect(host.querySelector('.overview-clear .button')?.getAttribute('href')).toBe('/work');
  });
});
