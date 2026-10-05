import { useState } from 'react';
import { Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, hasAuthority } from './api';
import { AdminDisputes, AdminDisputeDetail } from './AdminDisputes';
import { Account } from './Account';
import { Activity } from './Activity';
import { AuthEntry } from './Auth';
import { ActionGroup, StatePanel } from './components';
import { FinanceHome, TaxRecordDetail, TaxRecordsPage } from './Finance';
import { ClientJobs, FreelancerDiscovery } from './Jobs';
import { Overview } from './Overview';
import { useSession } from './session';
import { ClientApplicants, JobDetail, MyApplications } from './Workflow';
import { MyWork } from './WorkLifecycle';
import type { User, UserType } from './types';

function Brand() {
  return <div className="brand" aria-label="FreelaX">Freela<span>X</span></div>;
}

function RoleShell({ user }: { user: User }) {
  const { signOut } = useSession();
  const navigate = useNavigate();
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
    if (loggingOut) return;
    setLogoutError('');
    setLoggingOut(true);
    try {
      await signOut();
      navigate('/login', { replace: true });
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
      <div className="identity"><span>{role === 'CLIENT' ? 'Client' : 'Freelancer'}</span><strong>{user.displayName}</strong></div>
      <ActionGroup label="Phiên làm việc">
        {hasAuthority(user, 'ROLE_ADMIN') && <NavLink className="text-link admin-entry" to="/admin/disputes">Quản trị</NavLink>}
        <button className="text-button sign-out" type="button" onClick={() => void logout()} disabled={loggingOut}>
          {loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
      </ActionGroup>
    </header>
    {logoutError && <p className="form-error logout-error" role="alert">{logoutError}</p>}
    <nav className="primary-nav" aria-label="Điều hướng chính">
      {nav.map(item => <NavLink key={item.path} to={item.path} end={item.end}
        className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>{item.label}</NavLink>)}
    </nav>
    {role === 'FREELANCER' && location.pathname.startsWith('/work') && <nav className="subnav" aria-label="Khu vực công việc Freelancer">
      <NavLink to="/work" end>Khám phá</NavLink>
      <NavLink to="/work/applications">Ứng tuyển</NavLink>
      <NavLink to="/work/mine">Công việc của tôi</NavLink>
    </nav>}
    <main id="main" className="view" tabIndex={-1}>
      <Routes>
        <Route path="/" element={<Overview user={user} />} />
        <Route path="/work" element={role === 'CLIENT' ? <ClientJobs /> : <FreelancerDiscovery />} />
        {role === 'FREELANCER' && <>
          <Route path="/work/applications" element={<MyApplications />} />
          <Route path="/work/mine" element={<MyWork />} />
        </>}
        <Route path="/work/:jobId" element={<JobDetail user={user} />} />
        {role === 'CLIENT' && <Route path="/work/:jobId/applications" element={<ClientApplicants user={user} />} />}
        <Route path="/finance" element={<FinanceHome user={user} />} />
        <Route path="/finance/tax-records" element={<TaxRecordsPage />} />
        <Route path="/finance/tax-records/:taxRecordId" element={<TaxRecordDetail />} />
        <Route path="/activity" element={<Activity />} />
        <Route path="/admin/disputes" element={<AdminDisputes user={user} />} />
        <Route path="/admin/disputes/:disputeId" element={<AdminDisputeDetail user={user} />} />
        <Route path="/account" element={<Account user={user} onLogout={() => void logout()} loggingOut={loggingOut} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </main>
    <footer className="page-footer"><span>FreelaX / Marketplace</span></footer>
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
  if (session.status === 'guest') return <AuthEntry reason={session.reason} brand={<Brand />} />;
  return <RoleShell user={session.user} />;
}
