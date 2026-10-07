import { PageHeading } from './components';
import type { User } from './types';
import { OwnProfile, ProfilePlate } from './Profile';
import { LogOut } from 'lucide-react';
import { RoughBurst, RoughUnderline } from './ui/kinetic';

export function Account({ user, onLogout, loggingOut, onReconcileUser }: {
  user: User; onLogout: () => void; loggingOut: boolean; onReconcileUser?: () => Promise<void>;
}) {
  const client = user.userType === 'CLIENT';
  return <div className="account-page">
    <PageHeading eyebrow="Tài khoản / Marketplace" title={<>Tài khoản <span className="profile-title-accent">của bạn.<RoughUnderline seedKey="account-title" className="profile-title-underline" /><RoughBurst seedKey="account-title-rays" size={34} accent="ink" className="profile-title-rays" /></span></>}
      description="Thông tin xác thực được lấy từ /auth/me. Vai trò hiện tại quyết định các khu vực công việc và tài chính có thể xem."
      aside={client ? 'CLIENT / Đăng việc và duyệt bàn giao' : 'FREELANCER / Ứng tuyển và bàn giao'} />
    <section className="account-document" aria-labelledby="account-identity-title">
      <ProfilePlate name={user.displayName} role={user.userType} />
      <div className="account-identity-copy"><span className="account-source-label">Danh tính phiên</span>
      <h2 id="account-identity-title">{user.displayName}</h2>
      <dl className="account-record">
        <div><dt>Email</dt><dd>{user.email}</dd></div>
        <div><dt>Vai trò</dt><dd>{client ? 'Client / Khách hàng' : 'Freelancer / Người thực hiện'}</dd></div>
      </dl>
      <details className="technical-evidence account-technical"><summary>Mã tài khoản Marketplace</summary>
        <code>{user.id}</code></details></div>
    </section>
    <OwnProfile key={user.id} user={user} onReconcileUser={onReconcileUser} />
    <section className="account-actions" aria-label="Phiên đăng nhập">
      <div><span className="eyebrow">Phiên hiện tại</span><h2>Hoàn tất phiên làm việc?</h2>
        <p>Đăng xuất khỏi tài khoản này trên trình duyệt hiện tại.</p></div>
      <button className="button account-logout" type="button" onClick={onLogout} disabled={loggingOut}>
        <LogOut size={20} aria-hidden="true" />
        {loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
      </button>
    </section>
  </div>;
}
