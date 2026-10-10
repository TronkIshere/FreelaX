import { useState } from 'react';
import { motion } from 'motion/react';
import { Bell, BriefcaseBusiness, ChartNoAxesColumnIncreasing, House, UserRound } from 'lucide-react';
import { Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, hasAuthority } from './api';
import { AdminDisputes, AdminDisputeDetail } from './AdminDisputes';
import { Account } from './Account';
import { AdminReviews, AdminReview } from './AdminReviews';
import { PublicProfile } from './PublicProfile';
import { Activity } from './Activity';
import { AuthEntry } from './Auth';
import { ActionGroup, StatePanel } from './components';
import { FinanceHome, TaxRecordDetail, TaxRecordsPage } from './Finance';
import { PartnerReconciliation } from './PartnerReconciliation';
import { UnifiedReconciliation } from './UnifiedReconciliation';
import { ClientJobs, FreelancerDiscovery } from './Jobs';
import { Overview } from './Overview';
import { useSession } from './session';
import { ClientApplicants, JobDetail, MyApplications } from './Workflow';
import { MyWork } from './WorkLifecycle';
import { JobEditor } from './JobEditor';
import type { User, UserType } from './types';
import { kineticVariants, useKineticMotion } from './ui/kinetic';

function Brand() {
  return <div className="brand" aria-label="FreelaX">Freela<span>X</span></div>;
}

function PrimaryNavigationItem({ path, label, end }: { path: string; label: string; end: boolean }) {
  const icons: Record<string, typeof House> = { '/': House, '/work': BriefcaseBusiness,
    '/finance': ChartNoAxesColumnIncreasing, '/activity': Bell, '/account': UserRound };
  const Icon = icons[path];
  const { enabled, transition } = useKineticMotion();
  const [focused, setFocused] = useState(false);
  return <NavLink to={path} end={end} onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}
    className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
    {({ isActive }) => <>
      <motion.span className="nav-content" data-motion={enabled ? 'on' : 'off'} initial={false}
        variants={kineticVariants('ledger', enabled)} animate={enabled && focused ? 'active' : 'rest'}
        whileHover={enabled ? 'active' : undefined} transition={transition}>
        <Icon className="nav-mark" size={24} strokeWidth={1.8} aria-hidden="true" focusable="false" /><span>{label}</span>
      </motion.span>
      {isActive && <motion.span className="nav-accent" aria-hidden="true"
        initial={enabled ? { scaleX: 0.65 } : false} animate={{ scaleX: 1 }} transition={transition} />}
    </>}
  </NavLink>;
}

function RoleShell({ user }: { user: User }) {
  const { signOut, reconcileUser } = useSession();
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
      <div className="shell-brand"><Brand /><span className="shell-signature">Work / People / Payment</span></div>
      <nav className="primary-nav" aria-label="Điều hướng chính">
        {nav.map(item => <PrimaryNavigationItem key={item.path} path={item.path} label={item.label} end={item.end} />)}
      </nav>
      <div className="shell-account">
        <div className="identity"><span>{hasAuthority(user, 'ROLE_ADMIN') ? 'Admin' : role === 'CLIENT' ? 'Client' : 'Freelancer'}</span><strong>{user.displayName}</strong></div>
        <ActionGroup label="Phiên làm việc">
          {hasAuthority(user, 'ROLE_ADMIN') && <NavLink className="text-link admin-entry" to="/admin/disputes">Quản trị</NavLink>}
          <button className="text-button sign-out" type="button" onClick={() => void logout()} disabled={loggingOut}>
            {loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
        </ActionGroup>
      </div>
    </header>
    {logoutError && <p className="form-error logout-error" role="alert">{logoutError}</p>}
    {role === 'FREELANCER' && location.pathname.startsWith('/work') && <nav className="subnav" aria-label="Khu vực công việc Freelancer">
      <NavLink to="/work" end>Khám phá</NavLink>
      <NavLink to="/work/applications">Ứng tuyển</NavLink>
      <NavLink to="/work/mine">Công việc của tôi</NavLink>
    </nav>}
    {hasAuthority(user, 'ROLE_ADMIN') && location.pathname.startsWith('/admin/') && <nav className="subnav" aria-label="Khu vực quản trị"><NavLink to="/admin/disputes">Tranh chấp</NavLink><NavLink to="/admin/reviews">Đánh giá được báo cáo</NavLink><NavLink to="/admin/partner-reconciliation">Đối soát ký quỹ</NavLink><NavLink to="/admin/unified-reconciliation">Đối soát USDC</NavLink></nav>}
    <main id="main" className="view" tabIndex={-1}>
      <Routes>
        <Route path="/" element={<Overview user={user} />} />
        <Route path="/work" element={role === 'CLIENT' ? <ClientJobs /> : <FreelancerDiscovery />} />
        {role === 'CLIENT' && <>
          <Route path="/work/new" element={<JobEditor user={user} />} />
          <Route path="/work/:jobId/edit" element={<JobEditor user={user} />} />
        </>}
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
        <Route path="/admin/reviews" element={<AdminReviews user={user} />} />
        <Route path="/admin/reviews/:reviewId" element={<AdminReview user={user} />} />
        <Route path="/admin/partner-reconciliation" element={hasAuthority(user, 'ROLE_ADMIN') ? <PartnerReconciliation /> : <Navigate to="/" replace />} />
        <Route path="/admin/unified-reconciliation" element={hasAuthority(user, 'ROLE_ADMIN') ? <UnifiedReconciliation /> : <Navigate to="/" replace />} />
        <Route path="/profiles/:userId" element={<PublicProfile user={user} />} />
        <Route path="/account" element={<Account user={user} onLogout={() => void logout()} loggingOut={loggingOut} onReconcileUser={reconcileUser} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </main>
    <footer className="page-footer"><span className="app-footer-brand">FreelaX / Marketplace</span></footer>
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
