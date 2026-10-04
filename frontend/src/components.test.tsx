// @vitest-environment jsdom
import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ActionGroup, EvidenceDisclosure, FactGrid, PageHeading, SectionHeading, StatePanel } from './components';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
let host: HTMLDivElement;
let root: Root;
beforeEach(() => {
  host = document.createElement('div');
  document.body.append(host);
  root = createRoot(host);
});
afterEach(() => {
  act(() => root.unmount());
  host.remove();
});
function render(element: ReactNode) {
  act(() => root.render(element));
}

describe('P06.1 shared hierarchy', () => {
  it('keeps one page title and reserves aside space only when content exists', () => {
    const heading = { eyebrow: 'Công việc', title: 'Một hồ sơ công việc', description: 'Phạm vi đã thống nhất.' };
    render(<PageHeading {...heading} />);
    expect(host.querySelectorAll('h1')).toHaveLength(1);
    expect(host.querySelector('.page-head-with-aside')).toBeNull();
    render(<PageHeading {...heading} aside="Chờ duyệt bàn giao" />);
    expect(host.querySelectorAll('h1')).toHaveLength(1);
    expect(host.querySelector('.page-aside')?.textContent).toBe('Chờ duyệt bàn giao');
  });

  it.each(['empty', 'error', 'loading'] as const)('preserves %s announcement semantics and the retry handler', kind => {
    const retry = vi.fn();
    render(<StatePanel kind={kind} title="Hồ sơ công việc" body="Trạng thái từ Marketplace."
      action={{ label: 'Thử lại', onClick: retry }} />);
    expect(host.querySelector('section')?.getAttribute('role')).toBe(kind === 'error' ? 'alert' : 'status');
    expect(host.querySelector('section')?.getAttribute('aria-live')).toBe('polite');
    expect(host.querySelector('h2')?.textContent).toBe('Hồ sơ công việc');
    const skeleton = host.querySelector('.skeleton-stack');
    if (kind === 'loading') expect(skeleton?.getAttribute('aria-hidden')).toBe('true');
    else expect(skeleton).toBeNull();
    act(() => host.querySelector('button')!.click());
    expect(retry).toHaveBeenCalledOnce();
    expect(host.querySelector('button')?.type).toBe('button');
  });

  it('supports section and nested heading semantics without adding another page title', () => {
    render(<><SectionHeading id="terms" title="Điều khoản" description="Phạm vi đã chốt." aside="USD" />
      <SectionHeading level={3} title="Bàn giao" /></>);
    expect(host.querySelector('h2#terms')?.textContent).toBe('Điều khoản');
    expect(host.querySelector('h3')?.textContent).toBe('Bàn giao');
    expect(host.querySelector('h1')).toBeNull();
  });

  it('groups caller-provided facts as definition pairs, preserving zero and React content', () => {
    render(<FactGrid label="Điều khoản công việc" facts={[
      { label: 'Chỉnh sửa đã dùng', value: 0 },
      { label: 'Ngày giao', value: <time dateTime="2026-10-10">10/10/2026</time> },
    ]} />);
    expect(host.querySelector('dl')?.getAttribute('aria-label')).toBe('Điều khoản công việc');
    expect([...host.querySelectorAll('dt')].map(item => item.textContent)).toEqual(['Chỉnh sửa đã dùng', 'Ngày giao']);
    expect(host.querySelector('dd')?.textContent).toBe('0');
    expect(host.querySelector('dd time')?.getAttribute('datetime')).toBe('2026-10-10');
  });

  it('keeps technical evidence collapsed by default with native independent disclosures', () => {
    const untrusted = '<script>unexpected()</script>';
    render(<><EvidenceDisclosure summary="Mã và chữ ký"><code>{untrusted}</code></EvidenceDisclosure>
      <EvidenceDisclosure summary="Mã chứng từ"><code>certificate-id</code></EvidenceDisclosure></>);
    const disclosures = host.querySelectorAll('details');
    expect([...disclosures].every(item => !item.open)).toBe(true);
    expect(host.querySelector('script')).toBeNull();
    expect(host.querySelector('code')?.textContent).toBe(untrusted);
    act(() => host.querySelector('summary')!.click());
    expect(disclosures[0].open).toBe(true);
    expect(disclosures[1].open).toBe(false);
    act(() => host.querySelector('summary')!.click());
    expect(disclosures[0].open).toBe(false);
  });

  it('keeps action order, disabled state and link destination under caller control', () => {
    const primary = vi.fn();
    render(<ActionGroup label="Thao tác hồ sơ">
      <button className="button" type="button" disabled onClick={primary}>Gửi bàn giao</button>
      <a className="button button-secondary" href="/work">Quay lại</a>
    </ActionGroup>);
    expect(host.querySelector('[role="group"]')?.getAttribute('aria-label')).toBe('Thao tác hồ sơ');
    expect(host.querySelector('.action-group')?.firstElementChild?.tagName).toBe('BUTTON');
    act(() => host.querySelector('button')!.click());
    expect(primary).not.toHaveBeenCalled();
    expect(host.querySelector('a')?.getAttribute('href')).toBe('/work');
  });
});
