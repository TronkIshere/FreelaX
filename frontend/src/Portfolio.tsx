import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import { ActionGroup, SectionHeading } from './components';
import { DisputeError } from './ContractDispute';
import { profileUrl, skillList, validatePortfolio } from './profileContracts';
import type { PortfolioInput, PortfolioItem } from './types';

const blank: PortfolioInput = { title: '', description: '', projectUrl: null, thumbnailUrl: null, skills: [], completedAt: null, sortOrder: 0 };
export function Portfolio({ userId, editable = false }: { userId: string; editable?: boolean }) {
  const [items, setItems] = useState<PortfolioItem[] | null>(null); const [error, setError] = useState<unknown>(null);
  const [draft, setDraft] = useState<PortfolioInput | null>(null); const [item, setItem] = useState<PortfolioItem | undefined>();
  const [skillsText, setSkillsText] = useState('');
  const [deleting, setDeleting] = useState<PortfolioItem | null>(null); const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false); const [reconciled, setReconciled] = useState(false); const [notice, setNotice] = useState('');
  const lock = useRef(false); const generation = useRef(0);
  async function load() {
    const current = ++generation.current;
    try { const rows = await api.portfolio(userId); if (current === generation.current) { setItems(rows); setReconciled(true); return rows; } }
    catch (e) { if (current === generation.current) setError(e); } return null;
  }
  useEffect(() => { setItems(null); void load(); return () => { generation.current++; }; }, [userId]);
  async function save() {
    if (!draft || lock.current || uncertain) return;
    const payload = { ...draft, skills: skillList(skillsText), title: draft.title.trim(), description: draft.description.trim(), projectUrl: draft.projectUrl?.trim() || null, thumbnailUrl: draft.thumbnailUrl?.trim() || null };
    const invalid = validatePortfolio(payload); if (invalid) { setError(invalid); return; }
    lock.current = true; setBusy(true); setError(null); setNotice('');
    try { await api.savePortfolio(payload, item); setDraft(null); setItem(undefined); setNotice('Đã lưu portfolio.'); await load(); }
    catch (e) { setError(e); setUncertain(true); setReconciled(false); await load(); }
    finally { lock.current = false; setBusy(false); }
  }
  async function remove() {
    if (!deleting || lock.current || uncertain) return;
    lock.current = true; setBusy(true); setError(null); setNotice('');
    try { await api.deletePortfolio(deleting.id); setDeleting(null); setNotice('Đã xóa portfolio.'); await load(); }
    catch (e) { setError(e); setUncertain(true); setReconciled(false); const rows = await load(); if (rows && !rows.some(r => r.id === deleting.id)) { setDeleting(null); setUncertain(false); setNotice('Danh sách máy chủ không còn mục này.'); } }
    finally { lock.current = false; setBusy(false); }
  }
  const change = (key: keyof PortfolioInput, value: unknown) => setDraft(d => d ? { ...d, [key]: value } : d);
  return <section className="portfolio-section"><SectionHeading title="Portfolio" description="Công trình do chủ hồ sơ giới thiệu." />
    <DisputeError error={error} />{notice && <p role="status">{notice}</p>}
    {items === null ? <p role="status">Đang đọc portfolio…</p> : !items.length ? <p>Chưa có công trình được giới thiệu.</p> : items.map(row => <article className="portfolio-record" key={row.id}>
      <h3>{row.title}</h3><p className="submission-summary">{row.description}</p>
      {!!row.skills.length && <p className="metadata">{row.skills.join(', ')}</p>}{row.completedAt && <p className="metadata">Hoàn thành: {row.completedAt}</p>}
      {(['projectUrl', 'thumbnailUrl'] as const).map(field => { const url = profileUrl(row[field]); return url ? <p key={field}><a className="text-link" href={url.href} target="_blank" rel="noopener noreferrer">{field === 'projectUrl' ? 'Xem công trình' : 'Xem ảnh giới thiệu'} · {url.hostname} ↗</a></p> : null; })}
      {editable && <ActionGroup><button className="button button-secondary" disabled={busy || uncertain} onClick={() => { setItem(row); setDraft({ ...row }); setSkillsText(row.skills.join(', ')); setDeleting(null); }}>Sửa {row.title}</button><button className="text-button" disabled={busy || uncertain} onClick={() => { setDeleting(row); setDraft(null); }}>Xóa {row.title}</button></ActionGroup>}
    </article>)}
    <button className="text-button" disabled={busy} onClick={() => void load()}>Đọc lại portfolio</button>
    {editable && <button className="button button-secondary" disabled={busy || uncertain || !items || items.length >= 12} onClick={() => { setDraft({ ...blank }); setSkillsText(''); setItem(undefined); setDeleting(null); setError(null); }}>Thêm công trình</button>}
    {editable && draft && <form className="profile-editor" onSubmit={e => { e.preventDefault(); void save(); }}><SectionHeading title={item ? 'Sửa công trình' : 'Thêm công trình'} level={3} />
      <fieldset disabled={busy || uncertain}><label>Tiêu đề công trình<input value={draft.title} maxLength={160} onChange={e => change('title', e.target.value)} /></label>
        <label>Mô tả công trình<textarea value={draft.description} maxLength={2000} onChange={e => change('description', e.target.value)} /></label>
        <label>URL công trình HTTPS<input value={draft.projectUrl || ''} maxLength={2048} onChange={e => change('projectUrl', e.target.value || null)} /></label>
        <label>URL ảnh giới thiệu HTTPS<input value={draft.thumbnailUrl || ''} maxLength={2048} onChange={e => change('thumbnailUrl', e.target.value || null)} /></label>
        <label>Kỹ năng công trình<input value={skillsText} onChange={e => setSkillsText(e.target.value)} /></label>
        <label>Ngày hoàn thành<input type="date" value={draft.completedAt || ''} onChange={e => change('completedAt', e.target.value || null)} /></label>
        <label>Thứ tự hiển thị<input type="number" min="0" max="1000" step="1" value={draft.sortOrder} onChange={e => change('sortOrder', Number(e.target.value))} /></label></fieldset>
      <ActionGroup><button className="button" disabled={busy || uncertain}>Lưu công trình</button><button type="button" className="button button-secondary" disabled={busy} onClick={() => { setDraft(null); setUncertain(false); }}>Đóng bản nháp</button></ActionGroup>
    </form>}
    {editable && deleting && <section className="feedback-document" aria-label="Xác nhận xóa portfolio"><h3>Xóa {deleting.title}?</h3><p>Mục giới thiệu sẽ bị xóa khỏi hồ sơ.</p><ActionGroup><button className="button" disabled={busy || uncertain} onClick={() => void remove()}>Xác nhận xóa công trình</button><button className="button button-secondary" disabled={busy} onClick={() => setDeleting(null)}>Giữ công trình</button></ActionGroup></section>}
    {editable && uncertain && <section className="feedback-document"><p>Kết quả chưa được xác nhận. Đối chiếu danh sách trước khi thao tác lại; tạo lại có thể gây trùng công trình.</p><button className="button button-secondary" disabled={busy} onClick={() => void load()}>Đối chiếu danh sách máy chủ</button>{reconciled && <button className="button button-secondary" onClick={() => { const current = items?.find(r => r.id === item?.id); if (item && !current) { setError('Mục đã bị xóa trên máy chủ.'); return; } if (current) setItem(current); setUncertain(false); }}>Đã đối chiếu, cho phép thử lại bản nháp</button>}</section>}
  </section>;
}
