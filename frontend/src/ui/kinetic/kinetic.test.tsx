// @vitest-environment jsdom
import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { renderToStaticMarkup } from 'react-dom/server';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  CutPaperShape, KineticActionArrow, KineticCard, KineticLabel, KineticLedgerRow, KineticThumbnail,
  PrintTexture, RoughArrow, RoughBurst, RoughUnderline, TapeSticker,
  kineticMotion, kineticVariants, stableSeed,
} from './index';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
let host: HTMLDivElement;
let root: Root;
let reduced = false;
const listeners = new Set<() => void>();

beforeEach(() => {
  reduced = false;
  listeners.clear();
  const query = {
    get matches() { return reduced; },
    addEventListener: (_event: string, listener: () => void) => listeners.add(listener),
    removeEventListener: (_event: string, listener: () => void) => listeners.delete(listener),
    addListener: (listener: () => void) => listeners.add(listener),
    removeListener: (listener: () => void) => listeners.delete(listener),
  };
  vi.stubGlobal('matchMedia', vi.fn(() => query));
  host = document.createElement('div');
  document.body.append(host);
  root = createRoot(host);
});

afterEach(async () => {
  await act(async () => root.unmount());
  host.remove();
  vi.unstubAllGlobals();
});

async function render(children: ReactNode) {
  await act(async () => root.render(children));
}

describe('stable decorative identity', () => {
  it('keeps valid nonzero Rough seeds, including empty and Unicode input', () => {
    for (const identity of ['', 'job-id', 'Công việc', 'long-key'.repeat(100)]) {
      expect(stableSeed(identity)).toBe(stableSeed(identity));
      expect(stableSeed(identity)).toBeGreaterThan(0);
      expect(stableSeed(identity)).toBeLessThanOrEqual(0x7fffffff);
    }
    expect(stableSeed('job-a')).not.toBe(stableSeed('job-b'));
  });

  it('keeps each thumbnail attached to its identity after list reordering', async () => {
    const rows = (ids: string[]) => ids.map(id => <div key={id} data-record={id}><KineticThumbnail identity={id} /></div>);
    await render(rows(['job-a', 'job-b']));
    const before = host.querySelector('[data-record="job-a"]')!.innerHTML;
    await render(rows(['job-b', 'job-a']));
    expect(host.querySelector('[data-record="job-a"]')!.innerHTML).toBe(before);
    expect(host.querySelector('[data-record="job-b"]')!.innerHTML).not.toBe(before);
  });

  it.each(['slash', 'steps', 'split', 'fold'] as const)('supports controlled %s geometry without fake content', variant => {
    host.innerHTML = renderToStaticMarkup(<KineticThumbnail identity="real-record" variant={variant} />);
    expect(host.querySelector('.ku-thumbnail')?.getAttribute('data-variant')).toBe(variant);
    expect(host.querySelector('.ku-thumbnail')?.getAttribute('aria-hidden')).toBe('true');
    expect(host.querySelector('svg')?.getAttribute('focusable')).toBe('false');
    expect(host.textContent).toBe('');
    expect(host.querySelector('image, img, title')).toBeNull();
  });

  it.each([RoughArrow, RoughBurst, RoughUnderline])('keeps actual Rough paths stable across rerender and remount', async Mark => {
    await render(<Mark seedKey="record:a" />);
    const first = Array.from(host.querySelectorAll('path'), path => path.getAttribute('d'));
    expect(first.length).toBeGreaterThan(0);
    await render(<Mark seedKey="record:a" className="new-layout" />);
    expect(Array.from(host.querySelectorAll('path'), path => path.getAttribute('d'))).toEqual(first);
    await render(null);
    await render(<Mark seedKey="record:a" />);
    expect(Array.from(host.querySelectorAll('path'), path => path.getAttribute('d'))).toEqual(first);
    await render(<Mark seedKey="record:b" />);
    expect(Array.from(host.querySelectorAll('path'), path => path.getAttribute('d'))).not.toEqual(first);
  });

  it('renders controlled Rough variants and orientation without accessible decoration', async () => {
    await render(<><RoughArrow variant="straight" orientation="up-right" accent="cream" />
      <RoughBurst variant="burst" /><RoughUnderline variant="double" /></>);
    expect(host.querySelector('g')?.getAttribute('transform')).toBe('rotate(-25 50 30)');
    expect(host.querySelectorAll('svg[aria-hidden="true"][focusable="false"]')).toHaveLength(3);
    expect(host.querySelectorAll('path').length).toBeGreaterThan(10);
    expect(host.textContent).toBe('');
  });
});

describe('editorial composition without product assumptions', () => {
  it('keeps compact metric facts as valid dl children with a smaller hard shadow', async () => {
    await render(<dl><KineticCard as="div" depth="compact" variant="mint"><dt>Server label</dt><dd>3</dd></KineticCard></dl>);
    expect(host.querySelector('dl > div.ku-card--compact > dt')?.textContent).toBe('Server label');
    expect(host.querySelector('dl > div > dd')?.textContent).toBe('3');
    expect(kineticVariants('compactCard', true).rest).toMatchObject({ boxShadow: '3px 3px 0px #17212B' });
    expect(kineticVariants('compactCard', true).active).toMatchObject({ boxShadow: '4px 4px 0px #17212B' });
  });
  it.each(['vermilion', 'cream', 'cobalt', 'acid', 'mint'] as const)('renders a semantic %s document with the supplied content', async variant => {
    await render(<KineticCard as="section" variant={variant} aria-labelledby="document-heading">
      <h2 id="document-heading">Server title</h2><button type="button">Actual action</button>
    </KineticCard>);
    expect(host.querySelector('section')?.getAttribute('aria-labelledby')).toBe('document-heading');
    expect(host.querySelector('section')?.classList.contains('ku-surface--' + variant)).toBe(true);
    expect(host.textContent).toBe('Server titleActual action');
    const action = host.querySelector('button')!;
    await act(async () => action.focus());
    expect(document.activeElement).toBe(action);
    // The surrounding document does not add a redundant tab stop or a fake action.
    expect(host.querySelector('[tabindex]')).toBeNull();
  });

  it('keeps ledger list semantics and real action keyboard access without inventing facts', async () => {
    await render(<ul><KineticLedgerRow title={<a href="/work/server-job">Server job</a>}
      thumbnail={<KineticThumbnail identity="server-job" />} metadata="Server date" status="Server status"
      value="Server amount" attention action={<a href="/work/server-job" aria-label="Open server job"><KineticActionArrow /></a>} /></ul>);
    expect(host.querySelector('ul > li')).not.toBeNull();
    expect(host.querySelector('li')?.getAttribute('data-attention')).toBe('true');
    expect(host.textContent).toBe('Server jobServer statusServer amountServer date');
    expect(host.querySelectorAll('a')).toHaveLength(2);
    const action = host.querySelector('a[aria-label]')! as HTMLAnchorElement;
    await act(async () => action.focus());
    expect(document.activeElement).toBe(action);
    expect(host.querySelector('.ku-action-arrow')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('renders an empty ledger without fabricated status, amounts, thumbnails or actions', async () => {
    await render(<ul><KineticLedgerRow title="Actual title" /></ul>);
    expect(host.textContent).toBe('Actual title');
    expect(host.querySelector('.ku-ledger-facts, .ku-ledger-action, .ku-ledger-thumbnail')).toBeNull();
    expect(host.querySelector('[data-attention]')).toBeNull();
  });

  it('requires page-supplied tape/label text and confines opt-in texture to decoration', async () => {
    await render(<><TapeSticker>Actual label</TapeSticker><KineticLabel variant="mint">Server category</KineticLabel>
      <CutPaperShape accent="acid" variant="fold" texture /><PrintTexture /></>);
    expect(host.textContent).toBe('Actual labelServer category');
    expect(host.querySelectorAll('.ku-print-texture[aria-hidden="true"]')).toHaveLength(2);
    expect(host.querySelector('.ku-tape .ku-print-texture')).toBeNull();
    expect(host.querySelector('.ku-paper')?.getAttribute('aria-hidden')).toBe('true');
  });
});

describe('restrained motion and preference changes', () => {
  it('keeps interaction effects inside the approved distance/time limits and zero blur', () => {
    expect(kineticMotion.duration * 1000).toBeGreaterThanOrEqual(120);
    expect(kineticMotion.duration * 1000).toBeLessThanOrEqual(220);
    expect(kineticMotion.arrow).toBeGreaterThanOrEqual(3);
    expect(kineticMotion.arrow).toBeLessThanOrEqual(5);
    expect(kineticMotion.ledger).toBeGreaterThanOrEqual(2);
    expect(kineticMotion.ledger).toBeLessThanOrEqual(4);
    expect(kineticVariants('card', true).active).toMatchObject({ x: -1, y: -1, boxShadow: '8px 8px 0px #17212B' });
    for (const effect of ['card', 'ledger', 'arrow', 'thumbnail', 'sticker', 'underline'] as const) {
      const staticVariants = kineticVariants(effect, false);
      expect(staticVariants.active).toEqual(staticVariants.rest);
    }
  });

  it('disables movement for a card and its descendants when explicitly opted out', async () => {
    await render(<KineticCard motion={false}><TapeSticker>Actual label</TapeSticker><KineticActionArrow active /></KineticCard>);
    expect(host.querySelectorAll('[data-motion="on"]')).toHaveLength(0);
    expect(host.querySelectorAll('[data-motion="off"]')).toHaveLength(3);
  });

  it('respects reduced motion before any component is mounted', async () => {
    reduced = true;
    await render(<><KineticCard><TapeSticker>Label</TapeSticker><KineticActionArrow active /></KineticCard>
      <ul><KineticLedgerRow title="Title" /></ul><RoughUnderline active={false} /></>);
    expect(host.querySelectorAll('[data-motion="on"]')).toHaveLength(0);
    expect(host.querySelectorAll('[data-motion="off"]')).toHaveLength(4);
    expect(host.querySelector('.ku-moving-underline')?.getAttribute('style')).not.toContain('scaleX(0.65)');
  });

  it('turns motion off when the OS preference changes while components stay mounted', async () => {
    await render(<KineticCard><TapeSticker>Label</TapeSticker><KineticActionArrow active /></KineticCard>);
    expect(host.querySelectorAll('[data-motion="on"]')).toHaveLength(3);
    await act(async () => { reduced = true; listeners.forEach(listener => listener()); });
    expect(host.querySelectorAll('[data-motion="on"]')).toHaveLength(0);
    expect(host.querySelectorAll('[data-motion="off"]')).toHaveLength(3);
  });

  it('removes toolkit preference subscriptions after unmount', async () => {
    await render(<KineticCard motion={false}><TapeSticker>Label</TapeSticker></KineticCard>);
    // Motion may own its singleton listener; compare against the baseline after the first mount.
    await render(null);
    const baseline = listeners.size;
    await render(<KineticCard motion={false}><TapeSticker>Label</TapeSticker></KineticCard>);
    expect(listeners.size).toBeGreaterThan(baseline);
    await render(null);
    expect(listeners.size).toBe(baseline);
  });
});
