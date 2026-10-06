import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import { BriefcaseBusiness, CircleCheck, CircleX, Clock3, FileText } from 'lucide-react';
import { api } from './api';
import { reviewOpportunity } from './ContractReviews';
import { ActionGroup, PageHeading, StatePanel } from './components';
import { CutPaperShape, KineticActionArrow, KineticCard, KineticLabel, KineticLedgerRow, KineticThumbnail,
  RoughArrow, RoughBurst, RoughUnderline, TapeSticker, kineticVariants, useKineticMotion } from './ui/kinetic';
import type { KineticSurface } from './ui/kinetic';
import { applicationLabel, date, jobLabel, money } from './status';
import { cancelledContract, contractFinanceLabel, financePath, refundOwned, releaseOwned, settlementNeedsRefresh } from './financeStatus';
import type { ContractFinance } from './financeStatus';
import type { Job, MyApplication, Page, User } from './types';

const errorText = (cause: unknown) => cause instanceof Error ? cause.message : 'Không thể tải dữ liệu từ Marketplace.';

// Abstract browser/poster composition, with no illustrative data or product controls.
function ActionPoster() {
  const { enabled, transition } = useKineticMotion();
  return <div className="action-poster" aria-hidden="true">
    <motion.div className="poster-collage" variants={kineticVariants('thumbnail', enabled)} transition={transition}>
    <CutPaperShape className="poster-layer-acid" accent="acid" size="lg" rotation={-6} />
    <CutPaperShape className="poster-layer-cream" accent="cream" size="lg" rotation={3} />
    <CutPaperShape className="poster-paper" accent="cobalt" size="lg" rotation={6} texture />
    <TapeSticker className="poster-tab" rotation={2}><span className="poster-tab-blank" /></TapeSticker>
    <svg className="poster-window" viewBox="0 0 300 280" focusable="false" aria-hidden="true">
      <g transform="rotate(-9 150 140)">
        <path d="M37 46H273V250H37Z" fill="var(--ink)" />
        <path d="M31 40H267V244H31Z" fill="var(--cream)" stroke="var(--ink)" strokeWidth="3" />
        <path d="M31 40H267V73H31Z" fill="var(--ink)" />
        <circle cx="47" cy="56" r="4" fill="var(--acid)" /><circle cx="61" cy="56" r="4" fill="var(--cream)" /><circle cx="75" cy="56" r="4" fill="var(--cobalt)" />
        <path d="M46 89H144V186H46Z" fill="var(--acid)" />
        <path d="M46 186 82 125 112 151 144 117V186Z" fill="var(--cobalt)" />
        <path d="M160 89H251V127H160Z" fill="var(--cobalt)" />
        <path d="M160 145H250M160 160H233M160 175H242M46 208H250M46 222H194" stroke="var(--ink)" strokeWidth="6" />
      </g>
      <path d="m225 174 10 78 17-25 25 15 10-17-28-12 29-10Z" fill="var(--ink)" stroke="var(--cream)" strokeWidth="5" strokeLinejoin="round" />
    </svg>
    <RoughBurst className="poster-burst" seedKey="overview:poster-rays" accent="acid" size={68} />
    <RoughArrow className="poster-pointer" seedKey="overview:poster-pointer" accent="cream" size={70} orientation="up-right" />
    </motion.div>
  </div>;
}

function MetricMark({ status }: { status: string }) {
  const icons: Record<string, typeof Clock3> = { OPEN: BriefcaseBusiness, IN_PROGRESS: FileText,
    COMPLETED: CircleCheck, CANCELLED: CircleX };
  const Icon = icons[status] || Clock3;
  return <Icon className="metric-sign" size={28} strokeWidth={1.8} aria-hidden="true" focusable="false" />;
}

function statusSurface(status: string): KineticSurface {
  if (['COMPLETED', 'ACCEPTED'].includes(status)) return 'mint';
  if (['CANCELLED', 'REJECTED'].includes(status)) return 'vermilion';
  return 'acid';
}

function metricSurface(status: string): KineticSurface {
  return status === 'OPEN' || status === 'CANCELLED' ? 'vermilion'
    : status === 'IN_PROGRESS' ? 'cobalt' : status === 'COMPLETED' ? 'mint' : 'acid';
}

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
  const statusCounts = ['OPEN', 'AWAITING_PAYMENT', 'IN_PROGRESS', 'SUBMITTED_FOR_REVIEW', 'REVISION_REQUESTED', 'COMPLETED', 'CANCELLED']
    .map(status => ({ status, count: jobs?.data.filter(job => job.status === status).length || 0 })).filter(item => item.count > 0);
  return <div className="overview-page">
    <div className="overview-intro">
      <PageHeading eyebrow="Tổng quan" title={<>Công việc <span className="editorial-accent">của bạn.
        <RoughUnderline className="overview-title-stroke" seedKey="overview:title" size={230} /></span></>}
        descriptionClassName="overview-supporting-copy"
        description={client ? 'Bàn giao cần duyệt và hồ sơ đang tuyển.' : 'Việc cần bàn giao, chỉnh sửa và phản hồi ứng tuyển.'} />
    </div>
    {loading && <StatePanel kind="loading" title="Đang mở hồ sơ công việc"
      body="Marketplace đang trả trạng thái công việc và ứng tuyển của bạn." />}
    {!loading && error && <StatePanel kind="error" title="Không thể tải tổng quan" body={error}
      action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
    {!loading && !error && jobs && <><div className={'overview-composition' + (attention.length ? '' : ' overview-composition-clear')}>
      <div className="overview-primary-stack">
      <KineticCard as="section" className="overview-attention" variant={attention.length ? 'vermilion' : 'cream'} aria-labelledby="overview-attention-title">
        <header className="overview-attention-heading">
          <h2 id="overview-attention-title"><TapeSticker>{attention.length ? 'CẦN XỬ LÝ' : jobs.totalElements ? 'Bước tiếp theo' : 'Bắt đầu'}</TapeSticker></h2>
          {jobs.totalElements > jobs.data.length && <span className="overview-scope">Trong {jobs.data.length} công việc gần nhất</span>}
        </header>
        {attention.length ? <div className="overview-attention-rows">{attention.map(({ job, label, href }, index) =>
          <article className="overview-attention-row" key={job.id + ':' + href}>
            <div className="overview-action-copy">
            <div><h3><Link to={'/work/' + job.id}>{job.title}</Link></h3>
              <div className="overview-job-facts">
                <KineticLabel variant="cream">{href.startsWith('/finance') ? contractFinanceLabel(job, financial[job.id]) : jobLabel(job.status)}</KineticLabel>
                <span className="overview-budget">{money(job.budgetUsd)}{index === 0 && <RoughUnderline
                  className="overview-value-stroke" seedKey="overview:primary-value" accent="cream" size={115} />}</span>
              </div></div>
            <ActionGroup><Link className={'button' + (index ? ' button-secondary' : '')} to={href}>{label} <KineticActionArrow /></Link></ActionGroup>
            </div>
            {index === 0 && <ActionPoster />}
        </article>)}</div> : <div className="overview-clear">
          <div className="overview-action-copy">
          <h3>{client
            ? jobs.totalElements ? 'Xem lại tiến độ công việc của bạn.' : 'Bắt đầu công việc đầu tiên.'
            : pendingApplications ? 'Theo dõi những cơ hội bạn đã ứng tuyển.'
              : jobs.totalElements ? 'Tìm công việc tiếp theo phù hợp với bạn.' : 'Tìm công việc đầu tiên phù hợp với bạn.'}</h3>
          <p>{jobs.totalElements ? 'Chưa có bước cần xử lý trong các hồ sơ gần nhất.'
            : client ? 'Bạn chưa có công việc trong tài khoản.' : 'Hiện chưa có công việc được giao.'}</p>
          <ActionGroup><Link className="button" to={!client && pendingApplications ? '/work/applications' : '/work'}>
            {client ? 'Xem công việc' : pendingApplications ? 'Theo dõi ứng tuyển' : 'Khám phá công việc'} <KineticActionArrow /></Link></ActionGroup>
          </div>
          <div className="overview-next-mark" aria-hidden="true"><CutPaperShape accent="mint" variant="fold" size="sm" rotation={-3} />
            <RoughArrow seedKey="overview:next-action" accent="ink" size={80} /></div>
        </div>}
      </KineticCard>
      {statusCounts.length > 0 && <section className="overview-metrics" aria-label="Trạng thái hồ sơ gần nhất">
        <p className="overview-status-caption">Trạng thái trong {jobs.data.length} hồ sơ gần nhất</p>
        <dl className="overview-metric-blocks">{statusCounts.map(({ status, count }) =>
          <KineticCard as="div" depth="compact" className="overview-metric" variant={metricSurface(status)} key={status}>
            <dt>{jobLabel(status)}</dt><dd>{count}</dd><MetricMark status={status} />
            <RoughUnderline className="metric-stroke" seedKey={'overview:metric:' + status} size={42}
              accent={['OPEN', 'CANCELLED', 'IN_PROGRESS'].includes(status) ? 'cream' : 'ink'} />
          </KineticCard>)}</dl>
      </section>}
      </div>
      <div className={'overview-work-grid' + (client ? ' overview-work-client' : '')}>
        <section className="overview-recent" aria-labelledby="overview-recent-title">
          <header className="overview-ledger-heading"><h2 id="overview-recent-title">
            <CutPaperShape variant="strip" size="sm" accent="cobalt" rotation={3} className="overview-ledger-tab" />
            <TapeSticker rotation={-1}>Công việc gần đây</TapeSticker></h2>
            <Link to="/work">Xem tất cả <KineticActionArrow /></Link></header>
          {recent.length === 0 ? <p className="overview-empty-line">{attention.length
            ? 'Các hồ sơ gần nhất đang ở mục Cần xử lý.'
            : client ? 'Hồ sơ và tiến độ sẽ xuất hiện tại đây khi bạn có công việc.'
              : 'Khi được giao việc, bạn có thể theo dõi hồ sơ và tiến độ tại đây.'}</p> :
            <ul className="overview-rows">{recent.map(job => <KineticLedgerRow className="overview-row" key={job.id}
              thumbnail={<KineticThumbnail identity={job.id} texture />}
              title={<Link to={'/work/' + job.id}>{job.title}</Link>}
              status={<KineticLabel variant={statusSurface(job.status)}>{jobLabel(job.status)}</KineticLabel>}
              value={money(job.budgetUsd)} metadata={date(job.createdAt)}
              attention={['IN_PROGRESS', 'REVISION_REQUESTED', 'SUBMITTED_FOR_REVIEW'].includes(job.status)}
              action={<Link className="overview-row-arrow" to={'/work/' + job.id} aria-label={'Mở ' + job.title}><KineticActionArrow /></Link>} />)}</ul>}
          <p className="overview-total"><strong>{jobs.totalElements}</strong> <span className="overview-account-count-label">công việc trong tài khoản</span></p>
        </section>
      </div>
    </div>
        {!client && <details className="overview-applications" open={applicationsError ? true : undefined}>
          <summary>Ứng tuyển gần đây <span className="metadata">{applications ? applications.totalElements + ' hồ sơ' : ''}</span></summary>
          <div className="overview-applications-body">
          <Link className="text-link" to="/work/applications">Xem tất cả ứng tuyển →</Link>
          {applicationsError && <StatePanel kind="error" title="Chưa đọc được ứng tuyển" body={applicationsError}
            action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
          {!applicationsError && applications && (applications.data.length === 0
            ? <p className="overview-empty-line">Sau khi ứng tuyển, bạn có thể theo dõi phản hồi tại đây.</p>
            : <div className="overview-application-rows">{applications.data.slice(0, 3).map(item =>
              <article key={item.id}><Link to={'/work/' + item.job.id}>{item.job.title}</Link>
                <strong className="overview-status" data-state={item.status}>{applicationLabel(item.status)}</strong></article>)}</div>)}
          </div>
        </details>}
    </>}
  </div>;
}
