import { useEffect, useState, type FormEvent } from 'react';
import { api } from './api';
import { PageHeading, StatePanel } from './components';
import type { DiscoverJob, DiscoveryFilters, Job, Page } from './types';

const statusLabels: Record<string, string> = {
  OPEN: 'Đang tuyển',
  IN_PROGRESS: 'Đang thực hiện',
  SUBMITTED_FOR_REVIEW: 'Chờ duyệt bàn giao',
  REVISION_REQUESTED: 'Cần chỉnh sửa',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
};

const applicationLabels: Record<string, string> = {
  PENDING: 'Đang chờ phản hồi',
  ACCEPTED: 'Được chấp nhận',
  REJECTED: 'Không được chọn',
  CANCELLED: 'Đã hủy ứng tuyển',
};

const nextByStatus: Record<string, string> = {
  OPEN: 'Đang tuyển người thực hiện',
  IN_PROGRESS: 'Đang chờ bàn giao',
  SUBMITTED_FOR_REVIEW: 'Bàn giao chờ duyệt',
  REVISION_REQUESTED: 'Đang chờ bản sửa',
  COMPLETED: 'Quy trình đã đóng',
  CANCELLED: 'Không còn bước tiếp',
};

function statusClass(status: string) {
  if (status === 'COMPLETED' || status === 'IN_PROGRESS') return 'mint';
  if (status === 'SUBMITTED_FOR_REVIEW') return 'verm';
  if (status === 'CANCELLED') return 'muted';
  return 'acid';
}

function money(value: number) {
  return Number.isFinite(value)
    ? new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 }).format(value)
    : '—';
}

function date(value: string) {
  return typeof value === 'string' && value.length >= 10 ? value.slice(0, 10) : '—';
}

function JobRow({ job, index, kind }: { job: Job | DiscoverJob; index: number; kind: 'client' | 'discover' }) {
  const discovered = kind === 'discover' ? job as DiscoverJob : null;
  const participant = kind === 'client' ? job as Job : null;
  const application = discovered?.applicationStatus
    ? applicationLabels[discovered.applicationStatus] || 'Trạng thái ứng tuyển khác'
    : discovered?.hasApplied ? 'Đã ứng tuyển' : 'Chưa ứng tuyển';
  const ownership = discovered
    ? discovered.client?.displayName || 'Khách hàng'
    : participant?.freelancerId ? 'Đã giao người thực hiện' : 'Chưa giao người thực hiện';
  return <article className="job-row" aria-labelledby={'job-' + job.id}>
    <div className="row-index" aria-hidden="true">{String(index).padStart(2, '0')}</div>
    <div className="row-main">
      <h3 id={'job-' + job.id}>{job.title}</h3>
      <p>{job.description}</p>
      <span className="row-date">Đăng {date(job.createdAt)}</span>
    </div>
    <div className="cell">
      <span className="cell-label">Trạng thái</span>
      <strong className={'state-mark ' + statusClass(job.status)}>{statusLabels[job.status] || 'Trạng thái khác'}</strong>
    </div>
    <div className="cell">
      <span className="cell-label">{discovered ? 'Khách hàng' : 'Phân công'}</span>
      <strong>{ownership}</strong>
    </div>
    <div className="cell">
      <span className="cell-label">Ngân sách</span>
      <strong className="amount">{money(job.budgetUsd)}</strong>
    </div>
    <div className="cell next-cell">
      <span className="cell-label">{discovered ? 'Ứng tuyển của bạn' : 'Bước tiếp'}</span>
      <strong>{discovered ? application : nextByStatus[job.status] || 'Theo dõi trạng thái'}</strong>
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
      <div className="table-head" aria-hidden="true">
        <span>#</span><span>Công việc</span><span>Trạng thái</span>
        <span>{kind === 'client' ? 'Phân công' : 'Khách hàng'}</span><span>Ngân sách</span><span>{kind === 'client' ? 'Bước tiếp' : 'Ứng tuyển'}</span>
      </div>
      {result.data.map((job, index) => <JobRow key={job.id} job={job}
        index={result.currentPage * result.pageSize + index + 1} kind={kind} />)}
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

  return <>
    <PageHeading eyebrow="Client / Công việc" title="Công việc bạn tham gia"
      description="Hồ sơ công việc được lấy từ Marketplace và sắp theo thời gian tạo mới nhất."
      aside="Công việc → trạng thái → phân công → bước tiếp" />
    <section className="list-section" aria-label="Danh sách công việc">
      <div className="section-heading"><h2>Danh sách công việc</h2><span>NGUỒN / MARKETPLACE API</span></div>
      <JobResults result={result} loading={loading} error={error} retry={() => setAttempt(value => value + 1)}
        kind="client" requestedPage={page} onPage={setPage} />
    </section>
  </>;
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

  return <>
    <PageHeading eyebrow="Freelancer / Công việc / Khám phá" title="Tìm công việc hợp với bạn"
      description="Duyệt công việc đang mở từ Marketplace. Bộ lọc và tình trạng ứng tuyển phản ánh dữ liệu của tài khoản hiện tại."
      aside="Công việc → trạng thái → khách hàng → ứng tuyển" />
    <form className="filter-panel" onSubmit={submit} aria-label="Lọc công việc">
      <div className="filter-intro"><span className="eyebrow">Bộ lọc khám phá</span><p>Chọn điều kiện rồi nhấn Áp dụng để cập nhật danh sách.</p></div>
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
    <p className="inline-notice">Giai đoạn này chỉ hỗ trợ tìm kiếm và xem danh sách. Thao tác ứng tuyển sẽ được kết nối sau.</p>
    <section className="list-section" aria-label="Kết quả khám phá">
      <div className="section-heading"><h2>Công việc đang mở</h2><span>NGUỒN / MARKETPLACE API</span></div>
      <JobResults result={result} loading={loading} error={error} retry={() => setAttempt(value => value + 1)}
        kind="discover" requestedPage={page} onPage={setPage} />
    </section>
  </>;
}
