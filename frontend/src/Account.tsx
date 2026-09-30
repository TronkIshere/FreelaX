import { PageHeading } from './components';
import type { User } from './types';

export function Account({ user, onLogout, loggingOut }: {
  user: User; onLogout: () => void; loggingOut: boolean;
}) {
  const client = user.userType === 'CLIENT';
  return <>
    <PageHeading eyebrow="Tài khoản / Marketplace" title="Tài khoản của bạn."
      description="Thông tin xác thực được lấy từ /auth/me. Vai trò hiện tại quyết định các khu vực công việc và tài chính có thể xem."
      aside={client ? 'CLIENT / Đăng việc và duyệt bàn giao' : 'FREELANCER / Ứng tuyển và bàn giao'} />
    <section className="account-document" aria-labelledby="account-identity-title">
      <span className="category-label">Hồ sơ đã xác minh</span>
      <h2 id="account-identity-title">{user.displayName}</h2>
      <dl className="account-record">
        <div><dt>Email</dt><dd>{user.email}</dd></div>
        <div><dt>Vai trò</dt><dd>{client ? 'Client / Khách hàng' : 'Freelancer / Người thực hiện'}</dd></div>
      </dl>
      <details className="technical-evidence account-technical"><summary>Mã tài khoản Marketplace</summary>
        <code>{user.id}</code></details>
    </section>
    <section className="account-actions" aria-label="Phiên đăng nhập">
      <div><span className="eyebrow">Phiên hiện tại</span><h2>Hoàn tất phiên làm việc?</h2>
        <p>Đăng xuất khỏi tài khoản này trên trình duyệt hiện tại.</p></div>
      <button className="button button-secondary" type="button" onClick={onLogout} disabled={loggingOut}>
        {loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
      </button>
    </section>
  </>;
}
