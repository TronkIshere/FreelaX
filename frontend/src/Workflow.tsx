import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { ApiError, api } from './api';
import { WorkLifecycle } from './WorkLifecycle';
import { contractAmount, localInstant } from './workflowContracts';
import { ActionGroup, EvidenceDisclosure, FactGrid, PageHeading, SectionHeading, StatePanel } from './components';
import { Pagination } from './Jobs';
import { jobCategories } from './jobDiscovery';
import { applicationLabel, date, jobLabel, money, shortId } from './status';
import type { DiscoverJob, Job, JobApplication, MyApplication, Page, Requirement, User } from './types';

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
  return <>
    <PageHeading eyebrow="Freelancer / Công việc / Ứng tuyển" title="Ứng tuyển của bạn"
      description="Theo dõi quyết định của Client và trạng thái công việc từ Marketplace."
      aside="Ứng tuyển → công việc → quyết định" />
    <div className="filter-tabs" role="group" aria-label="Lọc trạng thái ứng tuyển">
      {filterOptions.map(([value, label]) => <button key={value} className={status === value ? 'active' : ''}
        type="button" aria-pressed={status === value}
        onClick={() => { setStatus(value); setPage(0); }}>{label}</button>)}
    </div>
    {loading ? <StatePanel kind="loading" title="Đang tải ứng tuyển" body="Đang lấy lịch sử ứng tuyển của tài khoản." />
      : error ? <StatePanel kind="error" title="Không thể tải ứng tuyển" body={error}
        action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />
      : !result || result.data.length === 0 ? <StatePanel kind="empty" title="Chưa có ứng tuyển trong bộ lọc này"
        body="Ứng tuyển thực tế sẽ xuất hiện ở đây sau khi máy chủ ghi nhận." />
      : <><div className="application-list" aria-label="Danh sách ứng tuyển">
        {result.data.map(application => <article className="application-row" key={application.id}>
          <div className="application-main">
            <span className="eyebrow">Công việc / {date(application.job.createdAt)}</span>
            <h2>{application.status === 'ACCEPTED' || application.job.status === 'OPEN'
              ? <Link to={'/work/' + application.job.id}>{application.job.title}</Link> : application.job.title}</h2>
            <p>{application.job.clientDisplayName || 'Khách hàng'} · {money(application.job.budgetUsd)}</p>
          </div>
          <div><span className="cell-label">Công việc</span><strong>{jobLabel(application.job.status)}</strong></div>
          <div><span className="cell-label">Ứng tuyển</span><strong>{applicationLabel(application.status)}</strong></div>
          <div><span className="cell-label">Thời gian</span><span>Nộp {timestamp(application.createdAt)}<br />Cập nhật {timestamp(application.updatedAt)}</span></div>
        </article>)}
      </div><Pagination page={result} onPage={setPage} /></>}
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
  const busyRef = useRef(false);
  const refresh = useCallback(async () => {
    const current = await api.job(jobId);
    if (current.clientUserId !== user.id) throw new Error('Bạn không sở hữu công việc này.');
    const list = await api.applicants(jobId);
    setJob(current); setApplications(list);
  }, [jobId, user.id]);
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    (async () => {
      if (user.userType !== 'CLIENT') throw new Error('Chỉ Client sở hữu công việc mới được xem ứng viên.');
      const current = await api.job(jobId);
      if (current.clientUserId !== user.id) throw new Error('Bạn không sở hữu công việc này.');
      const list = await api.applicants(jobId);
      if (active) { setJob(current); setApplications(list); setLoading(false); }
    })().catch(cause => { if (active) { setError(message(cause)); setLoading(false); } });
    return () => { active = false; };
  }, [jobId, user.id, user.userType, attempt]);

  async function assign(freelancerId: string) {
    if (busyRef.current || !job || job.status !== 'OPEN' || user.userType !== 'CLIENT') return;
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

  return <>
    <PageHeading eyebrow="Client / Công việc / Ứng viên" title={job?.title || 'Ứng viên công việc'}
      description="Danh sách ứng tuyển và quyết định phân công từ Marketplace."
      aside={job ? jobLabel(job.status) : 'Đang đối chiếu công việc'} />
    <div className="detail-topline"><Link to={'/work/' + jobId}>← Hồ sơ công việc</Link></div>
    {loading ? <StatePanel kind="loading" title="Đang tải ứng viên" body="Đang lấy hồ sơ ứng tuyển và trạng thái công việc." />
      : error ? <StatePanel kind="error" title="Không thể tải ứng viên" body={error}
        action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />
      : applications.length === 0 ? <StatePanel kind="empty" title="Chưa có ứng viên"
        body="Ứng tuyển sẽ xuất hiện ở đây khi Freelancer nộp qua Marketplace." />
      : <section className="applicant-list" aria-label="Danh sách ứng viên">
        <div className="section-heading"><h2>Người ứng tuyển</h2><span>{applications.length} ỨNG TUYỂN</span></div>
        {applications.map((application, index) => <article className="applicant-row" key={application.id}>
          <span className="row-index">{String(index + 1).padStart(2, '0')}</span>
          <div><span className="cell-label">Freelancer ID</span><CopyId value={application.freelancerId} label="Freelancer" /></div>
          <div><span className="cell-label">Trạng thái</span><strong>{applicationLabel(application.status)}</strong></div>
          <div><span className="cell-label">Nộp lúc</span><span>{timestamp(application.createdAt)}</span></div>
          <div className="applicant-action">
            {job?.status === 'OPEN' && application.status === 'PENDING' && <button className="button" type="button"
              disabled={busy} onClick={() => setSelected(application.id)}>Chọn Freelancer</button>}
            {selected === application.id && job?.status === 'OPEN' && <div className="confirm-band" role="group"
              aria-label="Xác nhận chọn Freelancer">
              <strong>Giao công việc cho Freelancer này?</strong>
              <p>Hợp đồng sẽ được chốt sau khi phân công. Freelancer chỉ bắt đầu sau khi milestone được funding.</p>
              <button className="button" type="button" disabled={busy} onClick={() => assign(application.freelancerId)}>
                {busy ? 'Đang phân công…' : 'Xác nhận chọn'}</button>
              <button className="button button-secondary" type="button" disabled={busy} onClick={() => setSelected(null)}>Quay lại</button>
            </div>}
          </div>
        </article>)}
        {mutationError && <p className="form-error" role="alert">{mutationError} <button className="text-button"
          onClick={() => setAttempt(value => value + 1)}>Tải lại</button></p>}
      </section>}
  </>;
}
