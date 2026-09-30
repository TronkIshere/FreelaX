import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from './api';
import { PageHeading, StatePanel } from './components';
import { applicationLabel, date, jobLabel } from './status';
import type { Job, MyApplication, Page, User } from './types';

const errorText = (cause: unknown) => cause instanceof Error ? cause.message : 'Không thể tải dữ liệu từ Marketplace.';

function nextAction(user: User, jobs: Job[], applications: Page<MyApplication> | null) {
  if (user.userType === 'CLIENT') {
    const review = jobs.find(job => job.status === 'SUBMITTED_FOR_REVIEW');
    if (review) return { title: 'Duyệt bản bàn giao', text: review.title, href: '/work/' + review.id };
    const open = jobs.find(job => job.status === 'OPEN');
    if (open) return { title: 'Xem ứng tuyển', text: open.title, href: '/work/' + open.id + '/applications' };
    return { title: 'Theo dõi công việc', text: 'Xem trạng thái các hồ sơ đang tham gia.', href: '/work' };
  }
  const revision = jobs.find(job => job.status === 'REVISION_REQUESTED');
  if (revision) return { title: 'Gửi bản sửa', text: revision.title, href: '/work/' + revision.id };
  const working = jobs.find(job => job.status === 'IN_PROGRESS');
  if (working) return { title: 'Bàn giao công việc', text: working.title, href: '/work/' + working.id };
  if (applications?.totalElements) return { title: 'Theo dõi ứng tuyển', text: 'Xem phản hồi từ các công việc đã ứng tuyển.', href: '/work/applications' };
  return { title: 'Khám phá công việc', text: 'Tìm công việc đang mở từ Marketplace.', href: '/work' };
}

export function Overview({ user }: { user: User }) {
  const [jobs, setJobs] = useState<Page<Job> | null>(null);
  const [applications, setApplications] = useState<Page<MyApplication> | null>(null);
  const [applicationsError, setApplicationsError] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const client = user.userType === 'CLIENT';

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setApplicationsError('');
    const work = api.myJobs(0, 8);
    const applied = client ? Promise.resolve(null) : api.myApplications(0, 'ALL', 4);
    Promise.allSettled([work, applied]).then(([jobResult, applicationResult]) => {
      if (!active) return;
      if (jobResult.status === 'fulfilled') setJobs(jobResult.value);
      else setError(errorText(jobResult.reason));
      if (applicationResult.status === 'fulfilled') setApplications(applicationResult.value);
      else setApplicationsError(errorText(applicationResult.reason));
      setLoading(false);
    });
    return () => { active = false; };
  }, [client, attempt]);

  const recent = jobs?.data.slice(0, 4) ?? [];
  const action = nextAction(user, jobs?.data ?? [], applications);
  return <>
    <PageHeading eyebrow={client ? 'Client / Tổng quan' : 'Freelancer / Tổng quan'}
      title={client ? 'Công việc, người thực hiện, bước kế tiếp.' : 'Việc đang làm. Việc đang tìm.'}
      description={client
        ? 'Theo dõi các công việc bạn tham gia và đi thẳng tới bước cần xử lý.'
        : 'Công việc và ứng tuyển của bạn, cập nhật từ Marketplace.'}
      aside={user.displayName + ' · ' + (client ? 'Client' : 'Freelancer')} />
    {loading && <StatePanel kind="loading" title="Đang mở hồ sơ công việc"
      body="Marketplace đang trả trạng thái công việc và ứng tuyển của bạn." />}
    {!loading && error && <StatePanel kind="error" title="Không thể tải tổng quan" body={error}
      action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
    {!loading && !error && jobs && <>
      <div className="overview-count"><span className="eyebrow">Hồ sơ từ Marketplace</span>
        <strong>{jobs.totalElements} công việc trong tài khoản</strong>
        <span>{recent.length} hồ sơ gần đây được hiển thị bên dưới</span></div>
      <div className="overview-grid">
        <section className="overview-recent" aria-labelledby="overview-recent-title">
          <div className="overview-section-head"><span className="category-label">Công việc gần đây</span>
            <Link to="/work">Xem tất cả →</Link></div>
          <h2 id="overview-recent-title">Từng hồ sơ, một trạng thái rõ ràng.</h2>
          {recent.length === 0 ? <StatePanel kind="empty" title="Chưa có công việc trong tài khoản"
            body={client ? 'Khi có công việc, trạng thái sẽ xuất hiện tại đây.' :
              'Khám phá công việc đang mở và theo dõi ứng tuyển của bạn.'} /> :
            <div className="overview-rows">{recent.map((job, index) => <article className="overview-row" key={job.id}>
              <span aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
              <div><h3><Link to={'/work/' + job.id}>{job.title}</Link></h3>
                <small>{date(job.createdAt)} · {jobLabel(job.status)}</small></div>
              <Link className="overview-row-arrow" to={'/work/' + job.id} aria-label={'Mở ' + job.title}>↗</Link>
            </article>)}</div>}
        </section>
        <aside className="overview-next" aria-label="Bước tiếp theo">
          <span className="eyebrow">Bước hữu ích tiếp theo</span>
          <h2>{action.title}</h2><p>{action.text}</p>
          <Link className="button" to={action.href}>Đi tới công việc →</Link>
          {!client && <p className="overview-secondary"><Link to="/finance">Thu nhập theo công việc ↗</Link></p>}
        </aside>
      </div>
      {!client && <section className="overview-applications" aria-labelledby="overview-applications-title">
        <div className="overview-section-head"><span className="category-label">Ứng tuyển</span>
          <Link to="/work/applications">Xem tất cả →</Link></div>
        <h2 id="overview-applications-title">Phản hồi gần đây.</h2>
        {applicationsError && <StatePanel kind="error" title="Chưa đọc được ứng tuyển" body={applicationsError}
          action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />}
        {!applicationsError && applications && (applications.data.length === 0
          ? <p className="overview-empty-line">Bạn chưa có ứng tuyển nào.</p>
          : <div className="overview-application-rows">{applications.data.slice(0, 3).map(item =>
            <article key={item.id}><Link to={'/work/' + item.job.id}>{item.job.title}</Link>
              <strong>{applicationLabel(item.status)}</strong></article>)}</div>)}
      </section>}
    </>}
  </>;
}
