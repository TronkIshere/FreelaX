import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api } from './api';
import { useSession } from './session';
import type { BankCode, RegisterInput, UserType } from './types';

const banks: { value: BankCode; label: string }[] = [
  { value: 'VIETCOMBANK', label: 'Vietcombank' },
  { value: 'VIETINBANK', label: 'VietinBank' },
  { value: 'BIDV', label: 'BIDV' },
  { value: 'AGRIBANK', label: 'Agribank' },
  { value: 'TECHCOMBANK', label: 'Techcombank' },
  { value: 'MBBANK', label: 'MB Bank' },
  { value: 'ACB', label: 'ACB' },
  { value: 'VPBANK', label: 'VPBank' },
  { value: 'SACOMBANK', label: 'Sacombank' },
  { value: 'TPBANK', label: 'TPBank' },
];

function PasswordInput({ id, value, onChange, autoComplete }: {
  id: string; value: string; onChange: (value: string) => void; autoComplete: string;
}) {
  const [visible, setVisible] = useState(false);
  return <div className="password-field">
    <input id={id} type={visible ? 'text' : 'password'} minLength={autoComplete === 'new-password' ? 6 : undefined} required
      autoComplete={autoComplete} value={value} onChange={event => onChange(event.target.value)} />
    <button type="button" aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
      aria-pressed={visible} onClick={() => setVisible(value => !value)}>{visible ? 'Ẩn' : 'Hiện'}</button>
  </div>;
}

function SignIn({ reason, registered }: { reason?: string; registered: boolean }) {
  const { signIn } = useSession();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
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

  return <section className="auth-main" aria-labelledby="auth-title">
    <div className="auth-lede"><span className="eyebrow">FreelaX / Vào không gian công việc</span>
      <h1 id="auth-title">Công việc<br /><em>có căn cứ.</em></h1>
      <p>Đăng nhập để tiếp tục công việc, bàn giao và theo dõi bằng chứng thanh toán theo vai trò của bạn.</p></div>
    <div className="auth-form-section">
      <div className="auth-form-heading"><span className="category-label">01 / Đăng nhập</span>
        <h2>Chào mừng trở lại.</h2><p>Vai trò Client hoặc Freelancer được xác nhận từ Marketplace sau khi đăng nhập.</p></div>
      {reason && <p className="inline-notice" role="status">{reason}</p>}
      {registered && <p className="lifecycle-success" role="status">Tài khoản đã được tạo. Đăng nhập để bắt đầu.</p>}
      <form className="auth-form" onSubmit={submit}>
        <label className="field" htmlFor="signin-email">Email</label>
        <input id="signin-email" type="email" autoComplete="username" required value={email}
          onChange={event => setEmail(event.target.value)} />
        <label className="field" htmlFor="signin-password">Mật khẩu</label>
        <PasswordInput id="signin-password" value={password} onChange={setPassword} autoComplete="current-password" />
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button" type="submit" disabled={busy}>{busy ? 'Đang xác minh…' : 'Đăng nhập'}</button>
      </form>
      <p className="auth-switch">Chưa có tài khoản? <Link to="/register">Tạo tài khoản</Link></p>
    </div>
  </section>;
}

function Register() {
  const navigate = useNavigate();
  const [role, setRole] = useState<UserType>('FREELANCER');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [taxCode, setTaxCode] = useState('');
  const [identityNumber, setIdentityNumber] = useState('');
  const [nationality, setNationality] = useState('');
  const [taxAddress, setTaxAddress] = useState('');
  const [bankCode, setBankCode] = useState<BankCode | ''>('');
  const [bankAccountNumber, setBankAccountNumber] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    const common = { displayName: displayName.trim(), email: email.trim(), password };
    const input: RegisterInput = role === 'CLIENT'
      ? { ...common, userType: 'CLIENT' }
      : { ...common, userType: 'FREELANCER', taxCode: taxCode.trim(),
        identityNumber: identityNumber.trim(), nationality: nationality.trim(),
        taxAddress: taxAddress.trim(), bankCode: bankCode as BankCode,
        bankAccountNumber: bankAccountNumber.trim() };
    try {
      await api.register(input);
      navigate('/login?registered=1', { replace: true });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể tạo tài khoản. Vui lòng thử lại.');
    } finally {
      setBusy(false);
    }
  }

  return <section className="auth-main" aria-labelledby="auth-title">
    <div className="auth-lede"><span className="eyebrow">FreelaX / Bắt đầu</span>
      <h1 id="auth-title">Cùng làm việc.<br /><em>Rõ từng bước.</em></h1>
      <p>Một tài khoản cho công việc, quy trình bàn giao và bằng chứng tài chính theo từng job.</p></div>
    <div className="auth-form-section">
      <div className="auth-form-heading"><span className="category-label">02 / Tạo tài khoản</span>
        <h2>Bạn tham gia với vai trò nào?</h2>
        <p>Vai trò này được lưu ở Marketplace. Thông tin thuế và ngân hàng chỉ cần khi đăng ký Freelancer.</p></div>
      <form className="auth-form" onSubmit={submit}>
        <fieldset className="role-choice"><legend>Chọn vai trò</legend>
          <label className="role-option"><input type="radio" name="userType" value="FREELANCER"
            checked={role === 'FREELANCER'} onChange={() => setRole('FREELANCER')} />
            <span><strong>Freelancer</strong><small>Tìm việc và phát triển sự nghiệp</small></span></label>
          <label className="role-option"><input type="radio" name="userType" value="CLIENT"
            checked={role === 'CLIENT'} onChange={() => setRole('CLIENT')} />
            <span><strong>Client</strong><small>Đăng dự án và tìm nhân sự</small></span></label>
        </fieldset>
        <div className="auth-field-grid">
          <div><label className="field" htmlFor="register-name">Họ và tên / tên hiển thị</label>
            <input id="register-name" autoComplete="name" required value={displayName}
              onChange={event => setDisplayName(event.target.value)} /></div>
          <div><label className="field" htmlFor="register-email">Email</label>
            <input id="register-email" type="email" autoComplete="email" required value={email}
              onChange={event => setEmail(event.target.value)} /></div>
        </div>
        <label className="field" htmlFor="register-password">Mật khẩu <small>Ít nhất 6 ký tự</small></label>
        <PasswordInput id="register-password" value={password} onChange={setPassword} autoComplete="new-password" />
        {role === 'FREELANCER' && <fieldset className="auth-extra">
          <legend>Thông tin bắt buộc cho Freelancer</legend>
          <p>Marketplace yêu cầu hồ sơ thuế và tài khoản nhận trước khi tạo tài khoản Freelancer.</p>
          <div className="auth-field-grid">
            <div><label className="field" htmlFor="register-tax">Mã số thuế</label>
              <input id="register-tax" required value={taxCode} onChange={event => setTaxCode(event.target.value)} /></div>
            <div><label className="field" htmlFor="register-identity">Số giấy tờ định danh</label>
              <input id="register-identity" required value={identityNumber}
                onChange={event => setIdentityNumber(event.target.value)} /></div>
            <div><label className="field" htmlFor="register-nationality">Quốc tịch</label>
              <input id="register-nationality" required value={nationality}
                onChange={event => setNationality(event.target.value)} /></div>
            <div><label className="field" htmlFor="register-address">Địa chỉ thuế</label>
              <input id="register-address" required value={taxAddress}
                onChange={event => setTaxAddress(event.target.value)} /></div>
            <div><label className="field" htmlFor="register-bank">Ngân hàng nhận</label>
              <select id="register-bank" required value={bankCode}
                onChange={event => setBankCode(event.target.value as BankCode | '')}>
                <option value="">Chọn ngân hàng</option>
                {banks.map(bank => <option key={bank.value} value={bank.value}>{bank.label}</option>)}
              </select></div>
            <div><label className="field" htmlFor="register-account">Số tài khoản nhận</label>
              <input id="register-account" required autoComplete="off" value={bankAccountNumber}
                onChange={event => setBankAccountNumber(event.target.value)} /></div>
          </div>
        </fieldset>}
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button" type="submit" disabled={busy}>{busy ? 'Đang tạo tài khoản…' : 'Tạo tài khoản'}</button>
      </form>
      <p className="auth-switch">Đã có tài khoản? <Link to="/login">Đăng nhập</Link></p>
    </div>
  </section>;
}

function FxPoster() {
  return <aside className="fx-poster" aria-label="FreelaX editorial poster">
    <div className="fx-poster-top"><strong>FREELAX / GLOBAL WORK</strong><span>ISSUE 01 — 2026</span></div>
    <div className="fx-geometry" aria-hidden="true"><span className="fx-disc" /><span className="fx-cut" /></div>
    <div className="fx-monogram" aria-hidden="true">F<span>X</span></div>
    <p>WORK.<br />PEOPLE.<br />PAYMENT.<br /><em>WITHOUT<br />BORDERS.</em></p>
    <div className="fx-poster-bottom"><span>THỰC HIỆN / BÀN GIAO / BẰNG CHỨNG</span><strong>FX↗</strong></div>
  </aside>;
}

export function AuthEntry({ reason, brand }: { reason?: string; brand: ReactNode }) {
  const location = useLocation();
  const isRegister = location.pathname === '/register';
  const registered = new URLSearchParams(location.search).get('registered') === '1';
  return <div className="public-shell">
    <a className="skip-link" href="#main">Đi tới nội dung</a>
    <header className="public-masthead">{brand}<span className="edition">MARKETPLACE / 01</span></header>
    <main id="main" className="auth-layout">
      {isRegister ? <Register /> : <SignIn reason={reason} registered={registered} />}
      <FxPoster />
    </main>
    <footer className="page-footer"><span>FreelaX / Marketplace</span><span>Công việc có người, trạng thái và bằng chứng</span></footer>
  </div>;
}
