import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Ban, BriefcaseBusiness, CalendarDays, ChevronDown, CircleCheck, Clock3, Hourglass, PencilLine, Plus, RotateCcw, Search, SlidersHorizontal, UserRound, X } from 'lucide-react';
import { api } from './api';
import { applicationLabel, date, jobLabel, money } from './status';
import { ActionGroup, FactGrid, PageHeading, StatePanel } from './components';
import type { DiscoverJob, DiscoveryFilters, Job, Page } from './types';
import { KineticActionArrow, KineticCard, KineticLedgerRow, RoughBurst, RoughUnderline, TapeSticker } from './ui/kinetic';
import { JobThumbnail } from './ui/job-thumbnails/JobThumbnail';
import { jobCategories, parseJobSkills } from './jobDiscovery';
import { JobCategoryPlate, JobIdentityCluster, jobStateTone } from './ui/JobRowIdentity';

function statusClass(status: string) {
  if (status === 'COMPLETED' || status === 'IN_PROGRESS') return 'mint';
  if (status === 'SUBMITTED_FOR_REVIEW') return 'verm';
  if (status === 'CANCELLED') return 'muted';
  return 'acid';
}

function JobRow({ job, kind }: { job: Job | DiscoverJob; kind: 'client' | 'discover' }) {
  const discovered = kind === 'discover' ? job as DiscoverJob : null;
  const participant = kind === 'client' ? job as Job : null;
  const application = discovered?.applicationStatus
    ? applicationLabel(discovered.applicationStatus)
    : discovered?.hasApplied ? 'Đã ứng tuyển' : 'Chưa ứng tuyển';
  const ownership = discovered
    ? discovered.client?.displayName || 'Khách hàng'
    : participant?.freelancerId ? 'Đã giao người thực hiện' : 'Chưa giao người thực hiện';
  const due = participant?.contract?.deliveryDueAt ?? job.deliveryDueAt;
  const maxRevisions = participant?.contract?.maxRevisions ?? job.maxRevisions;
  const revisionsUsed = participant?.contract?.revisionsUsed;
  const terms = [
    ...(due ? [{ label: 'Hạn bàn giao', value: date(due) }] : []),
    ...(typeof maxRevisions === 'number' && Number.isFinite(maxRevisions)
      ? [{ label: 'Chỉnh sửa', value: typeof revisionsUsed === 'number'
        ? revisionsUsed + '/' + maxRevisions + ' lần' : 'Tối đa ' + maxRevisions + ' lần' }] : []),
  ];
  const href = '/work/' + job.id + (!discovered && job.status === 'OPEN' ? '/applications' : '');
  const action = discovered ? (discovered.hasApplied ? 'Xem công việc' : 'Chi tiết & ứng tuyển')
    : job.status === 'OPEN' ? 'Xem ứng tuyển'
    : job.status === 'SUBMITTED_FOR_REVIEW' ? 'Duyệt bàn giao' : 'Xem công việc';
  return <article className="job-row" aria-labelledby={'job-' + job.id}>
    <div className="row-main">
      <h3 id={'job-' + job.id}><Link to={'/work/' + job.id} state={{ job }}>{job.title}</Link></h3>
      <p>{job.description}</p>
    </div>
    <div className="job-row-facts">
      <FactGrid label="Ngân sách và trạng thái" facts={[
        { label: 'Ngân sách', value: <span className="amount">{money(job.budgetUsd)}</span> },
        { label: 'Trạng thái', value: <span className={'state-mark ' + statusClass(job.status)}>{jobLabel(job.status)}</span> },
      ]} />
      {terms.length > 0 && <dl className="job-terms">{terms.map(term => <div key={term.label}>
        <dt>{term.label}</dt><dd>{term.value}</dd></div>)}</dl>}
    </div>
    <div className="job-row-next">
      <p className="metadata">{ownership}{discovered && <><br />Ứng tuyển: {application}</>}</p>
      <ActionGroup><Link className="button button-secondary" to={href} state={{ job }}>{action} →</Link></ActionGroup>
      <span className="metadata">Đăng {date(job.createdAt)}</span>
    </div>
  </article>;
}

function ExploreJobRow({ job, primary }: { job: DiscoverJob; primary: boolean }) {
  const application = job.applicationStatus ? applicationLabel(job.applicationStatus)
    : job.hasApplied ? 'Đã ứng tuyển' : 'Chưa ứng tuyển';
  return <KineticLedgerRow className={'job-row explore-job-row' + (primary ? '' : ' job-identity-row ' + jobStateTone(job.status))} aria-labelledby={'job-' + job.id}
    thumbnail={primary ? <JobThumbnail job={job} /> : <JobIdentityCluster job={job} />}
    title={<>{!primary && <JobCategoryPlate job={job} />}<h3 id={'job-' + job.id}><Link to={'/work/' + job.id} state={{ job }}>{job.title}</Link></h3>
      {job.description && <p className="explore-excerpt">{job.description}</p>}
      {!!job.skills?.length && <div className="explore-job-skills" aria-label="Kỹ năng công việc">
        {job.skills.map(skill => <span className="skill-tag" key={skill}>{skill}</span>)}</div>}</>}
    status={<span className={'state-mark job-progress-marker ' + jobStateTone(job.status)}>{jobLabel(job.status)}</span>}
    metadata={<><span>{job.createdAt ? <>Đăng {date(job.createdAt)}</> : 'Ngày đăng chưa có'}</span>
      <span className="explore-client">{job.client?.displayName || 'Khách hàng'} · Ứng tuyển: {application}</span></>}
    action={<span className="job-row-next">
      <span className="explore-budget"><span>Ngân sách</span><strong>{money(job.budgetUsd)}</strong></span>
      <Link className="button explore-job-action" to={'/work/' + job.id} state={{ job }}>
        {job.hasApplied ? 'Xem công việc' : 'Chi tiết & ứng tuyển'}<KineticActionArrow /></Link>
    </span>} />;
}

export function Pagination({ page, onPage }: { page: Page<unknown>; onPage: (value: number) => void }) {
  const current = page.totalPages === 0 ? 0 : page.currentPage + 1;
  return <nav className="pagination" aria-label="Phân trang công việc">
    <span>{page.totalElements} công việc · Trang {current}/{page.totalPages}</span>
    <div>
      <button className="button button-secondary" type="button" disabled={page.currentPage <= 0}
        onClick={() => onPage(page.currentPage - 1)}>Trang trước</button>
      <button className="button button-secondary" type="button"
        disabled={page.totalPages === 0 || page.currentPage >= page.totalPages - 1}
        onClick={() => onPage(page.currentPage + 1)}>Trang sau</button>
    </div>
  </nav>;
}

function JobResults<T extends Job | DiscoverJob>({ result, loading, error, retry, kind, requestedPage, onPage, clearFilters }: {
  result: Page<T> | null; loading: boolean; error: string; retry: () => void;
  kind: 'client' | 'discover'; requestedPage: number; onPage: (value: number) => void;
  clearFilters?: () => void;
}) {
  if (loading) return <StatePanel kind="loading" title="Đang tải công việc" body="Danh sách đang được lấy từ Marketplace." />;
  if (error) return <StatePanel kind="error" title="Không thể tải công việc" body={error}
    action={{ label: 'Thử lại', onClick: retry }} />;
  if (!result || result.data.length === 0) {
    if (result && result.totalElements > 0 && requestedPage > 0) {
      return <StatePanel kind="empty" title="Trang này không còn công việc" body="Danh sách có thể đã thay đổi."
        action={{ label: 'Về trang đầu', onClick: () => onPage(0) }} />;
    }
    return <StatePanel kind="empty" title={kind === 'client' ? 'Bạn chưa có công việc nào' : 'Không tìm thấy công việc phù hợp.'}
      body={kind === 'client'
        ? 'Khi bạn tham gia một công việc, hồ sơ sẽ xuất hiện ở đây.'
        : 'Thử thay đổi từ khóa hoặc bộ lọc để xem thêm công việc đang mở.'}
      action={kind === 'discover' && clearFilters ? { label: 'Xóa bộ lọc', onClick: clearFilters } : undefined} />;
  }
  return <>
    {kind === 'discover' ? <ul className="explore-ledger">
      {result.data.map((job, index) => <ExploreJobRow key={job.id} job={job as DiscoverJob} primary={index === 0} />)}
    </ul> : <div className="job-table">
      {result.data.map(job => <JobRow key={job.id} job={job} kind={kind} />)}
    </div>}
    <Pagination page={result} onPage={onPage} />
  </>;
}

// Client list presentation only. All actions open existing screens; no work/money mutations.
const clientJobStates = {
  OPEN: { surface: 'vermilion', icon: BriefcaseBusiness, action: 'Xem ứng viên', copy: 'Mở danh sách ứng viên để xem và chọn người thực hiện.' },
  AWAITING_PAYMENT: { surface: 'cream', icon: Hourglass, action: 'Xem funding', copy: 'Công việc đang chờ funding trước khi Freelancer bắt đầu.' },
  IN_PROGRESS: { surface: 'cobalt', icon: PencilLine, action: 'Theo dõi công việc', copy: 'Đang chờ Freelancer bàn giao.' },
  SUBMITTED_FOR_REVIEW: { surface: 'acid', icon: Clock3, action: 'Duyệt bàn giao', copy: 'Cần bạn xem và phản hồi bàn giao.' },
  REVISION_REQUESTED: { surface: 'vermilion', icon: RotateCcw, action: 'Xem tiến độ chỉnh sửa', copy: 'Đang chờ Freelancer gửi bản sửa.' },
  COMPLETED: { surface: 'mint', icon: CircleCheck, action: 'Xem hồ sơ', copy: 'Phần công việc đã hoàn tất. Trạng thái thanh toán được ghi nhận riêng.' },
  CANCELLED: { surface: 'cream', icon: Ban, action: 'Xem hồ sơ', copy: 'Công việc đã được hủy. Xem hồ sơ để theo dõi trạng thái liên quan.' },
} as const;

function ClientJobRecord({ job, primary }: { job: Job; primary: boolean }) {
  const state = clientJobStates[job.status as keyof typeof clientJobStates]
    ?? { surface: 'cream' as const, icon: Hourglass, action: 'Xem trạng thái', copy: 'Mở hồ sơ để xem trạng thái công việc.' };
  const contract = job.contract;
  const disputed = contract?.status === 'DISPUTED' || contract?.milestoneStatus === 'DISPUTED';
  const processing = contract?.milestoneStatus === 'RELEASE_PENDING' ? 'Đã duyệt · đang xử lý tiền'
    : contract?.milestoneStatus === 'REFUND_PENDING' ? 'Đang đối soát hoàn tiền' : null;
  const actionText = disputed ? 'Xem tranh chấp' : processing ? 'Xem trạng thái'
    : job.status === 'AWAITING_PAYMENT' && !contract?.milestoneId ? 'Xem trạng thái' : state.action;
  const href = '/work/' + job.id + (job.status === 'OPEN' && !disputed && !processing ? '/applications' : '');
  const due = contract?.deliveryDueAt ?? job.deliveryDueAt;
  const revisionTerms = contract && Number.isInteger(contract.revisionsUsed) && contract.revisionsUsed >= 0
    && Number.isInteger(contract.maxRevisions) && contract.maxRevisions >= 0;
  const Icon = state.icon;
  const titleId = 'client-job-' + job.id;
  const status = <span className={'client-job-status job-progress-marker ' + jobStateTone(job.status)}><Icon size={20} aria-hidden="true" />{jobLabel(job.status)}</span>;
  const facts = <div className="client-job-facts">
    <span><UserRound size={18} aria-hidden="true" />{job.freelancerId ? 'Đã giao Freelancer' : 'Chưa giao Freelancer'}</span>
    {due && <span><CalendarDays size={18} aria-hidden="true" />Hạn bàn giao <time dateTime={due}>{date(due)}</time></span>}
    {revisionTerms && <span><RotateCcw size={18} aria-hidden="true" />Chỉnh sửa <strong>{contract.revisionsUsed}/{contract.maxRevisions} lần</strong></span>}
  </div>;
  const content = <>{!primary && <JobCategoryPlate job={job} />}<h3 id={titleId}>{job.title}</h3>
    {job.description && <p className="client-job-description">{job.description}</p>}
    {!!job.skills?.length && <ul className="client-job-skills" aria-label="Kỹ năng công việc">
      {job.skills.map(skill => <li key={skill}>{skill}</li>)}</ul>}
  </>;
  const context = <p className="client-job-context">{disputed ? 'Hợp đồng đang tranh chấp.' : processing || state.copy}</p>;
  const action = <div className="client-job-next">
    <span className="client-job-budget">Ngân sách <strong>{money(job.budgetUsd)}</strong></span>
    <Link className={primary ? 'button' : 'client-job-link'} to={href} state={{ job }}
      aria-label={actionText + ': ' + job.title}>{actionText}<KineticActionArrow /></Link>
  </div>;
  if (primary) return <li className="client-job-primary">
    <KineticCard variant={disputed ? 'vermilion' : processing ? 'cream' : state.surface}
      aria-labelledby={titleId} className="client-job-primary-record">
      <div className="client-job-thumbnail"><JobThumbnail job={job} /></div>
      <div className="client-job-copy"><TapeSticker variant={state.surface === 'vermilion' ? 'acid' : 'cream'} rotation={1}>{status}</TapeSticker>
        {content}{facts}{context}</div>{action}
    </KineticCard>
  </li>;
  return <KineticLedgerRow className={'client-job-ledger-row job-identity-row ' + jobStateTone(job.status)} aria-labelledby={titleId}
    thumbnail={<JobIdentityCluster job={job} />} title={content} status={status}
    metadata={<>{facts}{context}</>} action={action} />;
}

export function ClientJobs() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<Job> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api.clientJobs(page).then(
      data => { if (active) { setResult(data); setLoading(false); } },
      cause => { if (active) { setError(cause instanceof Error ? cause.message : 'Không thể tải công việc.'); setLoading(false); } },
    );
    return () => { active = false; };
  }, [page, attempt]);

  return <div className="client-work-page">
    <PageHeading eyebrow="" descriptionClassName="client-work-supporting-copy"
      title={<><span className="client-work-title-start">Quản lý công việc
        <RoughBurst seedKey="client-work-heading:left" accent="vermilion" size={34} className="client-work-rays client-work-rays--left" />
      </span>{' '}<span className="client-work-title-emphasis">của bạn.
        <RoughUnderline seedKey="client-work-heading:underline" size={230} />
        <RoughBurst seedKey="client-work-heading:right" accent="ink" size={44} className="client-work-rays client-work-rays--right" />
      </span></>}
      description="Theo dõi tuyển dụng, bàn giao và trạng thái thực tế của các công việc bạn đã đăng." />
    <section className="client-work-results" aria-label="Danh sách công việc" aria-busy={loading}>
      <header className="client-work-results-heading"><div><h2>Công việc đã đăng</h2>
        {!loading && !error && result && <p aria-live="polite"><strong>{result.totalElements}</strong> công việc</p>}</div>
        <Link className="button client-work-create" to="/work/new"><Plus size={22} aria-hidden="true" />Đăng công việc<KineticActionArrow /></Link>
      </header>
      {loading ? <StatePanel kind="loading" title="Đang tải công việc" body="Danh sách đang được lấy từ Marketplace." />
        : error ? <StatePanel kind="error" title="Không thể tải công việc" body={error}
          action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />
        : !result || result.data.length === 0 ? result && result.totalElements > 0 && page > 0
          ? <StatePanel kind="empty" title="Trang này không còn công việc" body="Danh sách có thể đã thay đổi."
            action={{ label: 'Về trang đầu', onClick: () => setPage(0) }} />
          : <StatePanel kind="empty" title="Bạn chưa có công việc nào" body="Đăng công việc để bắt đầu tuyển Freelancer và theo dõi bàn giao." />
        : <><ul className="client-work-ledger" aria-label="Hồ sơ công việc của Client">
          {result.data.map((job, index) => <ClientJobRecord key={job.id} job={job} primary={index === 0} />)}
        </ul><Pagination page={result} onPage={setPage} /></>}
    </section>
  </div>;
}

const defaults: DiscoveryFilters = {
  keyword: '', minBudgetUsd: '', maxBudgetUsd: '', sort: 'NEWEST', application: 'ALL',
};

export function FreelancerDiscovery() {
  const [draft, setDraft] = useState<DiscoveryFilters>(defaults);
  const [filters, setFilters] = useState<DiscoveryFilters>(defaults);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<DiscoverJob> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filterError, setFilterError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [skillDraft, setSkillDraft] = useState('');
  const [skillTokens, setSkillTokens] = useState<string[]>([]);

  function collectSkills(text = skillDraft) {
    return parseJobSkills([...skillTokens, ...(text.trim() ? [text] : [])].join(','));
  }

  function addSkills(text: string, remaining = '') {
    try {
      setSkillTokens(collectSkills(text));
      setSkillDraft(remaining);
      setFilterError('');
    } catch (cause) {
      setFilterError(cause instanceof Error ? cause.message : 'Kỹ năng không hợp lệ.');
    }
  }

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api.discoverJobs(page, filters).then(
      data => { if (active) { setResult(data); setLoading(false); } },
      cause => { if (active) { setError(cause instanceof Error ? cause.message : 'Không thể tải công việc.'); setLoading(false); } },
    );
    return () => { active = false; };
  }, [page, filters, attempt]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    let skills: string[];
    try { skills = collectSkills(); }
    catch (cause) { setFilterError(cause instanceof Error ? cause.message : 'Kỹ năng không hợp lệ.'); return; }
    if (draft.minBudgetUsd && draft.maxBudgetUsd &&
        Number(draft.minBudgetUsd) > Number(draft.maxBudgetUsd)) {
      setFilterError('Ngân sách tối thiểu phải nhỏ hơn hoặc bằng ngân sách tối đa.');
      return;
    }
    setFilterError('');
    setSkillTokens(skills);
    setSkillDraft('');
    setPage(0);
    setFilters({ ...draft, ...(skills.length ? { skills } : {}) });
  }

  function clearFilters() {
    setDraft({ ...defaults }); setFilters({ ...defaults }); setPage(0); setFilterError('');
    setSkillDraft(''); setSkillTokens([]);
  }

  return <div className="jobs-page explore-page">
    <PageHeading eyebrow="Khám phá" title={<><span className="explore-title-start">
      <RoughBurst seedKey="explore-title-left" size={54} accent="vermilion" className="explore-title-rays explore-title-rays-left" />
      Tìm công việc</span>{' '}<span className="explore-title-emphasis">phù hợp.
      <RoughUnderline seedKey="explore-title" size={220} accent="ink" />
      <RoughBurst seedKey="explore-title-right" size={60} accent="ink" className="explore-title-rays explore-title-rays-right" /></span></>}
      description="Công việc đang tuyển, theo ngân sách và điều kiện của bạn." descriptionClassName="explore-supporting-copy" />
    <form className="filter-panel" onSubmit={submit} aria-label="Lọc công việc">
      <div className="explore-search">
        <label><span className="explore-search-label">Từ khóa</span><span className="explore-search-field"><Search size={24} aria-hidden="true" />
          <input type="search" placeholder="Tên hoặc mô tả công việc" value={draft.keyword}
            onChange={event => setDraft(value => ({ ...value, keyword: event.target.value }))} /></span></label>
        <button className="button explore-search-button" type="submit">Tìm công việc <KineticActionArrow /></button>
      </div>
      <aside className="explore-filters" aria-labelledby="explore-filter-title">
        <h2 id="explore-filter-title"><TapeSticker rotation={-1}><SlidersHorizontal size={20} aria-hidden="true" /> Bộ lọc</TapeSticker></h2>
        <label>Danh mục<span className="explore-select"><select value={draft.category ?? ''}
          onChange={event => setDraft(value => ({ ...value, category: event.target.value as DiscoveryFilters['category'] }))}>
          <option value="">Tất cả danh mục</option>
          {Object.entries(jobCategories).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select><ChevronDown size={18} aria-hidden="true" /></span></label>
        <div className="explore-skill-control">
          <label htmlFor="explore-skill-entry">Kỹ năng</label>
          <div className="explore-skill-entry">
            {skillTokens.length > 0 && <ul className="explore-skill-tokens" aria-label="Kỹ năng đã chọn">
              {skillTokens.map(skill => <li key={skill}><span>{skill}</span>
                <button type="button" aria-label={'Xóa kỹ năng ' + skill} onClick={() => {
                  setSkillTokens(value => value.filter(item => item !== skill)); setFilterError('');
                }}><X size={14} aria-hidden="true" /></button></li>)}
            </ul>}
            <input id="explore-skill-entry" type="text" value={skillDraft} placeholder="Thêm kỹ năng…"
              aria-describedby="explore-skills-help" onChange={event => {
                const text = event.target.value;
                setSkillDraft(text);
                const delimiter = text.lastIndexOf(',');
                if (delimiter >= 0) addSkills(text.slice(0, delimiter), text.slice(delimiter + 1));
              }} onKeyDown={event => {
                if (event.nativeEvent.isComposing) return;
                if (event.key === 'Enter' || event.key === ',') {
                  event.preventDefault();
                  if (skillDraft.trim()) addSkills(skillDraft);
                } else if (event.key === 'Backspace' && !skillDraft && skillTokens.length) {
                  event.preventDefault(); setSkillTokens(value => value.slice(0, -1)); setFilterError('');
                }
              }} />
          </div>
          <p id="explore-skills-help" className="explore-filter-help">Enter hoặc dấu phẩy để thêm. Khớp ít nhất một kỹ năng.</p>
        </div>
        <fieldset><legend>Ngân sách (USD)</legend>
      <label>Tối thiểu<input aria-label="Ngân sách tối thiểu (USD)" type="number" min="0" step="0.01" inputMode="decimal" value={draft.minBudgetUsd}
        onChange={event => setDraft(value => ({ ...value, minBudgetUsd: event.target.value }))} /></label>
      <label>Tối đa<input aria-label="Ngân sách tối đa (USD)" type="number" min="0" step="0.01" inputMode="decimal" value={draft.maxBudgetUsd}
        onChange={event => setDraft(value => ({ ...value, maxBudgetUsd: event.target.value }))} /></label>
        </fieldset>
      <label>Trạng thái ứng tuyển<span className="explore-select"><select value={draft.application}
        onChange={event => setDraft(value => ({ ...value, application: event.target.value as DiscoveryFilters['application'] }))}>
        <option value="ALL">Tất cả</option>
        <option value="APPLIED">Đã ứng tuyển</option>
        <option value="NOT_APPLIED">Chưa ứng tuyển</option>
      </select><ChevronDown size={18} aria-hidden="true" /></span></label>
      <div className="explore-filter-actions">
        <button className="button" type="submit">Áp dụng bộ lọc <KineticActionArrow /></button>
        <button className="explore-clear" type="button" onClick={clearFilters}><RotateCcw size={16} aria-hidden="true" /> Xóa bộ lọc</button>
      </div>
      {filterError && <p className="filter-error" role="alert">{filterError}</p>}
      </aside>
    <section className="list-section" aria-label="Kết quả khám phá">
      <header className="explore-results-heading">
        <div><h2>Công việc đang mở</h2>
          <p aria-live="polite">{loading ? 'Đang tìm công việc…' : error ? 'Chưa tải được kết quả' : result ? `${result.totalElements} công việc phù hợp` : 'Chưa có kết quả'}</p></div>
        <label>Sắp xếp<span className="explore-select"><select value={draft.sort}
          onChange={event => setDraft(value => ({ ...value, sort: event.target.value as DiscoveryFilters['sort'] }))}>
          <option value="NEWEST">Mới nhất</option>
          <option value="BUDGET_ASC">Ngân sách tăng dần</option>
          <option value="BUDGET_DESC">Ngân sách giảm dần</option>
        </select><ChevronDown size={18} aria-hidden="true" /></span></label>
      </header>
      <JobResults result={result} loading={loading} error={error} retry={() => setAttempt(value => value + 1)}
        kind="discover" requestedPage={page} onPage={setPage} clearFilters={clearFilters} />
    </section>
    </form>
  </div>;
}
