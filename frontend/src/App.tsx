import { PageHeading, StatePanel } from './components';
import { useState, type FormEvent } from 'react';
import { Link, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from './api';
import { ClientJobs, FreelancerDiscovery } from './Jobs';
import { ClientApplicants, JobDetail, MyApplications } from './Workflow';
import { useSession } from './session';
import type { User, UserType } from './types';

function Brand() {
  return <div className="brand" aria-label="FreelaX">Freela<span>X</span><small>MARKETPLACE / WORKSPACE</small></div>;
}

function SignIn({ reason }: { reason?: string }) {
  const { signIn } = useSession();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await signIn(email.trim(), password);
      navigate('/', { replace: true });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể đăng nhập. Vui lòng thử lại.');
    } finally {
      setBusy(false);
    }
  }

  return <div className="public-shell">
    <header className="public-masthead"><Brand /><span className="edition">WORKSPACE / 01</span></header>
    <main id="main" className="sign-in-layout">
      <div className="sign-in-intro">
        <span className="eyebrow">FreelaX / Không gian công việc</span>
        <h1>Công việc rõ ràng.<br /><em>Tiến độ có căn cứ.</em></h1>
        <p>Đăng nhập để xem công việc và vai trò của bạn từ tài khoản Marketplace.</p>
        <div className="intro-rule"><span>01 / Công việc</span><span>02 / Quy trình</span><span>03 / Bằng chứng</span></div>
      </div>
      <section className="sign-in-panel" aria-labelledby="sign-in-title">
        <span className="eyebrow">Tài khoản Marketplace</span>
        <h2 id="sign-in-title">Đăng nhập</h2>
        <p>Vai trò Client hoặc Freelancer được xác nhận từ máy chủ sau khi đăng nhập.</p>
        {reason && <p className="inline-notice" role="status">{reason}</p>}
        <form onSubmit={submit}>
          <label className="field">Email<input type="email" autoComplete="username" value={email} onChange={event => setEmail(event.target.value)} required /></label>
          <label className="field">Mật khẩu<input type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} required /></label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="button" type="submit" disabled={busy}>{busy ? 'Đang xác minh…' : 'Vào không gian công việc'}</button>
        </form>
      </section>
    </main>
    <footer className="page-footer"><span>FreelaX / Marketplace</span><span>Dữ liệu từ tài khoản và API thật</span></footer>
  </div>;
}

function Overview({ user }: { user: User }) {
  const isClient = user.userType === 'CLIENT';
  return <>
    <PageHeading eyebrow={isClient ? 'Client / Tổng quan' : 'Freelancer / Tổng quan'}
      title={isClient ? 'Công việc của bạn, trong một mạch rõ ràng.' : 'Tìm việc phù hợp, theo dõi từng bước.'}
      description={isClient
        ? 'Danh sách công việc đang lấy trực tiếp từ Marketplace. Tổng hợp thanh toán và hoạt động sẽ xuất hiện ở giai đoạn tiếp theo.'
        : 'Khám phá công việc đang mở từ Marketplace. Ứng tuyển được theo dõi trực tiếp từ Marketplace; dữ liệu thu nhập sẽ được kết nối ở giai đoạn tiếp theo.'}
      aside={'Đã xác minh tài khoản: ' + user.displayName} />
    <div className="overview-grid">
      <section className="document-panel">
        <span className="category-label">Bắt đầu từ công việc</span>
        <h2>{isClient ? 'Xem các công việc bạn tham gia' : 'Khám phá công việc đang tuyển'}</h2>
        <p>{isClient
          ? 'Trạng thái, ngân sách USD và người thực hiện được trình bày theo từng hồ sơ từ API.'
          : 'Lọc theo từ khóa, ngân sách, thứ tự và tình trạng ứng tuyển của chính bạn.'}</p>
        <Link className="button" to="/work">{isClient ? 'Xem công việc' : 'Khám phá công việc'}</Link>
      </section>
      <aside className="overview-side">
        <span className="eyebrow">Nền tảng hiện tại</span>
        <h2>Nền tảng giao diện</h2>
        <p>Trang tổng quan chỉ hiển thị thông tin đã được xác nhận. Chưa có số liệu ước tính hoặc dữ liệu mô phỏng.</p>
      </aside>
    </div>
  </>;
}

function Account({ user }: { user: User }) {
  return <>
    <PageHeading eyebrow="Tài khoản / Hồ sơ" title="Thông tin tài khoản"
      description="Thông tin dưới đây được lấy từ phiên xác thực Marketplace hiện tại." />
    <section className="account-record" aria-label="Thông tin tài khoản">
      <div><span>Tên hiển thị</span><strong>{user.displayName}</strong></div>
      <div><span>Email</span><strong>{user.email}</strong></div>
      <div><span>Vai trò đã xác minh</span><strong>{user.userType === 'CLIENT' ? 'Khách hàng' : 'Freelancer'}</strong></div>
      <div><span>Mã tài khoản</span><code>{user.id}</code></div>
    </section>
  </>;
}

function NextStage({ title, description }: { title: string; description: string }) {
  return <>
    <PageHeading eyebrow="Bước tiếp theo" title={title} description={description} />
    <StatePanel kind="empty" title="Màn hình này chưa được kết nối"
      body="P05.1 chỉ triển khai nền tảng đăng nhập và danh sách công việc. Không hiển thị dữ liệu giả cho khu vực này." />
  </>;
}

function RoleShell({ user }: { user: User }) {
  const { signOut } = useSession();
  const [logoutError, setLogoutError] = useState('');
  const [loggingOut, setLoggingOut] = useState(false);
  const role: UserType = user.userType;
  const location = useLocation();
  const nav = [
    { path: '/', label: 'Tổng quan', end: true },
    { path: '/work', label: 'Công việc', end: false },
    { path: '/finance', label: role === 'CLIENT' ? 'Thanh toán' : 'Thu nhập', end: false },
    { path: '/activity', label: 'Hoạt động', end: false },
    { path: '/account', label: 'Tài khoản', end: false },
  ];

  async function logout() {
    setLogoutError('');
    setLoggingOut(true);
    try {
      await signOut();
    } catch (error) {
      setLogoutError(error instanceof ApiError ? error.message : 'Không thể đăng xuất. Vui lòng thử lại.');
    } finally {
      setLoggingOut(false);
    }
  }

  return <div className="app-shell">
    <a className="skip-link" href="#main">Đi tới nội dung</a>
    <header className="masthead">
      <Brand />
      <div className="identity"><span>{role === 'CLIENT' ? 'KHÁCH HÀNG' : 'FREELANCER'}</span><strong>{user.displayName}</strong></div>
      <button className="text-button sign-out" type="button" onClick={logout} disabled={loggingOut}>{loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
    </header>
    {logoutError && <p className="form-error logout-error" role="alert">{logoutError}</p>}
    <nav className="primary-nav" aria-label="Điều hướng chính">
      {nav.map(item => <NavLink key={item.path} to={item.path} end={item.end}
        className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>{item.label}</NavLink>)}
    </nav>
    <div className="top-context"><span>FREELAX / {role === 'CLIENT' ? 'CLIENT' : 'FREELANCER'} WORKSPACE</span><span>Vai trò từ /auth/me</span></div>
    {role === 'FREELANCER' && location.pathname.startsWith('/work') && <nav className="subnav" aria-label="Khu vực công việc Freelancer">
      <NavLink to="/work" end>Khám phá</NavLink>
      <NavLink to="/work/applications">Ứng tuyển</NavLink>
      <NavLink to="/work/mine">Công việc của tôi</NavLink>
    </nav>}
    <main id="main" className="view">
      <Routes>
        <Route path="/" element={<Overview user={user} />} />
        <Route path="/work" element={role === 'CLIENT' ? <ClientJobs /> : <FreelancerDiscovery />} />
        {role === 'FREELANCER' && <>
          <Route path="/work/applications" element={<MyApplications />} />
          <Route path="/work/mine" element={<NextStage title="Công việc của tôi" description="Không gian công việc đang thực hiện sẽ được kết nối ở bước tiếp theo." />} />
        </>}
        <Route path="/work/:jobId" element={<JobDetail user={user} />} />
        {role === 'CLIENT' && <Route path="/work/:jobId/applications" element={<ClientApplicants user={user} />} />}
        <Route path="/finance" element={<NextStage title={role === 'CLIENT' ? 'Thanh toán' : 'Thu nhập'}
          description="Dữ liệu tài chính theo từng công việc sẽ được kết nối ở giai đoạn tiếp theo." />} />
        <Route path="/activity" element={<NextStage title="Hoạt động" description="Dòng hoạt động thực sẽ được kết nối ở bước tiếp theo." />} />
        <Route path="/account" element={<Account user={user} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </main>
    <footer className="page-footer"><span>FreelaX / Marketplace</span><span>Không gian công việc theo vai trò</span></footer>
  </div>;
}

export function App() {
  const { session, retryRestore } = useSession();
  if (session.status === 'checking') {
    return <div className="public-shell"><header className="public-masthead"><Brand /></header><main id="main">
      <StatePanel kind="loading" title="Đang khôi phục phiên làm việc" body="Đang xác minh phiên và vai trò tài khoản từ Marketplace." />
    </main></div>;
  }
  if (session.status === 'error') {
    return <div className="public-shell"><header className="public-masthead"><Brand /></header><main id="main">
      <StatePanel kind="error" title="Chưa thể xác minh phiên" body={session.message}
        action={{ label: 'Thử lại', onClick: retryRestore }} />
    </main></div>;
  }
  if (session.status === 'guest') return <SignIn reason={session.reason} />;
  return <RoleShell user={session.user} />;
}
