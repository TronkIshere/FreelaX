import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ContractLifecycle } from './ContractLifecycle';
import { ApiError, api } from './api';
import { ActionGroup, EvidenceDisclosure, PageHeading, SectionHeading, StatePanel } from './components';
import { Pagination } from './Jobs';
import { date, jobLabel, money, shortId, submissionLabel } from './status';
import type { Job, JobSubmission, Page, User } from './types';

const MAX_TEXT = 10000;
const MAX_URL = 2048;
const railSteps = ['Assigned', 'Working', 'Submitted', 'Review', 'Revision', 'Resubmitted', 'Approved'];

function message(error: unknown) {
  return error instanceof Error ? error.message : 'Yêu cầu không thành công. Vui lòng thử lại.';
}

function timestamp(value: string | null | undefined) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—';
}

export function safeDeliverableUrl(value: string | null | undefined): URL | null {
  if (!value) return null;
  try {
    const url = new URL(value.trim());
    return (url.protocol === 'https:' || url.protocol === 'http:') && !url.username && !url.password ? url : null;
  } catch {
    return null;
  }
}

function DeliverableLink({ value }: { value: string | null }) {
  if (!value) return null;
  const url = safeDeliverableUrl(value);
  if (!url) return <p className="deliverable-warning">Liên kết bàn giao không có định dạng HTTP/HTTPS an toàn.</p>;
  return <div className="deliverable-link">
    <span className="cell-label">Liên kết bàn giao · {url.hostname}</span>
    <span className="deliverable-address">{value}</span>
    <a href={url.href} target="_blank" rel="noopener noreferrer">Mở liên kết bàn giao ↗</a>
  </div>;
}

function currentRailIndex(job: Job, latest: JobSubmission | undefined) {
  if (job.status === 'IN_PROGRESS') return 1;
  if (job.status === 'REVISION_REQUESTED') return 4;
  if (job.status === 'COMPLETED') return 6;
  if (job.status === 'SUBMITTED_FOR_REVIEW') return latest && latest.version > 1 ? 5 : 3;
  return 0;
}

function ownership(job: Job, user: User) {
  if (job.status === 'COMPLETED') return 'Quy trình công việc đã hoàn thành';
  if (job.status === 'IN_PROGRESS') return user.userType === 'FREELANCER'
    ? 'Đến lượt bạn bàn giao công việc' : 'Đang chờ Freelancer bàn giao';
  if (job.status === 'SUBMITTED_FOR_REVIEW') return user.userType === 'CLIENT'
    ? 'Cần bạn duyệt bàn giao' : 'Đang chờ Client phản hồi';
  if (job.status === 'REVISION_REQUESTED') return user.userType === 'FREELANCER'
    ? 'Bạn cần chỉnh sửa theo phản hồi' : 'Đang chờ Freelancer gửi bản sửa';
  return jobLabel(job.status);
}

function LatestSubmission({ submission }: { submission: JobSubmission }) {
  return <section className="latest-submission" id="latest-submission" tabIndex={-1} aria-label="Bản bàn giao mới nhất">
    <SectionHeading title={'Bản bàn giao #' + submission.version} aside={submissionLabel(submission.status)} />
    {submission.reviewerFeedback && <section className="feedback-document" aria-label={'Phản hồi Client cho bản #' + submission.version}>
      <SectionHeading title="Phản hồi Client" level={3} />
      <p>{submission.reviewerFeedback}</p>
      <span className="metadata">Đã phản hồi {timestamp(submission.reviewedAt)}</span>
    </section>}
    <p className="submission-summary">{submission.summary}</p>
    <DeliverableLink value={submission.deliverableUrl} />
    <div className="submission-meta">
      <span>Gửi {timestamp(submission.createdAt)}</span>
      {submission.reviewedAt && <span>Đã xem xét {timestamp(submission.reviewedAt)}</span>}
      <span>Cập nhật {timestamp(submission.updatedAt)}</span>
    </div>
  </section>;
}

function HistoryLedger({ submissions, loading, error, retry }: {
  submissions: JobSubmission[]; loading: boolean; error: string; retry: () => void;
}) {
  const older = submissions.slice(1);
  return <section className="submission-ledger" aria-label="Lịch sử bàn giao">
    <SectionHeading title="Lịch sử bàn giao" aside={submissions.length + ' phiên bản'} />
    {loading ? <p role="status">Đang tải lịch sử bàn giao…</p>
      : error ? <div className="inline-state-error" role="alert"><p>{error}</p>
        <button className="text-button" type="button" onClick={retry}>Tải lại lịch sử</button></div>
      : submissions.length === 0 ? <p className="ledger-empty">Chưa có bản bàn giao nào được ghi nhận.</p>
      : older.length === 0 ? <p className="ledger-empty">Bản mới nhất được trình bày ở trên. Chưa có phiên bản cũ.</p>
      : <div className="ledger-rows">{older.map((submission) =>
        <details className="ledger-row" key={submission.id}>
          <summary><strong>#{submission.version}</strong><span>{submissionLabel(submission.status)}</span>
            <time>{timestamp(submission.createdAt)}</time></summary>
          <div className="ledger-body"><p>{submission.summary}</p>
          {submission.reviewerFeedback && <p className="ledger-feedback">Phản hồi: {submission.reviewerFeedback}</p>}
          {submission.deliverableUrl && <div className="ledger-link"><DeliverableLink value={submission.deliverableUrl} /></div>}
          <div className="ledger-dates">{submission.reviewedAt && <span>Đã xem xét {timestamp(submission.reviewedAt)}</span>}
            {submission.updatedAt && <span>Cập nhật {timestamp(submission.updatedAt)}</span>}</div></div>
        </details>)}</div>}
  </section>;
}

export function WorkLifecycle(props: { job: Job; user: User; onJobUpdated: (job: Job) => void; children?: ReactNode; footer?: ReactNode }) {
  return props.job.contract ? <ContractLifecycle {...props} /> : <LegacyWorkLifecycle {...props} />;
}

function LegacyWorkLifecycle({ job, user, onJobUpdated, children, footer }: {
  job: Job; user: User; onJobUpdated: (job: Job) => void; children?: ReactNode; footer?: ReactNode;
}) {
  const client = user.userType === 'CLIENT' && job.clientUserId === user.id;
  const freelancer = user.userType === 'FREELANCER' && job.freelancerId === user.id;
  const [submissions, setSubmissions] = useState<JobSubmission[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState('');
  const [mutationError, setMutationError] = useState('');
  const [syncError, setSyncError] = useState('');
  const [success, setSuccess] = useState('');
  const [summary, setSummary] = useState('');
  const [deliverableUrl, setDeliverableUrl] = useState('');
  const [feedback, setFeedback] = useState('');
  const [decision, setDecision] = useState<'revision' | 'approve' | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const busyRef = useRef(false);

  useEffect(() => {
    if (!client && !freelancer) {
      setHistoryLoading(false);
      return;
    }
    let active = true;
    setHistoryLoading(true);
    setHistoryError('');
    api.submissions(job.id).then(
      list => { if (active) { setSubmissions([...list].sort((a, b) => b.version - a.version)); setHistoryLoading(false); } },
      cause => { if (active) { setHistoryError(message(cause)); setHistoryLoading(false); } },
    );
    return () => { active = false; };
  }, [job.id, attempt, client, freelancer]);

  const synchronize = useCallback(async () => {
    const [nextJob, list] = await Promise.all([api.job(job.id), api.submissions(job.id)]);
    setSubmissions([...list].sort((a, b) => b.version - a.version));
    setHistoryError('');
    onJobUpdated(nextJob);
    return nextJob;
  }, [job.id, onJobUpdated]);

  async function retrySync() {
    try {
      await synchronize();
      setConfirmed(false);
      setSyncError('');
      setMutationError('');
    } catch (cause) {
      setSyncError(message(cause));
    }
  }

  async function mutate(action: () => Promise<unknown>, expectedStatus: string, successText: string) {
    if (busyRef.current || confirmed) return;
    busyRef.current = true;
    setBusy(true);
    setMutationError('');
    setSyncError('');
    setSuccess('');
    let accepted = false;
    try {
      await action();
      accepted = true;
      setConfirmed(true);
      const fresh = await synchronize();
      if (fresh.status === expectedStatus) {
        setConfirmed(false);
        setDecision(null);
        setSummary('');
        setDeliverableUrl('');
        setFeedback('');
        setSuccess(successText);
      } else {
        setSyncError('Thao tác đã được ghi nhận. Tải lại trạng thái từ Marketplace trước khi tiếp tục.');
      }
    } catch (cause) {
      if (accepted) {
        setSyncError('Thao tác đã được ghi nhận nhưng chưa tải lại được trạng thái: ' + message(cause));
        setSuccess(successText);
      } else {
        try {
          const fresh = await synchronize();
          if (fresh.status === expectedStatus) {
            setConfirmed(false);
            setDecision(null);
            setSuccess(successText);
          } else {
            setMutationError(message(cause));
          }
        } catch {
          setMutationError(message(cause));
          if (cause instanceof ApiError && cause.status === 0) {
            setConfirmed(true);
            setSyncError('Chưa xác nhận được kết quả. Tải lại từ Marketplace trước khi thử tiếp.');
          }
        }
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!freelancer || (job.status !== 'IN_PROGRESS' && job.status !== 'REVISION_REQUESTED')) return;
    const text = summary.trim();
    const url = deliverableUrl.trim();
    if (!text || text.length > MAX_TEXT) {
      setMutationError('Tóm tắt bàn giao là bắt buộc và không quá 10.000 ký tự.');
      return;
    }
    if (url.length > MAX_URL || (url && !safeDeliverableUrl(url))) {
      setMutationError('Liên kết bàn giao phải là URL HTTP/HTTPS hợp lệ, tối đa 2.048 ký tự.');
      return;
    }
    void mutate(() => api.submitWork(job.id, text, url || null), 'SUBMITTED_FOR_REVIEW',
      'Đã gửi bàn giao. Trạng thái mới được lấy từ Marketplace.');
  }

  function requestRevision(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!client || job.status !== 'SUBMITTED_FOR_REVIEW') return;
    const text = feedback.trim();
    if (!text || text.length > MAX_TEXT) {
      setMutationError('Phản hồi chỉnh sửa là bắt buộc và không quá 10.000 ký tự.');
      return;
    }
    void mutate(() => api.requestRevision(job.id, text), 'REVISION_REQUESTED',
      'Đã gửi yêu cầu chỉnh sửa. Phản hồi được lưu trên bản bàn giao.');
  }

  const latest = submissions[0];
  const active = currentRailIndex(job, latest);
  const tone = job.status === 'REVISION_REQUESTED' ? 'revision'
    : job.status === 'COMPLETED' ? 'complete'
      : job.status === 'SUBMITTED_FOR_REVIEW' ? 'review' : 'working';
  const canSubmit = freelancer && !historyLoading && !historyError &&
    (job.status === 'IN_PROGRESS' ||
      (job.status === 'REVISION_REQUESTED' && latest?.status === 'REVISION_REQUESTED'));
  const canReview = client && job.status === 'SUBMITTED_FOR_REVIEW' &&
    !historyLoading && !historyError && latest?.status === 'SUBMITTED';

  if (!client && !freelancer) return null;
  return <div className={'lifecycle lifecycle-' + tone}>
    <section className="ownership-band" aria-label="Lượt thực hiện">
      <div><h2>{ownership(job, user)}</h2>
        {job.status === 'COMPLETED' && <p>Xử lý tài chính có thể tiếp tục sau khi phần việc hoàn tất.</p>}</div>
      <ActionGroup>
        {job.status === 'REVISION_REQUESTED' && latest?.reviewerFeedback && <a className="text-link" href="#latest-submission">Xem phản hồi ↓</a>}
        {canSubmit && <a className="text-link" href="#work-primary-action">{job.status === 'REVISION_REQUESTED' ? 'Soạn bản sửa' : 'Soạn bàn giao'} ↓</a>}
        {canReview && <a className="text-link" href="#latest-submission">Xem bản bàn giao ↓</a>}
        {job.status === 'COMPLETED' && <Link className="button" to={'/finance?jobId=' + encodeURIComponent(job.id)}>Xem bằng chứng thanh toán →</Link>}
      </ActionGroup>
    </section>

    {children}
    {latest && <LatestSubmission submission={latest} />}

    {canSubmit && <section className="work-composer" id="work-primary-action" tabIndex={-1} aria-label="Soạn bàn giao">
      <SectionHeading title={job.status === 'REVISION_REQUESTED' ? 'Gửi bản sửa' : 'Gửi bàn giao'} />
      <form onSubmit={submit}>
        <label>Tóm tắt bàn giao <span>· bắt buộc, tối đa 10.000 ký tự</span>
          <textarea value={summary} onChange={event => setSummary(event.target.value)}
            maxLength={MAX_TEXT} required rows={6} disabled={busy || confirmed} /></label>
        <label>Liên kết bàn giao <span>· tùy chọn, HTTP/HTTPS</span>
          <input type="url" value={deliverableUrl} onChange={event => setDeliverableUrl(event.target.value)}
            maxLength={MAX_URL} disabled={busy || confirmed} placeholder="https://..." /></label>
        <ActionGroup>
          <button className="button" type="submit" disabled={busy || confirmed}>
            {busy ? 'Đang gửi…' : job.status === 'REVISION_REQUESTED' ? 'Gửi bản sửa' : 'Gửi bàn giao'}</button></ActionGroup>
      </form>
    </section>}

    {canReview && <section className="review-action" id="work-primary-action" tabIndex={-1} aria-label="Quyết định duyệt bàn giao">
      <SectionHeading title="Quyết định của bạn" />
      {!decision && <ActionGroup>
        <button className="button" type="button" onClick={() => setDecision('approve')} disabled={busy || confirmed}>Duyệt bàn giao</button>
        <button className="button button-secondary" type="button" onClick={() => setDecision('revision')}
          disabled={busy || confirmed}>Yêu cầu chỉnh sửa</button>
      </ActionGroup>}
      {decision === 'revision' && <form className="decision-form" onSubmit={requestRevision}>
        <p>Phản hồi này sẽ gắn với bản bàn giao #{latest.version}; Freelancer sẽ gửi một phiên bản mới.</p>
        <label>Phản hồi chỉnh sửa <span>· bắt buộc, tối đa 10.000 ký tự</span>
          <textarea value={feedback} onChange={event => setFeedback(event.target.value)}
            maxLength={MAX_TEXT} required rows={5} disabled={busy || confirmed} /></label>
        <ActionGroup><button className="button" type="submit" disabled={busy || confirmed}>
          {busy ? 'Đang gửi…' : 'Gửi yêu cầu chỉnh sửa'}</button>
          <button className="button button-secondary" type="button" onClick={() => setDecision(null)}
            disabled={busy || confirmed}>Quay lại</button></ActionGroup>
      </form>}
      {decision === 'approve' && <div className="approval-confirm" role="group" aria-label="Xác nhận duyệt bàn giao">
        <strong>Duyệt bản bàn giao #{latest.version}?</strong>
        <p>Duyệt sẽ hoàn tất phần việc và kích hoạt xử lý thanh toán/chi trả của backend. Tiền về ngân hàng có thể tiếp tục xử lý sau khi công việc chuyển sang Hoàn thành.</p>
        <ActionGroup><button className="button" type="button" disabled={busy || confirmed}
          onClick={() => void mutate(() => api.approveWork(job.id), 'COMPLETED',
            'Đã duyệt bàn giao. Công việc hoàn thành theo trạng thái Marketplace.')}>
          {busy ? 'Đang duyệt…' : 'Xác nhận duyệt'}</button>
          <button className="button button-secondary" type="button" disabled={busy || confirmed}
            onClick={() => setDecision(null)}>Quay lại</button></ActionGroup>
      </div>}
    </section>}

    {success && <p className="lifecycle-success" role="status">{success}</p>}
    {mutationError && <p className="form-error" role="alert">{mutationError}</p>}
    {syncError && <div className="inline-state-error" role="alert"><p>{syncError}</p>
      <button className="text-button" type="button" onClick={() => void retrySync()}>Tải lại trạng thái</button></div>}
    {!historyLoading && !historyError && job.status === 'SUBMITTED_FOR_REVIEW' && !latest &&
      <p className="inline-state-error" role="alert">Marketplace chưa trả bản bàn giao để duyệt. Hãy tải lại lịch sử.</p>}
    <nav className="workflow-rail" aria-label="Tiến trình công việc; các bước trình bày, không phải trạng thái API">
      {railSteps.map((step, index) => <span key={step} className={index === active ? 'current'
        : index < active ? 'past' : 'future'} aria-current={index === active ? 'step' : undefined}>{step}</span>)}
    </nav>
    <HistoryLedger submissions={submissions} loading={historyLoading} error={historyError}
      retry={() => setAttempt(value => value + 1)} />
    {footer}
    {submissions.length > 0 && <EvidenceDisclosure summary="Mã tham chiếu bàn giao">
      <dl className="reference-list">{submissions.map(item => <div key={item.id}>
        <dt>Bản #{item.version}</dt><dd><code>{item.id}</code></dd></div>)}</dl>
    </EvidenceDisclosure>}
  </div>;
}

export function MyWork() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<Job> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api.myJobs(page).then(
      data => { if (active) { setResult(data); setLoading(false); } },
      cause => { if (active) { setError(message(cause)); setLoading(false); } },
    );
    return () => { active = false; };
  }, [page, attempt]);
  return <>
    <PageHeading eyebrow="Freelancer / Công việc / Công việc của tôi" title="Công việc của tôi"
      description="Các công việc được giao và tiến độ thực tế từ Marketplace."
      aside="Công việc → bàn giao → phản hồi → hoàn thành" />
    {loading ? <StatePanel kind="loading" title="Đang tải công việc" body="Đang lấy công việc đã giao từ Marketplace." />
      : error ? <StatePanel kind="error" title="Không thể tải công việc" body={error}
        action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />
      : !result || result.data.length === 0 ? <StatePanel kind="empty" title="Chưa có công việc được giao"
        body="Công việc xuất hiện ở đây sau khi Client chọn bạn từ danh sách ứng tuyển." />
      : <><section className="my-work-list" aria-label="Công việc được giao">
        {result.data.map(item => <article key={item.id} className="my-work-row">
          <div><span className="eyebrow">Công việc / {date(item.createdAt)}</span>
            <h2><Link to={'/work/' + item.id}>{item.title}</Link></h2>
            <p>{item.description}</p></div>
          <div><span className="cell-label">Trạng thái</span><strong>{jobLabel(item.status)}</strong></div>
          <div><span className="cell-label">Ngân sách</span><strong>{money(item.budgetUsd)}</strong></div>
          <div><span className="cell-label">Mã công việc</span><code title={item.id}>{shortId(item.id)}</code></div>
        </article>)}
      </section><Pagination page={result} onPage={setPage} /></>}
  </>;
}
