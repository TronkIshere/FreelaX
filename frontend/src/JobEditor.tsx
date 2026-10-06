import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from './api';
import { ActionGroup, PageHeading, StatePanel } from './components';
import { jobCategories, parseJobSkills } from './jobDiscovery';
import type { Job, JobCategory, User } from './types';

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

  return <section className="job-editor">
    <PageHeading eyebrow="Công việc" title={jobId ? 'Sửa thông tin công việc' : 'Đăng công việc'}
      description={jobId ? 'Cập nhật nội dung, danh mục và kỹ năng công việc.' : 'Mô tả công việc và các điều kiện bàn giao.'} />
    <form onSubmit={submit} aria-label={jobId ? 'Sửa công việc' : 'Đăng công việc'}>
      <fieldset disabled={pending}>
        <label>Tiêu đề<input required maxLength={255} value={title} onChange={event => setTitle(event.target.value)} /></label>
        <label>Mô tả<textarea value={description} onChange={event => setDescription(event.target.value)} rows={4} /></label>
        <label>Danh mục<select required value={category} onChange={event => setCategory(event.target.value as JobCategory | '')}>
          <option value="">Chọn danh mục</option>{Object.entries(jobCategories).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></label>
        <label>Kỹ năng<input value={skills} onChange={event => setSkills(event.target.value)} aria-describedby="job-skills-help" /></label>
        <p id="job-skills-help">Tối đa 10 kỹ năng, mỗi kỹ năng 2–40 ký tự. Phân cách bằng dấu phẩy.</p>
        {!jobId && <>
          <div className="job-editor-fields">
            <label>Ngân sách (USD)<input type="number" min="0.01" step="0.01" required value={budget} onChange={event => setBudget(event.target.value)} /></label>
            <label>Hạn bàn giao<input type="datetime-local" required value={due} onChange={event => setDue(event.target.value)} /></label>
            <label>Thời hạn review (giờ)<input type="number" min="24" max="168" required value={reviewWindow} onChange={event => setReviewWindow(event.target.value)} /></label>
            <label>Số lần chỉnh sửa tối đa<input type="number" min="0" max="2" required value={maxRevisions} onChange={event => setMaxRevisions(event.target.value)} /></label>
          </div>
          <section aria-label="Sản phẩm bàn giao"><h2>Sản phẩm bàn giao</h2>
            {deliverables.map((item, index) => <div className="job-editor-requirement" key={index}>
              <label>Tên sản phẩm {index + 1}<input required maxLength={200} value={item.title}
                onChange={event => setDeliverables(values => values.map((value, i) => i === index ? { ...value, title: event.target.value } : value))} /></label>
              <label>Mô tả sản phẩm {index + 1}<textarea required maxLength={2000} value={item.description}
                onChange={event => setDeliverables(values => values.map((value, i) => i === index ? { ...value, description: event.target.value } : value))} /></label>
              {deliverables.length > 1 && <button className="text-button" type="button" onClick={() => setDeliverables(values => values.filter((_, i) => i !== index))}>Bỏ sản phẩm {index + 1}</button>}
            </div>)}
            <button className="button button-secondary" type="button" disabled={deliverables.length >= 10}
              onClick={() => setDeliverables(values => [...values, { title: '', description: '', required: true }])}>Thêm sản phẩm</button>
          </section>
          <section aria-label="Điều kiện nghiệm thu"><h2>Điều kiện nghiệm thu</h2>
            {criteria.map((item, index) => <div className="job-editor-requirement" key={index}>
              <label>Điều kiện {index + 1}<textarea required maxLength={1000} value={item.description}
                onChange={event => setCriteria(values => values.map((value, i) => i === index ? { ...value, description: event.target.value } : value))} /></label>
              {criteria.length > 1 && <button className="text-button" type="button" onClick={() => setCriteria(values => values.filter((_, i) => i !== index))}>Bỏ điều kiện {index + 1}</button>}
            </div>)}
            <button className="button button-secondary" type="button" disabled={criteria.length >= 20}
              onClick={() => setCriteria(values => [...values, { description: '', required: true }])}>Thêm điều kiện</button>
          </section>
        </>}
      </fieldset>
      {error && <p role="alert" className="form-error">{error}</p>}
      <ActionGroup><button className="button" disabled={pending} type="submit">{pending ? 'Đang lưu…' : jobId ? 'Lưu thay đổi' : 'Đăng công việc'}</button>
        <Link className="text-link" to={jobId ? '/work/' + jobId : '/work'}>Quay lại</Link></ActionGroup>
    </form>
  </section>;
}
