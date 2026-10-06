// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { JobEditor } from './JobEditor';
import { api } from './api';
import type { Job, User } from './types';
import { parseJobSkills } from './jobDiscovery';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const client: User = { id: 'client', email: 'client@example.test', displayName: 'Client', userType: 'CLIENT' };
const job: Job = { id: 'job', title: 'API', description: 'Brief', category: 'BACKEND_API', skills: ['Java'],
  status: 'OPEN', budgetUsd: 100, clientUserId: 'client', freelancerId: null, createdAt: null };
let host: HTMLDivElement, root: Root;
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
async function render(path = '/work/new', user = client) {
  await act(async () => root.render(<MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><Routes>
    <Route path="/work/new" element={<JobEditor user={user} />} />
    <Route path="/work/:jobId/edit" element={<JobEditor user={user} />} />
    <Route path="/work/:jobId" element={<p>Server job detail destination</p>} />
  </Routes></MemoryRouter>));
}
function edit(label: string, value: string) {
  const field = [...host.querySelectorAll('label')].find(el => el.textContent?.startsWith(label))!
    .querySelector('input,select,textarea') as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
  const proto = field instanceof HTMLSelectElement ? HTMLSelectElement.prototype : field instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  act(() => { Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(field, value);
    field.dispatchEvent(new Event(field instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true })); });
}
async function submit() { await act(async () => host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); }
function creation() {
  edit('Tiêu đề', 'REST API'); edit('Mô tả', 'Server scope'); edit('Danh mục', 'BACKEND_API'); edit('Kỹ năng', ' Java, Spring ');
  edit('Ngân sách', '100'); edit('Hạn bàn giao', '2030-01-01T10:00');
  edit('Tên sản phẩm 1', 'API source'); edit('Mô tả sản phẩm 1', 'Source and documentation'); edit('Điều kiện 1', 'Pass agreed tests');
}
function click(label: string) {
  const button = [...host.querySelectorAll('button')].find(el => el.textContent === label || el.getAttribute('aria-label') === label)!;
  act(() => button.click()); return button;
}

describe('Client job authoring', () => {
  it('requires an explicit category and sends real normalized job skills with the existing create contract', async () => {
    const create = vi.spyOn(api, 'createJob').mockResolvedValue(job);
    await render(); edit('Tiêu đề', 'API'); await submit();
    expect(create).not.toHaveBeenCalled(); expect(host.querySelector('[role="alert"]')?.textContent).toContain('danh mục');
    creation(); await submit();
    expect(create).toHaveBeenCalledOnce();
    expect(create).toHaveBeenCalledWith({ title: 'REST API', description: 'Server scope', category: 'BACKEND_API', skills: ['Java', 'Spring'],
      budgetUsd: 100, deliveryDueAt: new Date('2030-01-01T10:00').toISOString(), reviewWindowHours: 72, maxRevisions: 2,
      deliverables: [{ title: 'API source', description: 'Source and documentation', required: true }],
      acceptanceCriteria: [{ description: 'Pass agreed tests', required: true }] });
    expect(host.textContent).toContain('Server job detail destination');
  });
  it('updates category and job skills without changing budget, requirements or workflow', async () => {
    vi.spyOn(api, 'job').mockResolvedValue(job); const update = vi.spyOn(api, 'updateJob').mockResolvedValue(job);
    await render('/work/job/edit');
    expect((host.querySelector('select') as HTMLSelectElement).value).toBe('BACKEND_API');
    edit('Danh mục', 'MOBILE_APP'); edit('Kỹ năng', ' Flutter, Dart '); await submit();
    expect(update).toHaveBeenCalledWith('job', { title: 'API', description: 'Brief', category: 'MOBILE_APP', skills: ['Flutter', 'Dart'] });
  });
  it('prevents duplicate creation while pending and preserves data for a recoverable error', async () => {
    let reject!: (reason: Error) => void;
    const create = vi.spyOn(api, 'createJob').mockImplementation(() => new Promise((_, fail) => { reject = fail; }));
    await render(); creation(); await submit(); await submit();
    expect(create).toHaveBeenCalledOnce(); expect(host.querySelector('fieldset')?.disabled).toBe(true);
    expect([...host.querySelectorAll('fieldset')].every(field => field.disabled)).toBe(true);
    await act(async () => reject(new Error('Server unavailable')));
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('Server unavailable');
    expect((host.querySelector('input') as HTMLInputElement).value).toBe('REST API');
    expect(host.querySelector('fieldset')?.disabled).toBe(false);
  });
  it('rejects duplicate skills without submitting or discarding the draft', async () => {
    const create = vi.spyOn(api, 'createJob'); await render(); creation(); edit('Kỹ năng', 'Java, java'); await submit();
    expect(create).not.toHaveBeenCalled(); expect(host.querySelector('[role="alert"]')?.textContent).toContain('trùng');
  });
  it('does not fetch or expose authoring for a Freelancer', async () => {
    const get = vi.spyOn(api, 'job'); const create = vi.spyOn(api, 'createJob');
    await render('/work/job/edit', { ...client, userType: 'FREELANCER' });
    expect(host.querySelector('form')).toBeNull(); expect(get).not.toHaveBeenCalled(); expect(create).not.toHaveBeenCalled();
  });
  it.each([{ ...job, clientUserId: 'someone-else' }, { ...job, status: 'IN_PROGRESS' }])('blocks editing a non-owned or non-OPEN job', async row => {
    vi.spyOn(api, 'job').mockResolvedValue(row); const update = vi.spyOn(api, 'updateJob');
    await render('/work/job/edit'); expect(host.querySelector('form')).toBeNull(); expect(update).not.toHaveBeenCalled();
  });
});

describe('bounded job skills', () => {
  it('normalizes 10 valid skills while keeping them independent from profiles', () => {
    expect(parseJobSkills(' React, CSS ')).toEqual(['React', 'CSS']); expect(parseJobSkills('')).toEqual([]);
    expect(parseJobSkills(Array.from({ length: 10 }, (_, i) => 'Skill ' + i).join(','))).toHaveLength(10);
    expect(parseJobSkills('AB,' + 'x'.repeat(40))).toHaveLength(2);
  });
  it.each(['a', 'x'.repeat(41), 'React,', 'React,,CSS', 'React, react', Array.from({ length: 11 }, (_, i) => 'Skill ' + i).join(',')])(
    'rejects invalid skill input %s', value => { expect(() => parseJobSkills(value)).toThrow(); });
});

describe('editorial work order', () => {
  it('keeps four labeled sections on one page, real controls and no autofill/AI controls', async () => {
    await render();
    expect([...host.querySelectorAll('.job-editor-section h2')].map(el => el.textContent)).toEqual([
      'Nội dung công việc', 'Điều kiện thực hiện', 'Sản phẩm bàn giao', 'Điều kiện nghiệm thu',
    ]);
    expect([...host.querySelectorAll('.job-editor-section-number')].map(el => el.textContent)).toEqual(['01', '02', '03', '04']);
    expect(host.querySelectorAll('form')).toHaveLength(1);
    expect(host.querySelectorAll('input[type="number"]')).toHaveLength(3);
    expect(host.querySelectorAll('input[type="datetime-local"]')).toHaveLength(1);
    expect(host.textContent).not.toMatch(/AI|tự động điền|khuyến nghị|tốt nhất/i);
    expect((host.querySelector('select') as HTMLSelectElement).required).toBe(true);
    expect((host.querySelector('input') as HTMLInputElement).value).toBe('');
  });
  it('keeps the unsaved preview neutral even when the title and skills could imply a job family', async () => {
    const get = vi.spyOn(api, 'job'); const create = vi.spyOn(api, 'createJob');
    await render(); edit('Tiêu đề', 'React landing page'); edit('Kỹ năng', 'React, CSS');
    const preview = host.querySelector('aside')!;
    expect(preview.textContent).toContain('Chưa chọn danh mục');
    expect(preview.querySelector('.job-family-art')).toBeNull();
    expect(preview.querySelector('.job-progress-marker')).toBeNull();
    expect(preview.textContent).not.toMatch(/ứng tuyển|thanh toán|đang tuyển|freelancer|xếp hạng/i);
    expect(get).not.toHaveBeenCalled(); expect(create).not.toHaveBeenCalled();
  });
  it('shows only entered local draft values and the explicit semantic family with default variant', async () => {
    const create = vi.spyOn(api, 'createJob'); await render(); creation();
    const preview = host.querySelector('aside')!;
    expect(preview.querySelector('h2')?.textContent).toBe('REST API');
    expect([...preview.querySelectorAll('li')].map(el => el.textContent)).toEqual(['Java', 'Spring']);
    expect(preview.textContent).toContain('$100.00');
    expect(preview.textContent).toContain('2030-01-01 · 10:00');
    expect(preview.textContent).toContain('Bản nháp chưa lưu');
    expect(preview.querySelector('.job-family-art')?.getAttribute('data-family')).toBe('backend');
    expect(preview.querySelector('.job-family-art')?.getAttribute('data-visual-key')).toBe('backend:0');
    edit('Danh mục', 'WEB_FRONTEND');
    expect(preview.querySelector('.job-family-art')?.getAttribute('data-family')).toBe('web');
    expect(preview.querySelector('.job-family-art')?.getAttribute('data-visual-key')).toBe('web:0');
    edit('Danh mục', 'OTHER');
    expect(preview.querySelector('.job-family-art')?.getAttribute('data-family')).toBe('development');
    expect(create).not.toHaveBeenCalled();
  });
  it('removes an actual skill token from the draft and from the submitted normalized payload', async () => {
    const create = vi.spyOn(api, 'createJob').mockResolvedValue(job);
    await render(); creation(); click('Bỏ kỹ năng Java');
    expect(host.querySelector('[aria-label="Kỹ năng đã nhập"]')?.textContent).toBe('Spring');
    await submit(); expect(create.mock.calls[0][0].skills).toEqual(['Spring']);
  });
  it('keeps invalid comma input visible and rejected rather than silently dropping empty skills', async () => {
    const create = vi.spyOn(api, 'createJob'); await render(); creation(); edit('Kỹ năng', 'Java,,Spring');
    expect(host.querySelector('[aria-label="Kỹ năng đã nhập"]')).toBeNull();
    await submit(); expect(create).not.toHaveBeenCalled(); expect(host.querySelector('[role="alert"]')).not.toBeNull();
    expect((host.querySelector('[aria-describedby="job-skills-help"]') as HTMLInputElement).value).toBe('Java,,Spring');
  });
  it('prevents Enter in the skills control from accidentally submitting the whole job', async () => {
    await render(); creation();
    const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
    act(() => host.querySelector('[aria-describedby="job-skills-help"]')!.dispatchEvent(event));
    expect(event.defaultPrevented).toBe(true);
  });
  it('keeps deliverable add/remove real, capped at ten, with at least one row remaining', async () => {
    await render();
    expect(host.textContent).not.toContain('Bỏ sản phẩm 1');
    for (let i = 1; i < 10; i++) click('Thêm sản phẩm');
    expect(host.querySelectorAll('.job-editor-section--deliverables .job-editor-requirement')).toHaveLength(10);
    expect(click('Thêm sản phẩm').disabled).toBe(true);
    click('Bỏ sản phẩm 10');
    expect(host.querySelectorAll('.job-editor-section--deliverables .job-editor-requirement')).toHaveLength(9);
    for (let i = 9; i > 1; i--) click('Bỏ sản phẩm ' + i);
    expect(host.querySelectorAll('.job-editor-section--deliverables .job-editor-requirement')).toHaveLength(1);
    expect(host.textContent).not.toContain('Bỏ sản phẩm 1');
  });
  it('keeps acceptance add/remove real, capped at twenty, with at least one row remaining', async () => {
    await render();
    expect(host.textContent).not.toContain('Bỏ điều kiện 1');
    for (let i = 1; i < 20; i++) click('Thêm điều kiện');
    expect(host.querySelectorAll('.job-editor-section--acceptance .job-editor-requirement')).toHaveLength(20);
    expect(click('Thêm điều kiện').disabled).toBe(true);
    for (let i = 20; i > 1; i--) click('Bỏ điều kiện ' + i);
    expect(host.querySelectorAll('.job-editor-section--acceptance .job-editor-requirement')).toHaveLength(1);
    expect(host.textContent).not.toContain('Bỏ điều kiện 1');
  });
  it('submits additional trimmed obligations with required=true, not optional fake checklist state', async () => {
    const create = vi.spyOn(api, 'createJob').mockResolvedValue(job); await render(); creation();
    click('Thêm sản phẩm'); edit('Tên sản phẩm 2', ' Documentation '); edit('Mô tả sản phẩm 2', ' Setup instructions ');
    click('Thêm điều kiện'); edit('Điều kiện 2', ' Can follow setup ');
    await submit(); const payload = create.mock.calls[0][0];
    expect(payload.deliverables[1]).toEqual({ title: 'Documentation', description: 'Setup instructions', required: true });
    expect(payload.acceptanceCriteria[1]).toEqual({ description: 'Can follow setup', required: true });
  });
  it.each([
    ['Ngân sách', '0'], ['Hạn bàn giao', '2020-01-01T10:00'],
    ['Thời hạn review', '23'], ['Thời hạn review', '169'], ['Thời hạn review', '24.5'],
    ['Số lần chỉnh sửa', '-1'], ['Số lần chỉnh sửa', '3'], ['Số lần chỉnh sửa', '1.5'],
    ['Tên sản phẩm 1', ' '], ['Mô tả sản phẩm 1', ' '], ['Điều kiện 1', ' '],
  ])('preserves create validation for %s = %s', async (label, value) => {
    const create = vi.spyOn(api, 'createJob'); await render(); creation(); edit(label, value); await submit();
    expect(create).not.toHaveBeenCalled(); expect(host.querySelector('[role="alert"]')).not.toBeNull();
    expect(host.querySelectorAll('.job-editor-section')).toHaveLength(4);
  });
  it.each([[24, 0], [168, 2]])('accepts the existing review/revision boundaries %s / %s', async (review, revisions) => {
    const create = vi.spyOn(api, 'createJob').mockResolvedValue(job); await render(); creation();
    edit('Thời hạn review', String(review)); edit('Số lần chỉnh sửa', String(revisions)); await submit();
    expect(create.mock.calls[0][0]).toMatchObject({ reviewWindowHours: review, maxRevisions: revisions });
  });
  it('retains all obligation and condition inputs after a recoverable create error', async () => {
    vi.spyOn(api, 'createJob').mockRejectedValue(new Error('Try later')); await render(); creation();
    click('Thêm điều kiện'); edit('Điều kiện 2', 'Preserve me'); await submit();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('Try later');
    expect((host.querySelector('input[type="datetime-local"]') as HTMLInputElement).value).toBe('2030-01-01T10:00');
    expect([...host.querySelectorAll('textarea')].map(el => el.value)).toContain('Preserve me');
    expect([...host.querySelectorAll('textarea')].map(el => el.value)).toContain('Source and documentation');
    expect(host.querySelector('fieldset')?.disabled).toBe(false);
  });
  it('renders only four metadata controls in Edit, with informational saved context independent of draft', async () => {
    vi.spyOn(api, 'job').mockResolvedValue({ ...job, deliveryDueAt: '2030-01-01T10:00:00Z' });
    await render('/work/job/edit');
    const fields = host.querySelector('fieldset')!;
    expect([...fields.querySelectorAll('label')].map(el => el.firstChild?.textContent)).toEqual(['Tiêu đề', 'Mô tả', 'Danh mục', 'Kỹ năng']);
    expect(fields.querySelectorAll('input,select,textarea')).toHaveLength(4);
    expect(host.querySelector('input[type="number"],input[type="datetime-local"]')).toBeNull();
    expect(host.querySelector('.job-editor-section--deliverables,.job-editor-section--acceptance,.job-editor-section--conditions')).toBeNull();
    expect(host.querySelector('.job-editor-draft-summary')).toBeNull();
    const context = host.querySelector('aside')!;
    expect(context.textContent).toContain('Đang tuyển'); expect(context.textContent).toContain('$100.00');
    edit('Tiêu đề', 'Draft renamed'); edit('Danh mục', 'MOBILE_APP');
    expect(context.querySelector('h2')?.textContent).toBe('API');
    expect(context.querySelector('.job-family-art')?.getAttribute('data-family')).toBe('backend');
  });
  it.each(['AWAITING_PAYMENT', 'SUBMITTED_FOR_REVIEW', 'REVISION_REQUESTED', 'COMPLETED', 'CANCELLED'] as const)(
    'does not reveal Edit for server state %s', async status => {
      vi.spyOn(api, 'job').mockResolvedValue({ ...job, status }); await render('/work/job/edit');
      expect(host.querySelector('form')).toBeNull(); expect(host.textContent).toContain('Không thể sửa');
    });
  it('does not reveal Edit while loading, then allows retry after a failed load', async () => {
    let reject!: (reason: Error) => void;
    const get = vi.spyOn(api, 'job').mockImplementationOnce(() => new Promise((_, fail) => { reject = fail; })).mockResolvedValue(job);
    await render('/work/job/edit'); expect(host.textContent).toContain('Đang tải'); expect(host.querySelector('form')).toBeNull();
    await act(async () => reject(new Error('Network error')));
    expect(host.querySelector('form')).toBeNull(); await act(async () => click('Thử lại'));
    expect(get).toHaveBeenCalledTimes(2); expect(host.querySelector('form')).not.toBeNull();
  });
  it('keeps pending Edit metadata disabled and prevents a duplicate update', async () => {
    vi.spyOn(api, 'job').mockResolvedValue(job);
    let reject!: (reason: Error) => void;
    const update = vi.spyOn(api, 'updateJob').mockImplementation(() => new Promise((_, fail) => { reject = fail; }));
    await render('/work/job/edit'); edit('Tiêu đề', 'Retained title'); await submit(); await submit();
    expect(update).toHaveBeenCalledOnce(); expect(host.querySelector('fieldset')?.disabled).toBe(true);
    await act(async () => reject(new Error('Retry edit')));
    expect((host.querySelector('input') as HTMLInputElement).value).toBe('Retained title');
    expect(host.querySelector('fieldset')?.disabled).toBe(false);
  });
  it('keeps the new kinetic nodes decorative and controls usable with reduced motion', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
    await render();
    expect([...host.querySelectorAll('.job-editor-rays')].every(el => el.getAttribute('aria-hidden') === 'true')).toBe(true);
    expect(host.querySelector('form')?.getAttribute('aria-label')).toBe('Đăng công việc');
    expect(host.querySelector('.job-editor-submit')?.textContent).toContain('Đăng công việc');
    edit('Tiêu đề', 'Reduced motion draft'); expect(host.querySelector('aside h2')?.textContent).toBe('Reduced motion draft');
  });
  it('places the preview between identity and obligations in document/keyboard order', async () => {
    await render();
    expect([...host.querySelector('.job-editor-layout')!.children].map(el => el.className)).toEqual([
      'job-editor-identity', 'job-editor-preview', 'job-editor-obligations',
    ]);
    expect(host.querySelectorAll('.job-editor-section')).toHaveLength(4);
  });
  it('shows draft-empty summaries for blank and whitespace-only obligation rows without implying completion', async () => {
    const create = vi.spyOn(api, 'createJob'); await render();
    click('Thêm sản phẩm'); click('Thêm điều kiện'); edit('Tên sản phẩm 1', '  '); edit('Điều kiện 1', '  ');
    const preview = host.querySelector('aside')!;
    expect([...preview.querySelectorAll('.job-editor-draft-summary dd')].map(el => el.textContent)).toEqual([
      'Chưa có nội dung', 'Chưa có nội dung',
    ]);
    expect(preview.textContent).toContain('Bản nháp chưa lưu');
    expect(preview.textContent).not.toMatch(/đã lưu|hoàn tất|ready|valid|100%|đang tuyển|thanh toán/i);
    expect(create).not.toHaveBeenCalled();
  });
  it('counts only deliverables containing real local input and shows at most the first entered title', async () => {
    const create = vi.spyOn(api, 'createJob'); await render();
    click('Thêm sản phẩm'); click('Thêm sản phẩm');
    edit('Tên sản phẩm 1', ' Source code '); edit('Mô tả sản phẩm 2', 'Documentation draft');
    const summary = host.querySelector('.job-editor-draft-summary')!;
    expect(summary.textContent).toContain('2 mục đã mô tả');
    expect(summary.querySelector('.job-editor-summary-title')?.textContent).toBe('Source code');
    expect(summary.textContent).not.toContain('Documentation draft');
    edit('Tên sản phẩm 2', 'Second entered title');
    expect(summary.querySelectorAll('.job-editor-summary-title')).toHaveLength(1);
    expect(summary.textContent).not.toContain('Second entered title');
    click('Bỏ sản phẩm 2'); expect(summary.textContent).toContain('1 mục đã mô tả');
    edit('Tên sản phẩm 1', ''); expect(summary.textContent).toContain('Chưa có nội dung');
    expect(summary.querySelector('.job-editor-summary-title')).toBeNull();
    expect(create).not.toHaveBeenCalled();
  });
  it('updates acceptance counts from non-empty local criteria without repeating or saving their content', async () => {
    const create = vi.spyOn(api, 'createJob'); await render();
    click('Thêm điều kiện'); click('Thêm điều kiện');
    edit('Điều kiện 1', 'Matches agreed design'); edit('Điều kiện 2', 'Keyboard works');
    const summary = host.querySelector('.job-editor-draft-summary')!;
    expect(summary.textContent).toContain('2 điều kiện đã mô tả');
    expect(summary.textContent).not.toMatch(/Matches agreed design|Keyboard works/);
    click('Bỏ điều kiện 1'); expect(summary.textContent).toContain('1 điều kiện đã mô tả');
    edit('Điều kiện 1', '  ');
    expect([...summary.querySelectorAll('dd')].map(el => el.textContent)).toEqual(['Chưa có nội dung', 'Chưa có nội dung']);
    expect(create).not.toHaveBeenCalled();
  });
});
