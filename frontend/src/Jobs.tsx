import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ChevronDown, RotateCcw, Search, SlidersHorizontal, X } from 'lucide-react';
import { api } from './api';
import { applicationLabel, date, jobLabel, money } from './status';
import { ActionGroup, FactGrid, PageHeading, SectionHeading, StatePanel } from './components';
import type { DiscoverJob, DiscoveryFilters, Job, Page } from './types';
import { KineticActionArrow, KineticLedgerRow, RoughBurst, RoughUnderline, TapeSticker } from './ui/kinetic';
import { JobThumbnail } from './ui/job-thumbnails/JobThumbnail';
import { jobCategories, parseJobSkills } from './jobDiscovery';

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

function ExploreJobRow({ job }: { job: DiscoverJob }) {
  const application = job.applicationStatus ? applicationLabel(job.applicationStatus)
    : job.hasApplied ? 'Đã ứng tuyển' : 'Chưa ứng tuyển';
  return <KineticLedgerRow className="job-row explore-job-row" aria-labelledby={'job-' + job.id}
    thumbnail={<JobThumbnail job={job} />}
    title={<><h3 id={'job-' + job.id}><Link to={'/work/' + job.id} state={{ job }}>{job.title}</Link></h3>
      {job.description && <p className="explore-excerpt">{job.description}</p>}
      {!!job.skills?.length && <div className="explore-job-skills" aria-label="Kỹ năng công việc">
        {job.skills.map(skill => <span className="skill-tag" key={skill}>{skill}</span>)}</div>}</>}
    status={<span className={'state-mark ' + statusClass(job.status)}>{jobLabel(job.status)}</span>}
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
      {result.data.map(job => <ExploreJobRow key={job.id} job={job as DiscoverJob} />)}
    </ul> : <div className="job-table">
      {result.data.map(job => <JobRow key={job.id} job={job} kind={kind} />)}
    </div>}
    <Pagination page={result} onPage={onPage} />
  </>;
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

  return <div className="jobs-page">
    <PageHeading eyebrow="Công việc" title="Công việc bạn tham gia" description="Quản lý ứng tuyển và theo dõi bàn giao." />
    <section className="list-section" aria-label="Danh sách công việc">
      <SectionHeading title="Danh sách công việc" aside={<Link className="button" to="/work/new">Đăng công việc</Link>} />
      <JobResults result={result} loading={loading} error={error} retry={() => setAttempt(value => value + 1)}
        kind="client" requestedPage={page} onPage={setPage} />
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
