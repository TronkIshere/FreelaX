import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { FileText, Plus, X } from 'lucide-react';
import { api } from './api';
import { ActionGroup, PageHeading, StatePanel } from './components';
import { jobCategories, parseJobSkills } from './jobDiscovery';
import { date, jobLabel, money } from './status';
import { KineticActionArrow, KineticCard, RoughBurst, RoughUnderline, TapeSticker } from './ui/kinetic';
import { JobThumbnail } from './ui/job-thumbnails/JobThumbnail';
import { JobCategoryPlate, jobStateTone } from './ui/JobRowIdentity';
import type { Job, JobCategory, User } from './types';

function EditorSectionHeading({ number, title, id }: { number: string; title: string; id: string }) {
  return <header className="job-editor-section-heading">
    <span className="job-editor-section-number" aria-hidden="true">{number}</span><h2 id={id}>{title}</h2>
  </header>;
}

export function JobEditor({ user }: { user: User }) {
  const { jobId } = useParams();
  const navigate = useNavigate();
  const [job, setJob] = useState<Job | null>(null);
  const [loading, setLoading] = useState(!!jobId);
  const [loadError, setLoadError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState<JobCategory | ''>('');
  const [skills, setSkills] = useState('');
  const [budget, setBudget] = useState('');
  const [due, setDue] = useState('');
  const [reviewWindow, setReviewWindow] = useState('72');
  const [maxRevisions, setMaxRevisions] = useState('2');
  const [deliverables, setDeliverables] = useState([{ title: '', description: '', required: true }]);
  const [criteria, setCriteria] = useState([{ description: '', required: true }]);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const busy = useRef(false);

  // Keep the existing comma-string state and parser as the API/validation authority.
  let draftSkills: string[] = [];
  try { draftSkills = parseJobSkills(skills); } catch { /* Invalid input stays in the control until corrected. */ }

  useEffect(() => {
    if (!jobId || user.userType !== 'CLIENT') return;
    let active = true;
    setLoading(true); setLoadError('');
    api.job(jobId).then(value => {
      if (!active) return;
      setJob(value); setTitle(value.title); setDescription(value.description ?? '');
      setCategory(value.category ?? 'OTHER'); setSkills((value.skills ?? []).join(', ')); setLoading(false);
    }, cause => { if (active) { setLoadError(cause instanceof Error ? cause.message : 'Không thể tải công việc.'); setLoading(false); } });
    return () => { active = false; };
  }, [jobId, user.userType, attempt]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy.current || user.userType !== 'CLIENT' || (jobId && (!job || job.clientUserId !== user.id || job.status !== 'OPEN'))) return;
    setError('');
    let selectedSkills: string[];
    try { selectedSkills = parseJobSkills(skills); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Kỹ năng không hợp lệ.'); return; }
    if (!category || !Object.hasOwn(jobCategories, category) || !title.trim()) {
      setError('Cần tiêu đề và danh mục công việc.'); return;
    }
    const dueDate = new Date(due);
    if (!jobId && (!Number.isFinite(Number(budget)) || Number(budget) <= 0 || !due || !Number.isFinite(dueDate.getTime())
      || dueDate.getTime() < Date.now() + 24 * 60 * 60 * 1000
      || !Number.isInteger(Number(reviewWindow)) || Number(reviewWindow) < 24 || Number(reviewWindow) > 168
      || !Number.isInteger(Number(maxRevisions)) || Number(maxRevisions) < 0 || Number(maxRevisions) > 2
      || !deliverables.length || deliverables.some(item => !item.title.trim() || !item.description.trim())
      || !criteria.length || criteria.some(item => !item.description.trim()))) {
      setError('Kiểm tra ngân sách, hạn bàn giao (ít nhất 24 giờ tới), sản phẩm và điều kiện nghiệm thu.'); return;
    }
    busy.current = true; setPending(true);
    try {
      const metadata = { title: title.trim(), description: description.trim(), category, skills: selectedSkills };
      const saved = jobId ? await api.updateJob(jobId, metadata) : await api.createJob({ ...metadata,
        budgetUsd: Number(budget), deliveryDueAt: dueDate.toISOString(), reviewWindowHours: Number(reviewWindow),
        maxRevisions: Number(maxRevisions), deliverables: deliverables.map(item => ({ ...item, title: item.title.trim(), description: item.description.trim() })),
        acceptanceCriteria: criteria.map(item => ({ ...item, description: item.description.trim() })),
      });
      navigate('/work/' + saved.id);
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Không thể lưu công việc.'); }
    finally { busy.current = false; setPending(false); }
  }

  if (user.userType !== 'CLIENT') return <StatePanel kind="error" title="Chỉ Client có thể đăng hoặc sửa công việc" body="Vai trò được xác minh từ Marketplace." />;
  if (loading) return <StatePanel kind="loading" title="Đang tải công việc" body="Đang lấy thông tin mới nhất." />;
  if (loadError) return <StatePanel kind="error" title="Không thể tải công việc" body={loadError} action={{ label: 'Thử lại', onClick: () => setAttempt(value => value + 1) }} />;
  if (jobId && (!job || job.clientUserId !== user.id || job.status !== 'OPEN')) return <StatePanel kind="error"
    title="Không thể sửa công việc này" body="Chỉ Client sở hữu công việc đang tuyển có thể sửa thông tin." />;

  const previewIdentity = { category: category || undefined }; // Unsaved preview: explicit category, default variant, no title inference.
  const draftDeliverables = deliverables.filter(item => item.title.trim() || item.description.trim());
  const draftCriteria = criteria.filter(item => item.description.trim());
  const firstDraftDeliverableTitle = draftDeliverables.find(item => item.title.trim())?.title.trim();
  const preview = <aside className="job-editor-preview" aria-label={jobId ? 'Đang chỉnh sửa' : 'Bản xem trước'}>
    <KineticCard className="job-editor-preview-paper" as="div" variant="cream">
      <TapeSticker variant="acid">{jobId ? 'Đang chỉnh sửa' : 'Bản xem trước'}</TapeSticker>
      {jobId && job ? <>
        <div className="job-editor-preview-identity"><JobThumbnail job={job} /><JobCategoryPlate job={job} /></div>
        <h2>{job.title}</h2>
        <span className={'job-progress-marker ' + jobStateTone(job.status)}>{jobLabel(job.status)}</span>
        {!!job.skills?.length && <ul className="job-editor-preview-skills">{job.skills.map(skill => <li key={skill}>{skill}</li>)}</ul>}
        <dl><div><dt>Ngân sách</dt><dd>{money(job.budgetUsd)}</dd></div>
          {job.deliveryDueAt && <div><dt>Hạn bàn giao</dt><dd>{date(job.deliveryDueAt)}</dd></div>}</dl>
      </> : <>
        <div className="job-editor-preview-identity">{category ? <><JobThumbnail job={previewIdentity} /><JobCategoryPlate job={previewIdentity} /></>
          : <><FileText size={72} strokeWidth={1.3} aria-hidden="true" /><span className="job-editor-preview-neutral">Chưa chọn danh mục</span></>}</div>
        <h2 className={title.trim() ? undefined : 'job-editor-preview-placeholder'}>{title.trim() || 'Tiêu đề công việc'}</h2>
        {!!draftSkills.length && <ul className="job-editor-preview-skills">{draftSkills.map(skill => <li key={skill}>{skill}</li>)}</ul>}
        <dl><div><dt>Ngân sách dự kiến</dt><dd>{budget && Number.isFinite(Number(budget)) && Number(budget) > 0 ? money(Number(budget)) : '—'}</dd></div>
          <div><dt>Hạn bàn giao</dt><dd>{due ? due.replace('T', ' · ') : '—'}</dd></div></dl>
        <dl className="job-editor-draft-summary">
          <div><dt>Sản phẩm bàn giao</dt><dd>{draftDeliverables.length ? `${draftDeliverables.length} mục đã mô tả` : 'Chưa có nội dung'}</dd>
            {firstDraftDeliverableTitle && <dd className="job-editor-summary-title">{firstDraftDeliverableTitle}</dd>}</div>
          <div><dt>Điều kiện nghiệm thu</dt><dd>{draftCriteria.length ? `${draftCriteria.length} điều kiện đã mô tả` : 'Chưa có nội dung'}</dd></div>
        </dl>
        <p className="job-editor-help">Bản nháp chưa lưu. Hình minh họa và tóm tắt cập nhật theo nội dung bạn đang nhập. Biến thể hình có thể đổi sau khi công việc có mã riêng.</p>
      </>}
    </KineticCard>
  </aside>;
  return <section className={'job-editor job-editor--' + (jobId ? 'edit' : 'create')}>
    <PageHeading eyebrow="Công việc" title={<>
      <span className="job-editor-title-start">{jobId ? 'Chỉnh sửa' : 'Đăng một công việc'}
        <RoughBurst className="job-editor-rays job-editor-rays--left" accent="vermilion" size={38} seedKey="job-editor-title-left" /></span>{' '}
      <span className="job-editor-title-emphasis">{jobId ? 'thông tin công việc.' : 'rõ ràng.'}
        <RoughUnderline seedKey="job-editor-title-underline" />
        <RoughBurst className="job-editor-rays job-editor-rays--right" accent="ink" size={42} seedKey="job-editor-title-right" /></span>
    </>} descriptionClassName="job-editor-supporting-copy"
      description={jobId ? 'Cập nhật nội dung, danh mục và kỹ năng khi công việc vẫn đang tuyển.'
        : 'Thiết lập phạm vi, bàn giao và điều kiện nghiệm thu trước khi mở tuyển.'} />
    <form onSubmit={submit} aria-label={jobId ? 'Sửa công việc' : 'Đăng công việc'}>
      <div className="job-editor-layout">
      <fieldset className="job-editor-identity" disabled={pending} aria-label={jobId ? 'Thông tin có thể chỉnh sửa' : 'Nội dung bản công việc'}>
        <section className="job-editor-section job-editor-section--content" aria-labelledby="editor-content-title">
        <EditorSectionHeading number="01" title="Nội dung công việc" id="editor-content-title" />
        <label className="job-editor-title-field">Tiêu đề<input required maxLength={255} value={title} onChange={event => setTitle(event.target.value)} /></label>
        <label>Mô tả<textarea className="job-editor-writing" value={description} onChange={event => setDescription(event.target.value)} rows={5} /></label>
        <label>Danh mục<select required value={category} onChange={event => setCategory(event.target.value as JobCategory | '')}>
          <option value="">Chọn danh mục</option>{Object.entries(jobCategories).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></label>
        <div className="job-editor-skills">
        <label>Kỹ năng<input value={skills} onChange={event => setSkills(event.target.value)} aria-describedby="job-skills-help"
          onKeyDown={event => { if (event.key === 'Enter') event.preventDefault(); }} /></label>
        {!!draftSkills.length && <ul className="job-editor-skill-tokens" aria-label="Kỹ năng đã nhập">
          {draftSkills.map((skill, index) => <li key={skill}>{skill}<button type="button" aria-label={'Bỏ kỹ năng ' + skill}
            onClick={() => setSkills(draftSkills.filter((_, i) => i !== index).join(', '))}><X size={16} aria-hidden="true" /></button></li>)}
        </ul>}
        <p id="job-skills-help">Tối đa 10 kỹ năng, mỗi kỹ năng 2–40 ký tự. Phân cách bằng dấu phẩy.</p>
        </div>
        </section>
      </fieldset>
      {preview}
      {!jobId && <fieldset className="job-editor-obligations" disabled={pending} aria-label="Điều kiện và bàn giao">
          <section className="job-editor-section job-editor-section--conditions" aria-labelledby="editor-conditions-title">
          <EditorSectionHeading number="02" title="Điều kiện thực hiện" id="editor-conditions-title" />
          <div className="job-editor-fields">
            <label>Ngân sách (USD)<input type="number" min="0.01" step="0.01" required value={budget} onChange={event => setBudget(event.target.value)} /></label>
            <label>Hạn bàn giao<input type="datetime-local" required value={due} onChange={event => setDue(event.target.value)} aria-describedby="job-due-help" />
              <span className="job-editor-help" id="job-due-help">Ít nhất 24 giờ từ hiện tại.</span></label>
            <label>Thời hạn review (giờ)<input type="number" min="24" max="168" required value={reviewWindow} onChange={event => setReviewWindow(event.target.value)} aria-describedby="job-review-help" />
              <span className="job-editor-help" id="job-review-help">24–168 giờ.</span></label>
            <label>Số lần chỉnh sửa tối đa<input type="number" min="0" max="2" required value={maxRevisions} onChange={event => setMaxRevisions(event.target.value)} aria-describedby="job-revisions-help" />
              <span className="job-editor-help" id="job-revisions-help">0–2 lần.</span></label>
          </div>
          </section>
          <section className="job-editor-section job-editor-section--deliverables" aria-labelledby="editor-deliverables-title">
          <EditorSectionHeading number="03" title="Sản phẩm bàn giao" id="editor-deliverables-title" />
          <p className="job-editor-section-help">1–10 sản phẩm. Ghi rõ nội dung cần bàn giao.</p>
            {deliverables.map((item, index) => <div className="job-editor-requirement" key={index}>
              <span className="job-editor-item-number" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
              <div className="job-editor-item-fields">
              <label>Tên sản phẩm {index + 1}<input required maxLength={200} value={item.title}
                onChange={event => setDeliverables(values => values.map((value, i) => i === index ? { ...value, title: event.target.value } : value))} /></label>
              <label>Mô tả sản phẩm {index + 1}<textarea required maxLength={2000} rows={3} value={item.description}
                onChange={event => setDeliverables(values => values.map((value, i) => i === index ? { ...value, description: event.target.value } : value))} /></label>
              {deliverables.length > 1 && <button className="text-button job-editor-remove" type="button" onClick={() => setDeliverables(values => values.filter((_, i) => i !== index))}>Bỏ sản phẩm {index + 1}</button>}
              </div>
            </div>)}
            <button className="button button-secondary job-editor-add" type="button" disabled={deliverables.length >= 10}
              onClick={() => setDeliverables(values => [...values, { title: '', description: '', required: true }])}><Plus size={18} aria-hidden="true" />Thêm sản phẩm</button>
          </section>
          <section className="job-editor-section job-editor-section--acceptance" aria-labelledby="editor-acceptance-title">
          <EditorSectionHeading number="04" title="Điều kiện nghiệm thu" id="editor-acceptance-title" />
          <p className="job-editor-section-help">1–20 điều kiện. Xác định cách nghiệm thu phần việc.</p>
            {criteria.map((item, index) => <div className="job-editor-requirement" key={index}>
              <span className="job-editor-item-number" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
              <div className="job-editor-item-fields">
              <label>Điều kiện {index + 1}<textarea required maxLength={1000} rows={3} value={item.description}
                onChange={event => setCriteria(values => values.map((value, i) => i === index ? { ...value, description: event.target.value } : value))} /></label>
              {criteria.length > 1 && <button className="text-button job-editor-remove" type="button" onClick={() => setCriteria(values => values.filter((_, i) => i !== index))}>Bỏ điều kiện {index + 1}</button>}
              </div>
            </div>)}
            <button className="button button-secondary job-editor-add" type="button" disabled={criteria.length >= 20}
              onClick={() => setCriteria(values => [...values, { description: '', required: true }])}><Plus size={18} aria-hidden="true" />Thêm điều kiện</button>
          </section>
      </fieldset>}
      </div>
      {error && <p role="alert" className="form-error job-editor-error">{error}</p>}
      <div className="job-editor-closing">
      <ActionGroup><button className="button job-editor-submit" disabled={pending} type="submit">{pending ? 'Đang lưu…' : jobId ? 'Lưu thay đổi' : 'Đăng công việc'} <KineticActionArrow /></button>
        <Link className="text-link" to={jobId ? '/work/' + jobId : '/work'}>Quay lại</Link></ActionGroup>
      </div>
    </form>
  </section>;
}
