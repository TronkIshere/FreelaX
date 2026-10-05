import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from './api';
import { reviewOpportunity } from './ContractReviews';
import { ActionGroup, PageHeading, SectionHeading, StatePanel } from './components';
import { applicationLabel, date, jobLabel, money } from './status';
import { cancelledContract, contractFinanceLabel, financePath, refundOwned, releaseOwned, settlementNeedsRefresh } from './financeStatus';
import type { ContractFinance } from './financeStatus';
import type { Job, MyApplication, Page, User } from './types';

const errorText = (cause: unknown) => cause instanceof Error ? cause.message : 'Không thể tải dữ liệu từ Marketplace.';

function attentionItems(client: boolean, jobs: Job[], financial: Record<string, ContractFinance>) {
  const priorities = client
    ? ['SUBMITTED_FOR_REVIEW', 'OPEN', 'AWAITING_PAYMENT']
    : ['REVISION_REQUESTED', 'IN_PROGRESS'];
  const labels: Record<string, string> = client
    ? { SUBMITTED_FOR_REVIEW: 'Duyệt bản bàn giao', OPEN: 'Xem ứng tuyển', AWAITING_PAYMENT: 'Xem trạng thái' }
    : { REVISION_REQUESTED: 'Gửi bản sửa', IN_PROGRESS: 'Bàn giao công việc' };
  // Prioritize only states in the returned page; no applicant counts or funding actions are inferred.
  const financialItems = jobs.flatMap(job => {
    if (!job.contract) return [];
    const evidence = financial[job.id];
    if (evidence?.cancellation?.refundStatus === 'SUCCEEDED' || cancelledContract(job) || evidence?.cancellation?.cancellationStatus === 'CANCELLED') return [];
    if (refundOwned(job) || evidence?.cancellation?.cancellationStatus === 'REFUND_PENDING') return [{ job, label: 'Theo dõi hoàn tiền', href: financePath(job.id) }];
    if (evidence?.error || releaseOwned(job) || evidence?.settlement) {
      if (!evidence?.error && evidence?.settlement && !settlementNeedsRefresh(evidence.settlement)) return [];
      return [{ job, label: 'Đối chiếu tài chính', href: financePath(job.id) }];
    }
    return [];
  });
  const continuing = jobs.filter(job => !job.contract || (financial[job.id] && !financial[job.id].error &&
    !releaseOwned(job) && !refundOwned(job) && !cancelledContract(job) && !financial[job.id].settlement &&
    !['REFUND_PENDING', 'CANCELLED'].includes(financial[job.id].cancellation?.cancellationStatus || '') &&
    financial[job.id].cancellation?.refundStatus !== 'SUCCEEDED' &&
    (client ? job.status === 'AWAITING_PAYMENT' && job.contract.status === 'PENDING_FUNDING' ||
      job.status === 'SUBMITTED_FOR_REVIEW' && job.contract.status === 'UNDER_REVIEW' && job.contract.milestoneStatus === 'SUBMITTED'
      : ['ACTIVE', 'REVISION'].includes(job.contract.status) && ['FUNDED', 'IN_PROGRESS'].includes(job.contract.milestoneStatus || ''))));
  return [...priorities.flatMap(status => continuing.filter(job => job.status === status).map(job => ({
    job, label: labels[status], href: '/work/' + job.id + (client && status === 'OPEN' ? '/applications' : ''),
  }))), ...financialItems];
}

export function Overview({ user }: { user: User }) {
  const [jobs, setJobs] = useState<Page<Job> | null>(null);
  const [applications, setApplications] = useState<Page<MyApplication> | null>(null);
  const [applicationsError, setApplicationsError] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [financial, setFinancial] = useState<Record<string, ContractFinance>>({});
  const [reviewJobs, setReviewJobs] = useState<string[]>([]);
  const client = user.userType === 'CLIENT';

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setApplicationsError('');
    const work = api.myJobs(0, 8);
    const applied = client ? Promise.resolve(null) : api.myApplications(0, 'ALL', 4);
    Promise.allSettled([work, applied]).then(async ([jobResult, applicationResult]) => {
      const records = jobResult.status === 'fulfilled' ? await Promise.all(jobResult.value.data.filter(job => job.contract).map(async job => {
        const [settlement, cancellation] = await Promise.allSettled([api.settlement(job.contract!.id), api.cancellation(job.contract!.id)]);
        return [job.id, { settlement: settlement.status === 'fulfilled' ? settlement.value : null,
          cancellation: cancellation.status === 'fulfilled' ? cancellation.value : null,
          error: [settlement, cancellation].flatMap(result => result.status === 'rejected' ? [errorText(result.reason)] : []).join(' · ') }] as const;
      })) : [];
      if (!active) return;
      const financialRecords = Object.fromEntries(records);
      setFinancial(financialRecords);
      const invitations = jobResult.status === 'fulfilled' ? await Promise.all(jobResult.value.data.filter(job => job.status === 'COMPLETED' && job.contract).map(async job => {
        const evidence = financialRecords[job.id];
        if (!evidence || evidence.error || evidence.settlement?.moneyStatus !== 'SUCCEEDED' || ['REFUND_PENDING', 'CANCELLED'].includes(evidence.cancellation?.cancellationStatus || '') || evidence.cancellation?.refundStatus === 'SUCCEEDED') return null;
        try { const rows = await api.contractReviews(job.contract!.id); return reviewOpportunity(job, user, evidence.settlement, rows) ? job.id : null; } catch { return null; }
      })) : [];
      if (!active) return;
      setReviewJobs(invitations.filter((id): id is string => !!id));
      if (jobResult.status === 'fulfilled') setJobs(jobResult.value);
      else setError(errorText(jobResult.reason));
      if (applicationResult.status === 'fulfilled') setApplications(applicationResult.value);
      else setApplicationsError(errorText(applicationResult.reason));
      setLoading(false);
    });
    return () => { active = false; };
  }, [client, user.id, attempt]);

  useEffect(() => {
    const refresh = () => { if (document.visibilityState !== 'hidden') setAttempt(value => value + 1); };
    window.addEventListener('focus', refresh);
    window.addEventListener('freelax:review-update', refresh);
    window.addEventListener('freelax:rating-update', refresh);
    return () => { window.removeEventListener('focus', refresh); window.removeEventListener('freelax:review-update', refresh); window.removeEventListener('freelax:rating-update', refresh); };
  }, []);

  const attention = [...attentionItems(client, jobs?.data.filter(job => client ? job.clientUserId === user.id : job.freelancerId === user.id) ?? [], financial), ...(jobs?.data.filter(job => reviewJobs.includes(job.id)).map(job => ({ job, label: 'Đánh giá đối tác', href: '/work/' + encodeURIComponent(job.id) + '#contract-reviews' })) || [])];
  const attentionIds = new Set(attention.map(item => item.job.id));
  const recent = jobs?.data.filter(job => !attentionIds.has(job.id)).slice(0, 4) ?? [];
  const pendingApplications = applications?.data.some(item => item.status === 'PENDING');
  return <div className="overview-page">
    <PageHeading eyebrow="Tổng quan" title="Công việc của bạn"
      description={client ? 'Bàn giao cần duyệt và hồ sơ đang tuyển.' : 'Việc cần bàn giao, chỉnh sửa và phản hồi ứng tuyển.'} />
    {loading && <StatePanel kind="loading" title="Đang mở hồ sơ công việc"
      body="Marketplace đang trả trạng thái công việc và ứng tuyển của bạn." />}
    {!loading && error && <StatePanel kind="error" title="Không thể tải tổng quan" body={error}
      action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
    {!loading && !error && jobs && <div className={'overview-composition' + (attention.length ? '' : ' overview-composition-clear')}>
      <section className="overview-attention" aria-labelledby="overview-attention-title">
        <SectionHeading id="overview-attention-title" title={attention.length ? 'Cần xử lý' : jobs.totalElements ? 'Bước tiếp theo' : 'Bắt đầu'}
          aside={jobs.totalElements > jobs.data.length ? 'Trong ' + jobs.data.length + ' công việc gần nhất' : undefined} />
        {attention.length ? <div className="overview-attention-rows">{attention.map(({ job, label, href }, index) =>
          <article className="overview-attention-row" key={job.id + ':' + href}>
            <div><h3><Link to={'/work/' + job.id}>{job.title}</Link></h3>
              <div className="overview-job-facts">
                <span className="overview-status" data-state={href.startsWith('/finance') ? 'FINANCIAL' : job.status}>
                  {href.startsWith('/finance') ? contractFinanceLabel(job, financial[job.id]) : jobLabel(job.status)}</span>
                <span className="overview-budget">{money(job.budgetUsd)}</span>
              </div></div>
            <ActionGroup><Link className={'button' + (index ? ' button-secondary' : '')} to={href}>{label} →</Link></ActionGroup>
        </article>)}</div> : <div className="overview-clear">
          <h3>{client
            ? jobs.totalElements ? 'Xem lại tiến độ công việc của bạn.' : 'Bắt đầu công việc đầu tiên.'
            : pendingApplications ? 'Theo dõi những cơ hội bạn đã ứng tuyển.'
              : jobs.totalElements ? 'Tìm công việc tiếp theo phù hợp với bạn.' : 'Tìm công việc đầu tiên phù hợp với bạn.'}</h3>
          <p>{jobs.totalElements ? 'Chưa có bước cần xử lý trong các hồ sơ gần nhất.'
            : client ? 'Bạn chưa có công việc trong tài khoản.' : 'Hiện chưa có công việc được giao.'}</p>
          <ActionGroup><Link className="button" to={!client && pendingApplications ? '/work/applications' : '/work'}>
            {client ? 'Xem công việc' : pendingApplications ? 'Theo dõi ứng tuyển' : 'Khám phá công việc'} →</Link></ActionGroup>
        </div>}
      </section>
      <div className={'overview-work-grid' + (client ? ' overview-work-client' : '')}>
        <section className="overview-recent" aria-labelledby="overview-recent-title">
          <SectionHeading id="overview-recent-title" title="Công việc gần đây"
            aside={<Link to="/work">Xem tất cả →</Link>} />
          {recent.length === 0 ? <p className="overview-empty-line">{attention.length
            ? 'Các hồ sơ gần nhất đang ở mục Cần xử lý.'
            : client ? 'Hồ sơ và tiến độ sẽ xuất hiện tại đây khi bạn có công việc.'
              : 'Khi được giao việc, bạn có thể theo dõi hồ sơ và tiến độ tại đây.'}</p> :
            <div className="overview-rows">{recent.map(job => <article className="overview-row" key={job.id}>
              <div><h3><Link to={'/work/' + job.id}>{job.title}</Link></h3>
                <div className="overview-job-facts">
                  <span className="overview-status" data-state={job.status}>{jobLabel(job.status)}</span>
                  <span className="metadata">{money(job.budgetUsd)} · {date(job.createdAt)}</span>
                </div></div>
              <Link className="overview-row-arrow" to={'/work/' + job.id} aria-label={'Mở ' + job.title}>↗</Link>
            </article>)}</div>}
          <p className="overview-total"><strong>{jobs.totalElements}</strong> công việc trong tài khoản</p>
        </section>
        {!client && <section className="overview-applications" aria-labelledby="overview-applications-title">
          <SectionHeading id="overview-applications-title" title="Ứng tuyển gần đây"
            aside={<Link to="/work/applications">Xem tất cả →</Link>} />
          {applicationsError && <StatePanel kind="error" title="Chưa đọc được ứng tuyển" body={applicationsError}
            action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
          {!applicationsError && applications && (applications.data.length === 0
            ? <p className="overview-empty-line">Sau khi ứng tuyển, bạn có thể theo dõi phản hồi tại đây.</p>
            : <div className="overview-application-rows">{applications.data.slice(0, 3).map(item =>
              <article key={item.id}><Link to={'/work/' + item.job.id}>{item.job.title}</Link>
                <strong className="overview-status" data-state={item.status}>{applicationLabel(item.status)}</strong></article>)}</div>)}
        </section>}
      </div>
    </div>}
  </div>;
}
