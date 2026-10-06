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
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); });
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
