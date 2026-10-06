import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { Ban, BriefcaseBusiness, CalendarDays, CircleCheck, CircleX, Clock3, Hourglass, ListFilter, MessageSquare, SlidersHorizontal, Star, UserRound } from 'lucide-react';
import { ApiError, api } from './api';
import { WorkLifecycle } from './WorkLifecycle';
import { contractAmount, localInstant } from './workflowContracts';
import { ActionGroup, EvidenceDisclosure, FactGrid, PageHeading, SectionHeading, StatePanel } from './components';
import { Pagination } from './Jobs';
import { jobCategories } from './jobDiscovery';
import { applicationLabel, jobLabel, money, shortId } from './status';
import { KineticActionArrow, KineticCard, KineticLedgerRow, RoughBurst, RoughUnderline, TapeSticker } from './ui/kinetic';
import { JobThumbnail } from './ui/job-thumbnails/JobThumbnail';
import { JobCategoryPlate, JobIdentityCluster, applicationStateTone, jobStateTone } from './ui/JobRowIdentity';
import type { DiscoverJob, Job, JobApplication, MyApplication, Page, Profile, Requirement, User } from './types';

type Detail = Job | DiscoverJob;
const isDiscover = (job: Detail): job is DiscoverJob => 'hasApplied' in job;
const message = (error: unknown) => error instanceof Error ? error.message : 'Yêu cầu không thành công. Vui lòng thử lại.';
const timestamp = (value: string | null | undefined) => value ? value.replace('T', ' ').slice(0, 16) : '—';

const contractLabels: Record<string, string> = {
  PENDING_FUNDING: 'Chờ funding', ACTIVE: 'Đang hiệu lực', UNDER_REVIEW: 'Đang xét bàn giao',
  REVISION: 'Đang chỉnh sửa', COMPLETED: 'Đã hoàn tất', CANCELLED: 'Đã hủy',
  DISPUTED: 'Đang tranh chấp',
};
const milestoneLabels: Record<string, string> = {
  PENDING_FUNDING: 'Chờ funding', FUNDED: 'Đã funding', IN_PROGRESS: 'Đang thực hiện',
  SUBMITTED: 'Đã bàn giao', RELEASE_PENDING: 'Chờ xác nhận release', RELEASED: 'Release đã xác nhận',
  REFUND_PENDING: 'Chờ hoàn tiền', REFUNDED: 'Đã hoàn tiền', CANCELLED: 'Đã hủy',
  DISPUTED: 'Đang tranh chấp',
};

function ScopeList({ title, items }: { title: string; items: Requirement[] | undefined }) {
  if (!items?.length) return null;
  return <section className="job-scope">
    <SectionHeading title={title} level={3} />
    <ol>{[...items].sort((a, b) => a.order - b.order).map(item => <li key={item.id}>
      {item.title && <strong>{item.title}</strong>}
      <p>{item.description}</p>
      {typeof item.required === 'boolean' && <span className="metadata">{item.required ? 'Bắt buộc' : 'Tùy chọn'}</span>}
    </li>)}</ol>
  </section>;
}

function JobDocument({ job }: { job: Detail }) {
  const contract = !isDiscover(job) ? job.contract : null;
  const due = contract?.deliveryDueAt ?? job.deliveryDueAt;
  const reviewWindow = contract?.reviewWindowHours ?? job.reviewWindowHours;
  const maxRevisions = contract?.maxRevisions ?? job.maxRevisions;
  const terms = [
    ...(due ? [{ label: 'Hạn bàn giao', value: localInstant(due) }] : []),
    ...(typeof reviewWindow === 'number' ? [{ label: 'Thời hạn review mỗi lượt', value: reviewWindow + ' giờ' }] : []),
    ...(typeof maxRevisions === 'number' ? [{ label: 'Số lần chỉnh sửa tối đa', value: maxRevisions }] : []),
    ...(contract && typeof contract.revisionsUsed === 'number' && typeof contract.maxRevisions === 'number'
      ? [{ label: 'Chỉnh sửa đã dùng', value: contract.revisionsUsed + '/' + contract.maxRevisions + ' lần' }] : []),
  ];
  return <section className="work-document" aria-labelledby="work-document-title">
    <SectionHeading id="work-document-title" title="Nội dung công việc" />
    <p className="work-description">{job.description}</p>
    {job.category && <p>Danh mục: {jobCategories[job.category] ?? 'Khác'}</p>}
    {!!job.skills?.length && <p>Kỹ năng: {job.skills.join(', ')}</p>}
    <ActionGroup label="Hồ sơ các bên"><Link className="text-link" to={'/profiles/' + encodeURIComponent(isDiscover(job) ? job.client.id : job.clientUserId)}>Hồ sơ Client</Link>{!isDiscover(job) && job.freelancerId && <Link className="text-link" to={'/profiles/' + encodeURIComponent(job.freelancerId)}>Hồ sơ Freelancer</Link>}</ActionGroup>
    <div className="job-scope-grid">
      <ScopeList title="Sản phẩm bàn giao" items={contract?.deliverables ?? job.deliverables} />
      <ScopeList title="Điều kiện nghiệm thu" items={contract?.acceptanceCriteria ?? job.acceptanceCriteria} />
    </div>
    {terms.length > 0 && <section className="job-terms-document" aria-label="Điều khoản công việc">
      <SectionHeading title="Điều khoản" level={3} /><FactGrid facts={terms} />
    </section>}
    {contract && <section className="contract-context" aria-label="Hợp đồng và milestone">
      <SectionHeading title="Hợp đồng / Milestone" level={3} />
      <FactGrid facts={[
        { label: 'Hợp đồng', value: contractLabels[contract.status] ?? 'Trạng thái khác' },
        ...(contract.milestoneStatus ? [{ label: 'Milestone', value: milestoneLabels[contract.milestoneStatus] ?? 'Trạng thái khác' }] : []),
        ...(contract.amount != null && contract.currency
          ? [{ label: 'Giá trị hợp đồng', value: contractAmount(contract.amount) + ' ' + contract.currency }] : []),
      ]} />
    </section>}
  </section>;
}

function JobRecordFooter({ job }: { job: Detail }) {
  const participant = !isDiscover(job) ? job : null;
  return <footer className="job-record-footer">
    <p className="metadata">Tạo {timestamp(job.createdAt)}{participant?.updatedAt && <> · Cập nhật {timestamp(participant.updatedAt)}</>}</p>
    <EvidenceDisclosure summary="Mã tham chiếu công việc">
      <dl className="reference-list">
        <div><dt>Công việc</dt><dd><CopyId value={job.id} label="công việc" /></dd></div>
        <div><dt>Client</dt><dd><CopyId value={isDiscover(job) ? job.client.id : job.clientUserId} label="khách hàng" /></dd></div>
        {participant?.freelancerId && <div><dt>Freelancer</dt><dd><CopyId value={participant.freelancerId} label="Freelancer" /></dd></div>}
        {participant?.contract && <div><dt>Hợp đồng</dt><dd><CopyId value={participant.contract.id} label="hợp đồng" /></dd></div>}
        {participant?.contract?.milestoneId && <div><dt>Milestone</dt><dd><CopyId value={participant.contract.milestoneId} label="milestone" /></dd></div>}
      </dl>
    </EvidenceDisclosure>
  </footer>;
}

export function CopyId({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }
  return <span className="copy-id"><code title={value}>{shortId(value)}</code>
    <button className="text-button" type="button" onClick={copy} aria-label={'Sao chép ' + label}>
      {copied ? 'Đã sao chép' : 'Sao chép mã đầy đủ'}</button></span>;
}

async function loadDetail(user: User, jobId: string, preferDiscovery: boolean): Promise<Detail> {
  if (user.userType === 'CLIENT') return api.job(jobId);
  if (preferDiscovery) {
    const discovered = await api.findDiscoverJob(jobId);
    if (discovered) return discovered;
    return api.job(jobId);
  }
  try {
    return await api.job(jobId);
  } catch (error) {
    if (!(error instanceof ApiError) || (error.status !== 403 && error.status !== 404)) throw error;
    const discovered = await api.findDiscoverJob(jobId);
    if (discovered) return discovered;
    throw error;
  }
}

export function JobDetail({ user }: { user: User }) {
  const { jobId = '' } = useParams();
  const location = useLocation();
  const hinted = location.state as { job?: Detail } | null;
  const preferDiscovery = !!hinted?.job && isDiscover(hinted.job) && hinted.job.id === jobId;
  const [job, setJob] = useState<Detail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [mutationError, setMutationError] = useState('');
  const [busy, setBusy] = useState(false);
  const [applyBlocked, setApplyBlocked] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const busyRef = useRef(false);

  const reload = useCallback(async () => {
    if (!jobId) throw new Error('Thiếu mã công việc.');
    return loadDetail(user, jobId, preferDiscovery);
  }, [jobId, user, preferDiscovery]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    reload().then(
      data => { if (active) { setJob(data); setLoading(false); } },
      cause => { if (active) { setError(message(cause)); setLoading(false); } },
    );
    return () => { active = false; };
  }, [reload, attempt]);

  async function apply() {
    if (busyRef.current || !job || !isDiscover(job) || job.status !== 'OPEN' || job.hasApplied || applyBlocked || user.userType !== 'FREELANCER') return;
    busyRef.current = true;
    setBusy(true);
    setMutationError('');
    try {
      const application = await api.apply(jobId);
      setJob(current => current && isDiscover(current) ? {
        ...current, hasApplied: true, applicationId: application.id, applicationStatus: application.status,
      } : current);
      try { setJob(await reload()); } catch { /* Keep confirmed mutation result until retry. */ }
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 4008) {
        try { setJob(await reload()); } catch (refreshError) { setMutationError(message(refreshError)); }
      } else {
        setMutationError(message(cause));
        if (cause instanceof ApiError && cause.code === 4001) {
          setApplyBlocked(true);
          try { setJob(await reload()); } catch { /* Keep the backend error and prevent another Apply. */ }
        }
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  if (loading) return <StatePanel kind="loading" title="Đang tải hồ sơ công việc" body="Đang đối chiếu trạng thái mới nhất với Marketplace." />;
  if (error || !job) return <StatePanel kind="error" title="Không thể mở công việc" body={error || 'Không tìm thấy công việc.'}
    action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />;

  const discovered = isDiscover(job) ? job : null;
  const participant = !discovered ? job as Job : null;
  const owner = user.userType === 'CLIENT' && participant?.clientUserId === user.id;
  const assignedFreelancer = user.userType === 'FREELANCER' && participant?.freelancerId === user.id;
  const workState = (owner || assignedFreelancer) && (!!participant?.contract ||
    ['IN_PROGRESS', 'SUBMITTED_FOR_REVIEW', 'REVISION_REQUESTED', 'COMPLETED'].includes(job.status));
  const applied = !!discovered?.hasApplied;
  const context = discovered ? 'Client · ' + (discovered.client?.displayName || 'Khách hàng')
    : owner ? (participant?.freelancerId ? 'Bạn là Client · Đã chọn Freelancer' : 'Bạn là Client · Chưa phân công')
    : assignedFreelancer ? 'Bạn là Freelancer được giao công việc' : 'Hồ sơ công việc';
  const document = <JobDocument job={job} />;
  const footer = <JobRecordFooter job={job} />;
  return <article className="job-detail">
    <div className="detail-topline"><Link to="/work">← Danh sách công việc</Link></div>
    <PageHeading eyebrow="Hồ sơ công việc" title={job.title} description={context} />
    <FactGrid label="Thông tin chính" facts={[
      { label: 'Ngân sách', value: money(job.budgetUsd) },
      { label: 'Trạng thái công việc', value: <span className="job-current-state">{participant?.contract?.milestoneStatus === 'RELEASE_PENDING' ? 'Đã duyệt · Chờ xử lý tiền' : participant?.contract?.status === 'DISPUTED' ? 'Đang tranh chấp' : jobLabel(job.status)}</span> },
    ]} />
    {workState && participant ? <WorkLifecycle key={participant.id} job={participant} user={user}
      onJobUpdated={setJob} footer={footer}>{document}</WorkLifecycle> : <>
      <section className="action-band" aria-label="Bước tiếp">
        {discovered && user.userType === 'FREELANCER' && <>
          <h2>{applied ? 'Ứng tuyển đã ghi nhận' : job.status === 'OPEN' ? 'Sẵn sàng ứng tuyển?' : 'Ứng tuyển đã đóng'}</h2>
          {applied && <p>{applicationLabel(discovered.applicationStatus || 'PENDING')}</p>}
          <ActionGroup>
            <button className="button" type="button" onClick={apply}
              disabled={busy || applied || applyBlocked || job.status !== 'OPEN'}>{busy ? 'Đang gửi ứng tuyển…' : applied ? 'Đã ứng tuyển' : 'Ứng tuyển'}</button>
            {applied && <Link className="text-link" to="/work/applications">Xem ứng tuyển của tôi →</Link>}
          </ActionGroup>
        </>}
        {owner && <><h2>{job.status === 'OPEN' ? 'Xem người ứng tuyển' : job.status === 'AWAITING_PAYMENT' ? 'Cần bạn funding hợp đồng' : 'Không có bước cần xử lý'}</h2>
          {job.status === 'AWAITING_PAYMENT' && <p>Hợp đồng đã chốt; Freelancer chưa thể bắt đầu công việc.</p>}
          {job.status === 'OPEN' && <ActionGroup><Link className="button" to={'/work/' + jobId + '/applications'}>Xem ứng viên</Link>
            <Link className="button button-secondary" to={'/work/' + jobId + '/edit'}>Sửa công việc</Link></ActionGroup>}</>}
        {owner && participant?.contract?.milestoneId && job.status === 'AWAITING_PAYMENT' && <ActionGroup><a className="text-link" href="#funding">Kiểm tra ngân hàng / Funding mô phỏng ↓</a></ActionGroup>}
        {participant && !owner && user.userType === 'FREELANCER' && <>
          <h2>{assignedFreelancer && job.status === 'AWAITING_PAYMENT' ? 'Đang chờ Client hoàn tất funding' : 'Không có thao tác công việc khả dụng'}</h2>
          {assignedFreelancer && job.status === 'AWAITING_PAYMENT' && <p>Bàn giao sẽ mở khi trạng thái công việc cho phép.</p>}
        </>}
        {mutationError && <p className="form-error" role="alert">{mutationError} <button className="text-button" onClick={() => reload().then(data => { setJob(data); setApplyBlocked(false); setMutationError(''); }, cause => setMutationError(message(cause)))}>Tải lại</button></p>}
      </section>
      {document}
      {footer}
    </>}
  </article>;
}
const filterOptions = [
  ['ALL', 'Tất cả'], ['PENDING', 'Đang chờ'], ['ACCEPTED', 'Đã được chọn'],
  ['REJECTED', 'Không được chọn'], ['CANCELLED', 'Đã hủy'],
] as const;

const applicationIcons = { ALL: ListFilter, PENDING: Hourglass, ACCEPTED: CircleCheck,
  REJECTED: CircleX, CANCELLED: Ban };

function ApplicationRecord({ application, primary }: { application: MyApplication; primary: boolean }) {
  const { job } = application;
  // Preserve the existing MyApplications access condition; presentation grants no permissions.
  const canOpen = application.status === 'ACCEPTED' || job.status === 'OPEN';
  const Icon = applicationIcons[application.status];
  const titleId = 'application-' + application.id;
  const status = <span className={'applications-status applications-status--' + application.status + ' job-progress-marker ' + applicationStateTone(application.status)}>
    <Icon size={20} aria-hidden="true" /><span>{applicationLabel(application.status)}</span></span>;
  const copy = <>
    {!primary && <JobCategoryPlate job={job} />}
    <h3 id={titleId}>{job.title}</h3>
    {job.description && <p className="applications-description">{job.description}</p>}
    {!!job.skills?.length && <ul className="applications-skills" aria-label="Kỹ năng công việc">
      {job.skills.map(skill => <li key={skill}>{skill}</li>)}
    </ul>}
  </>;
  const budget = <span className="applications-budget">Ngân sách <strong>{money(job.budgetUsd)}</strong></span>;
  const facts = <div className="applications-facts">
    {job.clientDisplayName && <span><UserRound size={17} aria-hidden="true" />{job.clientDisplayName}</span>}
    <span><CalendarDays size={17} aria-hidden="true" />Nộp {timestamp(application.createdAt)}</span>
    {application.updatedAt && timestamp(application.updatedAt) !== timestamp(application.createdAt) &&
      <span>Cập nhật {timestamp(application.updatedAt)}</span>}
    <span>Công việc: {jobLabel(job.status)}</span>
    {!primary && budget}
  </div>;
  const action = <div className="applications-record-action">{status}
    {primary && budget}
    {canOpen && <Link className={primary ? 'button' : 'applications-detail-link'}
      to={'/work/' + job.id} aria-label={'Xem chi tiết: ' + job.title}>
      Xem chi tiết <KineticActionArrow /></Link>}
  </div>;
  if (primary) {
    const surface = { PENDING: 'acid', ACCEPTED: 'mint', REJECTED: 'vermilion', CANCELLED: 'cream' } as const;
    return <li className="applications-primary">
      <KineticCard variant={surface[application.status]} aria-labelledby={titleId}
        className={'applications-primary-record ' + applicationStateTone(application.status)}>
        <div className="applications-primary-thumbnail" aria-hidden="true"><JobThumbnail job={job} /></div>
        <div className="applications-record-copy">{copy}{facts}</div>
        {action}
      </KineticCard>
    </li>;
  }
  return <KineticLedgerRow aria-labelledby={titleId} className={'applications-ledger-row job-identity-row ' + applicationStateTone(application.status)}
    thumbnail={<JobIdentityCluster job={job} />} title={copy} metadata={facts} action={action} />;
}

export function MyApplications() {
  const [status, setStatus] = useState<string>('ALL');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<MyApplication> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    api.myApplications(page, status).then(
      data => { if (active) { setResult(data); setLoading(false); } },
      cause => { if (active) { setError(message(cause)); setLoading(false); } },
    );
    return () => { active = false; };
  }, [page, status, attempt]);
  return <div className="applications-page">
    <PageHeading eyebrow="" descriptionClassName="applications-supporting-copy" title={<><span className="applications-title-start">Theo dõi
      <RoughBurst seedKey="applications-heading:left" accent="vermilion" size={45} className="applications-title-rays applications-title-rays--left" />
      </span>{' '}<span className="applications-title-emphasis">ứng tuyển của bạn.
        <RoughUnderline seedKey="applications-heading:underline" size={330} />
        <RoughBurst seedKey="applications-heading:right" accent="ink" size={45} className="applications-title-rays applications-title-rays--right" />
      </span></>}
      description="Theo dõi quyết định của Client và trạng thái các công việc bạn đã ứng tuyển." />
    <div className="applications-tracker">
      <aside className="applications-filter" aria-labelledby="applications-filter-title">
        <h2 id="applications-filter-title"><TapeSticker rotation={-1}>
          <SlidersHorizontal size={22} aria-hidden="true" /> Trạng thái</TapeSticker></h2>
        <div className="applications-status-rail" role="group" aria-label="Lọc trạng thái ứng tuyển">
          {filterOptions.map(([value, label]) => {
            const Icon = applicationIcons[value];
            return <button key={value} className={status === value ? 'active' : ''}
              type="button" aria-pressed={status === value}
              onClick={() => { setStatus(value); setPage(0); }}>
              <Icon size={21} aria-hidden="true" /><span>{label}</span><KineticActionArrow active={status === value} />
            </button>;
          })}
        </div>
      </aside>
      <section className="applications-results" aria-labelledby="applications-results-title" aria-busy={loading}>
        <header className="applications-results-heading">
          <h2 id="applications-results-title">Ứng tuyển gần đây</h2>
          {!loading && !error && result && <p aria-live="polite"><strong>{result.totalElements}</strong>{' '}
            {status === 'ALL' ? 'ứng tuyển' : 'ứng tuyển trong bộ lọc này'}</p>}
        </header>
        {loading ? <StatePanel kind="loading" title="Đang tải ứng tuyển" body="Đang lấy lịch sử ứng tuyển của tài khoản." />
      : error ? <StatePanel kind="error" title="Không thể tải ứng tuyển" body={error}
        action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />
      : !result || result.data.length === 0 ? <div className="applications-empty">
        <StatePanel kind="empty" title="Chưa có ứng tuyển trong bộ lọc này"
          body="Ứng tuyển thực tế sẽ xuất hiện ở đây sau khi máy chủ ghi nhận." />
        <Link className="text-link" to="/work">Khám phá công việc <KineticActionArrow /></Link>
      </div>
      : <><ul className="applications-ledger" aria-label="Danh sách ứng tuyển">
        {result.data.map((application, index) => <ApplicationRecord key={application.id} application={application} primary={index === 0} />)}
      </ul><Pagination page={result} onPage={setPage} /></>}
      </section>
    </div>
  </div>;
}

type ApplicantProfile = { state: 'loading' | 'unavailable' } | { state: 'ready'; profile: Profile };

function CandidateEvidence({ entry, freelancerId }: { entry?: ApplicantProfile; freelancerId: string }) {
  const profile = entry?.state === 'ready' ? entry.profile : null;
  const name = profile?.displayName.trim();
  const initials = name ? name.split(/\s+/).map(word => Array.from(word)[0]).filter(Boolean).slice(-2).join('').toUpperCase() : null;
  const reputation = profile?.reputation;
  return <>
    <div className="candidate-identity">
      <span className="candidate-identity-tile" aria-hidden="true">{initials || <UserRound size={38} />}
        <RoughBurst seedKey={'candidate:' + freelancerId} size={32} accent="ink" className="candidate-identity-mark" /></span>
      <div><h3>{name || 'Hồ sơ Freelancer'}</h3>
        {profile?.headline && <p className="candidate-headline">{profile.headline}</p>}
        {profile && (profile.countryCode || profile.availability) && <p className="candidate-profile-context">
          {profile.countryCode && <span>{profile.countryCode}</span>}
          {profile.availability && <span>{profile.availability}</span>}
        </p>}
        {!profile && <><p className="candidate-profile-note" role="status">{entry?.state === 'unavailable'
          ? 'Không đọc được tóm tắt hồ sơ' : 'Đang đọc hồ sơ công khai…'}</p>
          <div className="candidate-reference"><span>Freelancer ID</span> <CopyId value={freelancerId} label="Freelancer" /></div></>}
      </div>
    </div>
    {!!profile?.skills?.length && <ul className="candidate-skills" aria-label="Kỹ năng từ hồ sơ công khai">
      {profile.skills.map(skill => <li key={skill}>{skill}</li>)}
    </ul>}
    {reputation && <ul className="candidate-reputation" aria-label="Uy tín công khai từ Marketplace">
      {reputation.completedContracts != null && <li><BriefcaseBusiness size={21} aria-hidden="true" /><span><strong>{reputation.completedContracts}</strong> hợp đồng hoàn thành</span></li>}
      {reputation.averageRating != null ? <li><Star size={21} aria-hidden="true" /><span><strong>{String(reputation.averageRating)} / 5</strong> · {reputation.reviewCount} đánh giá</span></li>
        : reputation.reviewCount != null && <li><MessageSquare size={21} aria-hidden="true" /><span><strong>{reputation.reviewCount}</strong> đánh giá công bố</span></li>}
      {reputation.onTimeRate != null && <li><Clock3 size={21} aria-hidden="true" /><span>Tỷ lệ đúng hạn: <strong>{String(reputation.onTimeRate)}</strong></span></li>}
    </ul>}
  </>;
}

export function ClientApplicants({ user }: { user: User }) {
  const { jobId = '' } = useParams();
  const [job, setJob] = useState<Job | null>(null);
  const [applications, setApplications] = useState<JobApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [mutationError, setMutationError] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [profiles, setProfiles] = useState<Record<string, ApplicantProfile>>({});
  const busyRef = useRef(false);
  const refresh = useCallback(async () => {
    if (user.userType !== 'CLIENT') throw new Error('Chỉ Client sở hữu công việc mới được xem ứng viên.');
    const current = await api.job(jobId);
    if (current.clientUserId !== user.id) throw new Error('Bạn không sở hữu công việc này.');
    const list = await api.applicants(jobId);
    setJob(current); setApplications(list);
  }, [jobId, user.id, user.userType]);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(''); setSelected(null); setMutationError('');
    (async () => {
      if (user.userType !== 'CLIENT') throw new Error('Chỉ Client sở hữu công việc mới được xem ứng viên.');
      const current = await api.job(jobId);
      if (current.clientUserId !== user.id) throw new Error('Bạn không sở hữu công việc này.');
      const list = await api.applicants(jobId);
      if (active) { setJob(current); setApplications(list); setLoading(false); }
    })().catch(cause => { if (active) { setError(message(cause)); setLoading(false); } });
    return () => { active = false; };
  }, [jobId, user.id, user.userType, attempt]);

  const owned = !loading && !error && user.userType === 'CLIENT' && job?.id === jobId && job.clientUserId === user.id;
  const profileIds = JSON.stringify(owned ? [...new Set(applications.map(application => application.freelancerId))] : []);
  useEffect(() => {
    let active = true;
    const ids: string[] = JSON.parse(profileIds);
    setProfiles(Object.fromEntries(ids.map(id => [id, { state: 'loading' }])));
    // Application records render immediately. Each independent public read settles separately.
    void Promise.allSettled(ids.map(async id => {
      let entry: ApplicantProfile;
      try {
        const profile = await api.profile(id);
        entry = profile.userId === id && profile.userType === 'FREELANCER'
          ? { state: 'ready', profile } : { state: 'unavailable' };
      } catch { entry = { state: 'unavailable' }; }
      if (active) setProfiles(current => ({ ...current, [id]: entry }));
    }));
    return () => { active = false; };
  }, [profileIds, jobId, user.id, user.userType]);

  async function assign(freelancerId: string) {
    if (busyRef.current || !owned || !job || job.status !== 'OPEN' || user.userType !== 'CLIENT') return;
    const applicant = applications.find(item => item.freelancerId === freelancerId && item.status === 'PENDING');
    if (!applicant) return;
    busyRef.current = true; setBusy(true); setMutationError('');
    try {
      const assigned = await api.assign(jobId, freelancerId);
      setJob(assigned);
      setSelected(null);
      await refresh();
    } catch (cause) {
      setMutationError(message(cause));
      try { await refresh(); } catch { /* Retain server error and last visible state. */ }
    } finally {
      busyRef.current = false; setBusy(false);
    }
  }

  return <div className="client-applicants-page">
    <PageHeading eyebrow="" descriptionClassName="candidates-supporting-copy"
      title={<><span className="candidates-title-start">Chọn người<RoughBurst seedKey="candidates:left" size={40}
        accent="vermilion" className="candidates-rays candidates-rays--left" /></span>{' '}
        <span className="candidates-title-emphasis">phù hợp.<RoughUnderline seedKey="candidates:underline" size={230} />
          <RoughBurst seedKey="candidates:right" size={45} accent="ink" className="candidates-rays candidates-rays--right" /></span></>}
      description="Đối chiếu hồ sơ công khai và xác nhận Freelancer cho công việc này." />
    <div className="candidates-topline"><Link to={'/work/' + encodeURIComponent(jobId)}>← Hồ sơ công việc</Link></div>
    {owned && job && <section className={'candidates-job-context ' + jobStateTone(job.status)} aria-label="Công việc đang đối chiếu">
      <div className="candidates-job-identity"><JobIdentityCluster job={job} /></div>
      <div className="candidates-job-copy"><JobCategoryPlate job={job} /><h2>{job.title}<RoughBurst seedKey={'candidate-job:' + job.id}
        size={35} accent="ink" className="candidates-job-title-mark" /></h2>
        {!!job.skills?.length && <ul className="candidates-job-skills" aria-label="Kỹ năng công việc">{job.skills.map(skill => <li key={skill}>{skill}</li>)}</ul>}
      </div>
      <div className="candidates-job-facts"><strong className="candidates-budget">{money(job.budgetUsd)}</strong>
        <span className={'candidates-job-status job-progress-marker ' + jobStateTone(job.status)}>{jobLabel(job.status)}</span>
        {job.deliveryDueAt && <span className="candidates-deadline"><CalendarDays size={22} aria-hidden="true" /><span>Hạn bàn giao · {localInstant(job.deliveryDueAt)}</span></span>}
      </div>
    </section>}
    {loading ? <StatePanel kind="loading" title="Đang tải ứng viên" body="Đang lấy hồ sơ ứng tuyển và trạng thái công việc." />
      : error ? <StatePanel kind="error" title="Không thể tải ứng viên" body={error}
        action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />
      : !owned ? null : applications.length === 0 ? <StatePanel kind="empty" title="Chưa có ứng viên"
        body="Ứng tuyển sẽ xuất hiện ở đây khi Freelancer nộp qua Marketplace." />
      : <section className="candidates-results" aria-labelledby="candidates-results-title">
        <header className="candidates-results-heading"><h2 id="candidates-results-title">Người ứng tuyển</h2>
          <p><strong>{applications.length}</strong> ứng tuyển</p></header>
        {job?.status !== 'OPEN' && <div className="candidates-read-only"><p>{job && jobLabel(job.status)} · Danh sách chỉ đọc; không còn thao tác phân công.</p>
          <Link className="text-link" to={'/work/' + encodeURIComponent(jobId)}>Tiếp tục tới công việc <KineticActionArrow /></Link></div>}
        <ol className="candidates-roster" aria-label="Danh sách ứng viên">
          {applications.map((application, index) => {
            const canSelect = owned && job?.status === 'OPEN' && application.status === 'PENDING';
            const confirming = canSelect && selected === application.id;
            const Icon = applicationIcons[application.status];
            return <li className={'candidate-row job-identity-row ' + applicationStateTone(application.status) + (confirming ? ' candidate-row--confirming' : '')}
              key={application.id}>
              <article aria-label={'Ứng viên ' + (index + 1)}>
                <span className="candidate-index" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
                <div className="candidate-evidence"><CandidateEvidence entry={profiles[application.freelancerId]} freelancerId={application.freelancerId} /></div>
                <div className="candidate-actions">
                  <span className="candidate-status job-progress-marker"><Icon size={19} aria-hidden="true" />{applicationLabel(application.status)}</span>
                  <span className="candidate-date"><CalendarDays size={17} aria-hidden="true" />Nộp {timestamp(application.createdAt)}</span>
                  {canSelect && <button className="button candidate-select" type="button" disabled={busy}
                    aria-expanded={confirming} aria-controls={'candidate-confirm-' + application.id}
                    onClick={() => setSelected(application.id)}>Chọn Freelancer <KineticActionArrow /></button>}
                  <Link className="candidate-profile-link" to={'/profiles/' + encodeURIComponent(application.freelancerId)}>Xem hồ sơ <KineticActionArrow /></Link>
                </div>
                {confirming && <div className="candidate-confirmation" id={'candidate-confirm-' + application.id} role="group" aria-label="Xác nhận chọn Freelancer">
                  <div><h4>Xác nhận lựa chọn</h4>
                    <p>Khi xác nhận, hồ sơ này sẽ được chấp nhận và các hồ sơ đang chờ còn lại sẽ được đóng (không được chọn).
                      Công việc chuyển sang bước funding. Freelancer chỉ bắt đầu sau khi milestone được funding.</p></div>
                  <ActionGroup><button className="button" type="button" disabled={busy} onClick={() => assign(application.freelancerId)}>
                    {busy ? 'Đang phân công…' : 'Xác nhận chọn'}</button>
                    <button className="button button-secondary" type="button" disabled={busy} onClick={() => setSelected(null)}>Quay lại</button></ActionGroup>
                </div>}
              </article>
            </li>;
          })}
        </ol>
        {mutationError && <p className="form-error" role="alert">{mutationError} <button className="text-button"
          onClick={() => setAttempt(value => value + 1)}>Tải lại</button></p>}
      </section>}
  </div>;
}
