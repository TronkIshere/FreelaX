import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from './api';
import { applicationLabel, date, jobLabel, money } from './status';
import { ActionGroup, FactGrid, PageHeading, SectionHeading, StatePanel } from './components';
import type { DiscoverJob, DiscoveryFilters, Job, Page } from './types';

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

function JobResults<T extends Job | DiscoverJob>({ result, loading, error, retry, kind, requestedPage, onPage }: {
  result: Page<T> | null; loading: boolean; error: string; retry: () => void;
  kind: 'client' | 'discover'; requestedPage: number; onPage: (value: number) => void;
}) {
  if (loading) return <StatePanel kind="loading" title="Đang tải công việc" body="Danh sách đang được lấy từ Marketplace." />;
  if (error) return <StatePanel kind="error" title="Không thể tải công việc" body={error}
    action={{ label: 'Thử lại', onClick: retry }} />;
  if (!result || result.data.length === 0) {
    if (result && result.totalElements > 0 && requestedPage > 0) {
      return <StatePanel kind="empty" title="Trang này không còn công việc" body="Danh sách có thể đã thay đổi."
        action={{ label: 'Về trang đầu', onClick: () => onPage(0) }} />;
    }
    return <StatePanel kind="empty" title={kind === 'client' ? 'Bạn chưa có công việc nào' : 'Chưa tìm thấy công việc phù hợp'}
      body={kind === 'client'
        ? 'Khi bạn tham gia một công việc, hồ sơ sẽ xuất hiện ở đây.'
        : 'Thử thay đổi từ khóa hoặc bộ lọc để xem thêm công việc đang mở.'} />;
  }
  return <>
    <div className="job-table">
      {result.data.map(job => <JobRow key={job.id} job={job} kind={kind} />)}
    </div>
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
      <SectionHeading title="Danh sách công việc" />
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
    if (draft.minBudgetUsd && draft.maxBudgetUsd &&
        Number(draft.minBudgetUsd) > Number(draft.maxBudgetUsd)) {
      setFilterError('Ngân sách tối thiểu phải nhỏ hơn hoặc bằng ngân sách tối đa.');
      return;
    }
    setFilterError('');
    setPage(0);
    setFilters({ ...draft });
  }

  return <div className="jobs-page">
    <PageHeading eyebrow="Khám phá" title="Tìm công việc hợp với bạn" description="Công việc đang tuyển, theo ngân sách và điều kiện của bạn." />
    <form className="filter-panel" onSubmit={submit} aria-label="Lọc công việc">
      <label>Từ khóa<input type="search" placeholder="Tên hoặc mô tả công việc" value={draft.keyword}
        onChange={event => setDraft(value => ({ ...value, keyword: event.target.value }))} /></label>
      <label>USD từ<input type="number" min="0" step="0.01" inputMode="decimal" value={draft.minBudgetUsd}
        onChange={event => setDraft(value => ({ ...value, minBudgetUsd: event.target.value }))} /></label>
      <label>USD đến<input type="number" min="0" step="0.01" inputMode="decimal" value={draft.maxBudgetUsd}
        onChange={event => setDraft(value => ({ ...value, maxBudgetUsd: event.target.value }))} /></label>
      <label>Sắp xếp<select value={draft.sort}
        onChange={event => setDraft(value => ({ ...value, sort: event.target.value as DiscoveryFilters['sort'] }))}>
        <option value="NEWEST">Mới nhất</option>
        <option value="BUDGET_ASC">Ngân sách tăng dần</option>
        <option value="BUDGET_DESC">Ngân sách giảm dần</option>
      </select></label>
      <label>Ứng tuyển<select value={draft.application}
        onChange={event => setDraft(value => ({ ...value, application: event.target.value as DiscoveryFilters['application'] }))}>
        <option value="ALL">Tất cả</option>
        <option value="APPLIED">Đã ứng tuyển</option>
        <option value="NOT_APPLIED">Chưa ứng tuyển</option>
      </select></label>
      <button className="button" type="submit">Áp dụng</button>
      {filterError && <p className="filter-error" role="alert">{filterError}</p>}
    </form>
    <section className="list-section" aria-label="Kết quả khám phá">
      <SectionHeading title="Công việc đang mở" />
      <JobResults result={result} loading={loading} error={error} retry={() => setAttempt(value => value + 1)}
        kind="discover" requestedPage={page} onPage={setPage} />
    </section>
  </div>;
}
