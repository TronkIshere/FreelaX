import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from './api';
import { ActionGroup, EvidenceDisclosure, FactGrid, SectionHeading, StatePanel } from './components';
import { DisputeError } from './ContractDispute';
import { Portfolio } from './Portfolio';
import { countryCodes, profileUrl, ratingUpdated, skillList, validateProfile, validateSkills } from './profileContracts';
import type { Profile, ProfilePatch, User } from './types';

export function ProfileLink({ value }: { value?: string | null }) {
  const url = profileUrl(value);
  return url ? <a className="text-link" href={url.href} target="_blank" rel="noopener noreferrer">{url.hostname} ↗</a> : null;
}
export function ProfileRecord({ profile }: { profile: Profile }) {
  const r = profile.reputation;
  return <>
    <SectionHeading title={profile.displayName} description={profile.headline || undefined} />
    {profile.bio && <p className="submission-summary">{profile.bio}</p>}
    {profileUrl(profile.avatarUrl) && <p>Ảnh đại diện: <ProfileLink value={profile.avatarUrl} /></p>}
    <FactGrid facts={[
      { label: 'Vai trò', value: profile.userType === 'CLIENT' ? 'Client' : 'Freelancer' },
      ...(profile.countryCode ? [{ label: 'Quốc gia', value: profile.countryCode }] : []),
      ...(profile.userType === 'FREELANCER' ? [
        ...(profile.hourlyRateUsd != null ? [{ label: 'Đơn giá tham khảo', value: `${profile.hourlyRateUsd} USD / giờ` }] : []),
        ...(profile.availability ? [{ label: 'Khả năng nhận việc', value: profile.availability }] : []),
      ] : profile.companyName ? [{ label: 'Công ty', value: profile.companyName }] : []),
    ]} />
    {profile.userType === 'CLIENT' && <ProfileLink value={profile.companyWebsite} />}
    {!!profile.languages?.length && <p>Ngôn ngữ: {profile.languages.map(l => `${l.code} · ${l.proficiency}`).join('; ')}</p>}
    {profile.userType === 'FREELANCER' && !!profile.skills?.length && <p>Kỹ năng: {profile.skills.join(', ')}</p>}
    <SectionHeading title="Uy tín từ hợp đồng" level={3} />
    <FactGrid facts={[
      { label: 'Hợp đồng hoàn thành', value: r.completedContracts },
      ...(r.fundedContracts != null ? [{ label: 'Hợp đồng đã cấp vốn', value: r.fundedContracts }] : []),
      { label: 'Tranh chấp', value: r.disputeCount }, { label: 'Đánh giá công bố', value: r.reviewCount },
      { label: 'Điểm trung bình', value: r.averageRating == null ? 'Chưa có đánh giá công bố' : `${r.averageRating} / 5` },
      ...(r.onTimeRate != null ? [{ label: 'Tỷ lệ đúng hạn', value: String(r.onTimeRate) }] : []),
      ...(r.paymentReleaseRate != null ? [{ label: 'Tỷ lệ release', value: String(r.paymentReleaseRate) }] : []),
      ...(r.medianReviewHours != null ? [{ label: 'Thời gian duyệt trung vị', value: `${r.medianReviewHours} giờ` }] : []),
    ]} />
    <EvidenceDisclosure summary="Nguồn hồ sơ và trạng thái xác minh">
      <p>Uy tín do Marketplace tính; cập nhật: {r.calculatedAt || 'Chưa có mốc cập nhật'}</p>
      <p>Email: {profile.verification.email}; danh tính: {profile.verification.identity}; phương thức thanh toán: {profile.verification.paymentMethod}</p>
      <p>Nguồn xác minh: {profile.verification.source}</p>
    </EvidenceDisclosure>
  </>;
}
function draftOf(p: Profile): ProfilePatch {
  return { version: p.version, displayName: p.displayName, avatarUrl: p.avatarUrl || null,
    headline: p.headline || null, bio: p.bio || null, countryCode: p.countryCode || null, languages: p.languages || [],
    ...(p.userType === 'FREELANCER' ? { hourlyRateUsd: p.hourlyRateUsd == null ? null : Number(p.hourlyRateUsd), availability: p.availability || null }
      : { companyName: p.companyName || null, companyWebsite: p.companyWebsite || null }) };
}
export function OwnProfile({ user, onReconcileUser }: { user: User; onReconcileUser?: () => Promise<void> }) {
  const [profile, setProfile] = useState<Profile | null>(null); const [draft, setDraft] = useState<ProfilePatch | null>(null);
  const [editing, setEditing] = useState(false); const [skills, setSkills] = useState('');
  const [error, setError] = useState<unknown>(null); const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false); const [stale, setStale] = useState(false); const [latest, setLatest] = useState<Profile | null>(null);
  const [identityRetry, setIdentityRetry] = useState(false); const lock = useRef(false); const generation = useRef(0);
  async function load() {
    const current = ++generation.current;
    try { const p = await api.ownProfile(); if (p.userId !== user.id || p.userType !== user.userType) throw new Error('Không thể đối chiếu chủ hồ sơ với phiên đã xác minh.'); if (current === generation.current) { setProfile(p); setError(null); } } catch (e) { if (current === generation.current) setError(e); }
  }
  useEffect(() => { setProfile(null); setDraft(null); setEditing(false); void load(); return () => { generation.current++; }; }, [user.id]);
  async function mutate(kind: 'profile' | 'skills') {
    if (lock.current || !profile || !draft || stale) return;
    const input = { ...draft, displayName: draft.displayName.trim(), headline: draft.headline?.trim() || null, bio: draft.bio?.trim() || null, avatarUrl: draft.avatarUrl?.trim() || null, languages: draft.languages.map(l => ({ code: l.code.trim(), proficiency: l.proficiency.trim() })), ...(user.userType === 'CLIENT' ? { companyName: draft.companyName?.trim() || null, companyWebsite: draft.companyWebsite?.trim() || null } : { availability: draft.availability?.trim() || null }) };
    const list = skillList(skills); const invalid = kind === 'profile' ? validateProfile(input) : validateSkills(list);
    if (invalid) { setError(invalid); return; }
    lock.current = true; setBusy(true); setError(null); setNotice('');
    let saved = false;
    try {
      const result = kind === 'profile' ? await api.patchProfile(input) : await api.replaceSkills(profile.version, list);
      saved = true; setProfile(result); setEditing(false); setDraft(draftOf(result)); setSkills(result.skills.join(', '));
      await load(); ratingUpdated([user.id]); setNotice('Đã lưu hồ sơ trên máy chủ.');
      if (kind === 'profile' && result.displayName !== user.displayName && onReconcileUser) {
        try { await onReconcileUser(); setIdentityRetry(false); } catch (e) { setIdentityRetry(true); setError(e); }
      }
    } catch (e) {
      setError(e);
      if (!saved) { setStale(true); setLatest(null); }
    } finally { lock.current = false; setBusy(false); }
  }
  async function reconcileDraft() {
    try { const p = await api.ownProfile(); setLatest(p); setProfile(p); setError(null); } catch (e) { setError(e); }
  }
  if (!profile) return <><StatePanel kind={error ? 'error' : 'loading'} title="Hồ sơ Marketplace" body="Đọc hồ sơ hiện tại từ máy chủ." action={error ? { label: 'Thử tải hồ sơ', onClick: () => void load() } : undefined} /><DisputeError error={error} /></>;
  const change = (key: keyof ProfilePatch, value: unknown) => setDraft(d => d ? { ...d, [key]: value } : d);
  return <section className="profile-document">
    <ProfileRecord profile={profile} /><Link className="text-link" to={'/profiles/' + encodeURIComponent(user.id)}>Xem hồ sơ công khai trong Marketplace</Link>
    <ActionGroup><button className="button button-secondary" disabled={busy || stale} onClick={() => { setDraft(draftOf(profile)); setSkills(profile.skills.join(', ')); setEditing(true); setLatest(null); setError(null); }}>Chỉnh sửa hồ sơ</button></ActionGroup>
    {notice && <p role="status">{notice}</p>}<DisputeError error={error} />
    {identityRetry && <button className="button button-secondary" onClick={() => { void onReconcileUser?.().then(() => { setIdentityRetry(false); setError(null); }).catch(setError); }}>Đọc lại danh tính phiên</button>}
    {editing && draft && <form className="profile-editor" onSubmit={e => { e.preventDefault(); void mutate('profile'); }}>
      <SectionHeading title="Thông tin hồ sơ" level={3} />
      <fieldset disabled={busy || stale}>
        <label>Tên hiển thị<input value={draft.displayName} maxLength={80} onChange={e => change('displayName', e.target.value)} /></label>
        <label>Tiêu đề giới thiệu<input value={draft.headline || ''} maxLength={120} onChange={e => change('headline', e.target.value || null)} /></label>
        <label>Giới thiệu<textarea value={draft.bio || ''} maxLength={2000} onChange={e => change('bio', e.target.value || null)} /></label>
        <label>URL ảnh đại diện HTTPS<input value={draft.avatarUrl || ''} maxLength={2048} onChange={e => change('avatarUrl', e.target.value || null)} /></label>
        <label>Quốc gia<select value={draft.countryCode || ''} onChange={e => change('countryCode', e.target.value || null)}><option value="">Chưa khai báo</option>{countryCodes.map(c => <option key={c}>{c}</option>)}</select></label>
        <fieldset><legend>Ngôn ngữ và trình độ</legend>{draft.languages.map((l, i) => <div className="profile-language" key={i}>
          <label>Mã ngôn ngữ {i + 1}<input maxLength={35} value={l.code} onChange={e => change('languages', draft.languages.map((v, n) => n === i ? { ...v, code: e.target.value } : v))} /></label>
          <label>Trình độ {i + 1}<input maxLength={40} value={l.proficiency} onChange={e => change('languages', draft.languages.map((v, n) => n === i ? { ...v, proficiency: e.target.value } : v))} /></label>
          <button type="button" className="text-button" onClick={() => change('languages', draft.languages.filter((_, n) => n !== i))}>Bỏ ngôn ngữ {i + 1}</button>
        </div>)}<button type="button" className="text-button" disabled={draft.languages.length >= 20} onClick={() => change('languages', [...draft.languages, { code: '', proficiency: '' }])}>Thêm ngôn ngữ</button></fieldset>
        {user.userType === 'FREELANCER' ? <>
          <label>Đơn giá USD / giờ<input type="number" min="0.01" max="999999.99" step="0.01" value={draft.hourlyRateUsd ?? ''} onChange={e => change('hourlyRateUsd', e.target.value === '' ? null : Number(e.target.value))} /></label>
          <label>Khả năng nhận việc<input maxLength={40} value={draft.availability || ''} onChange={e => change('availability', e.target.value || null)} /></label>
        </> : <><label>Tên công ty<input maxLength={120} value={draft.companyName || ''} onChange={e => change('companyName', e.target.value || null)} /></label><label>Website công ty HTTPS<input maxLength={2048} value={draft.companyWebsite || ''} onChange={e => change('companyWebsite', e.target.value || null)} /></label></>}
      </fieldset>
      {stale && <div className="feedback-document"><p>Bản nháp được giữ. Đọc hồ sơ mới nhất và xác nhận dùng phiên bản mới trước khi lưu lại.</p><button type="button" className="button button-secondary" onClick={() => void reconcileDraft()}>Đọc phiên bản mới nhất</button>{latest && <><p>Tên trên máy chủ: {latest.displayName}</p><button type="button" className="button button-secondary" onClick={() => { setDraft({ ...draft, version: latest.version }); setStale(false); }}>Giữ bản nháp, dùng phiên bản mới</button></>}</div>}
      <ActionGroup><button className="button" disabled={busy || stale}>{busy ? 'Đang lưu…' : 'Lưu hồ sơ'}</button><button type="button" className="button button-secondary" disabled={busy || stale} onClick={() => { setEditing(false); }}>Đóng bản nháp</button></ActionGroup>
      {user.userType === 'FREELANCER' && <><label>Kỹ năng (phân cách bằng dấu phẩy)<input value={skills} disabled={busy || stale} onChange={e => setSkills(e.target.value)} /></label><button type="button" className="button button-secondary" disabled={busy || stale} onClick={() => void mutate('skills')}>Lưu kỹ năng</button></>}
    </form>}
    {user.userType === 'FREELANCER' && <Portfolio userId={user.id} editable />}
  </section>;
}
