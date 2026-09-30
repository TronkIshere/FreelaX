// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Pagination } from './Jobs';
import { StatePanel } from './components';

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

describe('job list controls', () => {
  it('moves to the previous and next server page', () => {
    const onPage = vi.fn();
    act(() => root.render(<Pagination page={{
      currentPage: 1, pageSize: 10, totalPages: 3, totalElements: 25, data: [],
    }} onPage={onPage} />));
    const buttons = host.querySelectorAll('button');
    expect(host.textContent).toContain('Trang 2/3');
    expect(buttons[0].disabled).toBe(false);
    expect(buttons[1].disabled).toBe(false);
    act(() => buttons[0].click());
    act(() => buttons[1].click());
    expect(onPage.mock.calls).toEqual([[0], [2]]);
  });

  it('disables page controls when the server returns no results', () => {
    act(() => root.render(<Pagination page={{
      currentPage: 0, pageSize: 10, totalPages: 0, totalElements: 0, data: [],
    }} onPage={vi.fn()} />));
    expect([...host.querySelectorAll('button')].every(button => button.disabled)).toBe(true);
    expect(host.textContent).toContain('Trang 0/0');
  });

  it('shows a recoverable API error with an operable retry', () => {
    const retry = vi.fn();
    act(() => root.render(<StatePanel kind="error" title="Không thể tải công việc"
      body="Máy chủ tạm thời không phản hồi." action={{ label: 'Thử lại', onClick: retry }} />));
    expect(host.querySelector('[role="alert"]')).not.toBeNull();
    act(() => host.querySelector('button')!.click());
    expect(retry).toHaveBeenCalledOnce();
  });
});
